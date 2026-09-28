"""Django settings. Everything environment-specific comes from environment variables (see .env.example)."""

import os
from datetime import timedelta
from pathlib import Path
from urllib.parse import urlparse

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")


def env(name, default=None):
    return os.environ.get(name, default)


def env_bool(name, default=False):
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


def env_list(name, default=""):
    return [item.strip() for item in os.environ.get(name, default).split(",") if item.strip()]


DEBUG = env_bool("DEBUG", False)

SECRET_KEY = env("SECRET_KEY") or ("dev-insecure-key" if DEBUG else None)
if not SECRET_KEY:
    raise RuntimeError("SECRET_KEY must be set when DEBUG is off")

DOMAIN = env("DOMAIN", "localhost")
ALLOWED_HOSTS = env_list("ALLOWED_HOSTS", f"{DOMAIN},www.{DOMAIN},localhost,127.0.0.1,backend")
CSRF_TRUSTED_ORIGINS = env_list("CSRF_TRUSTED_ORIGINS", f"https://{DOMAIN},https://www.{DOMAIN}")

# Public base URL used to build absolute media links for the mobile app.
PUBLIC_BASE_URL = env("PUBLIC_BASE_URL", f"https://{DOMAIN}").rstrip("/")

TG_BOT_TOKEN = env("TG_BOT_TOKEN", "")
TG_BOT_USERNAME = env("TG_BOT_USERNAME", "").lstrip("@")
# Webhook mode (mobile/webhook.py): set to a random string when the server cannot reach api.telegram.org.
TG_WEBHOOK_SECRET = env("TG_WEBHOOK_SECRET", "")

INSTALLED_APPS = [
    "daphne",
    "api",
    "mobile",
    "studio",
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "channels",
]

CLOUDINARY_CLOUD_NAME = env("CLOUDINARY_CLOUD_NAME")
if CLOUDINARY_CLOUD_NAME:
    INSTALLED_APPS[1:1] = ["cloudinary_storage", "cloudinary"]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "config.middleware.SameOriginUnsafeRequestsMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

FRONTEND_DIR = Path(env("FRONTEND_DIR", str(BASE_DIR.parent / "frontend")))
if not FRONTEND_DIR.exists() and Path("/frontend").exists():
    FRONTEND_DIR = Path("/frontend")

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [FRONTEND_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

# ── Redis / Channels / Celery ────────────────────────────────────────────────
REDIS_URL = env("REDIS_URL") or f"redis://{env('REDIS_HOST', 'redis')}:{env('REDIS_PORT', '6379')}/0"

if env_bool("CHANNELS_IN_MEMORY", False):
    CHANNEL_LAYERS = {"default": {"BACKEND": "channels.layers.InMemoryChannelLayer"}}
else:
    CHANNEL_LAYERS = {
        "default": {
            "BACKEND": "channels_redis.core.RedisChannelLayer",
            "CONFIG": {"hosts": [REDIS_URL]},
        }
    }

# Cache backs DRF throttling and the "For you" feed snapshots, so it must be shared by all processes.
if env_bool("CHANNELS_IN_MEMORY", False):
    CACHES = {"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}}
else:
    CACHES = {
        "default": {
            "BACKEND": "django.core.cache.backends.redis.RedisCache",
            "LOCATION": REDIS_URL,
            "KEY_PREFIX": "outfitshare",
        }
    }

CELERY_BROKER_URL = REDIS_URL
CELERY_RESULT_BACKEND = None
CELERY_TASK_ALWAYS_EAGER = env_bool("CELERY_TASK_ALWAYS_EAGER", False)
CELERY_TASK_EAGER_PROPAGATES = True
CELERY_TASK_ROUTES = {"studio.tasks.*": {"queue": "ml"}}
CELERY_TASK_ACKS_LATE = True
CELERY_WORKER_PREFETCH_MULTIPLIER = 1

# ── Database ─────────────────────────────────────────────────────────────────
_database_url = env("DATABASE_URL")
if _database_url and _database_url.startswith("sqlite"):
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": _database_url.split("///", 1)[1] or ":memory:",
        }
    }
elif _database_url:
    _db = urlparse(_database_url)
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": _db.path[1:],
            "USER": _db.username,
            "PASSWORD": _db.password,
            "HOST": _db.hostname,
            "PORT": _db.port or 5432,
            "CONN_MAX_AGE": 60,
        }
    }
else:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": env("POSTGRES_DB", "outfits"),
            "USER": env("POSTGRES_USER", "outfits"),
            "PASSWORD": env("POSTGRES_PASSWORD", ""),
            "HOST": env("POSTGRES_HOST", "db"),
            "PORT": env("POSTGRES_PORT", "5432"),
            "CONN_MAX_AGE": 60,
        }
    }

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = "ru-ru"
TIME_ZONE = env("TIME_ZONE", "Asia/Yekaterinburg")
USE_I18N = True
USE_TZ = True

# ── Static & media ───────────────────────────────────────────────────────────
STATIC_URL = "/static/"
STATICFILES_DIRS = [FRONTEND_DIR / "static"] if (FRONTEND_DIR / "static").exists() else []
STATIC_ROOT = BASE_DIR / "staticfiles"

MEDIA_URL = "/media/"
MEDIA_ROOT = Path(env("MEDIA_ROOT", str(BASE_DIR / "media")))

STORAGES = {
    "default": {"BACKEND": "django.core.files.storage.FileSystemStorage"},
    "staticfiles": {"BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage"},
}

if CLOUDINARY_CLOUD_NAME:
    CLOUDINARY_STORAGE = {
        "CLOUD_NAME": CLOUDINARY_CLOUD_NAME,
        "API_KEY": env("CLOUDINARY_API_KEY"),
        "API_SECRET": env("CLOUDINARY_API_SECRET"),
    }
    STORAGES["default"] = {"BACKEND": "cloudinary_storage.storage.MediaCloudinaryStorage"}

DATA_UPLOAD_MAX_MEMORY_SIZE = 25 * 1024 * 1024
FILE_UPLOAD_MAX_MEMORY_SIZE = 10 * 1024 * 1024

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
AUTH_USER_MODEL = "api.CustomUser"

# ── Security ─────────────────────────────────────────────────────────────────
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
USE_X_FORWARDED_HOST = True
if not DEBUG:
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True
    # Telegram Desktop/Web opens the Mini App inside an iframe.
    SESSION_COOKIE_SAMESITE = "None"
    SECURE_CONTENT_TYPE_NOSNIFF = True
    X_FRAME_OPTIONS = "SAMEORIGIN"

# ── REST framework & JWT ─────────────────────────────────────────────────────
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": ["mobile.auth.JWTAuthentication"],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.IsAuthenticated"],
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_THROTTLE_CLASSES": ["rest_framework.throttling.ScopedRateThrottle"],
    "DEFAULT_THROTTLE_RATES": {
        "auth": "30/min",
        "auth_poll": "120/min",
        "write": "120/min",
        "upload": "30/min",
    },
    # Client IP for throttling = the address the HTTPS proxy in front of us saw (last X-Forwarded-For
    # entry). Values a client puts into X-Forwarded-For itself are ignored.
    "NUM_PROXIES": int(env("TRUSTED_PROXIES", "1")),
    "EXCEPTION_HANDLER": "mobile.exceptions.exception_handler",
}

JWT_SIGNING_KEY = env("JWT_SIGNING_KEY", SECRET_KEY)
JWT_ACCESS_TTL = timedelta(minutes=int(env("JWT_ACCESS_MINUTES", "30")))
JWT_REFRESH_TTL = timedelta(days=int(env("JWT_REFRESH_DAYS", "60")))
# A just-rotated refresh token is accepted once more within this window (lost responses on mobile).
JWT_REFRESH_REUSE_GRACE = timedelta(seconds=int(env("JWT_REFRESH_REUSE_GRACE_SECONDS", "60")))
LOGIN_NONCE_TTL = timedelta(minutes=5)

# SHA-256 fingerprints of the app signing certificate(s), comma-separated, for Android App Links
# (/.well-known/assetlinks.json): shared https links then open straight in the app.
ANDROID_APP_CERT_SHA256 = env_list("ANDROID_APP_CERT_SHA256", "")

# Lets the app sign in with just a Telegram ID. Only for local development.
ALLOW_DEV_LOGIN = env_bool("ALLOW_DEV_LOGIN", False)

# ── Studio (garment → mannequin pipeline) ────────────────────────────────────
STUDIO_BG_MODEL = env("STUDIO_BG_MODEL", "isnet-general-use")
STUDIO_FIT_SCORE_THRESHOLD = float(env("STUDIO_FIT_SCORE_THRESHOLD", "0.6"))
MANNEQUIN_CANVAS_SIZE = (750, 1000)

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {"plain": {"format": "%(asctime)s %(levelname)s %(name)s: %(message)s"}},
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "plain"}},
    "root": {"handlers": ["console"], "level": env("LOG_LEVEL", "INFO")},
}
