#!/bin/sh
# Roles: web | worker | bot | <any command>
set -e

wait_for_db() {
  python - <<'PY'
import os, sys, time
import django
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
django.setup()
from django.db import connection
for attempt in range(60):
    try:
        connection.ensure_connection()
        sys.exit(0)
    except Exception as exc:  # noqa: BLE001
        print(f"waiting for database... ({exc.__class__.__name__})", flush=True)
        time.sleep(2)
sys.exit("database is not reachable")
PY
}

case "$1" in
  web)
    wait_for_db
    python manage.py migrate --noinput
    python manage.py collectstatic --noinput --clear -v 0
    python manage.py setup_catalog
    exec daphne -b 0.0.0.0 -p 8000 --proxy-headers config.asgi:application
    ;;
  worker)
    wait_for_db
    exec celery -A config worker -Q ml,celery -c "${WORKER_CONCURRENCY:-1}" --loglevel=INFO
    ;;
  bot)
    wait_for_db
    exec python manage.py run_bot
    ;;
  *)
    exec "$@"
    ;;
esac
