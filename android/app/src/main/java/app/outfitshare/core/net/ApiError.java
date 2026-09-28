package app.outfitshare.core.net;

import androidx.annotation.Nullable;
import app.outfitshare.core.net.dto.Dto;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.Map;
import retrofit2.Response;

/** An API failure translated into something a screen can show. */
public final class ApiError {
  public enum Kind {
    OFFLINE,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CLIENT,
    SERVER
  }

  public final Kind kind;
  public final int status;
  public final String code;
  public final String message;
  @Nullable public final Map<String, Object> fields;

  private ApiError(Kind kind, int status, String code, String message, @Nullable Map<String, Object> fields) {
    this.kind = kind;
    this.status = status;
    this.code = code;
    this.message = message;
    this.fields = fields;
  }

  public boolean isOffline() {
    return kind == Kind.OFFLINE;
  }

  public static ApiError offline() {
    return new ApiError(Kind.OFFLINE, 0, "offline", "Нет подключения к сети", null);
  }

  static ApiError from(Throwable t) {
    if (t instanceof IOException) {
      return offline();
    }
    return new ApiError(Kind.CLIENT, 0, "unexpected", "Что-то пошло не так", null);
  }

  static ApiError from(Response<?> response, Gson gson) {
    int status = response.code();
    String code = "http_" + status;
    String message = status >= 500 ? "Сервер не ответил. Попробуйте ещё раз" : "Не получилось выполнить запрос";
    Map<String, Object> fields = null;
    try (okhttp3.ResponseBody body = response.errorBody()) {
      if (body != null) {
        Dto.ErrorEnvelope env = gson.fromJson(body.charStream(), Dto.ErrorEnvelope.class);
        if (env != null && env.error != null) {
          code = env.error.code != null ? env.error.code : code;
          message = env.error.message != null ? env.error.message : message;
          fields = env.error.fields;
        }
      }
    } catch (RuntimeException ignored) {
      // Non-JSON error page (proxy): keep the generic message.
    }
    Kind kind;
    if (status == 401) {
      kind = Kind.UNAUTHORIZED;
    } else if (status == 403) {
      kind = Kind.FORBIDDEN;
    } else if (status == 404 || status == 410) {
      kind = Kind.NOT_FOUND;
    } else if (status >= 500) {
      kind = Kind.SERVER;
    } else {
      kind = Kind.CLIENT;
    }
    return new ApiError(kind, status, code, message, fields);
  }

  /** First validation message for a field, if the server sent one. */
  @Nullable
  public String fieldMessage(String field) {
    if (fields == null || !fields.containsKey(field)) {
      return null;
    }
    Object value = fields.get(field);
    if (value instanceof java.util.List && !((java.util.List<?>) value).isEmpty()) {
      return String.valueOf(((java.util.List<?>) value).get(0));
    }
    return value == null ? null : String.valueOf(value);
  }
}
