package app.outfitshare.core.designsystem.motion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.SpringForce;
import app.outfitshare.core.designsystem.R;

/**
 * Доступ к токенам движения: длительности, кривые, пружины.
 *
 * <p>Все анимации дизайн-системы проверяют {@link #animationsEnabled()}: при системной настройке
 * «Убрать анимации» (масштаб 0) состояние меняется мгновенно, а хаптика сохраняется.
 */
public final class Motion {

  private Motion() {}

  /** {@code false}, если пользователь отключил анимации в системе. */
  public static boolean animationsEnabled() {
    return ValueAnimator.areAnimatorsEnabled();
  }

  /** Длительность из токена, например {@code R.integer.ds_motion_duration_medium}. */
  public static long duration(@NonNull Context context, @IntegerRes int token) {
    return context.getResources().getInteger(token);
  }

  public static long durationMicro(@NonNull Context context) {
    return duration(context, R.integer.ds_motion_duration_micro);
  }

  public static long durationShort(@NonNull Context context) {
    return duration(context, R.integer.ds_motion_duration_short);
  }

  public static long durationMedium(@NonNull Context context) {
    return duration(context, R.integer.ds_motion_duration_medium);
  }

  public static long durationLong(@NonNull Context context) {
    return duration(context, R.integer.ds_motion_duration_long);
  }

  /** Стандартная кривая: смена состояния внутри экрана. */
  public static Interpolator standard(@NonNull Context context) {
    return AnimationUtils.loadInterpolator(context, R.interpolator.ds_easing_standard);
  }

  /** Выразительное замедление: вход элемента, раскрытие, shared element «туда». */
  public static Interpolator emphasizedDecelerate(@NonNull Context context) {
    return AnimationUtils.loadInterpolator(context, R.interpolator.ds_easing_emphasized_decelerate);
  }

  /** Выразительное ускорение: уход элемента с экрана, shared element «обратно». */
  public static Interpolator emphasizedAccelerate(@NonNull Context context) {
    return AnimationUtils.loadInterpolator(context, R.interpolator.ds_easing_emphasized_accelerate);
  }

  /**
   * Пружина с параметрами из токенов ({@code DsMotionTokens.SPRING_*}).
   *
   * @param finalPosition конечное значение анимируемого свойства
   */
  public static SpringForce spring(float finalPosition, float stiffness, float dampingRatio) {
    return new SpringForce(finalPosition).setStiffness(stiffness).setDampingRatio(dampingRatio);
  }
}
