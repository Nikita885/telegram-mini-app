# Outfit Share

Соцсеть образов: пользователь собирает образ на манекене из вещей каталога, публикует его, лайкает,
комментирует, сохраняет в коллекции, делает ремиксы и переписывается с другими. Три клиента одного
бэкенда:

- **Telegram Mini App** — Django-шаблоны и JavaScript (`frontend/`, `backend/api`), вход по `initData`;
- **приложение для Android** (`android/`) — вход через Telegram-бота с подтверждением кода;
- **Студия** в приложении (для администраторов): фото вещи → вырезание фона и посадка на манекен
  нейросетевым пайплайном (`backend/studio`) → публикация в каталог.

## Стек

- **Backend:** Django 5.2, Django REST Framework, JWT (access + refresh с ротацией), Django Channels
  и Daphne (WebSocket), Celery (очередь Студии), Python 3.12
- **Данные:** PostgreSQL 16, Redis (каналы, кэш, лимиты, брокер Celery), медиа на диске или в Cloudinary
- **Студия:** rembg (ONNX), numpy/scipy — вырезание фона, опорные точки, посадка на манекен
- **Android:** Java 17, Material 3, собственная дизайн-система, Retrofit/OkHttp, CameraX, WorkManager
- **Инфраструктура:** Docker Compose (web, worker, bot, db, redis, опционально Caddy), GitHub Actions

## Структура

```
backend/            Django: api (Mini App), mobile (API v1 для приложения, бот), studio (пайплайн)
frontend/           шаблоны и статика Mini App
android/            приложение: :app и :core:designsystem  → android/README.md
docs/design/        дизайн-система: токены, компоненты, макеты всех экранов
tools/designsystem/ генераторы токенов, шрифтов, иконок и иллюстраций
deploy/             Caddyfile, конфиг nginx
DEPLOY.md           развёртывание на сервере
```

## Локальный запуск бэкенда

```bash
cd backend
python3.12 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.dev .env                 # SQLite, всё в памяти, тестовый вход без бота
python manage.py migrate
python manage.py setup_catalog   # категории и манекены
python manage.py runserver 0.0.0.0:8000
```

API — `http://localhost:8000/api/v1/`, Mini App — `http://localhost:8000/authorize/` (нужен Telegram).
Приложение в эмуляторе ходит на `http://10.0.2.2:8000` (см. `android/README.md`).

Весь стек в Docker, как на сервере: `cp .env.example .env`, заполнить, затем `docker compose up -d --build`.

## Проверки

```bash
cd backend && ruff check . && set -a && . ./.env.test && set +a && python manage.py test
python3 tools/designsystem/generate_tokens.py --check && python3 -m pytest -q tools/designsystem/tests
cd android && ./gradlew spotlessCheck lintDebug testDebugUnitTest
```

CI (`.github/workflows/ci.yml`) выполняет всё это, а также собирает Docker-образ и debug APK.

## Развёртывание

Пошагово, в том числе рядом с другим проектом на том же сервере и с переносом данных из старой
версии: [`DEPLOY.md`](DEPLOY.md).

## Дизайн-система

Редакционная дизайн-система Android-клиента (токены, компоненты, движение, доступность, макеты всех
экранов со всеми состояниями) — в [`docs/design`](docs/design/README.md); Android-модуль —
[`android/core/designsystem`](android/core/designsystem/README.md).
