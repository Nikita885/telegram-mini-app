"""Business rules for the mobile API. Views stay thin; every write goes through here."""

import json
import re

from django.core.files.base import ContentFile
from django.db import IntegrityError, transaction
from django.db.models import F, Q
from django.utils import timezone

from api.models import (
    ClothingItem,
    CommentLike,
    Dialog,
    Follow,
    Hashtag,
    Mannequin,
    Message,
    Notification,
    OutfitPost,
    PostClothingItem,
    PostComment,
    PostLike,
    TelegramUser,
)

from .exceptions import ApiError
from .models import Collection, CollectionItem
from .render import render_outfit

HASHTAG_RE = re.compile(r"#([\wа-яё]{2,40})", re.IGNORECASE)


# ── Visibility ───────────────────────────────────────────────────────────────


def following_ids(user):
    return set(Follow.objects.filter(follower=user).values_list("following_id", flat=True))


def visible_outfits(viewer):
    """Outfits the viewer may see: public, own, and followers-only from people they follow."""
    qs = OutfitPost.objects.filter(is_hidden=False, user__is_banned=False)
    if viewer is None:
        return qs.filter(visibility="all")
    return qs.filter(
        Q(visibility="all")
        | Q(user=viewer)
        | Q(visibility="followers", user__followers__follower=viewer)
    ).distinct()


def get_visible_outfit(viewer, outfit_id):
    post = visible_outfits(viewer).filter(pk=outfit_id).first()
    if post is None:
        if OutfitPost.objects.filter(pk=outfit_id).exists():
            raise ApiError("outfit_unavailable", "Образ недоступен", 403)
        raise ApiError("not_found", "Образ не найден", 404)
    return post


def with_outfit_relations(qs):
    return qs.select_related("user", "remix_of__user").prefetch_related(
        "hashtags", "items__clothing__category"
    )


def viewer_sets(viewer, posts, following=None):
    """liked/saved/following id sets for serializing a page of outfits in O(1) queries."""
    ids = [p.pk for p in posts]
    if viewer is None or not ids:
        return frozenset(), frozenset(), frozenset()
    liked = frozenset(PostLike.objects.filter(user=viewer, post_id__in=ids).values_list("post_id", flat=True))
    saved = frozenset(
        CollectionItem.objects.filter(collection__owner=viewer, post_id__in=ids).values_list("post_id", flat=True)
    )
    return liked, saved, frozenset(following if following is not None else following_ids(viewer))


# ── Notifications ────────────────────────────────────────────────────────────


def notify(recipient, sender, kind, post=None, comment=None):
    if recipient.pk == sender.pk:
        return None
    return Notification.objects.create(
        recipient=recipient, sender=sender, notif_type=kind, post=post, comment=comment
    )


# ── Follow ───────────────────────────────────────────────────────────────────


def set_follow(user, target, follow: bool):
    if user.pk == target.pk:
        raise ApiError("self_follow", "Нельзя подписаться на себя")
    if follow:
        try:
            with transaction.atomic():
                Follow.objects.create(follower=user, following=target)
        except IntegrityError:
            return True
        notify(target, user, "follow")
        return True
    Follow.objects.filter(follower=user, following=target).delete()
    Notification.objects.filter(recipient=target, sender=user, notif_type="follow").delete()
    return False


# ── Likes ────────────────────────────────────────────────────────────────────


@transaction.atomic
def set_like(user, post, liked: bool):
    if liked:
        _, created = PostLike.objects.get_or_create(post=post, user=user)
        if created:
            OutfitPost.objects.filter(pk=post.pk).update(likes_count=F("likes_count") + 1)
            if not Notification.objects.filter(recipient=post.user, sender=user, notif_type="like", post=post).exists():
                notify(post.user, user, "like", post=post)
    else:
        deleted, _ = PostLike.objects.filter(post=post, user=user).delete()
        if deleted:
            OutfitPost.objects.filter(pk=post.pk, likes_count__gt=0).update(likes_count=F("likes_count") - 1)
            Notification.objects.filter(recipient=post.user, sender=user, notif_type="like", post=post).delete()
    post.refresh_from_db(fields=["likes_count"])
    return post.likes_count


@transaction.atomic
def set_comment_like(user, comment, liked: bool):
    if liked:
        _, created = CommentLike.objects.get_or_create(comment=comment, user=user)
        if created:
            PostComment.objects.filter(pk=comment.pk).update(likes_count=F("likes_count") + 1)
    else:
        deleted, _ = CommentLike.objects.filter(comment=comment, user=user).delete()
        if deleted:
            PostComment.objects.filter(pk=comment.pk, likes_count__gt=0).update(likes_count=F("likes_count") - 1)
    comment.refresh_from_db(fields=["likes_count"])
    return comment.likes_count


# ── Comments ─────────────────────────────────────────────────────────────────


@transaction.atomic
def add_comment(user, post, text, parent=None):
    text = (text or "").strip()
    if not text:
        raise ApiError("empty_comment", "Комментарий пустой")
    if len(text) > 1000:
        raise ApiError("comment_too_long", "Комментарий длиннее 1000 символов")
    if parent is not None:
        if parent.post_id != post.pk:
            raise ApiError("invalid_parent", "Ответ на комментарий из другого образа")
        # One level of nesting: replies to replies attach to the thread root.
        parent = parent.parent or parent
    comment = PostComment.objects.create(post=post, user=user, text=text, parent=parent)
    notify(post.user, user, "comment", post=post, comment=comment)
    if parent is not None and parent.user_id not in (user.pk, post.user_id):
        notify(parent.user, user, "reply", post=post, comment=comment)
    return comment


def delete_comment(user, comment):
    if user.pk not in (comment.user_id, comment.post.user_id) and not user.is_moderator:
        raise ApiError("forbidden", "Можно удалить только свой комментарий", 403)
    comment.delete()


# ── Collections ──────────────────────────────────────────────────────────────


def save_outfit(user, post, collection=None):
    collection = collection or Collection.default_for(user)
    if collection.owner_id != user.pk:
        raise ApiError("forbidden", "Чужая коллекция", 403)
    CollectionItem.objects.get_or_create(collection=collection, post=post)
    Collection.objects.filter(pk=collection.pk).update(updated_at=timezone.now())
    return collection


def unsave_outfit(user, post, collection=None):
    qs = CollectionItem.objects.filter(collection__owner=user, post=post)
    if collection is not None:
        qs = qs.filter(collection=collection)
    qs.delete()


# ── Outfits ──────────────────────────────────────────────────────────────────


def _hashtags(explicit, description):
    tags = [t.strip().lstrip("#").lower() for t in (explicit or []) if t and t.strip().lstrip("#")]
    tags += [t.lower() for t in HASHTAG_RE.findall(description or "")]
    unique = []
    for tag in tags:
        tag = tag[:40]
        if tag and tag not in unique:
            unique.append(tag)
    return unique[:15]


@transaction.atomic
def create_outfit(user, data):
    gender = data["mannequin"]
    layers = data["layers"]
    if not layers:
        raise ApiError("empty_outfit", "Добавьте в образ хотя бы одну вещь")
    item_ids = {layer["item_id"] for layer in layers}
    items = {
        i.pk: i
        for i in ClothingItem.objects.filter(pk__in=item_ids, is_published=True).select_related("category")
    }
    missing = item_ids - set(items)
    if missing:
        raise ApiError("item_unavailable", "Некоторые вещи больше недоступны", 400)

    remix_of = None
    if data.get("remix_of"):
        remix_of = get_visible_outfit(user, data["remix_of"])

    post = OutfitPost.objects.create(
        user=user,
        mannequin_type=gender,
        description=data.get("description", "").strip(),
        visibility=data.get("visibility", "all"),
        remix_of=remix_of,
    )
    ordered = sorted(layers, key=lambda layer: layer.get("z", 0))
    PostClothingItem.objects.bulk_create(
        [
            PostClothingItem(
                post=post,
                clothing=items[layer["item_id"]],
                position_x=layer["x"],
                position_y=layer["y"],
                scale=layer["scale"],
                rotation=layer.get("rotation", 0.0),
                z_index=layer.get("z", 0),
                normalized=True,
                flipped=layer.get("flipped", False),
                fitted=layer.get("fitted", False),
            )
            for layer in ordered
        ]
    )
    for tag in _hashtags(data.get("hashtags"), post.description):
        hashtag, _ = Hashtag.objects.get_or_create(tag=tag)
        Hashtag.objects.filter(pk=hashtag.pk).update(usage_count=F("usage_count") + 1)
        post.hashtags.add(hashtag)

    mannequin = Mannequin.objects.filter(gender=gender).first()
    image = render_outfit(mannequin, [(items[layer["item_id"]], layer) for layer in ordered])
    post.final_image.save(f"outfit_{post.pk}.jpg", ContentFile(image), save=False)
    post.save(update_fields=["final_image"])

    if remix_of is not None:
        notify(remix_of.user, user, "remix", post=post)
    return post


def delete_outfit(user, post):
    if post.user_id != user.pk and not user.is_moderator:
        raise ApiError("forbidden", "Можно удалить только свой образ", 403)
    if post.final_image:
        post.final_image.delete(save=False)
    post.delete()


# ── Messaging ────────────────────────────────────────────────────────────────


def get_or_create_dialog(user, other):
    if user.pk == other.pk:
        raise ApiError("self_dialog", "Нельзя написать самому себе")
    u1, u2 = (user, other) if user.telegram_id < other.telegram_id else (other, user)
    dialog, _ = Dialog.objects.get_or_create(user1=u1, user2=u2)
    return dialog


def get_dialog(user, dialog_id):
    dialog = (
        Dialog.objects.select_related("user1", "user2")
        .filter(Q(user1=user) | Q(user2=user), pk=dialog_id)
        .first()
    )
    if dialog is None:
        raise ApiError("not_found", "Диалог не найден", 404)
    return dialog


@transaction.atomic
def send_message(user, dialog, text="", outfit=None):
    text = (text or "").strip()
    if not text and outfit is None:
        raise ApiError("empty_message", "Сообщение пустое")
    if len(text) > 4000:
        raise ApiError("message_too_long", "Сообщение длиннее 4000 символов")
    if outfit is not None and not text:
        # Keeps shared outfits readable in the Telegram Mini App, which parses this format.
        text = "[post:" + json.dumps(
            {
                "id": outfit.pk,
                "img": outfit.final_image.url if outfit.final_image else None,
                "author": outfit.user.first_name or outfit.user.username or "",
                "desc": (outfit.description or "")[:80],
            },
            ensure_ascii=False,
        ) + "]"
    message = Message.objects.create(dialog=dialog, sender=user, text=text, post=outfit)
    Dialog.objects.filter(pk=dialog.pk).update(updated_at=timezone.now())
    return message


def edit_message(user, message, text):
    if message.sender_id != user.pk:
        raise ApiError("forbidden", "Можно изменить только своё сообщение", 403)
    text = (text or "").strip()
    if not text:
        raise ApiError("empty_message", "Сообщение пустое")
    message.text = text
    message.edited = True
    message.save(update_fields=["text", "edited"])
    return message


def delete_message(user, message):
    if message.sender_id != user.pk:
        raise ApiError("forbidden", "Можно удалить только своё сообщение", 403)
    message.delete()


def mark_dialog_read(user, dialog):
    return (
        Message.objects.filter(dialog=dialog, is_read=False).exclude(sender=user).update(is_read=True)
    )


def unread_messages_count(user):
    return (
        Message.objects.filter(Q(dialog__user1=user) | Q(dialog__user2=user), is_read=False)
        .exclude(sender=user)
        .count()
    )


def resolve_user(user_id):
    user = TelegramUser.objects.filter(pk=user_id, is_banned=False).first()
    if user is None:
        raise ApiError("not_found", "Пользователь не найден", 404)
    return user
