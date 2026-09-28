"""JSON shapes of the mobile API. Plain functions: explicit, fast, easy to keep N+1-free."""

from django.conf import settings


def media_url(field, request=None):
    if not field:
        return None
    try:
        url = field.url
    except ValueError:
        return None
    if url.startswith("http://") or url.startswith("https://"):
        return url
    if request is not None:
        return request.build_absolute_uri(url)
    return f"{settings.PUBLIC_BASE_URL}{url}"


def iso(dt):
    return dt.isoformat() if dt else None


def user_short(user, request=None):
    return {
        "id": user.pk,
        "username": user.username or "",
        "name": user.display_name,
        "avatar_url": media_url(user.avatar, request),
        "avatar_color": user.avatar_random_color or "#B8AEA1",
        "role": user.role,
    }


def profile(user, request, *, counts, is_following=False, follows_you=False, is_me=False):
    data = user_short(user, request)
    data.update(
        {
            "first_name": user.first_name or "",
            "last_name": user.last_name or "",
            "bio": user.bio,
            "outfits_count": counts.get("outfits", 0),
            "followers_count": counts.get("followers", 0),
            "following_count": counts.get("following", 0),
            "is_following": is_following,
            "follows_you": follows_you,
            "is_me": is_me,
        }
    )
    return data


def mannequin(m, request):
    from django.conf import settings as s

    width, height = s.MANNEQUIN_CANVAS_SIZE
    return {
        "gender": m.gender,
        "name": m.get_gender_display(),
        "canvas_url": media_url(m.canvas, request),
        "width": width,
        "height": height,
        "anchors": m.anchors,
    }


def category(c):
    return {
        "id": c.pk,
        "slug": c.name,
        "name": c.get_name_display(),
        "zone": c.zone,
        "default_layer": c.default_layer,
        "order": c.order,
    }


def item(i, request):
    return {
        "id": i.pk,
        "name": i.name,
        "category": i.category.name,
        "category_name": i.category.get_name_display(),
        "zone": i.category.zone,
        "default_layer": i.category.default_layer,
        "gender": i.gender,
        "style": i.style or "",
        "style_name": i.get_style_display() if i.style else "",
        "color": i.color or "",
        "season": i.season or "all",
        "brand": i.brand or "",
        "price": str(i.price) if i.price is not None else None,
        "buy_link": i.buy_link or "",
        "description": i.item_description or "",
        "image_url": media_url(i.image, request),
        "fitted": {
            "male": media_url(i.fitted_male, request),
            "female": media_url(i.fitted_female, request),
        },
        "fit_score": i.fit_score,
    }


def outfit(p, request, *, liked_ids=frozenset(), saved_ids=frozenset(), following_ids=frozenset(), viewer_id=None):
    items, seen = [], set()
    layers = []
    for layer in p.items.all():
        clothing = layer.clothing
        if clothing.pk not in seen:
            seen.add(clothing.pk)
            items.append(item(clothing, request))
        if layer.normalized:
            layers.append(
                {
                    "item_id": clothing.pk,
                    "x": layer.position_x,
                    "y": layer.position_y,
                    "scale": layer.scale,
                    "rotation": layer.rotation,
                    "z": layer.z_index,
                    "flipped": layer.flipped,
                    "fitted": layer.fitted,
                }
            )
    remix = None
    if p.remix_of_id and p.remix_of is not None:
        remix = {"id": p.remix_of_id, "author": user_short(p.remix_of.user, request)}
    return {
        "id": p.pk,
        "author": user_short(p.user, request),
        "image_url": media_url(p.final_image, request),
        "description": p.description,
        "hashtags": [t.tag for t in p.hashtags.all()],
        "mannequin": p.mannequin_type,
        "visibility": p.visibility,
        "likes_count": p.likes_count,
        "comments_count": p.comments_count,
        "is_liked": p.pk in liked_ids,
        "is_saved": p.pk in saved_ids,
        "is_mine": viewer_id == p.user_id,
        "author_followed": p.user_id in following_ids,
        "created_at": iso(p.created_at),
        "remix_of": remix,
        "items": items,
        "layers": layers,
    }


def comment(c, request, *, liked_ids=frozenset(), viewer_id=None, post_author_id=None):
    return {
        "id": c.pk,
        "outfit_id": c.post_id,
        "parent_id": c.parent_id,
        "author": user_short(c.user, request),
        "text": c.text,
        "likes_count": c.likes_count,
        "is_liked": c.pk in liked_ids,
        "is_post_author": c.user_id == post_author_id,
        "can_delete": viewer_id in (c.user_id, post_author_id),
        "created_at": iso(c.created_at),
    }


def outfit_preview(p, request):
    if p is None:
        return None
    return {
        "id": p.pk,
        "image_url": media_url(p.final_image, request),
        "author": user_short(p.user, request),
        "description": (p.description or "")[:120],
    }


def message(m, request, viewer_id):
    text = m.text
    if m.post_id and text.startswith("[post:"):
        text = ""
    return {
        "id": m.pk,
        "dialog_id": m.dialog_id,
        "sender_id": m.sender_id,
        "is_mine": m.sender_id == viewer_id,
        "text": text,
        "outfit": outfit_preview(m.post, request) if m.post_id else None,
        "created_at": iso(m.created_at),
        "edited": m.edited,
        "is_read": m.is_read,
    }


def message_preview_text(m):
    if m is None:
        return ""
    if m.post_id or m.text.startswith("[post:"):
        return "Образ"
    return m.text


def dialog(d, request, viewer, *, last_message=None, unread=0):
    other = d.user2 if d.user1_id == viewer.pk else d.user1
    return {
        "id": d.pk,
        "user": user_short(other, request),
        "last_message": (
            {
                "text": message_preview_text(last_message),
                "is_mine": last_message.sender_id == viewer.pk,
                "is_outfit": bool(last_message.post_id or last_message.text.startswith("[post:")),
                "is_read": last_message.is_read,
                "created_at": iso(last_message.created_at),
            }
            if last_message
            else None
        ),
        "unread_count": unread,
        "pinned": d.pinned_for(viewer),
        "updated_at": iso(d.updated_at),
    }


def notification(n, request):
    return {
        "id": n.pk,
        "type": n.notif_type,
        "actor": user_short(n.sender, request),
        "outfit": {"id": n.post_id, "image_url": media_url(n.post.final_image, request)} if n.post_id else None,
        "comment": {"id": n.comment_id, "text": n.comment.text[:140]} if n.comment_id and n.comment else None,
        "is_read": n.is_read,
        "created_at": iso(n.created_at),
    }


def collection(c, request, covers):
    return {
        "id": c.pk,
        "title": c.title,
        "is_default": c.is_default,
        "is_private": c.is_private,
        "count": getattr(c, "items_count", 0),
        "covers": covers,
        "updated_at": iso(c.updated_at),
    }


def garment_job(j, request):
    return {
        "id": j.pk,
        "status": j.status,
        "stage": j.stage,
        "stages": j.STAGES,
        "progress": j.progress,
        "error": j.error,
        "category": j.category.name,
        "category_name": j.category.get_name_display(),
        "zone": j.category.zone,
        "gender": j.gender,
        "source_url": media_url(j.source, request),
        "cutout_url": media_url(j.cutout, request),
        "fitted": {"male": media_url(j.fitted_male, request), "female": media_url(j.fitted_female, request)},
        "preview": {"male": media_url(j.preview_male, request), "female": media_url(j.preview_female, request)},
        "attributes": j.attributes,
        "keypoints": j.keypoints,
        "fit_score": j.fit_score,
        "fit_threshold": settings.STUDIO_FIT_SCORE_THRESHOLD,
        "item_id": j.item_id,
        "created_at": iso(j.created_at),
        "updated_at": iso(j.updated_at),
    }
