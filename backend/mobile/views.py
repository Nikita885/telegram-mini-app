"""Mobile API v1 endpoints."""

import json
import secrets
from datetime import timedelta
from io import BytesIO

import requests
from django.conf import settings
from django.core.cache import cache
from django.core.files.base import ContentFile
from django.db.models import Count, F, OuterRef, Q, Subquery
from django.shortcuts import get_object_or_404
from django.utils import timezone
from PIL import Image
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, BasePermission, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from api.models import (
    ClothingCategory,
    ClothingItem,
    CommentLike,
    Follow,
    Hashtag,
    Mannequin,
    Message,
    Notification,
    OutfitPost,
    PostComment,
    TelegramUser,
)
from api.utils import verify_telegram_init_data
from studio.models import GarmentJob

from . import auth, presenters, serializers, services
from .exceptions import ApiError
from .models import Collection, CollectionItem, LoginNonce, Report

PAGE_SIZE = 20


# ── Helpers ──────────────────────────────────────────────────────────────────


def me(request) -> TelegramUser:
    return request.user.tg


def validated(serializer_class, data):
    serializer = serializer_class(data=data)
    serializer.is_valid(raise_exception=True)
    return serializer.validated_data


def int_param(request, name, default=None):
    """Integer query parameter; garbage is a 400 with a clear code, not a 500."""
    value = request.query_params.get(name)
    if value in (None, ""):
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise ApiError("invalid_parameter", f"Некорректный параметр {name}") from exc


def parse_bool(value) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in ("1", "true", "yes", "on")


def id_page(qs, request, limit=PAGE_SIZE):
    """Keyset pagination on descending primary key: ?before=<id>. Returns (rows, next_cursor)."""
    before = request.query_params.get("before")
    if before:
        try:
            qs = qs.filter(pk__lt=int(before))
        except ValueError as exc:
            raise ApiError("invalid_cursor", "Некорректный курсор") from exc
    try:
        limit = max(1, min(int(request.query_params.get("limit", limit)), 50))
    except ValueError:
        limit = PAGE_SIZE
    rows = list(qs.order_by("-pk")[: limit + 1])
    next_cursor = str(rows[limit - 1].pk) if len(rows) > limit else None
    return rows[:limit], next_cursor


def outfits_response(request, posts, next_cursor, following=None):
    viewer = me(request)
    liked, saved, following = services.viewer_sets(viewer, posts, following)
    return Response(
        {
            "results": [
                presenters.outfit(
                    p, request, liked_ids=liked, saved_ids=saved, following_ids=following, viewer_id=viewer.pk
                )
                for p in posts
            ],
            "next": next_cursor,
        }
    )


def auth_payload(request, user):
    return {**auth.issue_tokens(user), "user": presenters.user_short(user, request)}


class IsAppAdmin(BasePermission):
    message = "Студия доступна только администраторам"

    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.tg.is_admin)


# ── Auth ─────────────────────────────────────────────────────────────────────


@api_view(["GET"])
@permission_classes([AllowAny])
def app_config(request):
    return Response(
        {
            "bot_username": settings.TG_BOT_USERNAME,
            "dev_login": settings.ALLOW_DEV_LOGIN,
            "mannequin_canvas": list(settings.MANNEQUIN_CANVAS_SIZE),
            "free_layer_width": 0.4,
        }
    )


class TelegramStartView(APIView):
    """Begin a bot sign-in: the app shows `code`, the bot asks the user to confirm the same code."""

    permission_classes = [AllowAny]
    throttle_scope = "auth"

    def post(self, request):
        if not settings.TG_BOT_USERNAME:
            raise ApiError("bot_not_configured", "Вход через Telegram не настроен на сервере", 503)
        data = validated(serializers.TelegramStartSerializer, request.data)
        nonce = LoginNonce.objects.create(device=data.get("device", ""))
        return Response(
            {
                "nonce": nonce.nonce,
                "code": nonce.code,
                "bot_url": f"https://t.me/{settings.TG_BOT_USERNAME}?start=login_{nonce.nonce}",
                "expires_in": int(settings.LOGIN_NONCE_TTL.total_seconds()),
            },
            status=status.HTTP_201_CREATED,
        )


class TelegramPollView(APIView):
    permission_classes = [AllowAny]
    throttle_scope = "auth_poll"

    def post(self, request):
        data = validated(serializers.TelegramPollSerializer, request.data)
        nonce = LoginNonce.objects.select_related("user").filter(nonce=data["nonce"]).first()
        if nonce is None or nonce.consumed_at is not None:
            raise ApiError("nonce_invalid", "Ссылка для входа недействительна — начните заново", 410)
        if nonce.declined_at is not None:
            raise ApiError("login_declined", "Вход отклонён в Telegram", 410)
        if nonce.is_expired:
            raise ApiError("nonce_expired", "Время на подтверждение вышло — начните заново", 410)
        if nonce.user is None or nonce.confirmed_at is None:
            return Response({"status": "pending"}, status=status.HTTP_202_ACCEPTED)
        if nonce.user.is_banned:
            raise ApiError("banned", "Аккаунт заблокирован", 403)
        updated = LoginNonce.objects.filter(pk=nonce.pk, consumed_at__isnull=True).update(consumed_at=timezone.now())
        if not updated:
            raise ApiError("nonce_invalid", "Ссылка для входа уже использована", 410)
        return Response({"status": "ok", **auth_payload(request, nonce.user)})


class WebAppLoginView(APIView):
    """Sign in with Telegram WebApp initData (used when the app is opened from Telegram)."""

    permission_classes = [AllowAny]
    throttle_scope = "auth"

    def post(self, request):
        data = validated(serializers.WebAppLoginSerializer, request.data)
        parsed = verify_telegram_init_data(data["init_data"], settings.TG_BOT_TOKEN)
        if not parsed or not parsed.get("user"):
            raise ApiError("init_data_invalid", "Не удалось проверить данные Telegram", 403)
        user = upsert_telegram_user(json.loads(parsed["user"]))
        return Response(auth_payload(request, user))


def upsert_telegram_user(data: dict) -> TelegramUser:
    return TelegramUser.from_telegram(data)


class DevLoginView(APIView):
    permission_classes = [AllowAny]
    throttle_scope = "auth"

    def post(self, request):
        if not settings.ALLOW_DEV_LOGIN:
            raise ApiError("not_found", "Не найдено", 404)
        data = validated(serializers.DevLoginSerializer, request.data)
        user = upsert_telegram_user({"id": data["telegram_id"], "first_name": data.get("first_name") or "Тестовый"})
        return Response(auth_payload(request, user))


class RefreshView(APIView):
    permission_classes = [AllowAny]
    throttle_scope = "auth"

    def post(self, request):
        data = validated(serializers.RefreshSerializer, request.data)
        tokens = auth.rotate_refresh(data["refresh"])
        return Response(tokens)


@api_view(["POST"])
@permission_classes([AllowAny])
def logout(request):
    """Revoke the given refresh token; holding it is the proof of ownership."""
    token = request.data.get("refresh")
    if token:
        auth.revoke_refresh(token)
    return Response(status=status.HTTP_204_NO_CONTENT)


# ── Me ───────────────────────────────────────────────────────────────────────


def profile_counts(user):
    return {
        "outfits": OutfitPost.objects.filter(user=user, is_hidden=False).count(),
        "followers": Follow.objects.filter(following=user).count(),
        "following": Follow.objects.filter(follower=user).count(),
    }


@api_view(["GET", "PATCH"])
def me_view(request):
    user = me(request)
    if request.method == "PATCH":
        data = validated(serializers.ProfileUpdateSerializer, request.data)
        if "username" in data and data["username"]:
            taken = TelegramUser.objects.filter(username__iexact=data["username"]).exclude(pk=user.pk).exists()
            if taken:
                raise ApiError("username_taken", "Имя пользователя занято")
        for field in ("first_name", "last_name", "username", "bio"):
            if field in data:
                setattr(user, field, data[field])
        if "avatar_color" in data:
            user.avatar_random_color = data["avatar_color"]
        user.save()
    body = presenters.profile(user, request, counts=profile_counts(user), is_me=True)
    body["is_admin"] = user.is_admin
    return Response(body)


def _store_avatar(user, image: Image.Image):
    side = min(image.size)
    left, top = (image.width - side) // 2, (image.height - side) // 2
    image = image.convert("RGB").crop((left, top, left + side, top + side)).resize((400, 400), Image.LANCZOS)
    buf = BytesIO()
    image.save(buf, format="WEBP", quality=85)
    if user.avatar:
        user.avatar.delete(save=False)
    user.avatar.save(f"avatar_{user.telegram_id}_{int(timezone.now().timestamp())}.webp", ContentFile(buf.getvalue()))


class AvatarView(APIView):
    parser_classes = [MultiPartParser, FormParser, JSONParser]
    throttle_scope = "upload"

    def post(self, request):
        user = me(request)
        if request.data.get("source") == "telegram":
            image = _telegram_avatar(user)
        else:
            upload = request.FILES.get("avatar")
            if upload is None:
                raise ApiError("no_file", "Выберите фото")
            if upload.size > 10 * 1024 * 1024:
                raise ApiError("file_too_large", "Фото больше 10 МБ")
            try:
                image = Image.open(upload)
                image.load()
            except Exception as exc:  # noqa: BLE001
                raise ApiError("invalid_image", "Не удалось прочитать фото") from exc
        _store_avatar(user, image)
        return Response({"avatar_url": presenters.media_url(user.avatar, request)})

    def delete(self, request):
        user = me(request)
        if user.avatar:
            user.avatar.delete(save=False)
            user.avatar = None
            user.save(update_fields=["avatar"])
        return Response(status=status.HTTP_204_NO_CONTENT)


def _telegram_avatar(user) -> Image.Image:
    token = settings.TG_BOT_TOKEN
    if not token:
        raise ApiError("bot_not_configured", "Бот не настроен", 503)
    base = f"https://api.telegram.org/bot{token}"
    try:
        photos = requests.get(
            f"{base}/getUserProfilePhotos", params={"user_id": user.telegram_id, "limit": 1}, timeout=10
        ).json()
        if not photos.get("ok") or not photos["result"]["total_count"]:
            raise ApiError("no_telegram_photo", "В Telegram нет фото профиля или оно скрыто", 404)
        file_id = photos["result"]["photos"][0][-1]["file_id"]
        info = requests.get(f"{base}/getFile", params={"file_id": file_id}, timeout=10).json()
        if not info.get("ok"):
            raise ApiError("telegram_error", "Telegram не отдал фото", 502)
        resp = requests.get(f"https://api.telegram.org/file/bot{token}/{info['result']['file_path']}", timeout=20)
    except (requests.RequestException, ValueError):
        # No details: the request URL contains the bot token.
        raise ApiError(
            "telegram_unavailable", "Telegram сейчас недоступен с сервера — загрузите фото из галереи", 503
        ) from None
    image = Image.open(BytesIO(resp.content))
    image.load()
    return image


# ── Users ────────────────────────────────────────────────────────────────────


@api_view(["GET"])
def user_detail(request, user_id):
    viewer = me(request)
    user = services.resolve_user(user_id)
    return Response(
        presenters.profile(
            user,
            request,
            counts=profile_counts(user),
            is_following=Follow.objects.filter(follower=viewer, following=user).exists(),
            follows_you=Follow.objects.filter(follower=user, following=viewer).exists(),
            is_me=user.pk == viewer.pk,
        )
    )


@api_view(["GET"])
def user_outfits(request, user_id):
    user = services.resolve_user(user_id)
    qs = services.with_outfit_relations(services.visible_outfits(me(request)).filter(user=user))
    posts, cursor = id_page(qs, request, limit=30)
    return outfits_response(request, posts, cursor)


@api_view(["GET"])
def user_connections(request, user_id, kind):
    viewer = me(request)
    user = services.resolve_user(user_id)
    if kind == "followers":
        qs = Follow.objects.filter(following=user).select_related("follower")
        pick = "follower"
    elif kind == "following":
        qs = Follow.objects.filter(follower=user).select_related("following")
        pick = "following"
    else:
        raise ApiError("not_found", "Не найдено", 404)
    q = request.query_params.get("q", "").strip().lstrip("@")
    if q:
        qs = qs.filter(
            Q(**{f"{pick}__username__icontains": q})
            | Q(**{f"{pick}__first_name__icontains": q})
            | Q(**{f"{pick}__last_name__icontains": q})
        )
    rows, cursor = id_page(qs, request, limit=40)
    people = [getattr(r, pick) for r in rows]
    mine = services.following_ids(viewer)
    followers_of_viewer = set(
        Follow.objects.filter(following=viewer, follower__in=people).values_list("follower_id", flat=True)
    )
    return Response(
        {
            "results": [
                {
                    **presenters.user_short(p, request),
                    "is_following": p.pk in mine,
                    "follows_you": p.pk in followers_of_viewer,
                    "is_me": p.pk == viewer.pk,
                }
                for p in people
            ],
            "next": cursor,
        }
    )


@api_view(["POST", "DELETE"])
def follow(request, user_id):
    target = services.resolve_user(user_id)
    following = services.set_follow(me(request), target, request.method == "POST")
    return Response({"is_following": following, "followers_count": Follow.objects.filter(following=target).count()})


@api_view(["GET"])
def search_users(request):
    viewer = me(request)
    q = request.query_params.get("q", "").strip().lstrip("@")
    qs = TelegramUser.objects.filter(is_banned=False).exclude(pk=viewer.pk)
    if q:
        qs = qs.filter(Q(username__icontains=q) | Q(first_name__icontains=q) | Q(last_name__icontains=q))
    else:
        # "People for you": most followed accounts the viewer doesn't follow yet.
        qs = qs.exclude(pk__in=services.following_ids(viewer)).annotate(n=Count("followers")).order_by("-n")
    people = list(qs[:30])
    mine = services.following_ids(viewer)
    return Response(
        {
            "results": [{**presenters.user_short(p, request), "is_following": p.pk in mine} for p in people],
            "next": None,
        }
    )


# ── Feed & outfits ───────────────────────────────────────────────────────────


FEED_POOL = 400
FEED_SNAPSHOT_TTL = 15 * 60


def rank_for_you(rows, following, now):
    """Order (pk, user_id, created_at, likes, comments) rows: engagement with time decay, a boost
    for followed authors, and no author more than once in any three consecutive posts."""

    def score(row):
        _, author, created_at, likes, comments = row
        age_h = max((now - created_at).total_seconds() / 3600.0, 0.0)
        engagement = 1 + likes * 2 + comments * 3
        boost = 1.6 if author in following else 1.0
        return boost * engagement / ((age_h + 2) ** 1.25)

    ordered, recent_authors, backlog = [], [], []
    for row in sorted(rows, key=score, reverse=True):
        if row[1] in recent_authors[-2:]:
            backlog.append(row)
            continue
        ordered.append(row)
        recent_authors.append(row[1])
        while backlog and backlog[0][1] not in recent_authors[-2:]:
            nxt = backlog.pop(0)
            ordered.append(nxt)
            recent_authors.append(nxt[1])
    ordered += backlog
    return [row[0] for row in ordered]


@api_view(["GET"])
def feed(request):
    viewer = me(request)
    tab = request.query_params.get("tab", "for_you")
    visible = services.visible_outfits(viewer)
    if tab == "following":
        qs = services.with_outfit_relations(visible.filter(user_id__in=services.following_ids(viewer)))
        posts, cursor = id_page(qs, request)
        return outfits_response(request, posts, cursor)

    since = int_param(request, "since")
    if since is not None:
        return Response({"new_count": visible.exclude(user=viewer).filter(pk__gt=since).count()})

    # "For you" is ranked once per session into a snapshot of ids kept in the cache; the cursor
    # ("<snapshot>.<page>") walks that snapshot, so pages never repeat or skip posts even though
    # scores change while the user scrolls. Ranking reads five columns, not whole posts.
    following = services.following_ids(viewer)
    snapshot, page = None, 1
    token = request.query_params.get("page") or ""
    if "." in token:
        key, _, number = token.partition(".")
        if number.isdigit():
            snapshot = cache.get(f"feed:{viewer.pk}:{key}")
            page = max(1, int(number))
    elif token.isdigit():
        page = max(1, int(token))
    if snapshot is None:
        rows = visible.exclude(user=viewer).order_by("-pk").values_list(
            "pk", "user_id", "created_at", "likes_count", "comments_count"
        )[:FEED_POOL]
        key = secrets.token_urlsafe(6)
        snapshot = rank_for_you(list(rows), following, timezone.now())
        cache.set(f"feed:{viewer.pk}:{key}", snapshot, FEED_SNAPSHOT_TTL)
    start = (page - 1) * PAGE_SIZE
    ids = snapshot[start : start + PAGE_SIZE]
    # Posts deleted or hidden since the snapshot was taken simply drop out.
    by_id = {p.pk: p for p in services.with_outfit_relations(visible.filter(pk__in=ids))}
    posts = [by_id[pk] for pk in ids if pk in by_id]
    next_cursor = f"{key}.{page + 1}" if len(snapshot) > start + PAGE_SIZE else None
    return outfits_response(request, posts, next_cursor, following=following)


class OutfitCreateView(APIView):
    throttle_scope = "write"

    def post(self, request):
        data = validated(serializers.OutfitCreateSerializer, request.data)
        post = services.create_outfit(me(request), data)
        post = services.with_outfit_relations(OutfitPost.objects.filter(pk=post.pk)).get()
        return Response(
            presenters.outfit(post, request, viewer_id=me(request).pk), status=status.HTTP_201_CREATED
        )


@api_view(["GET", "DELETE"])
def outfit_detail(request, outfit_id):
    viewer = me(request)
    post = services.get_visible_outfit(viewer, outfit_id)
    if request.method == "DELETE":
        services.delete_outfit(viewer, post)
        return Response(status=status.HTTP_204_NO_CONTENT)
    post = services.with_outfit_relations(OutfitPost.objects.filter(pk=post.pk)).get()
    liked, saved, following = services.viewer_sets(viewer, [post])
    return Response(
        presenters.outfit(post, request, liked_ids=liked, saved_ids=saved, following_ids=following, viewer_id=viewer.pk)
    )


@api_view(["GET"])
def outfit_similar(request, outfit_id):
    viewer = me(request)
    post = services.get_visible_outfit(viewer, outfit_id)
    item_ids = list(post.items.values_list("clothing_id", flat=True))
    qs = (
        services.visible_outfits(viewer)
        .exclude(pk=post.pk)
        .filter(items__clothing_id__in=item_ids)
        # distinct=True: the visibility filter joins the author's followers, which would otherwise
        # multiply the count by their number.
        .annotate(overlap=Count("items", filter=Q(items__clothing_id__in=item_ids), distinct=True))
        .order_by("-overlap", "-likes_count", "-pk")
    )
    posts = list(services.with_outfit_relations(qs)[:12])
    return outfits_response(request, posts, None)


@api_view(["POST", "DELETE"])
def outfit_like(request, outfit_id):
    post = services.get_visible_outfit(me(request), outfit_id)
    liked = request.method == "POST"
    count = services.set_like(me(request), post, liked)
    return Response({"is_liked": liked, "likes_count": count})


@api_view(["POST", "DELETE"])
def outfit_save(request, outfit_id):
    viewer = me(request)
    post = services.get_visible_outfit(viewer, outfit_id)
    collection = None
    collection_id = request.data.get("collection_id") or request.query_params.get("collection_id")
    if collection_id:
        collection = get_object_or_404(Collection, pk=collection_id, owner=viewer)
    if request.method == "POST":
        collection = services.save_outfit(viewer, post, collection)
        return Response({"is_saved": True, "collection_id": collection.pk})
    services.unsave_outfit(viewer, post, collection)
    still = CollectionItem.objects.filter(collection__owner=viewer, post=post).exists()
    return Response({"is_saved": still})


@api_view(["GET", "POST"])
def outfit_comments(request, outfit_id):
    viewer = me(request)
    post = services.get_visible_outfit(viewer, outfit_id)
    if request.method == "POST":
        data = validated(serializers.CommentCreateSerializer, request.data)
        parent = None
        if data.get("parent_id"):
            parent = get_object_or_404(PostComment, pk=data["parent_id"], post=post)
        comment = services.add_comment(viewer, post, data["text"], parent)
        comment = PostComment.objects.select_related("user").get(pk=comment.pk)
        return Response(
            presenters.comment(comment, request, viewer_id=viewer.pk, post_author_id=post.user_id),
            status=status.HTTP_201_CREATED,
        )
    comments = list(PostComment.objects.filter(post=post).select_related("user").order_by("pk")[:500])
    liked = set(
        CommentLike.objects.filter(user=viewer, comment__post=post).values_list("comment_id", flat=True)
    )
    return Response(
        {
            "results": [
                presenters.comment(c, request, liked_ids=liked, viewer_id=viewer.pk, post_author_id=post.user_id)
                for c in comments
            ],
            "count": len(comments),
        }
    )


@api_view(["DELETE"])
def comment_detail(request, comment_id):
    comment = get_object_or_404(PostComment.objects.select_related("post"), pk=comment_id)
    services.delete_comment(me(request), comment)
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["POST", "DELETE"])
def comment_like(request, comment_id):
    comment = get_object_or_404(PostComment, pk=comment_id)
    services.get_visible_outfit(me(request), comment.post_id)
    liked = request.method == "POST"
    count = services.set_comment_like(me(request), comment, liked)
    return Response({"is_liked": liked, "likes_count": count})


# ── Search ───────────────────────────────────────────────────────────────────


@api_view(["GET"])
def search_outfits(request):
    q = request.query_params.get("q", "").strip()
    qs = services.with_outfit_relations(services.visible_outfits(me(request)))
    if q.startswith("#"):
        qs = qs.filter(hashtags__tag=q.lstrip("#").lower())
    elif q:
        qs = qs.filter(
            Q(hashtags__tag__icontains=q)
            | Q(description__icontains=q)
            | Q(items__clothing__name__icontains=q)
        ).distinct()
    posts, cursor = id_page(qs, request, limit=30)
    return outfits_response(request, posts, cursor)


@api_view(["GET"])
def trending_hashtags(request):
    since = timezone.now() - timedelta(days=30)
    recent = (
        Hashtag.objects.filter(posts__created_at__gte=since)
        .annotate(n=Count("posts"))
        .order_by("-n", "tag")[:20]
    )
    tags = [{"tag": h.tag, "count": h.n} for h in recent]
    if len(tags) < 8:
        known = {t["tag"] for t in tags}
        for h in Hashtag.objects.order_by("-usage_count")[:20]:
            if h.tag not in known:
                tags.append({"tag": h.tag, "count": h.usage_count})
    return Response({"results": tags[:20]})


# ── Catalog ──────────────────────────────────────────────────────────────────


@api_view(["GET"])
def mannequins(request):
    from studio.services import prepare_mannequin

    result = []
    for m in Mannequin.objects.all():
        prepare_mannequin(m)
        result.append(presenters.mannequin(m, request))
    return Response({"results": result})


@api_view(["GET"])
def categories(request):
    return Response({"results": [presenters.category(c) for c in ClothingCategory.objects.all()]})


@api_view(["GET"])
def catalog_items(request):
    params = request.query_params
    qs = ClothingItem.objects.filter(is_published=True).select_related("category")
    if params.get("category"):
        qs = qs.filter(category__name__in=[c for c in params["category"].split(",") if c])
    if params.get("gender") in ("male", "female"):
        qs = qs.filter(gender__in=[params["gender"], "unisex"])
    if params.get("style"):
        qs = qs.filter(style=params["style"])
    if params.get("season"):
        qs = qs.filter(season__in=[params["season"], "all"])
    if params.get("color"):
        qs = qs.filter(color__icontains=params["color"])
    if params.get("q"):
        q = params["q"].strip()
        qs = qs.filter(Q(name__icontains=q) | Q(tags__icontains=q) | Q(color__icontains=q) | Q(brand__icontains=q))
    items, cursor = id_page(qs, request, limit=30)
    return Response({"results": [presenters.item(i, request) for i in items], "next": cursor})


@api_view(["GET"])
def catalog_item(request, item_id):
    item = get_object_or_404(ClothingItem.objects.select_related("category"), pk=item_id, is_published=True)
    return Response(presenters.item(item, request))


@api_view(["GET"])
def catalog_item_similar(request, item_id):
    item = get_object_or_404(ClothingItem.objects.select_related("category"), pk=item_id, is_published=True)
    qs = (
        ClothingItem.objects.filter(is_published=True, category=item.category)
        .exclude(pk=item.pk)
        .select_related("category")
    )
    if item.gender != "unisex":
        qs = qs.filter(gender__in=[item.gender, "unisex"])
    items = sorted(
        qs[:60],
        key=lambda i: (
            -(bool(item.color) and i.color == item.color),
            -(bool(item.style) and i.style == item.style),
            -i.pk,
        ),
    )[:12]
    return Response({"results": [presenters.item(i, request) for i in items]})


# ── Collections ──────────────────────────────────────────────────────────────


def _collections_payload(request, collections):
    ids = [c.pk for c in collections]
    covers = {pk: [] for pk in ids}
    for ci in (
        CollectionItem.objects.filter(collection_id__in=ids).select_related("post").order_by("collection_id", "-pk")
    ):
        bucket = covers[ci.collection_id]
        if len(bucket) < 4 and ci.post.final_image:
            bucket.append(presenters.media_url(ci.post.final_image, request))
    return [presenters.collection(c, request, covers[c.pk]) for c in collections]


@api_view(["GET", "POST"])
def collections(request):
    viewer = me(request)
    if request.method == "POST":
        data = validated(serializers.CollectionSerializer, request.data)
        collection = Collection.objects.create(owner=viewer, **data)
        collection.items_count = 0
        return Response(_collections_payload(request, [collection])[0], status=status.HTTP_201_CREATED)
    Collection.default_for(viewer)
    owner_id = int_param(request, "user_id", viewer.pk)
    qs = Collection.objects.filter(owner_id=owner_id)
    if owner_id != viewer.pk:
        qs = qs.filter(is_private=False)
    rows = list(qs.annotate(items_count=Count("items")))
    return Response({"results": _collections_payload(request, rows)})


@api_view(["GET", "PATCH", "DELETE"])
def collection_detail(request, collection_id):
    viewer = me(request)
    collection = get_object_or_404(Collection, pk=collection_id)
    if collection.owner_id != viewer.pk and (collection.is_private or request.method != "GET"):
        raise ApiError("forbidden", "Коллекция недоступна", 403)
    if request.method == "DELETE":
        if collection.is_default:
            raise ApiError("default_collection", "«Сохранённое» удалить нельзя")
        collection.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
    if request.method == "PATCH":
        data = serializers.CollectionSerializer(data=request.data, partial=True)
        data.is_valid(raise_exception=True)
        for field, value in data.validated_data.items():
            setattr(collection, field, value)
        collection.save()
    posts_qs = services.with_outfit_relations(
        services.visible_outfits(viewer).filter(saved_in__collection=collection)
    )
    posts, cursor = id_page(posts_qs, request, limit=30)
    body = outfits_response(request, posts, cursor).data
    collection.items_count = collection.items.count()
    body["collection"] = _collections_payload(request, [collection])[0]
    return Response(body)


# ── Messaging ────────────────────────────────────────────────────────────────


@api_view(["GET", "POST"])
def dialogs(request):
    viewer = me(request)
    if request.method == "POST":
        data = validated(serializers.DialogCreateSerializer, request.data)
        dialog = services.get_or_create_dialog(viewer, services.resolve_user(data["user_id"]))
        return Response(presenters.dialog(dialog, request, viewer), status=status.HTTP_201_CREATED)
    qs = services.dialogs_for(viewer)
    last_id = (
        Message.objects.filter(dialog=OuterRef("pk"), created_at__gt=OuterRef("visible_from"))
        .order_by("-pk")
        .values("pk")[:1]
    )
    qs = qs.annotate(
        last_id=Subquery(last_id),
        unread=Count(
            "messages",
            filter=Q(messages__is_read=False, messages__created_at__gt=F("visible_from")) & ~Q(messages__sender=viewer),
        ),
    ).order_by("-my_pinned", "-updated_at")
    rows = list(qs[:200])
    last = Message.objects.in_bulk([d.last_id for d in rows if d.last_id])
    return Response(
        {
            "results": [
                presenters.dialog(d, request, viewer, last_message=last.get(d.last_id), unread=d.unread)
                for d in rows
                if d.last_id or d.my_pinned
            ]
        }
    )


@api_view(["DELETE"])
def dialog_detail(request, dialog_id):
    """«Удалить диалог» — only for the viewer; the other participant keeps the conversation."""
    viewer = me(request)
    dialog = services.get_dialog(viewer, dialog_id)
    dialog.clear_for(viewer)
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["POST"])
def dialog_pin(request, dialog_id):
    viewer = me(request)
    dialog = services.get_dialog(viewer, dialog_id)
    pinned = request.data.get("pinned")
    pinned = not dialog.pinned_for(viewer) if pinned is None else parse_bool(pinned)
    dialog.set_pinned(viewer, pinned)
    return Response({"pinned": pinned})


@api_view(["POST"])
def dialog_read(request, dialog_id):
    viewer = me(request)
    dialog = services.get_dialog(viewer, dialog_id)
    services.mark_dialog_read(viewer, dialog)
    other_id = dialog.user2_id if dialog.user1_id == viewer.pk else dialog.user1_id
    from .realtime import send_to_user

    send_to_user(other_id, "dialog.read", {"dialog_id": dialog.pk})
    return Response({"ok": True})


class DialogMessagesView(APIView):
    throttle_scope = "write"

    def get(self, request, dialog_id):
        viewer = me(request)
        dialog = services.get_dialog(viewer, dialog_id)
        qs = dialog.visible_messages(viewer).select_related("post__user")
        after = int_param(request, "after")
        if after is not None:
            rows = list(qs.filter(pk__gt=after).order_by("pk")[:200])
            return Response({"results": [presenters.message(m, request, viewer.pk) for m in rows], "next": None})
        rows, cursor = id_page(qs, request, limit=50)
        return Response(
            {
                "results": [presenters.message(m, request, viewer.pk) for m in rows],
                "next": cursor,
                "user": presenters.user_short(dialog.user2 if dialog.user1_id == viewer.pk else dialog.user1, request),
            }
        )

    def post(self, request, dialog_id):
        viewer = me(request)
        dialog = services.get_dialog(viewer, dialog_id)
        data = validated(serializers.MessageCreateSerializer, request.data)
        outfit = services.get_visible_outfit(viewer, data["outfit_id"]) if data.get("outfit_id") else None
        message = services.send_message(viewer, dialog, data.get("text", ""), outfit)
        message = Message.objects.select_related("post__user").get(pk=message.pk)
        return Response(presenters.message(message, request, viewer.pk), status=status.HTTP_201_CREATED)


@api_view(["PATCH", "DELETE"])
def message_detail(request, message_id):
    viewer = me(request)
    message = get_object_or_404(
        Message.objects.select_related("post__user").filter(Q(dialog__user1=viewer) | Q(dialog__user2=viewer)),
        pk=message_id,
    )
    if request.method == "DELETE":
        services.delete_message(viewer, message)
        return Response(status=status.HTTP_204_NO_CONTENT)
    data = validated(serializers.MessageEditSerializer, request.data)
    message = services.edit_message(viewer, message, data["text"])
    return Response(presenters.message(message, request, viewer.pk))


# ── Notifications ────────────────────────────────────────────────────────────


@api_view(["GET"])
def notifications(request):
    viewer = me(request)
    qs = Notification.objects.filter(recipient=viewer).select_related("sender", "post", "comment")
    rows, cursor = id_page(qs, request, limit=30)
    return Response({"results": [presenters.notification(n, request) for n in rows], "next": cursor})


@api_view(["POST"])
def notifications_read(request):
    Notification.objects.filter(recipient=me(request), is_read=False).update(is_read=True)
    return Response({"ok": True})


@api_view(["GET"])
def counters(request):
    viewer = me(request)
    return Response(
        {
            "notifications": Notification.objects.filter(recipient=viewer, is_read=False).count(),
            "messages": services.unread_messages_count(viewer),
        }
    )


# ── Reports ──────────────────────────────────────────────────────────────────


class ReportView(APIView):
    throttle_scope = "write"

    def post(self, request):
        data = validated(serializers.ReportSerializer, request.data)
        Report.objects.create(reporter=me(request), **data)
        return Response({"ok": True}, status=status.HTTP_201_CREATED)


# ── Studio (admins) ──────────────────────────────────────────────────────────


def _jobs_qs():
    return GarmentJob.objects.select_related("category")


class StudioJobsView(APIView):
    permission_classes = [IsAuthenticated, IsAppAdmin]
    parser_classes = [MultiPartParser, FormParser, JSONParser]
    throttle_scope = "upload"

    def get(self, request):
        status_filter = request.query_params.get("status")
        qs = _jobs_qs()
        groups = {
            "active": [GarmentJob.STATUS_QUEUED, GarmentJob.STATUS_PROCESSING],
            "review": [GarmentJob.STATUS_REVIEW],
            "failed": [GarmentJob.STATUS_FAILED],
            "published": [GarmentJob.STATUS_PUBLISHED],
        }
        if status_filter in groups:
            qs = qs.filter(status__in=groups[status_filter])
        rows, cursor = id_page(qs, request, limit=30)
        totals = dict(GarmentJob.objects.values_list("status").annotate(n=Count("pk")))
        return Response(
            {
                "results": [presenters.garment_job(j, request) for j in rows],
                "next": cursor,
                "totals": {
                    group: sum(totals.get(s, 0) for s in statuses) for group, statuses in groups.items()
                },
            }
        )

    def post(self, request):
        data = validated(serializers.GarmentJobCreateSerializer, request.data)
        photo = data["photo"]
        if photo.size > 20 * 1024 * 1024:
            raise ApiError("file_too_large", "Фото больше 20 МБ")
        job = GarmentJob.objects.create(
            created_by=me(request), category=data["category"], gender=data["gender"], source=photo
        )
        enqueue(job)
        return Response(presenters.garment_job(job, request), status=status.HTTP_201_CREATED)


def enqueue(job):
    from studio.tasks import process_garment_job

    if settings.CELERY_TASK_ALWAYS_EAGER:
        process_garment_job.apply(args=[job.pk])
    else:
        process_garment_job.delay(job.pk)


@api_view(["GET", "DELETE"])
@permission_classes([IsAuthenticated, IsAppAdmin])
def studio_job(request, job_id):
    job = get_object_or_404(_jobs_qs(), pk=job_id)
    if request.method == "DELETE":
        for field in ("source", "cutout", "fitted_male", "fitted_female", "preview_male", "preview_female"):
            f = getattr(job, field)
            if f and not (job.item_id and field in ("fitted_male", "fitted_female")):
                f.delete(save=False)
        job.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
    return Response(presenters.garment_job(job, request))


@api_view(["POST"])
@permission_classes([IsAuthenticated, IsAppAdmin])
def studio_job_retry(request, job_id):
    job = get_object_or_404(_jobs_qs(), pk=job_id)
    job.status = GarmentJob.STATUS_QUEUED
    job.stage = ""
    job.progress = 0
    job.error = ""
    job.save()
    enqueue(job)
    return Response(presenters.garment_job(job, request))


@api_view(["POST"])
@permission_classes([IsAuthenticated, IsAppAdmin])
def studio_job_refit(request, job_id):
    from studio.pipeline.garment import PipelineError
    from studio.services import refit_job

    job = get_object_or_404(_jobs_qs(), pk=job_id)
    data = validated(serializers.RefitSerializer, request.data)
    try:
        job = refit_job(job, data["keypoints"])
    except PipelineError as exc:
        raise ApiError(exc.code, exc.message) from exc
    return Response(presenters.garment_job(job, request))


@api_view(["POST"])
@permission_classes([IsAuthenticated, IsAppAdmin])
def studio_job_publish(request, job_id):
    from studio.services import publish_job

    job = get_object_or_404(_jobs_qs(), pk=job_id)
    if job.status not in (GarmentJob.STATUS_REVIEW, GarmentJob.STATUS_PUBLISHED):
        raise ApiError("job_not_ready", "Вещь ещё не обработана")
    data = validated(serializers.PublishItemSerializer, request.data)
    item = publish_job(job, data)
    item = ClothingItem.objects.select_related("category").get(pk=item.pk)
    job.refresh_from_db()
    return Response({"job": presenters.garment_job(job, request), "item": presenters.item(item, request)})
