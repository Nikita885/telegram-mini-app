"""Telegram bot (long polling): confirms app sign-ins and opens the Mini App.

Flow: the app asks the API for a login request (nonce + 4-digit code) and opens
t.me/<bot>?start=login_<nonce>. The bot shows the device and the code and waits for an explicit
«Это я, войти» / «Это не я». Only a confirmation binds the Telegram account to the request; the app,
polling the API, then gets tokens. Opening a login link someone else sent therefore signs nobody in.
"""

import html
import logging
import time

import requests
from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import close_old_connections
from django.utils import timezone

from mobile.auth import cleanup_expired
from mobile.models import LoginNonce
from mobile.views import upsert_telegram_user

log = logging.getLogger("bot")

LOGIN_PREFIX = "login_"
CONFIRM = "lok:"
DECLINE = "lno:"
HOUSEKEEPING_EVERY = 3600  # seconds


class BotNetworkError(Exception):
    """Telegram API unreachable. Carries no URL: request URLs contain the bot token."""


class Bot:
    def __init__(self, token: str):
        self.base = f"https://api.telegram.org/bot{token}"
        self.session = requests.Session()

    def call(self, method: str, **params):
        try:
            resp = self.session.post(f"{self.base}/{method}", json=params, timeout=params.get("timeout", 10) + 10)
            data = resp.json()
        except (requests.RequestException, ValueError) as exc:
            # `from None`: the original exception's message includes the URL, i.e. the token.
            raise BotNetworkError(f"{method}: {exc.__class__.__name__}") from None
        if not data.get("ok"):
            raise RuntimeError(f"{method}: {data.get('description')}")
        return data["result"]

    def call_until_ok(self, method: str, **params):
        """Retry while Telegram is unreachable (e.g. right after boot or a network hiccup)."""
        delay = 3
        while True:
            try:
                return self.call(method, **params)
            except BotNetworkError as exc:
                log.warning("%s — retrying in %s s", exc, delay)
                time.sleep(delay)
                delay = min(delay * 2, 60)

    def _safe(self, method: str, **params):
        try:
            return self.call(method, **params)
        except Exception as exc:  # noqa: BLE001 - a failed reply must not stop the bot
            log.warning("%s failed: %s", method, exc)
            return None

    def send(self, chat_id, text, button=None, buttons=None):
        params = {"chat_id": chat_id, "text": text, "parse_mode": "HTML"}
        rows = buttons or ([[button]] if button else None)
        if rows:
            params["reply_markup"] = {"inline_keyboard": rows}
        self._safe("sendMessage", **params)

    def edit(self, chat_id, message_id, text):
        self._safe("editMessageText", chat_id=chat_id, message_id=message_id, text=text, parse_mode="HTML")

    def answer(self, callback_id, text=""):
        self._safe("answerCallbackQuery", callback_query_id=callback_id, text=text)


def _open_nonce(value: str):
    nonce = LoginNonce.objects.filter(nonce=value).first()
    if nonce is None or not nonce.is_open or nonce.confirmed_at is not None:
        return None
    return nonce


def _login_prompt(nonce: LoginNonce) -> str:
    device = html.escape(nonce.device) if nonce.device else "не указано"
    return (
        "<b>Вход в Outfit Share</b>\n\n"
        f"Устройство: <b>{device}</b>\n"
        f"Код: <b>{nonce.code}</b>\n\n"
        "Нажмите «Это я», только если вы сами прямо сейчас входите в приложение и видите на экране "
        "этот же код. Если ссылку вам кто-то прислал — нажмите «Это не я»: иначе он получит доступ "
        "к вашему аккаунту."
    )


def handle_start(bot: Bot, message: dict):
    chat_id = message["chat"]["id"]
    parts = (message.get("text") or "").strip().split(maxsplit=1)
    payload = parts[1] if len(parts) > 1 else ""

    if payload.startswith(LOGIN_PREFIX):
        nonce = _open_nonce(payload[len(LOGIN_PREFIX):])
        if nonce is None:
            bot.send(
                chat_id,
                "Ссылка для входа устарела. Вернитесь в приложение и нажмите «Войти через Telegram» ещё раз.",
            )
            return
        bot.send(
            chat_id,
            _login_prompt(nonce),
            buttons=[
                [{"text": "✅ Это я, войти", "callback_data": CONFIRM + nonce.nonce}],
                [{"text": "Это не я", "callback_data": DECLINE + nonce.nonce}],
            ],
        )
        return

    web_app = {"text": "Открыть Outfit Share", "web_app": {"url": f"https://{settings.DOMAIN}/authorize/"}}
    bot.send(
        chat_id,
        "Привет! Это <b>Outfit Share</b> — собирайте образы на манекене и делитесь ими.\n\n"
        "Откройте мини-приложение кнопкой ниже или установите приложение для Android.",
        button=web_app,
    )


def handle_callback(bot: Bot, callback: dict):
    data = callback.get("data") or ""
    sender = callback.get("from") or {}
    message = callback.get("message") or {}
    chat_id = (message.get("chat") or {}).get("id")
    message_id = message.get("message_id")
    if sender.get("is_bot") or not data.startswith((CONFIRM, DECLINE)):
        bot.answer(callback.get("id"))
        return

    nonce = _open_nonce(data[len(CONFIRM):])
    if nonce is None:
        bot.answer(callback.get("id"), "Запрос на вход устарел")
        if chat_id and message_id:
            bot.edit(chat_id, message_id, "Запрос на вход устарел. Начните вход в приложении заново.")
        return

    if data.startswith(DECLINE):
        LoginNonce.objects.filter(pk=nonce.pk, confirmed_at__isnull=True).update(declined_at=timezone.now())
        bot.answer(callback.get("id"), "Вход отклонён")
        if chat_id and message_id:
            bot.edit(chat_id, message_id, "Вход отклонён. Никто не получил доступ к вашему аккаунту.")
        return

    user = upsert_telegram_user(sender)
    if user.is_banned:
        bot.answer(callback.get("id"), "Аккаунт заблокирован")
        return
    confirmed = LoginNonce.objects.filter(
        pk=nonce.pk, confirmed_at__isnull=True, declined_at__isnull=True
    ).update(user=user, confirmed_at=timezone.now())
    if not confirmed:
        bot.answer(callback.get("id"), "Запрос на вход уже обработан")
        return
    bot.answer(callback.get("id"), "Готово")
    if chat_id and message_id:
        name = html.escape(user.first_name or user.username or "друг")
        bot.edit(
            chat_id,
            message_id,
            f"Готово, {name}! ✅\n\nВернитесь в приложение <b>Outfit Share</b> — вход выполнится автоматически.",
        )


def handle_update(bot: Bot, update: dict):
    if update.get("callback_query"):
        handle_callback(bot, update["callback_query"])
        return
    message = update.get("message") or {}
    sender = message.get("from")
    if not sender or sender.get("is_bot") or not (message.get("text") or "").strip().startswith("/start"):
        return
    handle_start(bot, message)


class Command(BaseCommand):
    help = "Run the Telegram bot (login confirmation + Mini App entry point)"

    def handle(self, *args, **options):
        if not settings.TG_BOT_TOKEN:
            raise CommandError("TG_BOT_TOKEN is not set")
        bot = Bot(settings.TG_BOT_TOKEN)
        me = bot.call_until_ok("getMe")
        self.stdout.write(self.style.SUCCESS(f"Bot @{me['username']} started"))
        if settings.TG_BOT_USERNAME and settings.TG_BOT_USERNAME.lower() != me["username"].lower():
            self.stderr.write(f"TG_BOT_USERNAME={settings.TG_BOT_USERNAME} differs from @{me['username']}")
        # Long polling does not work while a webhook is set (e.g. left over from an older deployment).
        if bot.call_until_ok("getWebhookInfo").get("url"):
            self.stderr.write("A webhook was set for this bot; removing it to use long polling")
            bot.call_until_ok("deleteWebhook")
        offset = None
        next_housekeeping = 0.0
        while True:
            if time.monotonic() >= next_housekeeping:
                close_old_connections()
                try:
                    cleanup_expired()
                except Exception:  # noqa: BLE001
                    log.exception("cleanup failed")
                next_housekeeping = time.monotonic() + HOUSEKEEPING_EVERY
            try:
                params = {"timeout": 30, "allowed_updates": ["message", "callback_query"]}
                if offset is not None:
                    params["offset"] = offset
                updates = bot.call("getUpdates", **params)
                close_old_connections()
                for update in updates:
                    offset = update["update_id"] + 1
                    try:
                        handle_update(bot, update)
                    except Exception:  # noqa: BLE001 - one bad update must not stop the bot
                        log.exception("update %s failed", update.get("update_id"))
            except BotNetworkError as exc:
                log.warning("%s", exc)
                time.sleep(3)
            except RuntimeError as exc:
                log.warning("%s", exc)
                time.sleep(5)
