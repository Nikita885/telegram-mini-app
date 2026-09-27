"""ASGI entrypoint: HTTP via Django, WebSockets via Channels.

/ws/v1/ is the mobile app socket (JWT in the query string, no browser Origin);
everything else is the Telegram Mini App socket (session cookie + Origin check).
"""

import os

from django.core.asgi import get_asgi_application

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
django_asgi_app = get_asgi_application()

from channels.auth import AuthMiddlewareStack  # noqa: E402
from channels.routing import ProtocolTypeRouter, URLRouter  # noqa: E402
from channels.security.websocket import AllowedHostsOriginValidator  # noqa: E402
from django.urls import re_path  # noqa: E402

import api.routing  # noqa: E402
from mobile.consumers import AppConsumer  # noqa: E402

mini_app_ws = AllowedHostsOriginValidator(AuthMiddlewareStack(URLRouter(api.routing.websocket_urlpatterns)))
app_ws = URLRouter([re_path(r"^ws/v1/$", AppConsumer.as_asgi())])


async def websocket_router(scope, receive, send):
    if scope.get("path", "").startswith("/ws/v1/"):
        return await app_ws(scope, receive, send)
    return await mini_app_ws(scope, receive, send)


application = ProtocolTypeRouter({"http": django_asgi_app, "websocket": websocket_router})
