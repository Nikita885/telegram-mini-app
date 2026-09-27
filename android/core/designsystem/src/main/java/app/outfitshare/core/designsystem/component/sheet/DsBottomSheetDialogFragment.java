package app.outfitshare.core.designsystem.component.sheet;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Базовый bottom sheet: ручка перетаскивания, заголовок антиквой, контент наследника.
 *
 * <p>Используется для комментариев, «Ещё» у образа, выбора коллекции, фильтров поиска, выбора
 * категории/зоны в Студии. Тема — {@code ThemeOverlay.Ds.BottomSheetDialog}: молочная поверхность,
 * верхние углы 20 dp, затемнение {@code scrim}, ширина на планшете не больше 640 dp.
 */
public abstract class DsBottomSheetDialogFragment extends BottomSheetDialogFragment {

  /** Заголовок листа или {@code null}. */
  @Nullable
  protected CharSequence getSheetTitle() {
    return null;
  }

  /** Раскрывать сразу на всю высоту контента, минуя промежуточное состояние. */
  protected boolean isSkipCollapsed() {
    return true;
  }

  /** Создаёт содержимое листа. */
  @NonNull
  protected abstract View onCreateSheetContent(
      @NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state);

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.setDismissWithAnimation(true);
    dialog.getBehavior().setSkipCollapsed(isSkipCollapsed());
    return dialog;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View frame = inflater.inflate(R.layout.ds_bottom_sheet_frame, container, false);
    TextView title = frame.findViewById(R.id.ds_sheet_title);
    CharSequence text = getSheetTitle();
    title.setText(text);
    title.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    A11y.setHeading(title);
    ViewGroup content = frame.findViewById(R.id.ds_sheet_content);
    content.addView(onCreateSheetContent(inflater, content, savedInstanceState));
    return frame;
  }
}
