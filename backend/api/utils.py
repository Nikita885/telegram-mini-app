import hashlib
import hmac
from urllib.parse import parse_qsl
from time import time

def verify_telegram_init_data(init_data: str, bot_token: str):
    """
    Проверяет подлинность данных, переданных от Telegram WebApp.
    Возвращает словарь с параметрами (без hash) или None при ошибке.
    """
    if not init_data:
        return None

    parsed = dict(parse_qsl(init_data, keep_blank_values=True))

    hash_from_telegram = parsed.pop('hash', None)
    if not hash_from_telegram:
        return None

    items = [f"{k}={v}" for k, v in sorted(parsed.items())]
    data_check_string = '\n'.join(items)

    secret_key = hmac.new(
        b"WebAppData",
        bot_token.encode("utf-8"),
        hashlib.sha256
    ).digest()

    calculated_hash = hmac.new(
        secret_key,
        data_check_string.encode("utf-8"),
        hashlib.sha256
    ).hexdigest()

    if calculated_hash != hash_from_telegram:
        return None

    auth_date = int(parsed.get('auth_date', 0))
    if abs(time() - auth_date) > 86400:
        return None

    return parsed