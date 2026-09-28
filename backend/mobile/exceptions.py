"""Uniform error body for the mobile API: {"error": {"code", "message", "fields"?}}."""

from rest_framework import status
from rest_framework.exceptions import APIException, ValidationError
from rest_framework.response import Response
from rest_framework.views import exception_handler as drf_exception_handler

MESSAGES = {
    "not_authenticated": "Войдите, чтобы продолжить",
    "authentication_failed": "Сессия истекла — войдите снова",
    "permission_denied": "Недостаточно прав",
    "not_found": "Не найдено",
    "throttled": "Слишком много запросов — попробуйте чуть позже",
    "method_not_allowed": "Метод не поддерживается",
    "parse_error": "Некорректный запрос",
    "invalid": "Проверьте введённые данные",
}


class ApiError(APIException):
    """Business-rule error with a stable machine code."""

    status_code = status.HTTP_400_BAD_REQUEST

    def __init__(self, code: str, message: str, status_code: int = 400):
        super().__init__(detail=message, code=code)
        self.status_code = status_code
        self.error_code = code
        self.message = message


def exception_handler(exc, context):
    response = drf_exception_handler(exc, context)
    if response is None:
        return None
    if isinstance(exc, ApiError):
        body = {"code": exc.error_code, "message": exc.message}
    elif isinstance(exc, ValidationError):
        fields = exc.detail if isinstance(exc.detail, dict) else {"non_field_errors": exc.detail}
        body = {"code": "invalid", "message": MESSAGES["invalid"], "fields": fields}
    else:
        code = getattr(exc, "default_code", "error")
        detail = getattr(exc, "detail", "")
        detail_code = getattr(detail, "code", None)
        if code == "authentication_failed" and isinstance(detail, str) and detail in (
            "token_expired",
            "token_invalid",
            "token_reused",
            "user_inactive",
        ):
            code = str(detail)
        elif detail_code and detail_code not in ("invalid",):
            code = detail_code if code == "error" else code
        message = MESSAGES.get(code) or MESSAGES.get(getattr(exc, "default_code", ""), str(detail))
        body = {"code": code, "message": message}
    out = Response({"error": body}, status=response.status_code)
    for header in ("WWW-Authenticate", "Retry-After"):
        if header in response:
            out[header] = response[header]
    return out
