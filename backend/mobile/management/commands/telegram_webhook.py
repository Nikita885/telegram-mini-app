"""Register the webhook with Telegram (webhook mode, see mobile/webhook.py).

The server itself may be unable to reach api.telegram.org, so by default this prints a link to open
in a browser on any device where Telegram works; --set calls the API from here (e.g. via HTTPS_PROXY).
"""

import json
from urllib.parse import urlencode

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError

from mobile.telegram_bot import Bot, BotNetworkError


class Command(BaseCommand):
    help = "Print (or call with --set/--delete/--info) the Telegram setWebhook request"

    def add_arguments(self, parser):
        action = parser.add_mutually_exclusive_group()
        action.add_argument("--set", action="store_true", help="call setWebhook from this server")
        action.add_argument("--delete", action="store_true", help="call deleteWebhook (back to polling)")
        action.add_argument("--info", action="store_true", help="call getWebhookInfo")

    def handle(self, *args, **options):
        if not settings.TG_BOT_TOKEN:
            raise CommandError("TG_BOT_TOKEN is not set")
        if not settings.TG_WEBHOOK_SECRET and not (options["delete"] or options["info"]):
            raise CommandError("TG_WEBHOOK_SECRET is not set in .env")
        params = {
            "url": f"https://{settings.DOMAIN}/tg/webhook/",
            "secret_token": settings.TG_WEBHOOK_SECRET,
            "allowed_updates": json.dumps(["message", "callback_query"]),
            "drop_pending_updates": "true",
        }
        base = f"https://api.telegram.org/bot{settings.TG_BOT_TOKEN}"
        if not (options["set"] or options["delete"] or options["info"]):
            self.stdout.write(
                "Откройте эти ссылки в браузере там, где Telegram доступен (не публикуйте их — в них токен):\n"
            )
            self.stdout.write(f"  установить: {base}/setWebhook?{urlencode(params)}\n")
            self.stdout.write(f"  проверить:  {base}/getWebhookInfo\n")
            return
        bot = Bot(settings.TG_BOT_TOKEN)
        try:
            if options["set"]:
                result = bot.call("setWebhook", **{**params, "allowed_updates": ["message", "callback_query"],
                                                   "drop_pending_updates": True})
            elif options["delete"]:
                result = bot.call("deleteWebhook")
            else:
                result = bot.call("getWebhookInfo")
        except BotNetworkError as exc:
            raise CommandError(
                f"Telegram недоступен с сервера ({exc}). Запустите без флагов и откройте ссылку в браузере."
            ) from None
        self.stdout.write(json.dumps(result, ensure_ascii=False, indent=2))
