#!/bin/sh
# Roles: web | worker | bot | <any command>
set -e

# Started as root: make the volumes writable for the unprivileged user, then drop privileges.
# (Volumes created by older versions of the stack, or copied in during a migration, may be root-owned.)
if [ "$(id -u)" = "0" ]; then
  for dir in /app/media /app/models /app/staticfiles; do
    mkdir -p "$dir"
    find "$dir" ! -user app -exec chown app:app {} +
  done
  exec setpriv --reuid=app --regid=app --init-groups "$0" "$@"
fi

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
    exec daphne -b 0.0.0.0 -p "${PORT:-8000}" --proxy-headers config.asgi:application
    ;;
  worker)
    wait_for_db
    # A fresh child per Studio job: onnxruntime keeps its memory arena after inference, and the
    # few seconds of model loading are cheaper than holding ~3 GB between rare admin uploads.
    exec celery -A config worker -Q ml,celery -c "${WORKER_CONCURRENCY:-1}" --max-tasks-per-child=1 --loglevel=INFO
    ;;
  bot)
    wait_for_db
    exec python manage.py run_bot
    ;;
  *)
    exec "$@"
    ;;
esac
