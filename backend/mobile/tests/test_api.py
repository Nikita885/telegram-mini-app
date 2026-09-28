import io
import tempfile
from datetime import timedelta

from django.core.files.base import ContentFile
from django.core.management import call_command
from django.test import Client, TestCase, override_settings
from django.utils import timezone
from PIL import Image, ImageDraw
from rest_framework.test import APIClient

from api.models import (
    ClothingCategory,
    ClothingItem,
    Dialog,
    Follow,
    Message,
    Notification,
    OutfitPost,
    TelegramUser,
)
from mobile import auth, services
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
        self.edits = []
        self.answers = []

    def send(self, chat_id, text, button=None, buttons=None):
        self.sent.append((chat_id, text, buttons))

    def edit(self, chat_id, message_id, text):
        self.edits.append((chat_id, message_id, text))

    def answer(self, callback_id, text=""):
        self.answers.append(text)


NINA = {"id": 777, "first_name": "Нина", "username": "nina"}


def start_update(nonce, sender=NINA):
    return {"message": {"text": f"/start login_{nonce}", "chat": {"id": sender["id"]}, "from": sender}}


def button_update(bot, label, sender=NINA):
    """Press the inline button whose text contains `label` on the last message the bot sent."""
    data = next(b["callback_data"] for row in bot.sent[-1][2] for b in row if label in b["text"])
    return {"callback_query": {"id": "cb1", "data": data, "from": sender,
                               "message": {"message_id": 10, "chat": {"id": sender["id"]}}}}


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
        start = client.post("/api/v1/auth/telegram/start/", {"device": "Pixel 8 · Android 15"}, format="json").json()
        self.assertIn("login_", start["bot_url"])
        self.assertRegex(start["code"], r"^\d{4}$")
        pending = client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(pending.status_code, 202)

        bot = FakeBot()
        handle_update(bot, start_update(start["nonce"]))
        # The bot asks for confirmation, repeating the device and the code shown in the app.
        prompt = bot.sent[0][1]
        self.assertIn("Pixel 8 · Android 15", prompt)
        self.assertIn(start["code"], prompt)
        still = client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(still.status_code, 202)

        handle_update(bot, button_update(bot, "Это я"))
        self.assertIn("Готово", bot.edits[-1][2])
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

    def test_opening_a_login_link_alone_signs_nobody_in(self):
        """Phishing: an attacker sends someone their login link; pressing Start must not log them in."""
        attacker = APIClient()
        start = attacker.post("/api/v1/auth/telegram/start/").json()
        handle_update(FakeBot(), start_update(start["nonce"], {"id": 1001, "first_name": "Алиса"}))
        resp = attacker.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(resp.status_code, 202)
        self.assertNotIn("access", resp.json())

    def test_declined_login(self):
        client = APIClient()
        start = client.post("/api/v1/auth/telegram/start/").json()
        bot = FakeBot()
        handle_update(bot, start_update(start["nonce"]))
        handle_update(bot, button_update(bot, "не я"))
        resp = client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json")
        self.assertEqual(resp.status_code, 410)
        self.assertEqual(resp.json()["error"]["code"], "login_declined")
        # A declined request cannot be confirmed afterwards.
        handle_update(bot, {"callback_query": {"id": "cb2", "data": "lok:" + start["nonce"], "from": NINA,
                                               "message": {"message_id": 10, "chat": {"id": 777}}}})
        self.assertEqual(
            client.post("/api/v1/auth/telegram/poll/", {"nonce": start["nonce"]}, format="json").status_code, 410
        )

    def test_device_name_is_escaped_in_bot_message(self):
        start = APIClient().post("/api/v1/auth/telegram/start/", {"device": "<b>x</b>"}, format="json").json()
        bot = FakeBot()
        handle_update(bot, start_update(start["nonce"]))
        self.assertIn("&lt;b&gt;x&lt;/b&gt;", bot.sent[0][1])

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
        # Outside the grace window a second use of the old token is treated as a leak.
        RefreshToken.objects.filter(rotated_at__isnull=False).update(rotated_at=timezone.now() - timedelta(minutes=5))
        reused = client.post("/api/v1/auth/refresh/", {"refresh": tokens["refresh"]}, format="json")
        self.assertEqual(reused.status_code, 401)
        self.assertEqual(reused.json()["error"]["code"], "token_reused")
        # Reuse revokes the whole family, including the freshly rotated token.
        after = client.post("/api/v1/auth/refresh/", {"refresh": rotated.json()["refresh"]}, format="json")
        self.assertEqual(after.status_code, 401)
        self.assertFalse(RefreshToken.objects.filter(user=self.alice, revoked_at__isnull=True).exists())

    def test_refresh_retry_within_grace_window(self):
        """The response with the new pair got lost: retrying with the old token must not log out everywhere."""
        other_device = auth.issue_tokens(self.alice)
        tokens = auth.issue_tokens(self.alice)
        client = APIClient()
        first = client.post("/api/v1/auth/refresh/", {"refresh": tokens["refresh"]}, format="json")
        retry = client.post("/api/v1/auth/refresh/", {"refresh": tokens["refresh"]}, format="json")
        self.assertEqual(first.status_code, 200)
        self.assertEqual(retry.status_code, 200)
        ok = client.post("/api/v1/auth/refresh/", {"refresh": other_device["refresh"]}, format="json")
        self.assertEqual(ok.status_code, 200)

    def test_reuse_after_logout_is_not_graced(self):
        tokens = auth.issue_tokens(self.alice)
        client = APIClient()
        client.post("/api/v1/auth/logout/", {"refresh": tokens["refresh"]}, format="json")
        resp = client.post("/api/v1/auth/refresh/", {"refresh": tokens["refresh"]}, format="json")
        self.assertEqual(resp.json()["error"]["code"], "token_reused")

    def test_cleanup_expired(self):
        old = LoginNonce.objects.create()
        LoginNonce.objects.filter(pk=old.pk).update(created_at=timezone.now() - timedelta(days=2))
        fresh = LoginNonce.objects.create()
        auth.issue_tokens(self.alice)
        RefreshToken.objects.update(expires_at=timezone.now() - timedelta(seconds=1))
        auth.cleanup_expired()
        self.assertEqual(list(LoginNonce.objects.values_list("pk", flat=True)), [fresh.pk])
        self.assertFalse(RefreshToken.objects.exists())

    def test_throttle_ignores_client_supplied_forwarded_for(self):
        from rest_framework.throttling import ScopedRateThrottle
        from rest_framework.test import APIRequestFactory

        factory = APIRequestFactory()
        throttle = ScopedRateThrottle()
        a = factory.post("/", HTTP_X_FORWARDED_FOR="1.1.1.1, 203.0.113.7", REMOTE_ADDR="127.0.0.1")
        b = factory.post("/", HTTP_X_FORWARDED_FOR="2.2.2.2, 203.0.113.7", REMOTE_ADDR="127.0.0.1")
        self.assertEqual(throttle.get_ident(a), "203.0.113.7")
        self.assertEqual(throttle.get_ident(a), throttle.get_ident(b))

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
        # Not enumerable by id: private outfits must not be reachable by guessing the media URL.
        self.assertRegex(post.final_image.name, r"^outfits/outfit_[\w-]{22}\.jpg$")

    def test_rename_outfit_images_command(self):
        outfit = self.create_outfit(self.alice)
        post = OutfitPost.objects.get(pk=outfit["id"])
        storage = post.final_image.storage
        legacy = storage.save(f"outfits/outfit_{post.pk}.jpg", ContentFile(b"jpeg"))
        OutfitPost.objects.filter(pk=post.pk).update(final_image=legacy)
        d = services.get_or_create_dialog(self.alice, self.bob)
        msg = Message.objects.create(dialog=d, sender=self.alice, text=f'[post:{{"img": "/media/{legacy}"}}]')
        call_command("rename_outfit_images", stdout=io.StringIO())
        post.refresh_from_db()
        msg.refresh_from_db()
        self.assertRegex(post.final_image.name, r"^outfits/outfit_[\w-]{22}\.jpg$")
        self.assertFalse(storage.exists(legacy))
        self.assertIn(post.final_image.name, msg.text)

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

    def test_for_you_pages_do_not_repeat_or_skip(self):
        authors = [TelegramUser.objects.create(telegram_id=2000 + i, first_name=f"a{i}") for i in range(5)]
        created = {self.create_outfit(authors[i % 5])["id"] for i in range(45)}
        bob = self.client_for(self.bob)
        seen, cursor = [], ""
        while True:
            body = bob.get("/api/v1/feed/", {"tab": "for_you", "page": cursor}).json()
            seen += [o["id"] for o in body["results"]]
            # Engagement changes while scrolling must not reshuffle the pages already handed out.
            OutfitPost.objects.filter(pk=seen[-1]).update(likes_count=1000)
            if not body["next"]:
                break
            cursor = body["next"]
        self.assertEqual(len(seen), len(set(seen)))
        self.assertEqual(set(seen), created)

    def test_feed_query_count_does_not_grow_with_pool(self):
        for _ in range(3):
            self.create_outfit(self.alice)
        bob = self.client_for(self.bob)
        with self.assertNumQueries(10):
            bob.get("/api/v1/feed/?tab=for_you")
        for _ in range(25):
            self.create_outfit(self.alice)
        with self.assertNumQueries(10):
            resp = bob.get("/api/v1/feed/?tab=for_you")
        self.assertEqual(len(resp.json()["results"]), 20)

    def test_similar_overlap_ignores_author_followers(self):
        carol = TelegramUser.objects.create(telegram_id=1003, first_name="Кэрол")
        for i in range(5):
            follower = TelegramUser.objects.create(telegram_id=3000 + i, first_name=f"f{i}")
            Follow.objects.create(follower=follower, following=self.bob)
        second = ClothingItem(category=self.item.category, name="Вторая", gender="unisex")
        second.image.save("second.png", ContentFile(png_bytes()), save=False)
        second.save()
        both = [{"item_id": self.item.pk, "x": 0.5, "y": 0.5, "scale": 1.0, "z": 1},
                {"item_id": second.pk, "x": 0.5, "y": 0.6, "scale": 1.0, "z": 2}]
        base = self.create_outfit(self.alice, layers=both)
        two_matches = self.create_outfit(carol, layers=both)
        one_match = self.create_outfit(self.bob)  # popular author, one shared item
        resp = self.client_for(self.alice).get(f"/api/v1/outfits/{base['id']}/similar/")
        ids = [o["id"] for o in resp.json()["results"]]
        self.assertEqual(ids, [two_matches["id"], one_match["id"]])

    def test_bad_parameters_are_400(self):
        c = self.client_for(self.alice)
        for url in ("/api/v1/feed/?since=abc", "/api/v1/collections/?user_id=abc"):
            resp = c.get(url)
            self.assertEqual(resp.status_code, 400, url)
            self.assertEqual(resp.json()["error"]["code"], "invalid_parameter")

    def test_similar_items_of_unpublished_item_is_404(self):
        hidden = ClothingItem.objects.create(
            category=self.item.category, name="Черновик", gender="unisex", is_published=False,
            image=self.item.image.name,
        )
        resp = self.client_for(self.alice).get(f"/api/v1/catalog/items/{hidden.pk}/similar/")
        self.assertEqual(resp.status_code, 404)


class ProfileTests(ApiTestCase):
    def test_telegram_owner_wins_the_username(self):
        bob = self.client_for(self.bob)
        self.assertEqual(bob.patch("/api/v1/me/", {"username": "real_nina"}, format="json").status_code, 200)
        self.assertEqual(
            self.client_for(self.alice).patch("/api/v1/me/", {"username": "Real_Nina"}, format="json").status_code, 400
        )
        # The real @real_nina signs in through Telegram: the squatter loses the name.
        nina = TelegramUser.from_telegram({"id": 4242, "first_name": "Нина", "username": "real_nina"})
        self.bob.refresh_from_db()
        self.assertEqual(nina.username, "real_nina")
        self.assertIsNone(self.bob.username)

    def test_empty_telegram_fields_do_not_erase_app_profile(self):
        body = {"username": "alice_style", "first_name": "Алиса"}
        self.client_for(self.alice).patch("/api/v1/me/", body, format="json")
        TelegramUser.from_telegram({"id": self.alice.telegram_id})
        self.alice.refresh_from_db()
        self.assertEqual((self.alice.username, self.alice.first_name), ("alice_style", "Алиса"))


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


    def test_delete_dialog_only_for_myself(self):
        d = services.get_or_create_dialog(self.alice, self.bob)
        services.send_message(self.bob, d, "привет")
        alice, bob = self.client_for(self.alice), self.client_for(self.bob)
        self.assertEqual(alice.delete(f"/api/v1/dialogs/{d.pk}/").status_code, 204)
        self.assertEqual(alice.get("/api/v1/dialogs/").json()["results"], [])
        self.assertEqual(alice.get(f"/api/v1/dialogs/{d.pk}/messages/").json()["results"], [])
        self.assertEqual(alice.get("/api/v1/counters/").json()["messages"], 0)
        # Bob still has the whole conversation.
        self.assertEqual(len(bob.get(f"/api/v1/dialogs/{d.pk}/messages/").json()["results"]), 1)
        self.assertEqual(len(bob.get("/api/v1/dialogs/").json()["results"]), 1)
        # A new message brings the dialog back for Alice, without the old history.
        bob.post(f"/api/v1/dialogs/{d.pk}/messages/", {"text": "ты тут?"}, format="json")
        rows = alice.get("/api/v1/dialogs/").json()["results"]
        self.assertEqual((len(rows), rows[0]["unread_count"]), (1, 1))
        texts = [m["text"] for m in alice.get(f"/api/v1/dialogs/{d.pk}/messages/").json()["results"]]
        self.assertEqual(texts, ["ты тут?"])

    def test_messages_are_purged_when_both_delete(self):
        d = services.get_or_create_dialog(self.alice, self.bob)
        services.send_message(self.bob, d, "привет")
        self.client_for(self.alice).delete(f"/api/v1/dialogs/{d.pk}/")
        self.assertEqual(Message.objects.filter(dialog=d).count(), 1)
        self.client_for(self.bob).delete(f"/api/v1/dialogs/{d.pk}/")
        self.assertEqual(Message.objects.filter(dialog=d).count(), 0)

    def test_pin_is_per_user(self):
        d = services.get_or_create_dialog(self.alice, self.bob)
        services.send_message(self.bob, d, "привет")
        alice, bob = self.client_for(self.alice), self.client_for(self.bob)
        self.assertTrue(alice.post(f"/api/v1/dialogs/{d.pk}/pin/", {"pinned": "true"}, format="json").json()["pinned"])
        self.assertTrue(alice.get("/api/v1/dialogs/").json()["results"][0]["pinned"])
        self.assertFalse(bob.get("/api/v1/dialogs/").json()["results"][0]["pinned"])
        unpinned = alice.post(f"/api/v1/dialogs/{d.pk}/pin/", {"pinned": "false"}, format="json")
        self.assertFalse(unpinned.json()["pinned"])


class MiniAppTests(ApiTestCase):
    def mini_app_client(self, user):
        client = Client()
        session = client.session
        session["telegram_id"] = user.telegram_id
        session.save()
        return client

    def test_cross_site_requests_are_blocked(self):
        d = services.get_or_create_dialog(self.alice, self.bob)
        client = self.mini_app_client(self.alice)
        url = f"/api/dialogs/{d.pk}/action/"
        evil = client.post(url, {"action": "pin"}, HTTP_ORIGIN="https://evil.example", HTTP_HOST="testserver")
        self.assertEqual(evil.status_code, 403)
        own = client.post(url, {"action": "pin"}, HTTP_ORIGIN="http://testserver", HTTP_HOST="testserver")
        self.assertEqual(own.status_code, 200)
        self.assertTrue(own.json()["pinned"])

    def test_mini_app_delete_is_per_user_too(self):
        d = services.get_or_create_dialog(self.alice, self.bob)
        services.send_message(self.bob, d, "привет")
        client = self.mini_app_client(self.alice)
        client.post(f"/api/dialogs/{d.pk}/action/", {"action": "delete"})
        self.assertTrue(Dialog.objects.filter(pk=d.pk).exists())
        self.assertEqual(client.get("/api/dialogs/").json()["dialogs"], [])
        bob_rows = self.mini_app_client(self.bob).get("/api/dialogs/").json()["dialogs"]
        self.assertEqual(bob_rows[0]["last_message"]["text"], "привет")


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
        resp = self.client_for(self.bob).get("/api/v1/catalog/items/?category=shirt")
        names = [i["name"] for i in resp.json()["results"]]
        self.assertIn("Синяя рубашка", names)
