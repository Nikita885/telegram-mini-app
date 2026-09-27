package app.outfitshare.core.designsystem.component.skeleton;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.motion.Motion;
import app.outfitshare.core.designsystem.theme.DsTheme;

/**
 * Скелетон загрузки: блоки {@code ds_bg_skeleton_*} повторяют форму будущего контента, по ним
 * пробегает блик {@code skeletonHighlight}.
 *
 * <p>Блик рисуется режимом {@code SRC_ATOP} в аппаратном слое — только поверх блоков, фон экрана не
 * засвечивается. При «Убрать анимации» блик не бежит, скелетон статичен. Для TalkBack весь скелетон
 * — один элемент «Загрузка…».
 */
public class SkeletonLayout extends FrameLayout {

  /** Ширина блика относительно ширины скелетона. */
  private static final float BAND_WIDTH_RATIO = 0.6f;

  private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix shimmerMatrix = new Matrix();
  private final boolean shimmerEnabled;
  @Nullable private ValueAnimator animator;
  private float progress;

  public SkeletonLayout(@NonNull Context context) {
    this(context, null);
  }

  public SkeletonLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsSkeletonStyle);
  }

  public SkeletonLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_Skeleton);
    TypedArray a =
        context.obtainStyledAttributes(
            attrs, R.styleable.SkeletonLayout, defStyleAttr, R.style.Widget_Ds_Skeleton);
    try {
      shimmerEnabled = a.getBoolean(R.styleable.SkeletonLayout_dsShimmer, true);
    } finally {
      a.recycle();
    }
    shimmerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
    setWillNotDraw(false);
    setContentDescription(context.getString(R.string.ds_state_loading));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setFocusable(true);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    int highlight = DsTheme.color(getContext(), R.attr.dsColorSkeletonHighlight);
    int transparent = ColorUtils.setAlphaComponent(highlight, 0);
    float band = w * BAND_WIDTH_RATIO;
    shimmerPaint.setShader(
        new LinearGradient(
            0f,
            0f,
            band,
            0f,
            new int[] {transparent, highlight, transparent},
            null,
            Shader.TileMode.CLAMP));
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    start();
  }

  @Override
  protected void onDetachedFromWindow() {
    stop();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (!isAttachedToWindow()) {
      return;
    }
    if (visibility == VISIBLE && isShown()) {
      start();
    } else {
      stop();
    }
  }

  @Override
  protected void dispatchDraw(@NonNull Canvas canvas) {
    super.dispatchDraw(canvas);
    if (animator == null || getWidth() == 0) {
      return;
    }
    float band = getWidth() * BAND_WIDTH_RATIO;
    float travel = getWidth() + band * 2f;
    boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    float x = rtl ? getWidth() - travel * progress : -band + travel * progress;
    shimmerMatrix.setTranslate(x, 0f);
    shimmerPaint.getShader().setLocalMatrix(shimmerMatrix);
    canvas.drawRect(0f, 0f, getWidth(), getHeight(), shimmerPaint);
  }

  private void start() {
    if (!shimmerEnabled || animator != null || !Motion.animationsEnabled()) {
      return;
    }
    setLayerType(LAYER_TYPE_HARDWARE, null);
    ValueAnimator shimmer = ValueAnimator.ofFloat(0f, 1f);
    shimmer.setDuration(Motion.duration(getContext(), R.integer.ds_motion_duration_shimmer));
    shimmer.setInterpolator(new LinearInterpolator());
    shimmer.setRepeatCount(ValueAnimator.INFINITE);
    shimmer.addUpdateListener(
        animation -> {
          progress = (float) animation.getAnimatedValue();
          invalidate();
        });
    shimmer.start();
    animator = shimmer;
  }

  private void stop() {
    if (animator != null) {
      animator.cancel();
      animator = null;
      setLayerType(LAYER_TYPE_NONE, null);
      invalidate();
    }
  }
}
