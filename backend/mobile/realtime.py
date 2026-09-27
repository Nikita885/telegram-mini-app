"""Server → app push over WebSocket. Every user has a personal channel group."""

import logging

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.db import transaction

log = logging.getLogger(__name__)


def user_group(user_id: int) -> str:
    return f"user_{user_id}"


def send_to_user(user_id: int, event: str, payload: dict):
    """Deliver after the current transaction commits, so the app never sees uncommitted rows."""

    def _send():
        layer = get_channel_layer()
        if layer is None:
            return
        try:
            async_to_sync(layer.group_send)(
                user_group(user_id), {"type": "app.event", "event": event, "payload": payload}
            )
        except Exception:  # noqa: BLE001 - realtime is best effort, REST stays the source of truth
            log.warning("realtime send failed for user %s", user_id, exc_info=True)

    transaction.on_commit(_send)
