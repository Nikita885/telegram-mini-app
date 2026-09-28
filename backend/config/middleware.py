"""Cross-site request protection for the Telegram Mini App endpoints.

The Mini App authenticates with a session cookie that must be SameSite=None (Telegram Web embeds it in
an iframe), and its JSON endpoints are csrf_exempt because the frontend sends no CSRF token. Without
another check any website could make a signed-in visitor's browser like, follow, comment or delete
dialogs. Browsers always attach Origin (or at least Referer) to cross-site unsafe requests, so those
headers are verified here instead. The mobile API (/api/v1/) uses bearer tokens and is not affected.
"""

from urllib.parse import urlsplit

from django.conf import settings
from django.http import JsonResponse

SAFE_METHODS = {"GET", "HEAD", "OPTIONS", "TRACE"}
EXEMPT_PREFIXES = ("/api/v1/",)


def _origin_of(url: str) -> str:
    parts = urlsplit(url)
    return f"{parts.scheme}://{parts.netloc}" if parts.scheme and parts.netloc else "null"


class SameOriginUnsafeRequestsMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if request.method not in SAFE_METHODS and not request.path.startswith(EXEMPT_PREFIXES):
            origin = request.META.get("HTTP_ORIGIN")
            if origin is None and request.META.get("HTTP_REFERER"):
                origin = _origin_of(request.META["HTTP_REFERER"])
            if origin is not None and origin not in self._allowed(request):
                return JsonResponse({"error": "Cross-site request blocked"}, status=403)
        return self.get_response(request)

    @staticmethod
    def _allowed(request):
        own = f"{request.scheme}://{request.get_host()}"
        return {own, *settings.CSRF_TRUSTED_ORIGINS}
