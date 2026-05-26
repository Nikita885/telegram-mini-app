FROM python:3.11-slim

WORKDIR /app

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/ .
COPY frontend/ /frontend/

CMD ["sh", "-c", "python manage.py migrate && python manage.py collectstatic --noinput && exec daphne -b 0.0.0.0 -p ${PORT:-8000} config.asgi:application"]
