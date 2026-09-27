"""Telegram bot (long polling): confirms app sign-ins and opens the Mini App.

Flow: the app asks the API for a nonce and opens t.me/<bot>?start=login_<nonce>; the user presses
Start, this bot binds their Telegram account to the nonce; the app, polling the API, gets tokens.
"""

import logging
import time

import requests
from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone

from mobile.models import LoginNonce
from mobile.views import upsert_telegram_user

log = logging.getLogger("bot")


class Bot:
    def __init__(self, token: str):
        self.base = f"https://api.telegram.org/bot{token}"
        self.session = requests.Session()

    def call(self, method: str, **params):
        resp = self.session.post(f"{self.base}/{method}", json=params, timeout=params.get("timeout", 10) + 10)
        data = resp.json()
        if not data.get("ok"):
            raise RuntimeError(f"{method}: {data.get('description')}")
        return data["result"]

    def send(self, chat_id, text, button=None):
        params = {"chat_id": chat_id, "text": text, "parse_mode": "HTML"}
        if button:
            params["reply_markup"] = {"inline_keyboard": [[button]]}
        try:
            self.call("sendMessage", **params)
        except Exception:  # noqa: BLE001
            log.warning("sendMessage failed", exc_info=True)


def handle_update(bot: Bot, update: dict):
    message = update.get("message") or {}
    text = (message.get("text") or "").strip()
    sender = message.get("from")
    if not sender or sender.get("is_bot") or not text.startswith("/start"):
        return
    chat_id = message["chat"]["id"]
    parts = text.split(maxsplit=1)
    payload = parts[1] if len(parts) > 1 else ""
    web_app = {"text": "Открыть Outfit Share", "web_app": {"url": f"https://{settings.DOMAIN}/authorize/"}}

    if payload.startswith("login_"):
        nonce = LoginNonce.objects.filter(nonce=payload[len("login_"):]).first()
        if nonce is None or nonce.consumed_at or nonce.is_expired:
            bot.send(chat_id, "Ссылка для входа устарела. Вернитесь в приложение и нажмите «Войти через Telegram» ещё раз.")
            return
        user = upsert_telegram_user(sender)
        if user.is_banned:
            bot.send(chat_id, "Аккаунт заблокирован.")
            return
        nonce.user = user
        nonce.confirmed_at = timezone.now()
        nonce.save(update_fields=["user", "confirmed_at"])
        bot.send(
            chat_id,
            f"Готово, {user.first_name or user.username or 'друг'}! ✅\n\n"
            "Вернитесь в приложение <b>Outfit Share</b> — вход выполнится автоматически.",
        )
        return

    bot.send(
        chat_id,
        "Привет! Это <b>Outfit Share</b> — собирайте образы на манекене и делитесь ими.\n\n"
        "Откройте мини-приложение кнопкой ниже или установите приложение для Android.",
        button=web_app,
    )


class Command(BaseCommand):
    help = "Run the Telegram bot (login confirmation + Mini App entry point)"

    def handle(self, *args, **options):
        if not settings.TG_BOT_TOKEN:
            raise CommandError("TG_BOT_TOKEN is not set")
        bot = Bot(settings.TG_BOT_TOKEN)
        me = bot.call("getMe")
        self.stdout.write(self.style.SUCCESS(f"Bot @{me['username']} started"))
        if settings.TG_BOT_USERNAME and settings.TG_BOT_USERNAME.lower() != me["username"].lower():
            self.stderr.write(f"TG_BOT_USERNAME={settings.TG_BOT_USERNAME} differs from @{me['username']}")
        offset = None
        while True:
            try:
                params = {"timeout": 30, "allowed_updates": ["message"]}
                if offset is not None:
                    params["offset"] = offset
                for update in bot.call("getUpdates", **params):
                    offset = update["update_id"] + 1
                    try:
                        handle_update(bot, update)
                    except Exception:  # noqa: BLE001 - one bad update must not stop the bot
                        log.exception("update %s failed", update.get("update_id"))
            except requests.RequestException:
                time.sleep(3)
            except RuntimeError as exc:
                log.warning("%s", exc)
                time.sleep(5)
