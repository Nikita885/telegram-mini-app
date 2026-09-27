package app.outfitshare.core.designsystem.component.state;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import app.outfitshare.core.designsystem.motion.Motion;
import com.google.android.material.button.MaterialButton;

/**
 * Единая витрина состояний экрана: загрузка, пусто, ошибка, офлайн, нет прав.
 *
 * <p>Кладётся поверх контента в том же контейнере; в состоянии {@code CONTENT} скрывается. Загрузка
 * показывает скелетон экрана ({@code app:dsLoadingLayout}) или круговой индикатор. Переходы между
 * состояниями — кроссфейд {@code duration.short}. Заголовок сообщения — live region: TalkBack
 * озвучивает появление ошибки или пустого состояния.
 */
public class StateView extends FrameLayout {

  private final View loading;
  private final View messageContainer;
  private final ImageView illustration;
  private final TextView title;
  private final TextView text;
  private final MaterialButton action;
  private final MaterialButton secondaryAction;

  @Nullable private View skeleton;
  private ScreenState state = ScreenState.content();

  public StateView(@NonNull Context context) {
    this(context, null);
  }

  public StateView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsStateViewStyle);
  }

  public StateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_StateView);
    LayoutInflater.from(context).inflate(R.layout.ds_view_state, this, true);
    loading = findViewById(R.id.ds_state_loading);
    messageContainer = findViewById(R.id.ds_state_message);
    illustration = findViewById(R.id.ds_state_illustration);
    title = findViewById(R.id.ds_state_title);
    text = findViewById(R.id.ds_state_text);
    action = findViewById(R.id.ds_state_action);
    secondaryAction = findViewById(R.id.ds_state_secondary_action);
    A11y.setHeading(title);
    A11y.announceChanges(title);

    TypedArray a =
        context.obtainStyledAttributes(
            attrs, R.styleable.StateView, defStyleAttr, R.style.Widget_Ds_StateView);
    try {
      int layout = a.getResourceId(R.styleable.StateView_dsLoadingLayout, 0);
      if (layout != 0) {
        setLoadingLayout(layout);
      }
    } finally {
      a.recycle();
    }
    setVisibility(GONE);
  }

  /** Скелетон, повторяющий форму контента экрана (например, {@code ds_skeleton_outfit_feed}). */
  public void setLoadingLayout(@LayoutRes int layout) {
    FrameLayout container = (FrameLayout) loading;
    if (skeleton != null) {
      container.removeView(skeleton);
    }
    skeleton = LayoutInflater.from(getContext()).inflate(layout, container, false);
    container.addView(skeleton);
    findViewById(R.id.ds_state_progress).setVisibility(GONE);
  }

  /** Главное действие: «Повторить», «Найти людей», «Открыть настройки». */
  public void setOnActionClickListener(@Nullable OnClickListener listener) {
    action.setOnClickListener(listener);
  }

  public void setOnSecondaryActionClickListener(@Nullable OnClickListener listener) {
    secondaryAction.setOnClickListener(listener);
  }

  @NonNull
  public ScreenState getState() {
    return state;
  }

  /** Показывает состояние. Повторный вызов с тем же состоянием ничего не делает. */
  public void setState(@NonNull ScreenState newState) {
    if (newState.equals(state)
        && (getVisibility() == VISIBLE) == (newState.getKind() != ScreenState.Kind.CONTENT)) {
      return;
    }
    state = newState;
    switch (newState.getKind()) {
      case CONTENT:
        fadeOut(this);
        return;
      case LOADING:
        fadeIn(this);
        swap(messageContainer, loading);
        return;
      default:
        bindMessage(newState);
        fadeIn(this);
        swap(loading, messageContainer);
    }
  }

  private void bindMessage(ScreenState s) {
    Defaults d = Defaults.of(s.getKind());
    illustration.setImageResource(s.hasIllustration() ? s.getIllustration() : d.illustration);
    title.setText(orDefault(s.getTitle(), d.title));
    CharSequence message = orDefault(s.getMessage(), d.message);
    text.setText(message);
    text.setVisibility(TextUtils.isEmpty(message) ? GONE : VISIBLE);
    CharSequence actionLabel = orDefault(s.getActionLabel(), d.action);
    action.setText(actionLabel);
    action.setVisibility(TextUtils.isEmpty(actionLabel) ? GONE : VISIBLE);
    CharSequence secondary = s.getSecondaryActionLabel();
    secondaryAction.setText(secondary);
    secondaryAction.setVisibility(TextUtils.isEmpty(secondary) ? GONE : VISIBLE);
  }

  @Nullable
  private CharSequence orDefault(@Nullable CharSequence value, @StringRes int fallback) {
    if (!TextUtils.isEmpty(value)) {
      return value;
    }
    return fallback == 0 ? null : getResources().getText(fallback);
  }

  private void swap(View hide, View show) {
    hide.setVisibility(GONE);
    fadeIn(show);
  }

  private static void fadeIn(View view) {
    if (view.getVisibility() == VISIBLE && view.getAlpha() == 1f) {
      return;
    }
    view.animate().cancel();
    view.setVisibility(VISIBLE);
    if (!Motion.animationsEnabled()) {
      view.setAlpha(1f);
      return;
    }
    view.setAlpha(0f);
    view.animate()
        .alpha(1f)
        .setDuration(Motion.durationShort(view.getContext()))
        .setInterpolator(Motion.standard(view.getContext()))
        .start();
  }

  private static void fadeOut(View view) {
    view.animate().cancel();
    if (view.getVisibility() != VISIBLE || !Motion.animationsEnabled()) {
      view.setVisibility(GONE);
      return;
    }
    view.animate()
        .alpha(0f)
        .setDuration(Motion.durationShort(view.getContext()))
        .setInterpolator(Motion.standard(view.getContext()))
        .withEndAction(() -> view.setVisibility(GONE))
        .start();
  }

  /** Тексты и иллюстрации по умолчанию для типовых состояний. */
  private enum Defaults {
    EMPTY(R.drawable.ds_illustration_empty_feed, 0, 0, 0),
    ERROR(
        R.drawable.ds_illustration_error,
        R.string.ds_state_error_title,
        R.string.ds_state_error_message,
        R.string.ds_action_retry),
    OFFLINE(
        R.drawable.ds_illustration_offline,
        R.string.ds_state_offline_title,
        R.string.ds_state_offline_message,
        R.string.ds_action_retry),
    NO_PERMISSION(
        R.drawable.ds_illustration_locked,
        R.string.ds_state_no_permission_title,
        0,
        R.string.ds_action_open_settings);

    @DrawableRes final int illustration;
    @StringRes final int title;
    @StringRes final int message;
    @StringRes final int action;

    Defaults(int illustration, int title, int message, int action) {
      this.illustration = illustration;
      this.title = title;
      this.message = message;
      this.action = action;
    }

    static Defaults of(ScreenState.Kind kind) {
      switch (kind) {
        case ERROR:
          return ERROR;
        case OFFLINE:
          return OFFLINE;
        case NO_PERMISSION:
          return NO_PERMISSION;
        default:
          return EMPTY;
      }
    }
  }
}
