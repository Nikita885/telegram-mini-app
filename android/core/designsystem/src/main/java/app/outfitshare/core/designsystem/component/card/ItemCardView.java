package app.outfitshare.core.designsystem.component.card;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import app.outfitshare.core.designsystem.component.image.AspectRatioImageView;
import com.google.android.material.button.MaterialButton;

/**
 * Карточка вещи: каталог конструктора, «похожие вещи», вещи в образе, очередь Студии.
 *
 * <p>Режимы: просмотр (тап — открыть вещь), выбор ({@link Checkable}: рамка цвета {@code selection}
 * и галочка — не только цвет), быстрое действие «+» ({@code app:dsItemShowAction}). Бейдж в углу —
 * статус или fit score.
 */
public class ItemCardView extends LinearLayout implements Checkable {

  private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

  private final AspectRatioImageView image;
  private final View selection;
  private final ImageView check;
  private final TextView badge;
  private final MaterialButton action;
  private final TextView title;
  private final TextView subtitle;

  private boolean checkable;
  private boolean checked;

  public ItemCardView(@NonNull Context context) {
    this(context, null);
  }

  public ItemCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsItemCardStyle);
  }

  public ItemCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_ItemCard);
    setOrientation(VERTICAL);
    setClickable(true);
    setFocusable(true);
    setStateListAnimator(
        AnimatorInflater.loadStateListAnimator(context, R.animator.ds_press_scale));
    LayoutInflater.from(context).inflate(R.layout.ds_view_item_card, this, true);
    image = findViewById(R.id.ds_item_image);
    selection = findViewById(R.id.ds_item_selection);
    check = findViewById(R.id.ds_item_check);
    badge = findViewById(R.id.ds_item_badge);
    action = findViewById(R.id.ds_item_action);
    title = findViewById(R.id.ds_item_title);
    subtitle = findViewById(R.id.ds_item_subtitle);

    TypedArray a =
        context.obtainStyledAttributes(
            attrs, R.styleable.ItemCardView, defStyleAttr, R.style.Widget_Ds_ItemCard);
    try {
      action.setVisibility(
          a.getBoolean(R.styleable.ItemCardView_dsItemShowAction, false) ? VISIBLE : GONE);
      setTitle(a.getText(R.styleable.ItemCardView_dsItemTitle));
      setSubtitle(a.getText(R.styleable.ItemCardView_dsItemSubtitle));
    } finally {
      a.recycle();
    }
  }

  /** Изображение вещи (PNG с прозрачным фоном после пайплайна Студии). */
  @NonNull
  public AspectRatioImageView getImageView() {
    return image;
  }

  /** Название вещи: «Тренч оверсайз». */
  public void setTitle(@Nullable CharSequence text) {
    title.setText(text);
    title.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    updateDescription();
  }

  /** Подпись: категория, бренд или «в 12 образах». */
  public void setSubtitle(@Nullable CharSequence text) {
    subtitle.setText(text);
    subtitle.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    updateDescription();
  }

  /** Бейдж в углу: «92 %», «Новое», «Проверка». Стиль — {@code Widget.Ds.TextBadge.*}. */
  public void setBadge(@Nullable CharSequence text) {
    badge.setText(text);
    badge.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    updateDescription();
  }

  /** Быстрое действие «+» (добавить в образ). {@code null} — скрыть. */
  public void setOnActionClickListener(@Nullable OnClickListener listener) {
    action.setOnClickListener(listener);
    action.setVisibility(listener == null ? GONE : VISIBLE);
  }

  /** Включает режим выбора: карточка становится флажком для TalkBack. */
  public void setCheckable(boolean checkable) {
    this.checkable = checkable;
    if (!checkable) {
      setChecked(false);
    }
  }

  @Override
  public void setChecked(boolean checked) {
    if (this.checked == checked) {
      return;
    }
    this.checked = checked;
    selection.setVisibility(checked ? VISIBLE : GONE);
    check.setVisibility(checked ? VISIBLE : GONE);
    refreshDrawableState();
    ViewCompat.setStateDescription(
        this,
        checkable
            ? getResources()
                .getString(checked ? R.string.ds_a11y_selected : R.string.ds_a11y_not_selected)
            : null);
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void toggle() {
    setChecked(!checked);
  }

  @Override
  public boolean performClick() {
    if (checkable) {
      toggle();
    }
    return super.performClick();
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
    if (checkable) {
      info.setClassName(CheckBox.class.getName());
      A11y.setChecked(info, checked);
    }
  }

  private void updateDescription() {
    StringBuilder text = new StringBuilder();
    for (TextView part : new TextView[] {title, subtitle, badge}) {
      if (part.getVisibility() == VISIBLE && !TextUtils.isEmpty(part.getText())) {
        if (text.length() > 0) {
          text.append(", ");
        }
        text.append(part.getText());
      }
    }
    setContentDescription(text.toString());
  }
}
