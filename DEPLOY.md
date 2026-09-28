# Развёртывание на сервере — moiservis.pro (90.156.208.209)

Поднимаем стек Outfit Share: Django API и Telegram Mini App, Celery-воркер Студии, Telegram-бот,
PostgreSQL и Redis. Всё в Docker Compose **рядом с уже работающим проектом**. Наш стек изолирован:
у него своё имя проекта `outfitshare`, свои тома (`outfitshare_*`), своя сеть, а наружу он слушает
только `127.0.0.1:8010`. Чужие контейнеры, тома и порты не трогаем.

Команды выполняются на сервере под `root`. Строки с `<...>` замените своими значениями.

---

## 0. Перед началом (на своём компьютере)

1. **Перевыпустите токен бота.** Старый токен лежит в истории git — считайте его украденным.
   @BotFather → `/mybots` → ваш бот → *API Token* → *Revoke current token*. Новый токен и
   username бота (без `@`) понадобятся в шаге 5.
   Если старая версия Mini App на сервере работает с этим же ботом, после отзыва токена вход в неё
   перестанет работать. Это нормально: новый стек её заменит (шаги 6 и 11).
2. **DNS.** Запись `A moiservis.pro → 90.156.208.209` уже есть. Проверка:
   ```bash
   nslookup moiservis.pro      # должно вернуть 90.156.208.209
   ```
   Нужен ещё и `www` — добавьте вторую A-запись `www → 90.156.208.209` и дождитесь её (TTL 600 = 10 минут).
3. **Код.** Все изменения лежат в ветке `claude/design-system-screens-m0z0fh`. Удобнее всего слить её
   в `main` через Pull Request на GitHub и деплоить `main`. Можно деплоить и прямо эту ветку (шаг 4).

---

## 1. Подключиться и осмотреться (ничего не меняем)

```bash
ssh root@90.156.208.209

free -h && nproc && df -h /
docker --version && docker compose version
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
ss -tlnp | grep -E ':(80|443|5432|6379|8000|8010) '
systemctl is-active nginx; ls /etc/nginx/sites-enabled/ 2>/dev/null
grep -rl "moiservis.pro" /etc/nginx/ 2>/dev/null
```

По результатам определите свой случай.

| Что видно | Что это значит | Куда дальше |
|---|---|---|
| `nginx` — `active`, порты 80/443 слушает `nginx` | nginx стоит прямо на сервере | вариант **A** в шаге 8 |
| 80/443 никто не слушает | порты свободны | вариант **B** (встроенный Caddy) |
| 80/443 слушает `docker-proxy` | их занял контейнер другого проекта (его nginx/traefik) | вариант **C** |
| порт `8010` уже занят | его занимает другой проект | в шаге 5 задайте другой `APP_PORT`, например `8020` |
| в `docker ps` есть `0.0.0.0:5432` или `0.0.0.0:6379` | **база или Redis другого проекта открыты в интернет** (Docker обходит ufw) | закройте, см. шаг 11 |

**Это старая версия этого же приложения?** Если среди контейнеров есть связка `*-backend` + `*-db` +
`*-redis`, найдите папку, из которой она запущена:

```bash
docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' <имя-контейнера-backend>
ls <эта-папка>            # есть backend/api и frontend/ — значит, это старый Outfit Share / Mini App
```

Если это она, дальше понадобится перенос данных (шаг 6). Если это другой, не связанный проект, —
пропустите шаги 6 и 11, он продолжит работать как раньше.

---

## 2. Память и swap

Студии (нейросеть, вырезающая фон) на время обработки одного фото нужно около **2,5 ГБ** памяти.
Остальному стеку хватает ~0,5 ГБ. Если в `free -h` меньше 4 ГБ всего или строка `Swap:` пустая,
добавьте swap:

```bash
swapon --show                      # пусто — swap нет
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

На сервере с 2 ГБ RAM в шаге 5 поставьте `STUDIO_BG_MODEL=u2netp` и `WORKER_MEMORY=2500m`.

---

## 3. Docker

Раз другой проект уже на Docker, он установлен. Нужен плагин Compose v2: `docker compose version`
должен вывести `v2.x`. Если команда не найдена:

```bash
apt update && apt install -y docker-compose-plugin
# если Docker нет вообще:  curl -fsSL https://get.docker.com | sh
```

---

## 4. Код

Отдельная папка, чтобы не пересечься с другим проектом:

```bash
mkdir -p /opt && cd /opt
git clone https://github.com/Nikita885/telegram-mini-app.git outfitshare
cd /opt/outfitshare
git checkout main          # после слияния PR; до слияния: git checkout claude/design-system-screens-m0z0fh
```

Если репозиторий приватный, git спросит логин и пароль. Вместо пароля вставьте Personal Access Token
(GitHub → Settings → Developer settings → Tokens, право `repo: read`).

---

## 5. Настройки (`.env`)

```bash
cd /opt/outfitshare
cp .env.example .env
python3 -c "import secrets; print(secrets.token_urlsafe(50))"   # → SECRET_KEY
python3 -c "import secrets; print(secrets.token_urlsafe(24))"   # → POSTGRES_PASSWORD
nano .env
chmod 600 .env
```

Что заполнить обязательно:

| Переменная | Значение |
|---|---|
| `SECRET_KEY` | первая строка генератора |
| `TG_BOT_TOKEN` | **новый** токен из шага 0 |
| `TG_BOT_USERNAME` | username бота без `@` |
| `POSTGRES_PASSWORD` | вторая строка генератора |
| `APP_PORT` | `8010`, или свободный порт, если 8010 занят (проверка: `ss -tlnp \| grep ':8010 '` — пусто) |

Остальное уже настроено под `moiservis.pro`. Если добавили `www`, допишите его в `ALLOWED_HOSTS` и
`CSRF_TRUSTED_ORIGINS`. Если старая версия хранила фото в Cloudinary (в её `.env` есть
`CLOUDINARY_*`), перенесите эти три строки как есть, иначе старые картинки пропадут.

---

## 6. Перенос данных из старой версии (только если она есть на сервере)

Выполняется **до первого запуска** нового стека. `OLD` — папка старой версии из шага 1.

```bash
OLD=/opt/app                                     # ваша папка
grep -E '^(POSTGRES_|CLOUDINARY_)' $OLD/backend/.env $OLD/.env 2>/dev/null
docker ps --format '{{.Names}}' | grep -i db     # имя контейнера старой базы
```

1. **Дамп старой базы** (пользователь и база — из вывода `grep` выше, обычно `outfits`):
   ```bash
   docker exec <старый-db-контейнер> pg_dump -U <POSTGRES_USER> -d <POSTGRES_DB> -Fc > /root/outfits-old.dump
   ls -lh /root/outfits-old.dump                  # файл не пустой
   ```
2. **Поднять только новую базу и восстановить дамп:**
   ```bash
   cd /opt/outfitshare
   docker compose up -d db redis
   docker compose ps                              # db — healthy
   docker compose exec -T db pg_restore -U outfits -d outfits --no-owner --no-privileges < /root/outfits-old.dump
   ```
   (`outfits`/`outfits` — это `POSTGRES_USER`/`POSTGRES_DB` из нового `.env`.)
3. Выполните шаг 7: при старте `web` сам применит новые миграции к перенесённой базе. Закрепления
   диалогов, сообщения, образы и пользователи сохраняются.
4. **Перенести фото** (если не Cloudinary) и дать контейнерам поправить права:
   ```bash
   docker run --rm -v outfitshare_media:/dst -v $OLD/media:/src:ro alpine cp -a /src/. /dst/
   docker compose restart web worker
   ```
5. **Переименовать старые картинки образов** в неугадываемые имена. Раньше приватный образ
   открывался по ссылке `/media/outfits/outfit_<номер>.jpg`:
   ```bash
   docker compose exec web python manage.py rename_outfit_images
   ```

---

## 7. Запуск

```bash
cd /opt/outfitshare
docker compose up -d --build          # первая сборка 5–10 минут
docker compose ps                     # web — healthy, worker/bot/db/redis — running
docker compose logs -f web            # ждём "Listening on TCP address 0.0.0.0:8000", выход — Ctrl+C
curl -s http://127.0.0.1:8010/health/ # {"status": "ok"}
```

При старте `web` сам применяет миграции, собирает статику и создаёт категории и манекены.

---

## 8. HTTPS и домен

### Вариант A — nginx на сервере (самый вероятный)

```bash
cd /opt/outfitshare
cp deploy/nginx/outfitshare.conf /etc/nginx/sites-available/outfitshare.conf
nano /etc/nginx/sites-available/outfitshare.conf   # если APP_PORT не 8010 — поправьте порт в proxy_pass
```

Если в шаге 1 `grep` нашёл конфиг с `moiservis.pro` (обычно от старой версии), отключите его.
Проверьте, что в файле нет доменов **другого** проекта. Если есть, удалите из него только блок
`server { ... }` с `moiservis.pro`, а не весь файл.

```bash
mv /etc/nginx/sites-enabled/<старый-файл> /root/<старый-файл>.disabled
ln -s /etc/nginx/sites-available/outfitshare.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
apt install -y certbot python3-certbot-nginx        # если certbot ещё нет
certbot --nginx -d moiservis.pro                     # с www: -d moiservis.pro -d www.moiservis.pro
ufw status                                           # если active:  ufw allow 'Nginx Full'
```

Если сертификат для домена уже выпускался, certbot предложит переустановить его — соглашайтесь
(*Attempt to reinstall*).

### Вариант B — порты 80/443 свободны: встроенный Caddy

```bash
cd /opt/outfitshare
docker compose --profile caddy up -d
ufw status                                           # если active:  ufw allow 80,443/tcp
```

Caddy сам получит сертификат Let's Encrypt. Фото он раздаёт напрямую из тома.

### Вариант C — 80/443 занял прокси другого проекта в Docker

Добавьте в его конфиг сайт `moiservis.pro`. Сначала подключите тот контейнер к нашей сети:

```bash
docker network connect outfitshare_default <контейнер-прокси>
```

Для nginx внутри контейнера в `server` для `moiservis.pro` укажите
`proxy_pass http://outfitshare-web-1:8000;`, остальное скопируйте из
`deploy/nginx/outfitshare.conf`. Сертификат выпускайте так же, как этот прокси выпускает остальные.
Команду `network connect` нужно повторять после пересоздания контейнера-прокси. Если здесь
сомневаетесь, пришлите вывод шага 1 — подскажу точные строки.

---

## 9. Бот, Mini App и администратор

```bash
docker compose logs bot          # "Bot @<имя> started"
```

- Ошибка `Conflict: terminated by other getUpdates request` значит, что этот токен использует ещё
  один процесс (старая версия или локальный запуск). Остановите его.
- Повторяется `getMe: ConnectionError — retrying` — сервер не достаёт до `api.telegram.org`
  (у российских хостингов Telegram часто заблокирован). Проверка: `curl -4 -m 10 -sI https://api.telegram.org`.
  Решение — раздел 9а.

### 9а. Если Telegram заблокирован на сервере: webhook-режим

Telegram сам присылает события на `https://moiservis.pro/tg/webhook/`, а бот отвечает в том же
HTTP-ответе — исходящих соединений к Telegram сервер не делает вообще.

```bash
cd /opt/outfitshare
echo "TG_WEBHOOK_SECRET=$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')" >> .env
docker compose up -d
docker compose logs bot --tail 3                          # "Webhook mode: ..."
docker compose exec web python manage.py telegram_webhook   # напечатает две ссылки
```

Первую ссылку («установить») откройте в браузере **там, где Telegram работает** (телефон с VPN,
компьютер не в России) — ответ `{"ok":true,...}`. Вторая («проверить») покажет `pending_update_count`
и `last_error_message`, если Telegram не может достучаться до сервера. Ссылки содержат токен — не
публикуйте их.

Не работает только загрузка фото профиля «из Telegram» (сервер не может скачать фото) — приложение
предложит выбрать фото из галереи. Вернуться к обычному режиму: убрать `TG_WEBHOOK_SECRET` из `.env`,
`docker compose up -d` — бот сам снимет webhook, как только Telegram станет доступен.

**Альтернатива — прокси.** Если есть HTTP- или SOCKS5-прокси за рубежом, бот может ходить через него
в обычном режиме: создайте `docker-compose.override.yml`

```yaml
services:
  bot:
    environment:
      HTTPS_PROXY: socks5h://<логин>:<пароль>@<адрес>:<порт>   # или http://...
  web:
    environment:
      HTTPS_PROXY: socks5h://<логин>:<пароль>@<адрес>:<порт>
```

и выполните `docker compose up -d`.
- **Mini App в Telegram:** @BotFather → `/mybots` → бот → *Bot Settings* → *Menu Button* →
  URL `https://moiservis.pro/authorize/`. Если Mini App настроен через *Configure Mini App*, укажите
  тот же адрес.
- **Администратор Студии.** Войдите один раз в приложение или Mini App через Telegram, затем:
  ```bash
  docker compose exec web python manage.py create_admin --telegram-id <ваш Telegram ID>   # ID подскажет @userinfobot
  ```
- **Админка Django** (каталог, жалобы, пользователи):
  ```bash
  docker compose exec web python manage.py createsuperuser
  ```
  Вход — `https://moiservis.pro/admin/`.

---

## 10. Проверка

- `https://moiservis.pro/health/` → `{"status": "ok"}`
- `https://moiservis.pro/authorize/` — открывается Mini App; в Telegram — кнопкой меню бота.
- Приложение: «Войти через Telegram» → бот показывает устройство и тот же 4-значный код, что и
  приложение → «✅ Это я, войти» → приложение входит само.
- Студия (под администратором): фото вещи → через 10–60 секунд вещь в статусе «Готово к проверке».
- Поделиться образом → ссылка `https://moiservis.pro/o/<номер>` открывает страницу с превью.

---

## 11. Выключить старую версию (если переносили данные)

Только после проверки шага 10:

```bash
cd $OLD && docker compose down          # без -v: её данные остаются на диске для отката
docker ps --format 'table {{.Names}}\t{{.Ports}}'   # не должно остаться 0.0.0.0:5432 и 0.0.0.0:6379
```

Если открытые наружу порты 5432/6379 принадлежат другому, **не связанному** проекту, в его
`docker-compose.yml` замените `"5432:5432"` на `"127.0.0.1:5432:5432"` (и так же для 6379) и
перезапустите его. Иначе его база доступна всему интернету.

**Откат:** `cd /opt/outfitshare && docker compose down`, затем `cd $OLD && docker compose up -d` и
верните старый конфиг nginx (`/root/<старый-файл>.disabled`).

---

## 12. Ссылки сразу в приложении (App Links) — после сборки APK

Когда подпишете APK (см. `android/README.md`), узнайте SHA-256 отпечаток сертификата и допишите его
в `.env`:

```bash
ANDROID_APP_CERT_SHA256=AA:BB:CC:...    # несколько через запятую
docker compose up -d web
curl -s https://moiservis.pro/.well-known/assetlinks.json   # JSON с пакетом app.outfitshare
```

После этого ссылки `https://moiservis.pro/o/...` и `/u/...` открываются сразу в приложении.

---

## 13. Обновления

```bash
cd /opt/outfitshare
git pull
docker compose up -d --build     # миграции применятся сами
```

---

## 14. Резервные копии

```bash
mkdir -p /root/backups
cat > /root/backups/outfitshare.sh <<'EOF'
#!/bin/sh
set -e
cd /opt/outfitshare
docker compose exec -T db pg_dump -U outfits -d outfits -Fc > /root/backups/outfits-$(date +%F).dump
docker run --rm -v outfitshare_media:/m:ro -v /root/backups:/b alpine tar czf /b/media-$(date +%F).tgz -C /m .
find /root/backups -name 'outfits-*.dump' -mtime +14 -delete
find /root/backups -name 'media-*.tgz' -mtime +14 -delete
EOF
chmod +x /root/backups/outfitshare.sh
(crontab -l 2>/dev/null; echo "30 4 * * * /root/backups/outfitshare.sh") | crontab -
```

Восстановление базы: `docker compose exec -T db pg_restore -U outfits -d outfits --clean --if-exists < /root/backups/outfits-<дата>.dump`.

---

## 15. Если что-то не так

| Симптом | Где смотреть | Что обычно помогает |
|---|---|---|
| 502 Bad Gateway | `docker compose ps`, `docker compose logs web` | дождаться `healthy`; проверить порт в конфиге nginx = `APP_PORT` |
| `DisallowedHost` / 400 | `docker compose logs web` | домен в `ALLOWED_HOSTS` в `.env`, затем `docker compose up -d` |
| certbot не выпускает сертификат | вывод certbot | DNS указывает на сервер, порт 80 открыт (`ufw allow 'Nginx Full'`) |
| бот молчит | `docker compose logs bot` | новый `TG_BOT_TOKEN`; другой процесс с этим токеном остановлен |
| задача Студии «Не удалось обработать» | `docker compose logs worker` | памяти мало — swap (шаг 2) или `STUDIO_BG_MODEL=u2netp` |
| приложение не входит | в приложении видно ошибку | сервер доступен по `https://moiservis.pro`, бот запущен, код в боте совпадает |

Полезное: `docker compose logs -f --tail=100 <web|worker|bot>`, `docker stats`, `df -h`.

**Не запускайте** `docker system prune -a --volumes` и `docker volume prune` — они удалят и данные
другого проекта.
