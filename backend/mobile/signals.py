"""Keep counters and realtime events consistent for writes from both the app and the Mini App."""

from django.db.models import F
from django.db.models.signals import post_delete, post_save
from django.dispatch import receiver

from api.models import Message, Notification, OutfitPost, PostComment

from .realtime import send_to_user


@receiver(post_save, sender=PostComment)
def comment_created(sender, instance, created, **kwargs):
    if created:
        OutfitPost.objects.filter(pk=instance.post_id).update(comments_count=F("comments_count") + 1)


@receiver(post_delete, sender=PostComment)
def comment_deleted(sender, instance, **kwargs):
    OutfitPost.objects.filter(pk=instance.post_id, comments_count__gt=0).update(
        comments_count=F("comments_count") - 1
    )


def _participants(dialog_id):
    from api.models import Dialog

    ids = Dialog.objects.filter(pk=dialog_id).values_list("user1_id", "user2_id").first()
    return ids or ()


@receiver(post_save, sender=Message)
def message_saved(sender, instance, created, **kwargs):
    event = "message.new" if created else "message.edited"
    payload = {
        "id": instance.pk,
        "dialog_id": instance.dialog_id,
        "sender_id": instance.sender_id,
        "text": "" if instance.post_id and instance.text.startswith("[post:") else instance.text,
        "outfit_id": instance.post_id,
        "created_at": instance.created_at.isoformat() if instance.created_at else None,
        "edited": instance.edited,
        "is_read": instance.is_read,
    }
    for user_id in _participants(instance.dialog_id):
        send_to_user(user_id, event, payload)


@receiver(post_delete, sender=Message)
def message_deleted(sender, instance, **kwargs):
    for user_id in _participants(instance.dialog_id):
        send_to_user(user_id, "message.deleted", {"id": instance.pk, "dialog_id": instance.dialog_id})


@receiver(post_save, sender=Notification)
def notification_created(sender, instance, created, **kwargs):
    if created:
        send_to_user(instance.recipient_id, "notification.new", {"id": instance.pk, "type": instance.notif_type})
