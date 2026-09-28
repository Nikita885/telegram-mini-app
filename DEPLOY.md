# Развёртывание на сервере Timeweb (Ubuntu) — moiservis.pro

Разворачивается текущее рабочее приложение: Django + PostgreSQL + Redis в Docker, nginx с HTTPS снаружи.
HTTPS обязателен — Telegram открывает Mini App только по `https://`.

## 0. Перед началом

1. **Перевыпусти токен бота**: старый лежит в git (`backend/config/settings.py`), считай его скомпрометированным.
   @BotFather → `/mybots` → бот → *API Token* → *Revoke current token*. Новый токен понадобится в шаге 3.
2. Проверь, что DNS уже указывает на сервер (с любого компьютера):
   ```bash
   nslookup moiservis.pro
   ```
   Должно быть `90.156.208.209`. Если нужен `www`, добавь в DNS вторую A-запись `www → 90.156.208.209`.

## 1. Подключение и подготовка сервера

```bash
ssh root@90.156.208.209
```

Дальше все команды — на сервере.

```bash
apt update && apt upgrade -y
apt install -y git nginx certbot python3-certbot-nginx ufw
curl -fsSL https://get.docker.com | sh
```

Файрвол (открыты только SSH и веб; PostgreSQL и Redis наружу не торчат):

```bash
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable
```

## 2. Код

```bash
mkdir -p /opt && cd /opt
git clone https://github.com/Nikita885/telegram-mini-app.git app
cd /opt/app
git checkout main
mkdir -p media
```

## 3. Переменные окружения

Сгенерируй секреты:

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(50))"
python3 -c "import secrets; print(secrets.token_urlsafe(24))"
```

Создай `backend/.env` (подставь свои значения вместо `<...>`):

```bash
cat > backend/.env <<'EOF'
DJANGO_SETTINGS_MODULE=config.settings_prod
SECRET_KEY=<первая_строка_из_генератора>
TG_BOT_TOKEN=<новый_токен_от_BotFather>
DOMAIN=moiservis.pro

POSTGRES_DB=outfits
POSTGRES_USER=outfits
POSTGRES_PASSWORD=<вторая_строка_из_генератора>
POSTGRES_HOST=db
POSTGRES_PORT=5432
REDIS_HOST=redis

# Необязательно: если хочешь хранить фото в Cloudinary, а не на сервере
# CLOUDINARY_CLOUD_NAME=
# CLOUDINARY_API_KEY=
# CLOUDINARY_API_SECRET=
EOF
chmod 600 backend/.env
```

## 4. Продакшен-настройки Django

В репозитории `DEBUG=True`, секреты захардкожены и нет `CSRF_TRUSTED_ORIGINS` (без него не войти в `/admin/` по HTTPS).
Этот файл переопределяет всё это из `.env`, не трогая исходный `settings.py`:

```bash
cat > backend/config/settings_prod.py <<'EOF'
import os

from .settings import *  # noqa: F401,F403

DEBUG = False
SECRET_KEY = os.environ["SECRET_KEY"]
TG_BOT_TOKEN = os.environ["TG_BOT_TOKEN"]

_domain = os.environ.get("DOMAIN", "moiservis.pro")
ALLOWED_HOSTS = [_domain, f"www.{_domain}", "localhost", "127.0.0.1"]
CSRF_TRUSTED_ORIGINS = [f"https://{_domain}", f"https://www.{_domain}"]

SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
# Telegram Desktop/Web открывает Mini App в iframe — без SameSite=None сессия не сохранится
SESSION_COOKIE_SAMESITE = "None"

LANGUAGE_CODE = "ru-ru"
TIME_ZONE = "Asia/Yekaterinburg"
EOF
```

## 5. Docker Compose для продакшена

Отдельный файл: БД и Redis без проброса портов наружу, приложение слушает только `127.0.0.1:8000` (снаружи — через nginx), автоперезапуск.

```bash
cat > docker-compose.prod.yml <<'EOF'
services:
  db:
    image: postgres:16
    env_file: ./backend/.env
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

  redis:
    image: redis:7
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    restart: unless-stopped

  backend:
    build: ./backend
    env_file: ./backend/.env
    command: >
      sh -c "./scripts/wait-for-it.sh db:5432 --strict --timeout=60 &&
             ./scripts/wait-for-it.sh redis:6379 --strict --timeout=60 &&
             python manage.py migrate --noinput &&
             python manage.py collectstatic --noinput --clear &&
             exec daphne -b 0.0.0.0 -p 8000 config.asgi:application"
    volumes:
      - ./frontend:/frontend
      - ./media:/app/media
    ports:
      - "127.0.0.1:8000:8000"
    depends_on:
      - db
      - redis
    restart: unless-stopped

volumes:
  postgres_data:
  redis_data:
EOF
```

Запуск:

```bash
chmod +x backend/scripts/wait-for-it.sh
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend
```

Жди строку `Listening on TCP address 0.0.0.0:8000`, потом `Ctrl+C` (контейнер продолжит работать).

## 6. Начальные данные и админ

```bash
DC="docker compose -f docker-compose.prod.yml exec backend"
$DC python manage.py init_categories
$DC python manage.py init_mannequins --path /frontend/static/images
$DC python manage.py createsuperuser
```

`createsuperuser` спросит email, username и пароль — это вход в `/admin/`, где заводится каталог одежды.

## 7. nginx + HTTPS

```bash
cat > /etc/nginx/sites-available/moiservis.pro <<'EOF'
server {
    listen 80;
    server_name moiservis.pro www.moiservis.pro;

    client_max_body_size 25M;

    location /media/ {
        alias /opt/app/media/;
        expires 30d;
        access_log off;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
    }

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
ln -sf /etc/nginx/sites-available/moiservis.pro /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

Сертификат Let's Encrypt (certbot сам допишет HTTPS в конфиг и включит автопродление):

```bash
certbot --nginx -d moiservis.pro -d www.moiservis.pro --redirect -m <твой_email> --agree-tos -n
```

(Если `www` в DNS не добавлял — убери `-d www.moiservis.pro`.)

Проверка: открой `https://moiservis.pro/admin/` — должна быть страница входа Django.

## 8. Подключение к Telegram

В @BotFather:
1. `/mybots` → бот → *Bot Settings* → *Menu Button* → URL: `https://moiservis.pro/authorize/`
2. (Опционально) `/newapp` — чтобы Mini App открывалась по ссылке `t.me/<бот>/<имя>`, тот же URL.

Открой бота в Telegram → кнопка меню → приложение должно авторизовать тебя и открыть ленту.
Если видишь «initData пустой» — ты открыл страницу в обычном браузере, а не из Telegram.

## 9. Обслуживание

| Задача | Команда (из `/opt/app`) |
|---|---|
| Логи | `docker compose -f docker-compose.prod.yml logs -f backend` |
| Обновить код | `git pull && docker compose -f docker-compose.prod.yml up -d --build` |
| Перезапуск | `docker compose -f docker-compose.prod.yml restart backend` |
| Бэкап БД | `docker compose -f docker-compose.prod.yml exec -T db pg_dump -U outfits outfits > backup_$(date +%F).sql` |
| Бэкап фото | `tar czf media_$(date +%F).tgz media` |

`settings_prod.py`, `docker-compose.prod.yml` и `backend/.env` создаются только на сервере — `git pull` их не затрёт.
