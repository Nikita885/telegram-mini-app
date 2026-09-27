package app.outfitshare.core.designsystem.component.button;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.outfitshare.core.designsystem.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

/**
 * Кнопка дизайн-системы с состоянием загрузки («Опубликовать» → индикатор).
 *
 * <p>Варианты задаются стилем: {@code Widget.Ds.Button.Primary|Secondary|Ghost|Danger|Icon}. Во
 * время загрузки кнопка не принимает нажатия, текст сохраняется (ширина не прыгает), а TalkBack
 * слышит «Загрузка…» как состояние.
 */
public class DsButton extends MaterialButton {

  private boolean loading;
  @Nullable private Drawable savedIcon;
  private int savedIconGravity;

  public DsButton(@NonNull Context context) {
    super(context);
  }

  public DsButton(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public DsButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public boolean isLoading() {
    return loading;
  }

  /** Включает/выключает индикатор загрузки на месте иконки. */
  public void setLoading(boolean loading) {
    if (this.loading == loading) {
      return;
    }
    this.loading = loading;
    if (loading) {
      savedIcon = getIcon();
      savedIconGravity = getIconGravity();
      setIconGravity(ICON_GRAVITY_TEXT_START);
      setIcon(createProgressDrawable());
      setClickable(false);
      ViewCompat.setStateDescription(this, getResources().getString(R.string.ds_state_loading));
    } else {
      setIcon(savedIcon);
      setIconGravity(savedIconGravity);
      savedIcon = null;
      setClickable(true);
      ViewCompat.setStateDescription(this, null);
    }
  }

  private Drawable createProgressDrawable() {
    CircularProgressIndicatorSpec spec =
        new CircularProgressIndicatorSpec(
            getContext(), null, 0, R.style.Widget_Ds_CircularProgress_InButton);
    spec.indicatorColors = new int[] {getCurrentTextColor()};
    IndeterminateDrawable<CircularProgressIndicatorSpec> drawable =
        IndeterminateDrawable.createCircularDrawable(getContext(), spec);
    drawable.start();
    return drawable;
  }
}
