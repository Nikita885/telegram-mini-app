package app.outfitshare.core.designsystem.component.snackbar;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.haptics.Haptics;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

/**
 * Снекбары дизайн-системы.
 *
 * <p>Главный сценарий — «Отменить»: действие (удаление комментария, вещи из образа, снятие с
 * публикации) выполняется в интерфейсе сразу, а на сервер уходит только после закрытия снекбара без
 * отмены. Окно отмены — токен {@code duration.undoWindow}; при включённом TalkBack Material
 * увеличивает его по системной рекомендации.
 */
public final class DsSnackbar {

  private DsSnackbar() {}

  /**
   * Снекбар с действием «Отменить».
   *
   * @param onUndo вернуть состояние интерфейса
   * @param onCommit окно отмены истекло — отправить действие на сервер
   */
  @NonNull
  public static Snackbar undo(
      @NonNull View anchor,
      @NonNull CharSequence message,
      @NonNull Runnable onUndo,
      @Nullable Runnable onCommit) {
    int window = anchor.getResources().getInteger(R.integer.ds_motion_duration_undo_window);
    Snackbar snackbar = Snackbar.make(anchor, message, window);
    snackbar.setAction(R.string.ds_action_undo, v -> onUndo.run());
    snackbar.addCallback(
        new BaseTransientBottomBar.BaseCallback<Snackbar>() {
          @Override
          public void onDismissed(Snackbar bar, int event) {
            if (event != DISMISS_EVENT_ACTION && onCommit != null) {
              onCommit.run();
            }
          }
        });
    snackbar.show();
    return snackbar;
  }

  /** Короткое сообщение без действия: «Ссылка скопирована». */
  @NonNull
  public static Snackbar message(@NonNull View anchor, @NonNull CharSequence message) {
    int duration = anchor.getResources().getInteger(R.integer.ds_motion_duration_snackbar);
    Snackbar snackbar = Snackbar.make(anchor, message, duration);
    snackbar.show();
    return snackbar;
  }

  /** Ошибка действия с «Повторить» (если есть что повторять) и хаптикой отказа. */
  @NonNull
  public static Snackbar error(
      @NonNull View anchor, @NonNull CharSequence message, @Nullable Runnable retry) {
    int duration = anchor.getResources().getInteger(R.integer.ds_motion_duration_undo_window);
    Snackbar snackbar = Snackbar.make(anchor, message, duration);
    if (retry != null) {
      snackbar.setAction(R.string.ds_action_retry, v -> retry.run());
    }
    Haptics.perform(anchor, Haptics.Event.REJECT);
    snackbar.show();
    return snackbar;
  }
}
