package app.outfitshare.core.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.App;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Runs Retrofit calls and delivers a {@link Result} on the main thread. */
public final class Calls {
  private Calls() {}

  public interface OnResult<T> {
    void onResult(Result<T> result);
  }

  public static final class Result<T> {
    @Nullable public final T data;
    @Nullable public final ApiError error;
    /** Served from the offline cache: show the "офлайн" banner. */
    public final boolean fromCache;

    private Result(@Nullable T data, @Nullable ApiError error, boolean fromCache) {
      this.data = data;
      this.error = error;
      this.fromCache = fromCache;
    }

    public boolean ok() {
      return error == null;
    }

    public static <T> Result<T> success(@Nullable T data, boolean fromCache) {
      return new Result<>(data, null, fromCache);
    }

    public static <T> Result<T> failure(ApiError error) {
      return new Result<>(null, error, false);
    }
  }

  public static <T> Call<T> run(Call<T> call, OnResult<T> onResult) {
    call.enqueue(
        new Callback<T>() {
          @Override
          public void onResponse(@NonNull Call<T> c, @NonNull Response<T> response) {
            if (response.isSuccessful()) {
              boolean cached = response.headers().get(ApiClient.FROM_CACHE_HEADER) != null;
              onResult.onResult(Result.success(response.body(), cached));
            } else {
              onResult.onResult(Result.failure(ApiError.from(response, App.get().container().api.gson())));
            }
          }

          @Override
          public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
            if (c.isCanceled()) {
              return;
            }
            onResult.onResult(Result.failure(ApiError.from(t)));
          }
        });
    return call;
  }
}
