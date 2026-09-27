from channels.testing import WebsocketCommunicator
from django.test import TransactionTestCase, override_settings

from api.models import Dialog, Message, TelegramUser
from config.asgi import application
from mobile import auth


@override_settings(CHANNEL_LAYERS={"default": {"BACKEND": "channels.layers.InMemoryChannelLayer"}})
class AppSocketTests(TransactionTestCase):
    async def test_rejects_without_token(self):
        comm = WebsocketCommunicator(application, "/ws/v1/")
        connected, _ = await comm.connect()
        self.assertFalse(connected)

    async def test_receives_new_message_event(self):
        from asgiref.sync import sync_to_async

        alice = await sync_to_async(TelegramUser.objects.create)(telegram_id=1, username="a")
        bob = await sync_to_async(TelegramUser.objects.create)(telegram_id=2, username="b")
        token = (await sync_to_async(auth.issue_tokens)(bob))["access"]
        comm = WebsocketCommunicator(application, f"/ws/v1/?token={token}")
        connected, _ = await comm.connect()
        self.assertTrue(connected)
        self.assertEqual((await comm.receive_json_from())["event"], "ready")
        dialog = await sync_to_async(Dialog.objects.create)(user1=alice, user2=bob)
        await sync_to_async(Message.objects.create)(dialog=dialog, sender=alice, text="hi")
        event = await comm.receive_json_from(timeout=2)
        self.assertEqual(event["event"], "message.new")
        self.assertEqual(event["payload"]["text"], "hi")
        await comm.disconnect()
