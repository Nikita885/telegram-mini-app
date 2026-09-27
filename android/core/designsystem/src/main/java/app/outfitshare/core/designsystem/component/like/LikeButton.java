package app.outfitshare.core.designsystem.component.like;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.motion.Motion;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.designsystem.tokens.DsMotionTokens;

/**
 * Кнопка «Нравится» со счётчиком.
 *
 * <p>Поведение: оптимистичный переключатель — состояние меняется сразу, приложение получает {@link
 * OnLikeChangeListener} и отправляет запрос; при ошибке вызывает {@link #setLiked(boolean, long)} с
 * прежним значением. При лайке — «пружинка» сердца (токен {@code spring.heart}) и хаптика {@code
 * LIKE}; снятие лайка — без вибрации.
 *
 * <p>Доступность: роль переключателя, состояние «отмечено/не отмечено», число отметок в описании.
 */
public class LikeButton extends LinearLayout implements Checkable {

  /** Пользователь переключил лайк. */
  public interface OnLikeChangeListener {
    /** Вызывается после оптимистичного обновления кнопки. */
    void onLikeChanged(@NonNull LikeButton button, boolean liked);
  }

  private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

  private final ImageView icon;
  private final TextView countView;
  private final CountFormatter formatter;
  private final boolean showCount;

  private boolean checked;
  private long count;
  @Nullable private OnLikeChangeListener listener;
  @Nullable private SpringAnimation springX;
  @Nullable private SpringAnimation springY;

  public LikeButton(@NonNull Context context) {
    this(context, null);
  }

  public LikeButton(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsLikeButtonStyle);
  }

  public LikeButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_LikeButton);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setClickable(true);
    setFocusable(true);
    LayoutInflater.from(context).inflate(R.layout.ds_view_like_button, this, true);
    icon = findViewById(R.id.ds_like_icon);
    countView = findViewById(R.id.ds_like_count);

    boolean onPhoto;
    TypedArray a =
        context.obtainStyledAttributes(
            attrs, R.styleable.LikeButton, defStyleAttr, R.style.Widget_Ds_LikeButton);
    try {
      showCount = a.getBoolean(R.styleable.LikeButton_dsLikeShowCount, true);
      onPhoto = a.getBoolean(R.styleable.LikeButton_dsLikeOnPhoto, false);
    } finally {
      a.recycle();
    }
    if (onPhoto) {
      setBackground(ContextCompat.getDrawable(context, R.drawable.ds_bg_on_photo_button));
      ImageViewCompat.setImageTintList(
          icon, DsTheme.colorStateList(context, R.color.ds_like_tint_on_photo));
      countView.setTextColor(DsTheme.color(context, R.attr.dsColorOnPhoto));
    }

    formatter =
        new CountFormatter(
            context.getString(R.string.ds_count_thousands),
            context.getString(R.string.ds_count_millions),
            getResources().getConfiguration().getLocales().get(0));
    render();
  }

  public void setOnLikeChangeListener(@Nullable OnLikeChangeListener listener) {
    this.listener = listener;
  }

  /** Состояние с сервера или откат оптимистичного обновления — без анимации и хаптики. */
  public void setLiked(boolean liked, long count) {
    this.checked = liked;
    this.count = Math.max(0L, count);
    render();
  }

  /** Лайк жестом двойного тапа по фото: только ставит отметку, повторный двойной тап не снимает. */
  public void likeFromGesture() {
    if (!checked) {
      toggleByUser();
    }
  }

  @Override
  public boolean performClick() {
    toggleByUser();
    return super.performClick();
  }

  @Override
  public void setChecked(boolean checked) {
    setLiked(checked, count);
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void toggle() {
    toggleByUser();
  }

  public long getCount() {
    return count;
  }

  @Override
  protected int[] onCreateDrawableState(int extraSpace) {
    int[] state = super.onCreateDrawableState(extraSpace + 1);
    if (checked) {
      mergeDrawableStates(state, CHECKED_STATE_SET);
    }
    return state;
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(ToggleButton.class.getName());
    A11y.setChecked(info, checked);
  }

  private void toggleByUser() {
    checked = !checked;
    count = Math.max(0L, count + (checked ? 1 : -1));
    render();
    if (checked) {
      Haptics.perform(this, Haptics.Event.LIKE);
      pop();
    }
    if (listener != null) {
      listener.onLikeChanged(this, checked);
    }
  }

  private void render() {
    refreshDrawableState();
    countView.setVisibility(showCount && count > 0 ? View.VISIBLE : View.GONE);
    countView.setText(formatter.format(count));
    setContentDescription(buildDescription());
    ViewCompat.setStateDescription(
        this,
        getResources().getString(checked ? R.string.ds_a11y_liked : R.string.ds_a11y_not_liked));
  }

  private CharSequence buildDescription() {
    String label = getResources().getString(R.string.ds_a11y_like);
    if (count <= 0) {
      return label;
    }
    int quantity = (int) Math.min(Integer.MAX_VALUE, count);
    String likes =
        getResources().getQuantityString(R.plurals.ds_a11y_likes_count, quantity, quantity);
    return getResources().getString(R.string.ds_a11y_label_with_count, label, likes);
  }

  private void pop() {
    if (!Motion.animationsEnabled()) {
      return;
    }
    if (springX == null || springY == null) {
      springX = new SpringAnimation(icon, DynamicAnimation.SCALE_X);
      springY = new SpringAnimation(icon, DynamicAnimation.SCALE_Y);
      for (SpringAnimation spring : new SpringAnimation[] {springX, springY}) {
        spring.setSpring(
            Motion.spring(
                1f,
                DsMotionTokens.SPRING_HEART_STIFFNESS,
                DsMotionTokens.SPRING_HEART_DAMPING_RATIO));
      }
    }
    SpringAnimation x = springX;
    SpringAnimation y = springY;
    x.cancel();
    y.cancel();
    icon.animate().cancel();
    icon.animate()
        .scaleX(DsMotionTokens.HEART_PEAK_SCALE)
        .scaleY(DsMotionTokens.HEART_PEAK_SCALE)
        .setDuration(Motion.durationMicro(getContext()))
        .setInterpolator(Motion.emphasizedDecelerate(getContext()))
        .withEndAction(
            () -> {
              x.animateToFinalPosition(1f);
              y.animateToFinalPosition(1f);
            })
        .start();
  }
}
