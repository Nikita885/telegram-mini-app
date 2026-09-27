package app.outfitshare.core.designsystem.component.avatar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.theme.DsTheme;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * Аватар: фото в круге или инициалы на цвете из палитры токенов.
 *
 * <p>Размеры — токены {@code size.avatar*} ({@code app:dsAvatarSize="xs|s|m|l|xl|xxl"}). Инициалы
 * набраны антиквой дизайн-системы — журнальная деталь. Цвет подложки выбирается стабильно по id
 * пользователя ({@link AvatarPalette}), контраст инициалов с каждой подложкой ≥ 4.5:1 проверяется
 * генератором токенов.
 *
 * <p>Доступность: если рядом с аватаром выводится имя, задайте {@code
 * app:dsAvatarDecorative="true"} — TalkBack не будет читать имя дважды.
 */
public class AvatarView extends ShapeableImageView {

  /** Доля кегля инициалов от диаметра аватара. */
  private static final float INITIALS_TEXT_RATIO = 0.4f;

  private static final int[] SIZES = {
    R.dimen.ds_size_avatar_xs,
    R.dimen.ds_size_avatar_s,
    R.dimen.ds_size_avatar_m,
    R.dimen.ds_size_avatar_l,
    R.dimen.ds_size_avatar_xl,
    R.dimen.ds_size_avatar_xxl,
  };
  private static final int DEFAULT_SIZE_INDEX = 2;
  private static final int LARGE_SIZE_INDEX = 3;

  private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] palette;
  private final boolean decorative;

  private int sizePx;
  private int sizeIndex;
  private String initials = "";
  @Nullable private CharSequence displayName;
  private boolean showOnline;
  @Nullable private Drawable onlineDot;

  public AvatarView(@NonNull Context context) {
    this(context, null);
  }

  public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsAvatarStyle);
  }

  public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView, defStyleAttr, 0);
    try {
      sizeIndex = a.getInt(R.styleable.AvatarView_dsAvatarSize, DEFAULT_SIZE_INDEX);
      decorative = a.getBoolean(R.styleable.AvatarView_dsAvatarDecorative, false);
      showOnline = a.getBoolean(R.styleable.AvatarView_dsAvatarShowOnline, false);
    } finally {
      a.recycle();
    }
    sizePx = getResources().getDimensionPixelSize(SIZES[clampSizeIndex(sizeIndex)]);

    TypedArray colors = getResources().obtainTypedArray(R.array.ds_avatar_palette);
    palette = new int[colors.length()];
    for (int i = 0; i < palette.length; i++) {
      palette[i] = colors.getColor(i, 0);
    }
    colors.recycle();

    backgroundPaint.setColor(palette[0]);
    textPaint.setColor(DsTheme.color(context, R.attr.dsColorOnAvatar));
    textPaint.setTextAlign(Paint.Align.CENTER);
    Typeface typeface = ResourcesCompat.getFont(context, R.font.ds_display_medium);
    textPaint.setTypeface(typeface);

    if (decorative) {
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    updateContentDescription();
  }

  /**
   * Пользователь без фото (или пока фото грузится): инициалы на цвете палитры.
   *
   * @param userId стабильный id — определяет цвет подложки
   * @param name отображаемое имя или @username
   */
  public void setUser(long userId, @Nullable CharSequence name) {
    displayName = name;
    initials = Initials.of(name == null ? null : name.toString());
    backgroundPaint.setColor(palette[AvatarPalette.indexFor(userId, palette.length)]);
    updateContentDescription();
    invalidate();
  }

  /** Размер из шкалы токенов: 0 = xs … 5 = xxl. */
  public void setAvatarSize(int index) {
    sizeIndex = clampSizeIndex(index);
    sizePx = getResources().getDimensionPixelSize(SIZES[sizeIndex]);
    requestLayout();
  }

  /** Показывать ли индикатор «в сети». */
  public void setShowOnline(boolean show) {
    if (showOnline != show) {
      showOnline = show;
      updateContentDescription();
      invalidate();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = resolveSize(sizePx, widthMeasureSpec);
    int height = resolveSize(sizePx, heightMeasureSpec);
    int side = Math.min(width, height);
    setMeasuredDimension(side, side);
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    if (getDrawable() == null) {
      drawInitials(canvas);
    } else {
      super.onDraw(canvas);
    }
    if (showOnline) {
      drawOnlineDot(canvas);
    }
  }

  private void drawInitials(Canvas canvas) {
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    float radius = Math.min(cx, cy);
    canvas.drawCircle(cx, cy, radius, backgroundPaint);
    if (initials.isEmpty()) {
      return;
    }
    textPaint.setTextSize(radius * 2f * INITIALS_TEXT_RATIO);
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
    canvas.drawText(initials, cx, baseline, textPaint);
  }

  private void drawOnlineDot(Canvas canvas) {
    if (onlineDot == null) {
      onlineDot = ContextCompat.getDrawable(getContext(), R.drawable.ds_bg_online_dot);
    }
    if (onlineDot == null) {
      return;
    }
    @DimenRes
    int dotRes =
        sizeIndex >= LARGE_SIZE_INDEX ? R.dimen.ds_size_icon_small : R.dimen.ds_size_badge_dot;
    int dot = getResources().getDimensionPixelSize(dotRes);
    boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    int left = rtl ? 0 : getWidth() - dot;
    int top = getHeight() - dot;
    onlineDot.setBounds(left, top, left + dot, top + dot);
    onlineDot.draw(canvas);
  }

  private void updateContentDescription() {
    if (decorative) {
      setContentDescription(null);
      return;
    }
    String description =
        displayName == null || displayName.length() == 0
            ? getResources().getString(R.string.ds_a11y_avatar_unknown)
            : getResources().getString(R.string.ds_a11y_avatar, displayName);
    if (showOnline) {
      description = getResources().getString(R.string.ds_a11y_with_online, description);
    }
    setContentDescription(description);
  }

  private static int clampSizeIndex(int index) {
    return Math.max(0, Math.min(SIZES.length - 1, index));
  }
}
