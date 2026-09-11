# Telegram Mini App — примерка одежды

Telegram Mini App для примерки и заказа одежды прямо в Telegram, с админ-панелью для управления каталогом. Дипломный проект (ВКР).

## Задача

Пользователь Telegram открывает мини-приложение, выбирает манекен, «примеряет» вещи из каталога в реальном времени (обновления через WebSocket) и оформляет заказ. Администратор через Django-админку управляет каталогом одежды, манекенами и остатками на складе.

## Стек технологий

- **Backend:** Django 4.2, Django REST Framework, Django Channels (WebSocket, ASGI-сервер Daphne)
- **БД и кэш:** PostgreSQL 16, Redis (слой каналов Channels)
- **Хранение медиа:** Cloudinary
- **Frontend:** Django-шаблоны + JavaScript/CSS
- **Инфраструктура:** Docker Compose, деплой на Railway

## Как запустить локально

```bash
git clone https://github.com/Nikita885/telegram-mini-app.git
cd telegram-mini-app
cp backend/.env.example backend/.env
# заполнить SECRET_KEY и данные для PostgreSQL в backend/.env
docker compose up --build
```

Backend поднимется на `http://localhost:8000` (ASGI-сервер Daphne, WebSocket через Channels).

**Переменные окружения** (`backend/.env`):

| Переменная | Назначение |
|---|---|
| `DEBUG` | Режим отладки Django |
| `SECRET_KEY` | Секретный ключ Django |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Доступ к БД |
| `POSTGRES_HOST` / `POSTGRES_PORT` | Хост и порт БД |
