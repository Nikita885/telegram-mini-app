import json
from unittest import mock

from django.test import override_settings
from rest_framework.test import APIClient

from mobile.models import LoginNonce
from mobile.tests.test_api import NINA, ApiTestCase

SECRET = "hook-secret"


@override_settings(TG_WEBHOOK_SECRET=SECRET)
class WebhookTests(ApiTestCase):
    def post(self, update, secret=SECRET):
        return APIClient().post(
            "/tg/webhook/", json.dumps(update), content_type="application/json",
            HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN=secret,
        )

    def test_rejects_wrong_secret(self):
        self.assertEqual(self.post({"update_id": 1}, secret="nope").status_code, 403)
        with override_settings(TG_WEBHOOK_SECRET=""):
            self.assertEqual(self.post({"update_id": 1}).status_code, 403)

    def test_login_works_without_any_outgoing_connection(self):
        with mock.patch("requests.Session.post", side_effect=AssertionError("no outgoing calls")), \
                mock.patch("requests.post", side_effect=AssertionError("no outgoing calls")):
            app = APIClient()
            start = app.post("/api/v1/auth/telegram/start/", {"device": "Pixel 8"}, format="json").json()

            prompt = self.post({"update_id": 1, "message": {
                "text": f"/start login_{start['nonce']}", "chat": {"id": 777}, "from": NINA}}).json()
            self.assertEqual(prompt["method"], "sendMessage")
            self.assertIn(start["code"], prompt["text"])
            confirm = prompt["reply_markup"]["inline_keyboard"][0][0]["callback_data"]

            done = self.post({"update_id": 2, "callback_query": {
                "id": "cb", "data": confirm, "from": NINA,
                "message": {"message_id": 5, "chat": {"id": 777}}}}).json()
            self.assertEqual(done["method"], "editMessageText")
            self.assertIn("Готово", done["text"])

            tokens = app.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(tokens.status_code, 200)
        self.assertIn("access", tokens.json())
        self.assertIsNotNone(LoginNonce.objects.get(nonce=start["nonce"]).consumed_at)

    def test_plain_start_offers_the_mini_app(self):
        reply = self.post({"update_id": 3, "message": {"text": "/start", "chat": {"id": 777}, "from": NINA}}).json()
        self.assertEqual(reply["method"], "sendMessage")
        self.assertIn("web_app", reply["reply_markup"]["inline_keyboard"][0][0])

    def test_irrelevant_update_gets_empty_200(self):
        resp = self.post({"update_id": 4, "message": {"text": "hello", "chat": {"id": 777}, "from": NINA}})
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.content, b"")

    def test_telegram_avatar_when_telegram_is_blocked(self):
        import requests

        with override_settings(TG_BOT_TOKEN="1:x"), mock.patch("requests.get", side_effect=requests.ConnectTimeout()):
            resp = self.client_for(self.alice).post("/api/v1/me/avatar/", {"source": "telegram"}, format="json")
        self.assertEqual(resp.status_code, 503)
        self.assertEqual(resp.json()["error"]["code"], "telegram_unavailable")
