package app.outfitshare.core.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.net.ApiError;

/** Screen states with the copy from the mockups. */
public final class States {
  private States() {}

  public static ScreenState empty(@DrawableRes int art, String title, @Nullable String text, @Nullable String action) {
    return ScreenState.builder(ScreenState.Kind.EMPTY).illustration(art).title(title).message(text).action(action).build();
  }

  public static ScreenState empty(
      @DrawableRes int art, String title, @Nullable String text, @Nullable String action, @Nullable String secondary) {
    return ScreenState.builder(ScreenState.Kind.EMPTY)
        .illustration(art).title(title).message(text).action(action).secondaryAction(secondary).build();
  }

  /** Network failure: offline (no cache) or a server error with the screen's own title. */
  public static ScreenState failure(ApiError error, String errorTitle) {
    if (error.isOffline()) {
      return ScreenState.builder(ScreenState.Kind.OFFLINE)
          .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_offline)
          .title("Нет подключения")
          .message("Всё обновится само, как только появится сеть.")
          .action("Повторить")
          .build();
    }
    if (error.kind == ApiError.Kind.FORBIDDEN || error.kind == ApiError.Kind.NOT_FOUND) {
      return ScreenState.builder(ScreenState.Kind.NO_PERMISSION)
          .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_locked)
          .title(error.kind == ApiError.Kind.NOT_FOUND ? "Не найдено" : "Нет доступа")
          .message(error.message)
          .build();
    }
    return ScreenState.builder(ScreenState.Kind.ERROR)
        .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_error)
        .title(errorTitle)
        .message("Проверьте подключение и попробуйте ещё раз.")
        .action("Повторить")
        .build();
  }

  public static ScreenState locked(String title, String text, @Nullable String action) {
    return ScreenState.builder(ScreenState.Kind.NO_PERMISSION)
        .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_locked).title(title).message(text).action(action).build();
  }
}
