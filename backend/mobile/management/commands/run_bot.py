"""Telegram bot process.

Polling mode (default): long-polls api.telegram.org — needs outgoing access to Telegram (directly or via
HTTPS_PROXY). Webhook mode (TG_WEBHOOK_SECRET set): Telegram delivers updates to /tg/webhook/ served by
the web container and the replies go back in the HTTP response, so the server never connects to Telegram;
this process then only does housekeeping. See `manage.py telegram_webhook`.
"""

import logging
import time

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import close_old_connections

from mobile.auth import cleanup_expired
from mobile.telegram_bot import Bot, BotNetworkError, ReplyBot, handle_update  # noqa: F401 - re-exported

log = logging.getLogger("bot")

HOUSEKEEPING_EVERY = 3600  # seconds


def housekeeping():
    close_old_connections()
    try:
        cleanup_expired()
    except Exception:  # noqa: BLE001
        log.exception("cleanup failed")


class Command(BaseCommand):
    help = "Run the Telegram bot (login confirmation + Mini App entry point)"

    def handle(self, *args, **options):
        if not settings.TG_BOT_TOKEN:
            raise CommandError("TG_BOT_TOKEN is not set")
        if settings.TG_WEBHOOK_SECRET:
            self.stdout.write(self.style.SUCCESS(
                f"Webhook mode: Telegram sends updates to https://{settings.DOMAIN}/tg/webhook/ — no polling"
            ))
            while True:
                housekeeping()
                time.sleep(HOUSEKEEPING_EVERY)
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
                housekeeping()
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
