package app.outfitshare.core.designsystem.component.dialog;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import app.outfitshare.core.designsystem.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Objects;

/**
 * Диалог подтверждения. Используется только для необратимых действий без «Отменить» (выход,
 * удаление аккаунта, блокировка). Обратимое удаление — сразу, со снекбаром «Отменить».
 *
 * <pre>{@code
 * ConfirmDialog.with(context)
 *     .title("Удалить черновик?")
 *     .message("Образ исчезнет со всех устройств.")
 *     .confirm("Удалить")
 *     .destructive()
 *     .onConfirm(viewModel::deleteDraft)
 *     .show();
 * }</pre>
 */
public final class ConfirmDialog {

  private final Context context;
  @Nullable private CharSequence title;
  @Nullable private CharSequence message;
  @Nullable private CharSequence confirmLabel;
  @Nullable private CharSequence cancelLabel;
  private boolean destructive;
  @Nullable private Runnable onConfirm;
  @Nullable private Runnable onCancel;

  private ConfirmDialog(Context context) {
    this.context = Objects.requireNonNull(context);
  }

  /** Начинает описание диалога. */
  @NonNull
  public static ConfirmDialog with(@NonNull Context context) {
    return new ConfirmDialog(context);
  }

  @NonNull
  public ConfirmDialog title(@NonNull CharSequence title) {
    this.title = title;
    return this;
  }

  @NonNull
  public ConfirmDialog message(@Nullable CharSequence message) {
    this.message = message;
    return this;
  }

  /** Подпись кнопки подтверждения — глагол действия («Удалить», «Выйти»), не «ОК». */
  @NonNull
  public ConfirmDialog confirm(@NonNull CharSequence label) {
    this.confirmLabel = label;
    return this;
  }

  @NonNull
  public ConfirmDialog cancel(@NonNull CharSequence label) {
    this.cancelLabel = label;
    return this;
  }

  /** Кнопка подтверждения окрашивается в {@code danger}. */
  @NonNull
  public ConfirmDialog destructive() {
    this.destructive = true;
    return this;
  }

  @NonNull
  public ConfirmDialog onConfirm(@NonNull Runnable action) {
    this.onConfirm = action;
    return this;
  }

  @NonNull
  public ConfirmDialog onCancel(@Nullable Runnable action) {
    this.onCancel = action;
    return this;
  }

  /** Показывает диалог. */
  @NonNull
  public AlertDialog show() {
    if (title == null || confirmLabel == null) {
      throw new IllegalStateException("ConfirmDialog: нужны title и confirm");
    }
    int overlay =
        destructive
            ? R.style.ThemeOverlay_Ds_MaterialAlertDialog_Destructive
            : R.style.ThemeOverlay_Ds_MaterialAlertDialog;
    @StringRes int defaultCancel = R.string.ds_action_cancel;
    MaterialAlertDialogBuilder builder =
        new MaterialAlertDialogBuilder(context, overlay)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                confirmLabel,
                (dialog, which) -> {
                  if (onConfirm != null) {
                    onConfirm.run();
                  }
                })
            .setOnCancelListener(
                dialog -> {
                  if (onCancel != null) {
                    onCancel.run();
                  }
                });
    if (cancelLabel != null) {
      builder.setNegativeButton(cancelLabel, (dialog, which) -> dialog.cancel());
    } else {
      builder.setNegativeButton(defaultCancel, (dialog, which) -> dialog.cancel());
    }
    return builder.show();
  }
}
