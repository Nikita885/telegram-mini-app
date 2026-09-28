"""Telegram webhook: updates arrive here instead of being long-polled.

The reply to an update is returned in the HTTP response, so the server needs no outgoing connection
to api.telegram.org (blocked from some hosting providers). Enabled by TG_WEBHOOK_SECRET; Telegram sends
it back in X-Telegram-Bot-Api-Secret-Token on every request.
"""

import hmac
import json
import logging

from django.conf import settings
from django.http import HttpResponse, JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST

from .telegram_bot import ReplyBot, handle_update

log = logging.getLogger("bot")


@csrf_exempt
@require_POST
def telegram_webhook(request):
    secret = settings.TG_WEBHOOK_SECRET
    sent = request.headers.get("X-Telegram-Bot-Api-Secret-Token", "")
    if not secret or not hmac.compare_digest(sent, secret):
        return HttpResponse(status=403)
    try:
        update = json.loads(request.body)
    except ValueError:
        return HttpResponse(status=400)
    bot = ReplyBot()
    try:
        handle_update(bot, update)
    except Exception:  # noqa: BLE001 - answer 200 anyway, or Telegram retries the same update forever
        log.exception("webhook update %s failed", update.get("update_id"))
        return HttpResponse(status=200)
    reply = bot.reply()
    return JsonResponse(reply) if reply else HttpResponse(status=200)
