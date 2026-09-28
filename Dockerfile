# Outfit Share backend: Django (HTTP + WebSocket via Daphne), Celery worker, Telegram bot.
# One image, the role is the command: web | worker | bot (see backend/docker-entrypoint.sh).
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    # rembg downloads its ONNX models here; docker-compose keeps them in a volume.
    U2NET_HOME=/app/models \
    # numba (used by rembg's matting) caches compiled code; site-packages is read-only for "app".
    NUMBA_CACHE_DIR=/app/models/.numba

WORKDIR /app

COPY backend/requirements.txt .
RUN pip install -r requirements.txt

RUN useradd --create-home --uid 1000 app
COPY backend/ .
COPY frontend/ /frontend/
RUN chmod +x docker-entrypoint.sh \
    && mkdir -p media models staticfiles \
    && chown -R app:app media models staticfiles

EXPOSE 8000
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["web"]
