package app.outfitshare.core.designsystem.component.list;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.theme.DsTheme;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Строка списка: настройки, уведомления, подписчики, диалоги, очередь Студии.
 *
 * <p>Слоты: иконка или произвольный leading-вид (аватар), заголовок и подзаголовок, значение
 * справа, trailing-вид (кнопка «Подписаться»), шеврон. Минимальная высота 56 dp, текст переносится
 * — при шрифте 200 % строка растёт, а не обрезается.
 *
 * <p>Переключатель ({@link #setToggle}) управляется всей строкой: одна цель касания и одна фраза
 * для TalkBack («Уведомления о лайках, переключатель, включено»).
 */
public class ListRowView extends LinearLayout {

  /** Изменение переключателя строки. */
  public interface OnToggleListener {
    /** Новое состояние переключателя. */
    void onToggled(@NonNull ListRowView row, boolean checked);
  }

  private final ImageView icon;
  private final FrameLayout leading;
  private final TextView title;
  private final TextView subtitle;
  private final TextView value;
  private final FrameLayout trailing;
  private final ImageView chevron;

  @Nullable private MaterialSwitch toggle;
  @Nullable private OnToggleListener toggleListener;

  public ListRowView(@NonNull Context context) {
    this(context, null);
  }

  public ListRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsListRowStyle);
  }

  public ListRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_ListRow);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.ds_view_list_row, this, true);
    icon = findViewById(R.id.ds_row_icon);
    leading = findViewById(R.id.ds_row_leading);
    title = findViewById(R.id.ds_row_title);
    subtitle = findViewById(R.id.ds_row_subtitle);
    value = findViewById(R.id.ds_row_value);
    trailing = findViewById(R.id.ds_row_trailing);
    chevron = findViewById(R.id.ds_row_chevron);

    TypedArray a =
        context.obtainStyledAttributes(
            attrs, R.styleable.ListRowView, defStyleAttr, R.style.Widget_Ds_ListRow);
    try {
      setIcon(a.getDrawable(R.styleable.ListRowView_dsRowIcon));
      setTitle(a.getText(R.styleable.ListRowView_dsRowTitle));
      setSubtitle(a.getText(R.styleable.ListRowView_dsRowSubtitle));
      setValue(a.getText(R.styleable.ListRowView_dsRowValue));
      setShowChevron(a.getBoolean(R.styleable.ListRowView_dsRowShowChevron, false));
      setDestructive(a.getBoolean(R.styleable.ListRowView_dsRowDestructive, false));
    } finally {
      a.recycle();
    }
  }

  public void setIcon(@Nullable Drawable drawable) {
    icon.setImageDrawable(drawable);
    icon.setVisibility(drawable == null ? GONE : VISIBLE);
  }

  public void setTitle(@Nullable CharSequence text) {
    title.setText(text);
  }

  public void setSubtitle(@Nullable CharSequence text) {
    subtitle.setText(text);
    subtitle.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
  }

  /** Текущее значение справа: «Как в системе», «Русский». */
  public void setValue(@Nullable CharSequence text) {
    value.setText(text);
    value.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
  }

  public void setShowChevron(boolean show) {
    chevron.setVisibility(show ? VISIBLE : GONE);
  }

  /** Разрушающее действие («Выйти», «Удалить аккаунт»): текст и иконка цвета {@code danger}. */
  public void setDestructive(boolean destructive) {
    int color =
        DsTheme.color(getContext(), destructive ? R.attr.dsColorDanger : R.attr.dsColorOnSurface);
    title.setTextColor(color);
    if (destructive) {
      ImageViewCompat.setImageTintList(
          icon, DsTheme.colorStateList(getContext(), R.color.ds_content_danger));
    }
  }

  /** Произвольный вид слева, например {@code AvatarView}. */
  public void setLeadingView(@Nullable View view) {
    setSlot(leading, view);
  }

  /** Произвольный вид справа, например кнопка «Подписаться». */
  public void setTrailingView(@Nullable View view) {
    setSlot(trailing, view);
  }

  /**
   * Превращает строку в переключатель.
   *
   * @param checked исходное состояние
   * @param listener вызывается при переключении пользователем
   */
  public void setToggle(boolean checked, @Nullable OnToggleListener listener) {
    if (toggle == null) {
      MaterialSwitch view = new MaterialSwitch(getContext());
      view.setClickable(false);
      view.setFocusable(false);
      view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      toggle = view;
      setTrailingView(view);
      setClickable(true);
      setFocusable(true);
    }
    toggle.setChecked(checked);
    toggleListener = listener;
  }

  public boolean isToggleChecked() {
    return toggle != null && toggle.isChecked();
  }

  @Override
  public boolean performClick() {
    if (toggle != null) {
      boolean checked = !toggle.isChecked();
      toggle.setChecked(checked);
      Haptics.perform(this, checked ? Haptics.Event.TOGGLE_ON : Haptics.Event.TOGGLE_OFF);
      if (toggleListener != null) {
        toggleListener.onToggled(this, checked);
      }
    }
    return super.performClick();
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    if (toggle != null) {
      info.setClassName(Switch.class.getName());
      A11y.setChecked(info, toggle.isChecked());
    }
  }

  private static void setSlot(FrameLayout slot, @Nullable View view) {
    slot.removeAllViews();
    if (view == null) {
      slot.setVisibility(GONE);
      return;
    }
    if (view.getParent() instanceof ViewGroup) {
      ((ViewGroup) view.getParent()).removeView(view);
    }
    slot.addView(view);
    slot.setVisibility(VISIBLE);
  }
}
