"""Media files served by the app itself (a front proxy may serve /media/ directly instead)."""

import mimetypes
import os

from django.conf import settings
from django.http import Http404, HttpResponse, HttpResponseNotModified
from django.utils.http import http_date

# Slim images ship no /etc/mime.types; the stdlib table misses these.
mimetypes.add_type("image/webp", ".webp")
mimetypes.add_type("image/avif", ".avif")


def serve_media(request, path):
    """Whole-file response: uploads are small, and one read beats chunked streaming under ASGI."""
    root = os.path.realpath(settings.MEDIA_ROOT)
    full = os.path.realpath(os.path.join(root, path))
    if not full.startswith(root + os.sep) or not os.path.isfile(full):
        raise Http404
    stat = os.stat(full)
    etag = f'"{int(stat.st_mtime)}-{stat.st_size}"'
    if request.headers.get("If-None-Match") == etag:
        return HttpResponseNotModified()
    with open(full, "rb") as f:
        data = f.read()
    content_type = mimetypes.guess_type(full)[0] or "application/octet-stream"
    response = HttpResponse(data, content_type=content_type)
    response["ETag"] = etag
    response["Last-Modified"] = http_date(stat.st_mtime)
    response["Cache-Control"] = "public, max-age=86400"
    return response
