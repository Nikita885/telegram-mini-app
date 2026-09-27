"""JWT access/refresh tokens for the mobile app, bound to TelegramUser."""

import uuid

import jwt
from django.conf import settings
from django.utils import timezone
from rest_framework import authentication, exceptions

from api.models import TelegramUser

from .models import RefreshToken

ALGORITHM = "HS256"


class AppUser:
    """Wraps TelegramUser so DRF permission checks (`is_authenticated`) work."""

    is_authenticated = True
    is_anonymous = False

    def __init__(self, tg_user: TelegramUser):
        self.tg = tg_user
        self.pk = tg_user.pk
        self.id = tg_user.pk

    def __getattr__(self, item):
        return getattr(self.tg, item)


def _encode(payload: dict) -> str:
    return jwt.encode(payload, settings.JWT_SIGNING_KEY, algorithm=ALGORITHM)


def decode(token: str, expected_type: str) -> dict:
    try:
        payload = jwt.decode(token, settings.JWT_SIGNING_KEY, algorithms=[ALGORITHM])
    except jwt.ExpiredSignatureError as exc:
        raise exceptions.AuthenticationFailed("token_expired") from exc
    except jwt.InvalidTokenError as exc:
        raise exceptions.AuthenticationFailed("token_invalid") from exc
    if payload.get("type") != expected_type:
        raise exceptions.AuthenticationFailed("token_invalid")
    return payload


def issue_tokens(user: TelegramUser) -> dict:
    now = timezone.now()
    access_exp = now + settings.JWT_ACCESS_TTL
    refresh_exp = now + settings.JWT_REFRESH_TTL
    jti = uuid.uuid4().hex
    RefreshToken.objects.create(jti=jti, user=user, expires_at=refresh_exp)
    access = _encode({"sub": str(user.pk), "type": "access", "iat": now, "exp": access_exp})
    refresh = _encode({"sub": str(user.pk), "type": "refresh", "jti": jti, "iat": now, "exp": refresh_exp})
    return {
        "access": access,
        "refresh": refresh,
        "access_expires_at": access_exp.isoformat(),
    }


def rotate_refresh(token: str) -> dict:
    """Exchange a refresh token for a new pair; the old one is revoked (reuse → all revoked)."""
    payload = decode(token, "refresh")
    record = RefreshToken.objects.select_related("user").filter(jti=payload.get("jti")).first()
    if record is None:
        raise exceptions.AuthenticationFailed("token_invalid")
    if record.revoked_at is not None:
        # A revoked token presented again means it leaked: log the user out everywhere.
        RefreshToken.objects.filter(user=record.user, revoked_at__isnull=True).update(revoked_at=timezone.now())
        raise exceptions.AuthenticationFailed("token_reused")
    if not record.is_active or record.user.is_banned:
        raise exceptions.AuthenticationFailed("token_invalid")
    record.revoked_at = timezone.now()
    record.save(update_fields=["revoked_at"])
    return issue_tokens(record.user)


def revoke_refresh(token: str):
    try:
        payload = jwt.decode(token, settings.JWT_SIGNING_KEY, algorithms=[ALGORITHM])
    except jwt.InvalidTokenError:
        return
    RefreshToken.objects.filter(jti=payload.get("jti"), revoked_at__isnull=True).update(revoked_at=timezone.now())


def user_from_access(token: str) -> TelegramUser:
    payload = decode(token, "access")
    user = TelegramUser.objects.filter(pk=payload.get("sub")).first()
    if user is None or user.is_banned:
        raise exceptions.AuthenticationFailed("user_inactive")
    return user


class JWTAuthentication(authentication.BaseAuthentication):
    keyword = "Bearer"

    def authenticate(self, request):
        header = authentication.get_authorization_header(request).decode("latin-1")
        if not header:
            return None
        parts = header.split()
        if len(parts) != 2 or parts[0] != self.keyword:
            raise exceptions.AuthenticationFailed("token_invalid")
        user = user_from_access(parts[1])
        return AppUser(user), parts[1]

    def authenticate_header(self, request):
        return self.keyword
