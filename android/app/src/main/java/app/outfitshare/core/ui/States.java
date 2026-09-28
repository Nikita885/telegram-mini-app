package app.outfitshare.core.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.net.ApiError;

/** Screen states with the copy from the mockups. */
public final class States {
  private States() {}

  public static ScreenState empty(
      @DrawableRes int art, String title, @Nullable String text, @Nullable String action) {
    return ScreenState.builder(ScreenState.Kind.EMPTY)
        .illustration(art)
        .title(title)
        .message(text)
        .action(action)
        .build();
  }

  public static ScreenState empty(
      @DrawableRes int art,
      String title,
      @Nullable String text,
      @Nullable String action,
      @Nullable String secondary) {
    return ScreenState.builder(ScreenState.Kind.EMPTY)
        .illustration(art)
        .title(title)
        .message(text)
        .action(action)
        .secondaryAction(secondary)
        .build();
  }

  /** Network failure: offline (no cache) or a server error with the screen's own title. */
  public static ScreenState failure(ApiError error, String errorTitle) {
    if (error.isOffline()) {
      return ScreenState.builder(ScreenState.Kind.OFFLINE)
          .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_offline)
          .title(Res.str(app.outfitshare.R.string.state_offline_title))
          .message(Res.str(app.outfitshare.R.string.state_offline_text))
          .action(Res.str(app.outfitshare.R.string.action_retry))
          .build();
    }
    if (error.kind == ApiError.Kind.FORBIDDEN || error.kind == ApiError.Kind.NOT_FOUND) {
      return ScreenState.builder(ScreenState.Kind.NO_PERMISSION)
          .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_locked)
          .title(
              Res.str(
                  error.kind == ApiError.Kind.NOT_FOUND
                      ? app.outfitshare.R.string.state_not_found
                      : app.outfitshare.R.string.state_forbidden))
          .message(error.message)
          .build();
    }
    return ScreenState.builder(ScreenState.Kind.ERROR)
        .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_error)
        .title(errorTitle)
        .message(Res.str(app.outfitshare.R.string.state_error_text))
        .action(Res.str(app.outfitshare.R.string.action_retry))
        .build();
  }

  public static ScreenState locked(String title, String text, @Nullable String action) {
    return ScreenState.builder(ScreenState.Kind.NO_PERMISSION)
        .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_locked)
        .title(title)
        .message(text)
        .action(action)
        .build();
  }
}
