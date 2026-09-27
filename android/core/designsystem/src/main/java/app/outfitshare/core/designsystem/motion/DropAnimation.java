package app.outfitshare.core.designsystem.motion;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.tokens.DsMotionTokens;

/**
 * Движение вещей в конструкторе.
 *
 * <ul>
 *   <li>{@link #drop} — вещь «падает» на манекен сверху и садится с лёгким отскоком (пружина {@code
 *       drop}); в момент касания — хаптика {@code SNAP}.
 *   <li>{@link #snapTo} — перетаскиваемый слой примагничивается к опорным точкам (пружина {@code
 *       snap}), хаптика — в момент захвата.
 * </ul>
 *
 * <p>Анимируются translation-свойства: итоговая геометрия слоя задаётся раскладкой холста, анимация
 * лишь приводит к нулевому смещению.
 */
public final class DropAnimation {

  private DropAnimation() {}

  /**
   * Проигрывает «падение» слоя, уже размещённого в конечной позиции.
   *
   * @param layer вид слоя вещи
   * @param onLanded вызывается после полной остановки пружины
   */
  public static void drop(@NonNull View layer, @Nullable Runnable onLanded) {
    cancel(layer);
    if (!Motion.animationsEnabled()) {
      layer.setTranslationY(0f);
      layer.setAlpha(1f);
      layer.setScaleX(1f);
      layer.setScaleY(1f);
      Haptics.perform(layer, Haptics.Event.SNAP);
      if (onLanded != null) {
        onLanded.run();
      }
      return;
    }

    float offset = layer.getResources().getDimension(R.dimen.ds_motion_drop_offset);
    layer.setTranslationY(-offset);
    layer.setAlpha(0f);
    layer.setScaleX(DsMotionTokens.DROP_START_SCALE);
    layer.setScaleY(DsMotionTokens.DROP_START_SCALE);

    layer
        .animate()
        .alpha(1f)
        .setDuration(Motion.durationShort(layer.getContext()))
        .setInterpolator(Motion.standard(layer.getContext()))
        .start();

    SpringAnimation fall =
        spring(layer, DynamicAnimation.TRANSLATION_Y, DsMotionTokens.SPRING_DROP_STIFFNESS);
    boolean[] landed = {false};
    fall.addUpdateListener(
        (animation, value, velocity) -> {
          if (!landed[0] && value >= 0f) {
            landed[0] = true;
            Haptics.perform(layer, Haptics.Event.SNAP);
          }
        });
    fall.addEndListener(
        (animation, canceled, value, velocity) -> {
          if (!canceled && onLanded != null) {
            onLanded.run();
          }
        });
    spring(layer, DynamicAnimation.SCALE_X, DsMotionTokens.SPRING_DROP_STIFFNESS)
        .animateToFinalPosition(1f);
    spring(layer, DynamicAnimation.SCALE_Y, DsMotionTokens.SPRING_DROP_STIFFNESS)
        .animateToFinalPosition(1f);
    fall.animateToFinalPosition(0f);
  }

  /**
   * Примагничивает слой к точке крепления.
   *
   * @param translationX целевой translationX слоя, px
   * @param translationY целевой translationY слоя, px
   */
  public static void snapTo(
      @NonNull View layer, float translationX, float translationY, @Nullable Runnable onSnapped) {
    cancel(layer);
    Haptics.perform(layer, Haptics.Event.SNAP);
    if (!Motion.animationsEnabled()) {
      layer.setTranslationX(translationX);
      layer.setTranslationY(translationY);
      if (onSnapped != null) {
        onSnapped.run();
      }
      return;
    }
    SpringAnimation x = snapSpring(layer, DynamicAnimation.TRANSLATION_X);
    SpringAnimation y = snapSpring(layer, DynamicAnimation.TRANSLATION_Y);
    if (onSnapped != null) {
      y.addEndListener(
          (animation, canceled, value, velocity) -> {
            if (!canceled) {
              onSnapped.run();
            }
          });
    }
    x.animateToFinalPosition(translationX);
    y.animateToFinalPosition(translationY);
  }

  /** Останавливает все пружины слоя (например, при начале нового жеста). */
  public static void cancel(@NonNull View layer) {
    layer.animate().cancel();
    for (DynamicAnimation.ViewProperty property : PROPERTIES) {
      Object tag = layer.getTag(tagFor(property));
      if (tag instanceof SpringAnimation) {
        ((SpringAnimation) tag).cancel();
      }
    }
  }

  private static final DynamicAnimation.ViewProperty[] PROPERTIES = {
    DynamicAnimation.TRANSLATION_X,
    DynamicAnimation.TRANSLATION_Y,
    DynamicAnimation.SCALE_X,
    DynamicAnimation.SCALE_Y,
  };

  private static SpringAnimation snapSpring(View layer, DynamicAnimation.ViewProperty property) {
    SpringAnimation animation = spring(layer, property, DsMotionTokens.SPRING_SNAP_STIFFNESS);
    animation.getSpring().setDampingRatio(DsMotionTokens.SPRING_SNAP_DAMPING_RATIO);
    return animation;
  }

  private static SpringAnimation spring(
      View layer, DynamicAnimation.ViewProperty property, float stiffness) {
    int key = tagFor(property);
    SpringAnimation animation = new SpringAnimation(layer, property);
    animation.setSpring(Motion.spring(0f, stiffness, DsMotionTokens.SPRING_DROP_DAMPING_RATIO));
    layer.setTag(key, animation);
    return animation;
  }

  private static int tagFor(DynamicAnimation.ViewProperty property) {
    if (property == DynamicAnimation.TRANSLATION_X) {
      return R.id.ds_tag_spring_translation_x;
    } else if (property == DynamicAnimation.TRANSLATION_Y) {
      return R.id.ds_tag_spring_translation_y;
    } else if (property == DynamicAnimation.SCALE_X) {
      return R.id.ds_tag_spring_scale_x;
    }
    return R.id.ds_tag_spring_scale_y;
  }
}
