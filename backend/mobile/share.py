"""Public pages behind the links the app shares (https://<domain>/o/<id>, /u/<id>).

The app opens them directly once Android App Links are verified (/.well-known/assetlinks.json);
everyone else gets a small page with a preview for messengers and ways to open the outfit.
"""

import json

from django.conf import settings
from django.http import Http404, HttpResponse
from django.shortcuts import render
from django.views.decorators.cache import cache_control

from api.models import TelegramUser

from . import presenters, services

APP_PACKAGE = "app.outfitshare"


def _page(request, *, kind, obj_id, title, text, image=None, status=200):
    bot = settings.TG_BOT_USERNAME
    context = {
        "title": title,
        "text": text,
        "image": image,
        "url": request.build_absolute_uri(),
        # Chrome on Android: open the app if installed, otherwise fall back to the Telegram bot.
        "app_link": (
            f"intent://{kind}/{obj_id}#Intent;scheme=outfitshare;package={APP_PACKAGE};"
            + (f"S.browser_fallback_url=https%3A%2F%2Ft.me%2F{bot};" if bot else "")
            + "end"
        ),
        "telegram_link": f"https://t.me/{bot}" if bot else None,
    }
    return render(request, "mobile/share.html", context, status=status)


@cache_control(public=True, max_age=300)
def outfit_page(request, outfit_id):
    post = services.visible_outfits(None).select_related("user").filter(pk=outfit_id).first()
    if post is None:
        # Private, followers-only, hidden or deleted: say nothing about it.
        return _page(
            request, kind="outfit", obj_id=outfit_id, title="Образ в Outfit Share",
            text="Откройте приложение, чтобы посмотреть его.", status=404,
        )
    author = post.user.display_name
    description = (post.description or "").strip() or f"Образ {author} в Outfit Share"
    return _page(
        request, kind="outfit", obj_id=post.pk, title=f"{author} — образ в Outfit Share",
        text=description[:200], image=presenters.media_url(post.final_image, request),
    )


@cache_control(public=True, max_age=300)
def user_page(request, user_id):
    user = TelegramUser.objects.filter(pk=user_id, is_banned=False).first()
    if user is None:
        raise Http404
    return _page(
        request, kind="user", obj_id=user.pk, title=f"{user.display_name} в Outfit Share",
        text=(user.bio or "").strip()[:200] or "Образы, коллекции и ремиксы.",
        image=presenters.media_url(user.avatar, request),
    )


def assetlinks(request):
    """Digital Asset Links for Android App Links; fingerprints come from ANDROID_APP_CERT_SHA256."""
    fingerprints = [f.strip().upper() for f in settings.ANDROID_APP_CERT_SHA256 if f.strip()]
    if not fingerprints:
        raise Http404
    statement = [{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {"namespace": "android_app", "package_name": APP_PACKAGE, "sha256_cert_fingerprints": fingerprints},
    }]
    return HttpResponse(json.dumps(statement, indent=2), content_type="application/json")
