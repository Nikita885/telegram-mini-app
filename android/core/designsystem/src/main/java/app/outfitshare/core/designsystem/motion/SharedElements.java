package app.outfitshare.core.designsystem.motion;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.theme.DsTheme;
import com.google.android.material.transition.Hold;
import com.google.android.material.transition.MaterialContainerTransform;

/**
 * Shared element переход «карточка образа → просмотр образа».
 *
 * <p>Используется контейнерная трансформация Material: фото карточки вырастает в фото экрана
 * просмотра. Источник удерживается {@link Hold}, чтобы лента не мигала под переходом.
 *
 * <pre>{@code
 * // Лента, при клике на карточку:
 * SharedElements.holdSource(this);
 * card.setSharedElementName(SharedElements.outfitPhoto(outfit.id));
 * // навигация с extras: FragmentNavigatorExtras(card.getPhotoView() to name)
 *
 * // Просмотр образа, onCreate():
 * SharedElements.setUpDestination(this, R.id.nav_host);
 * postponeEnterTransition(); // startPostponedEnterTransition() — когда фото загрузилось
 * }</pre>
 */
public final class SharedElements {

  private static final String OUTFIT_PHOTO_PREFIX = "outfit_photo_";

  private SharedElements() {}

  /** Уникальное transitionName фото образа — одинаковое в ленте, профиле, поиске и просмотре. */
  @NonNull
  public static String outfitPhoto(long outfitId) {
    return OUTFIT_PHOTO_PREFIX + outfitId;
  }

  /**
   * Настраивает входной и возвратный переход экрана назначения.
   *
   * @param drawingViewId контейнер, над которым рисуется переход (обычно NavHost)
   */
  public static void setUpDestination(@NonNull Fragment destination, @IdRes int drawingViewId) {
    Context context = destination.requireContext();
    destination.setSharedElementEnterTransition(containerTransform(context, drawingViewId, true));
    destination.setSharedElementReturnTransition(containerTransform(context, drawingViewId, false));
  }

  /** Удерживает экран-источник на месте, пока идёт переход. */
  public static void holdSource(@NonNull Fragment source) {
    long duration = Motion.durationLong(source.requireContext());
    Hold exit = new Hold();
    exit.setDuration(duration);
    Hold reenter = new Hold();
    reenter.setDuration(duration);
    source.setExitTransition(exit);
    source.setReenterTransition(reenter);
  }

  /** Контейнерная трансформация с длительностью и кривыми из токенов. */
  @NonNull
  public static MaterialContainerTransform containerTransform(
      @NonNull Context context, @IdRes int drawingViewId, boolean entering) {
    MaterialContainerTransform transform = new MaterialContainerTransform();
    transform.setDrawingViewId(drawingViewId);
    transform.setDuration(Motion.durationLong(context));
    transform.setInterpolator(
        entering ? Motion.emphasizedDecelerate(context) : Motion.emphasizedAccelerate(context));
    transform.setScrimColor(Color.TRANSPARENT);
    transform.setAllContainerColors(DsTheme.color(context, R.attr.dsColorBackground));
    transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
    return transform;
  }
}
