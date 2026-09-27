import secrets

from django.conf import settings
from django.db import models
from django.utils import timezone

from api.models import OutfitPost, TelegramUser


def _new_nonce():
    return secrets.token_urlsafe(16)


class LoginNonce(models.Model):
    """One-time login request confirmed through the Telegram bot (/start login_<nonce>)."""

    nonce = models.CharField(max_length=64, unique=True, default=_new_nonce)
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    confirmed_at = models.DateTimeField(null=True, blank=True)
    consumed_at = models.DateTimeField(null=True, blank=True)

    @property
    def is_expired(self):
        return timezone.now() - self.created_at > settings.LOGIN_NONCE_TTL

    def __str__(self):
        return f"LoginNonce({self.nonce[:6]}…, user={self.user_id})"


class RefreshToken(models.Model):
    """Server-side record of issued refresh tokens: enables rotation and logout."""

    jti = models.CharField(max_length=64, unique=True)
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name="refresh_tokens")
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()
    revoked_at = models.DateTimeField(null=True, blank=True)

    @property
    def is_active(self):
        return self.revoked_at is None and self.expires_at > timezone.now()


class Collection(models.Model):
    DEFAULT_TITLE = "Сохранённое"

    owner = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name="collections")
    title = models.CharField(max_length=60)
    is_default = models.BooleanField(default=False)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-is_default", "-updated_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["owner"], condition=models.Q(is_default=True), name="one_default_collection"
            )
        ]

    def __str__(self):
        return f"{self.owner}: {self.title}"

    @classmethod
    def default_for(cls, user):
        collection, _ = cls.objects.get_or_create(
            owner=user, is_default=True, defaults={"title": cls.DEFAULT_TITLE}
        )
        return collection


class CollectionItem(models.Model):
    collection = models.ForeignKey(Collection, on_delete=models.CASCADE, related_name="items")
    post = models.ForeignKey(OutfitPost, on_delete=models.CASCADE, related_name="saved_in")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]
        unique_together = ("collection", "post")


class Report(models.Model):
    TARGET_CHOICES = [("post", "Образ"), ("comment", "Комментарий"), ("user", "Пользователь")]
    STATUS_CHOICES = [("open", "Открыта"), ("resolved", "Решена"), ("rejected", "Отклонена")]

    reporter = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name="reports")
    target_type = models.CharField(max_length=10, choices=TARGET_CHOICES)
    target_id = models.BigIntegerField()
    reason = models.CharField(max_length=500)
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default="open")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"Report[{self.target_type}#{self.target_id}] {self.status}"
