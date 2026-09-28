from django.test import override_settings
from rest_framework.test import APIClient

from api.models import OutfitPost
from mobile.tests.test_api import ApiTestCase


class SharePageTests(ApiTestCase):
    def test_public_outfit_page_has_preview(self):
        outfit = self.create_outfit(self.alice, description="Синяя классика")
        post = OutfitPost.objects.get(pk=outfit["id"])
        resp = APIClient().get(f"/o/{post.pk}")
        self.assertEqual(resp.status_code, 200)
        html = resp.content.decode()
        self.assertIn('property="og:image"', html)
        self.assertIn(post.final_image.name, html)
        self.assertIn("Синяя классика", html)
        self.assertIn(f"intent://outfit/{post.pk}#Intent;scheme=outfitshare;package=app.outfitshare", html)

    def test_private_outfit_page_reveals_nothing(self):
        outfit = self.create_outfit(self.alice, visibility="private", description="секретный образ")
        post = OutfitPost.objects.get(pk=outfit["id"])
        resp = APIClient().get(f"/o/{post.pk}/")
        self.assertEqual(resp.status_code, 404)
        html = resp.content.decode()
        self.assertNotIn("секретный", html)
        self.assertNotIn(post.final_image.name, html)
        self.assertNotIn("og:image", html)

    def test_user_page(self):
        self.assertEqual(APIClient().get(f"/u/{self.alice.pk}").status_code, 200)
        self.assertEqual(APIClient().get("/u/999999").status_code, 404)

    def test_assetlinks(self):
        self.assertEqual(APIClient().get("/.well-known/assetlinks.json").status_code, 404)
        with override_settings(ANDROID_APP_CERT_SHA256=["aa:bb"]):
            body = APIClient().get("/.well-known/assetlinks.json").json()
        self.assertEqual(body[0]["target"]["package_name"], "app.outfitshare")
        self.assertEqual(body[0]["target"]["sha256_cert_fingerprints"], ["AA:BB"])
