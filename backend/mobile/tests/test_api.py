import io
import tempfile
from datetime import timedelta

from django.core.files.base import ContentFile
from django.core.management import call_command
from django.test import TestCase, override_settings
from django.utils import timezone
from PIL import Image, ImageDraw
from rest_framework.test import APIClient

from api.models import ClothingCategory, ClothingItem, Dialog, Message, Notification, OutfitPost, TelegramUser
from mobile import auth
from mobile.management.commands.run_bot import handle_update
from mobile.models import LoginNonce, RefreshToken

MEDIA = tempfile.mkdtemp(prefix="outfit-test-media-")


def png_bytes(size=(300, 400), shape="shirt", color=(40, 60, 160, 255)):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w, h = size
    if shape == "shirt":
        d.polygon([(w * 0.2, 0), (w * 0.8, 0), (w, h * 0.3), (w * 0.85, h * 0.35), (w * 0.85, h), (w * 0.15, h),
                   (w * 0.15, h * 0.35), (0, h * 0.3)], fill=color)
    else:
        d.rectangle([w * 0.1, 0, w * 0.9, h], fill=color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


class FakeBot:
    def __init__(self):
        self.sent = []

    def send(self, chat_id, text, button=None):
        self.sent.append((chat_id, text))


@override_settings(MEDIA_ROOT=MEDIA, TG_BOT_USERNAME="outfit_test_bot", CELERY_TASK_ALWAYS_EAGER=True)
class ApiTestCase(TestCase):
    @classmethod
    def setUpTestData(cls):
        call_command("setup_catalog", verbosity=0, stdout=io.StringIO())
        cls.alice = TelegramUser.objects.create(telegram_id=1001, username="alice", first_name="Алиса")
        cls.bob = TelegramUser.objects.create(telegram_id=1002, username="bob", first_name="Боб")
        shirt = ClothingCategory.objects.get(name="shirt")
        cls.item = ClothingItem(category=shirt, name="Рубашка", gender="unisex", style="casual", color="синий")
        cls.item.image.save("shirt.png", ContentFile(png_bytes()), save=False)
        cls.item.save()

    def client_for(self, user):
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION=f"Bearer {auth.issue_tokens(user)['access']}")
        return client

    def create_outfit(self, user, **extra):
        body = {
            "mannequin": "male",
            "description": "Синяя классика #офис",
            "layers": [{"item_id": self.item.pk, "x": 0.5, "y": 0.5, "scale": 1.0, "z": 30, "fitted": False}],
            **extra,
        }
        resp = self.client_for(user).post("/api/v1/outfits/", body, format="json")
        self.assertEqual(resp.status_code, 201, resp.content)
        return resp.json()


class AuthTests(ApiTestCase):
    def test_telegram_login_flow(self):
        client = APIClient()
        start = client.post("/api/v1/auth/telegram/start/").json()
        self.assertIn("login_", start["bot_url"])
        pending = client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(pending.status_code, 202)

        bot = FakeBot()
        handle_update(bot, {"message": {"text": f"/start login_{start['nonce']}", "chat": {"id": 5},
                                        "from": {"id": 777, "first_name": "Нина", "username": "nina"}}})
        self.assertIn("Готово", bot.sent[0][1])

        done = client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(done.status_code, 200)
        body = done.json()
        self.assertEqual(body["user"]["username"], "nina")
        me = APIClient()
        me.credentials(HTTP_AUTHORIZATION=f"Bearer {body['access']}")
        self.assertEqual(me.get("/api/v1/me/").json()["username"], "nina")
        # Nonce is single-use.
        again = client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(again.status_code, 410)

    def test_expired_nonce(self):
        nonce = LoginNonce.objects.create(user=self.alice)
        LoginNonce.objects.filter(pk=nonce.pk).update(created_at=timezone.now() - timedelta(minutes=10))
        resp = APIClient().post("/api/v1/auth/telegram/poll/", {"nonce": nonce.nonce}, format="json")
        self.assertEqual(resp.status_code, 410)
        self.assertEqual(resp.json()["error"]["code"], "nonce_expired")

    def test_refresh_rotation_and_reuse_detection(self):
        tokens = auth.issue_tokens(self.alice)
        client = APIClient()
        rotated = client.post("/api/v1/auth/refresh/", {"refresh": tokens["refresh"]}, format="json")
        self.assertEqual(rotated.status_code, 200)
        reused = client.post("/api/v1/auth/refresh/", {"refresh": tokens["refresh"]}, format="json")
        self.assertEqual(reused.status_code, 401)
        self.assertEqual(reused.json()["error"]["code"], "token_reused")
        # Reuse revokes the whole family, including the freshly rotated token.
        after = client.post("/api/v1/auth/refresh/", {"refresh": rotated.json()["refresh"]}, format="json")
        self.assertEqual(after.status_code, 401)
        self.assertFalse(RefreshToken.objects.filter(user=self.alice, revoked_at__isnull=True).exists())

    def test_requires_token(self):
        resp = APIClient().get("/api/v1/feed/")
        self.assertEqual(resp.status_code, 401)
        self.assertIn("error", resp.json())

    def test_dev_login_disabled_by_default(self):
        self.assertEqual(APIClient().post("/api/v1/auth/dev/", {"telegram_id": 1}, format="json").status_code, 404)


class OutfitTests(ApiTestCase):
    def test_create_renders_image_and_hashtags(self):
        outfit = self.create_outfit(self.alice, hashtags=["#Лето"])
        self.assertTrue(outfit["image_url"].endswith(".jpg"))
        self.assertEqual(sorted(outfit["hashtags"]), ["лето", "офис"])
        self.assertEqual(outfit["layers"][0]["item_id"], self.item.pk)
        post = OutfitPost.objects.get(pk=outfit["id"])
        with post.final_image.open("rb") as f:
            self.assertEqual(Image.open(f).size, (1080, 1440))

    def test_like_comment_counters_and_notifications(self):
        outfit = self.create_outfit(self.alice)
        bob = self.client_for(self.bob)
        self.assertEqual(bob.post(f"/api/v1/outfits/{outfit['id']}/like/").json()["likes_count"], 1)
        self.assertEqual(bob.post(f"/api/v1/outfits/{outfit['id']}/like/").json()["likes_count"], 1)
        c = bob.post(f"/api/v1/outfits/{outfit['id']}/comments/", {"text": "Круто"}, format="json").json()
        reply = self.client_for(self.alice).post(
            f"/api/v1/outfits/{outfit['id']}/comments/", {"text": "Спасибо", "parent_id": c["id"]}, format="json"
        ).json()
        self.assertEqual(reply["parent_id"], c["id"])
        detail = bob.get(f"/api/v1/outfits/{outfit['id']}/").json()
        self.assertEqual((detail["likes_count"], detail["comments_count"], detail["is_liked"]), (1, 2, True))
        kinds = set(Notification.objects.values_list("notif_type", flat=True))
        self.assertEqual(kinds, {"like", "comment", "reply"})
        self.assertEqual(bob.delete(f"/api/v1/outfits/{outfit['id']}/like/").json()["likes_count"], 0)

    def test_visibility(self):
        private = self.create_outfit(self.alice, visibility="private")
        followers = self.create_outfit(self.alice, visibility="followers")
        bob = self.client_for(self.bob)
        self.assertEqual(bob.get(f"/api/v1/outfits/{private['id']}/").status_code, 403)
        self.assertEqual(bob.get(f"/api/v1/outfits/{followers['id']}/").status_code, 403)
        bob.post(f"/api/v1/users/{self.alice.pk}/follow/")
        self.assertEqual(bob.get(f"/api/v1/outfits/{followers['id']}/").status_code, 200)
        self.assertEqual(bob.get(f"/api/v1/outfits/{private['id']}/").status_code, 403)
        ids = [o["id"] for o in bob.get("/api/v1/feed/?tab=following").json()["results"]]
        self.assertEqual(ids, [followers["id"]])

    def test_only_author_deletes(self):
        outfit = self.create_outfit(self.alice)
        self.assertEqual(self.client_for(self.bob).delete(f"/api/v1/outfits/{outfit['id']}/").status_code, 403)
        self.assertEqual(self.client_for(self.alice).delete(f"/api/v1/outfits/{outfit['id']}/").status_code, 204)

    def test_remix_and_save(self):
        original = self.create_outfit(self.alice)
        remix = self.create_outfit(self.bob, remix_of=original["id"])
        self.assertEqual(remix["remix_of"]["id"], original["id"])
        self.assertTrue(Notification.objects.filter(recipient=self.alice, notif_type="remix").exists())
        bob = self.client_for(self.bob)
        self.assertTrue(bob.post(f"/api/v1/outfits/{original['id']}/save/").json()["is_saved"])
        cols = bob.get("/api/v1/collections/").json()["results"]
        self.assertEqual(cols[0]["count"], 1)
        self.assertEqual(len(cols[0]["covers"]), 1)

    def test_feed_pagination_is_query_bounded(self):
        for _ in range(3):
            self.create_outfit(self.alice)
        bob = self.client_for(self.bob)
        with self.assertNumQueries(9):
            resp = bob.get("/api/v1/feed/?tab=for_you")
        self.assertEqual(len(resp.json()["results"]), 3)


class MessagingTests(ApiTestCase):
    def test_dialog_messages_and_share(self):
        outfit = self.create_outfit(self.alice)
        bob = self.client_for(self.bob)
        dialog = bob.post("/api/v1/dialogs/", {"user_id": self.alice.pk}, format="json").json()
        bob.post(f"/api/v1/dialogs/{dialog['id']}/messages/", {"text": "Привет"}, format="json")
        shared = bob.post(
            f"/api/v1/dialogs/{dialog['id']}/messages/", {"outfit_id": outfit["id"]}, format="json"
        ).json()
        self.assertEqual(shared["outfit"]["id"], outfit["id"])
        self.assertEqual(shared["text"], "")
        # The Mini App still sees its own share format.
        self.assertTrue(Message.objects.get(pk=shared["id"]).text.startswith("[post:"))

        alice = self.client_for(self.alice)
        dialogs = alice.get("/api/v1/dialogs/").json()["results"]
        self.assertEqual(dialogs[0]["unread_count"], 2)
        self.assertEqual(dialogs[0]["last_message"]["text"], "Образ")
        self.assertEqual(alice.get("/api/v1/counters/").json()["messages"], 2)
        alice.post(f"/api/v1/dialogs/{dialog['id']}/read/")
        self.assertEqual(alice.get("/api/v1/counters/").json()["messages"], 0)
        msgs = alice.get(f"/api/v1/dialogs/{dialog['id']}/messages/").json()["results"]
        self.assertEqual([m["is_mine"] for m in msgs], [False, False])

    def test_cannot_touch_foreign_dialog_or_message(self):
        carol = TelegramUser.objects.create(telegram_id=1003, username="carol")
        d = Dialog.objects.create(user1=self.alice, user2=self.bob)
        m = Message.objects.create(dialog=d, sender=self.alice, text="секрет")
        c = self.client_for(carol)
        self.assertEqual(c.get(f"/api/v1/dialogs/{d.pk}/messages/").status_code, 404)
        self.assertEqual(c.delete(f"/api/v1/messages/{m.pk}/").status_code, 404)
        self.assertEqual(self.client_for(self.bob).delete(f"/api/v1/messages/{m.pk}/").status_code, 403)


class StudioTests(ApiTestCase):
    def test_non_admin_forbidden(self):
        self.assertEqual(self.client_for(self.bob).get("/api/v1/studio/jobs/").status_code, 403)

    def test_job_pipeline_refit_and_publish(self):
        admin = TelegramUser.objects.create(telegram_id=1, username="boss", role="admin")
        client = self.client_for(admin)
        photo = io.BytesIO(png_bytes((600, 800)))
        photo.name = "shirt.png"
        created = client.post(
            "/api/v1/studio/jobs/", {"photo": photo, "category": "shirt", "gender": "unisex"}, format="multipart"
        )
        self.assertEqual(created.status_code, 201, created.content)
        job = client.get(f"/api/v1/studio/jobs/{created.json()['id']}/").json()
        self.assertEqual(job["status"], "review", job)
        self.assertIsNotNone(job["fitted"]["male"])
        self.assertIsNotNone(job["fitted"]["female"])
        self.assertGreater(job["fit_score"], 0.5)
        self.assertIn("shoulder_left", job["keypoints"])

        kp = job["keypoints"]["shoulder_left"]
        refit = client.post(
            f"/api/v1/studio/jobs/{job['id']}/refit/",
            {"keypoints": {"shoulder_left": [kp[0] + 5, kp[1]]}},
            format="json",
        ).json()
        self.assertEqual(refit["keypoints"]["shoulder_left"][0], kp[0] + 5)

        published = client.post(
            f"/api/v1/studio/jobs/{job['id']}/publish/", {"name": "Синяя рубашка", "style": "casual"}, format="json"
        ).json()
        self.assertEqual(published["job"]["status"], "published")
        item = published["item"]
        self.assertTrue(item["fitted"]["male"])
        # Immediately available in the constructor catalog.
        names = [i["name"] for i in self.client_for(self.bob).get("/api/v1/catalog/items/?category=shirt").json()["results"]]
        self.assertIn("Синяя рубашка", names)
