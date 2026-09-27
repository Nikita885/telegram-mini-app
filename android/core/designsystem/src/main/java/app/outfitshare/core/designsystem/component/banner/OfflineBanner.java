package app.outfitshare.core.designsystem.component.banner;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import app.outfitshare.core.designsystem.motion.Motion;
import com.google.android.material.button.MaterialButton;

/**
 * Баннер «Офлайн — показываем сохранённое» над кэшированным контентом.
 *
 * <p>Если кэша нет, вместо баннера экран показывает {@code StateView} в состоянии {@code OFFLINE}.
 * Появление и исчезновение — сдвиг сверху; текст — live region.
 */
public class OfflineBanner extends LinearLayout {

  private final TextView text;
  private final MaterialButton action;

  public OfflineBanner(@NonNull Context context) {
    this(context, null);
  }

  public OfflineBanner(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsOfflineBannerStyle);
  }

  public OfflineBanner(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_OfflineBanner);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.ds_view_offline_banner, this, true);
    text = findViewById(R.id.ds_banner_text);
    action = findViewById(R.id.ds_banner_action);
    A11y.announceChanges(text);
    setVisibility(GONE);
  }

  /** Действие «Повторить»; {@code null} — без кнопки. */
  public void setOnRetryClickListener(@Nullable OnClickListener listener) {
    action.setOnClickListener(listener);
    action.setVisibility(listener == null ? GONE : VISIBLE);
  }

  /** Показывает или прячет баннер. */
  public void setOffline(boolean offline) {
    if (offline == (getVisibility() == VISIBLE)) {
      return;
    }
    text.setText(offline ? R.string.ds_offline_banner : R.string.ds_offline_banner_back_online);
    animate().cancel();
    if (!Motion.animationsEnabled()) {
      setVisibility(offline ? VISIBLE : GONE);
      return;
    }
    float offset = getResources().getDimension(R.dimen.ds_motion_enter_offset);
    if (offline) {
      setVisibility(VISIBLE);
      setAlpha(0f);
      setTranslationY(-offset);
      animate()
          .alpha(1f)
          .translationY(0f)
          .setDuration(Motion.durationMedium(getContext()))
          .setInterpolator(Motion.emphasizedDecelerate(getContext()))
          .start();
    } else {
      animate()
          .alpha(0f)
          .translationY(-offset)
          .setDuration(Motion.durationShort(getContext()))
          .setInterpolator(Motion.emphasizedAccelerate(getContext()))
          .withEndAction(() -> setVisibility(View.GONE))
          .start();
    }
  }
}
