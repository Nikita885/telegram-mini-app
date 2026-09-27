"""App WebSocket: one connection per signed-in device, receives every event for its user."""

import json
from urllib.parse import parse_qs

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncWebsocketConsumer
from django.db.models import Q
from rest_framework.exceptions import AuthenticationFailed

from api.models import Dialog

from .auth import user_from_access
from .realtime import user_group


class AppConsumer(AsyncWebsocketConsumer):
    async def connect(self):
        query = parse_qs(self.scope.get("query_string", b"").decode())
        token = (query.get("token") or [""])[0]
        self.user_id = await self._authenticate(token)
        if self.user_id is None:
            await self.close(code=4401)
            return
        self.group = user_group(self.user_id)
        await self.channel_layer.group_add(self.group, self.channel_name)
        await self.accept()
        await self.send(json.dumps({"event": "ready", "payload": {"user_id": self.user_id}}))

    async def disconnect(self, code):
        if getattr(self, "group", None):
            await self.channel_layer.group_discard(self.group, self.channel_name)

    async def receive(self, text_data=None, bytes_data=None):
        try:
            data = json.loads(text_data or "{}")
        except json.JSONDecodeError:
            return
        kind = data.get("type")
        if kind == "ping":
            await self.send(json.dumps({"event": "pong", "payload": {}}))
        elif kind == "typing":
            other = await self._other_participant(data.get("dialog_id"))
            if other:
                await self.channel_layer.group_send(
                    user_group(other),
                    {"type": "app.event", "event": "typing",
                     "payload": {"dialog_id": data.get("dialog_id"), "user_id": self.user_id}},
                )

    async def app_event(self, event):
        await self.send(json.dumps({"event": event["event"], "payload": event["payload"]}, ensure_ascii=False))

    @database_sync_to_async
    def _authenticate(self, token):
        if not token:
            return None
        try:
            return user_from_access(token).pk
        except AuthenticationFailed:
            return None

    @database_sync_to_async
    def _other_participant(self, dialog_id):
        try:
            dialog = Dialog.objects.filter(
                Q(user1_id=self.user_id) | Q(user2_id=self.user_id), pk=int(dialog_id)
            ).values_list("user1_id", "user2_id").first()
        except (TypeError, ValueError):
            return None
        if not dialog:
            return None
        return dialog[1] if dialog[0] == self.user_id else dialog[0]
