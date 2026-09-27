package app.outfitshare.core.designsystem.motion;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.tokens.DsMotionTokens;

/**
 * Большое сердце по двойному тапу на фото образа.
 *
 * <p>Появляется пружиной {@code heart} (с перелётом), держится и растворяется, увеличиваясь до
 * {@code heartBurstScale}. Это украшение: сам лайк отражается кнопкой, поэтому при выключенных
 * анимациях всплеск не показывается.
 */
public final class HeartBurst {

  private HeartBurst() {}

  /** Проигрывает всплеск на заранее размещённом по центру фото {@code heart}. */
  public static void play(@NonNull ImageView heart) {
    cancel(heart);
    if (!Motion.animationsEnabled()) {
      heart.setVisibility(View.INVISIBLE);
      return;
    }
    Context context = heart.getContext();
    final long total = Motion.duration(context, R.integer.ds_motion_duration_heart_burst);
    final long fade = Motion.durationMedium(context);

    heart.setVisibility(View.VISIBLE);
    heart.setAlpha(0f);
    heart.setScaleX(0f);
    heart.setScaleY(0f);
    heart.animate().alpha(1f).setDuration(Motion.durationShort(context)).start();

    SpringAnimation scaleX = popSpring(heart, DynamicAnimation.SCALE_X);
    SpringAnimation scaleY = popSpring(heart, DynamicAnimation.SCALE_Y);
    scaleX.start();
    scaleY.start();

    Runnable fadeOut =
        () -> {
          scaleX.cancel();
          scaleY.cancel();
          heart
              .animate()
              .alpha(0f)
              .scaleX(DsMotionTokens.HEART_BURST_SCALE)
              .scaleY(DsMotionTokens.HEART_BURST_SCALE)
              .setDuration(fade)
              .setInterpolator(Motion.emphasizedAccelerate(context))
              .withEndAction(() -> heart.setVisibility(View.INVISIBLE))
              .start();
        };
    heart.setTag(R.id.ds_tag_heart_burst_fade, fadeOut);
    heart.postDelayed(fadeOut, total - fade);
  }

  /** Прерывает текущий всплеск (например, при переиспользовании карточки в списке). */
  public static void cancel(@NonNull ImageView heart) {
    Object pending = heart.getTag(R.id.ds_tag_heart_burst_fade);
    if (pending instanceof Runnable) {
      heart.removeCallbacks((Runnable) pending);
      heart.setTag(R.id.ds_tag_heart_burst_fade, null);
    }
    heart.animate().cancel();
    heart.setVisibility(View.INVISIBLE);
  }

  private static SpringAnimation popSpring(View view, DynamicAnimation.ViewProperty property) {
    SpringAnimation animation = new SpringAnimation(view, property);
    animation.setSpring(
        Motion.spring(
            1f, DsMotionTokens.SPRING_HEART_STIFFNESS, DsMotionTokens.SPRING_HEART_DAMPING_RATIO));
    return animation;
  }
}
