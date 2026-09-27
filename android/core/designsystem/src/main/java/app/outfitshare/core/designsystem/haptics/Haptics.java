package app.outfitshare.core.designsystem.haptics;

import android.os.Build;
import android.view.View;
import androidx.annotation.NonNull;
import app.outfitshare.core.designsystem.tokens.DsHapticTokens;

/**
 * Хаптика ключевых действий: snap вещи к манекену, лайк, публикация, затвор камеры.
 *
 * <p>Используется {@link View#performHapticFeedback(int)}: система сама учитывает пользовательскую
 * настройку «Вибрация при касании», поэтому отдельный переключатель в приложении не нужен.
 */
public final class Haptics {

  /** События, для которых дизайн-система определяет тактильный отклик. */
  public enum Event {
    /** Вещь примагнитилась к опорным точкам / «приземлилась» на манекен. */
    SNAP(DsHapticTokens.SNAP),
    /** Поставлен лайк (снятие лайка — без вибрации). */
    LIKE(DsHapticTokens.LIKE),
    /** Образ или вещь опубликованы. */
    PUBLISH(DsHapticTokens.PUBLISH),
    /** Снимок в Студии. */
    SHUTTER(DsHapticTokens.SHUTTER),
    /** Действие отклонено: ошибка, недоступно. */
    REJECT(DsHapticTokens.REJECT),
    /** Начало перетаскивания слоя или опорной точки. */
    DRAG_START(DsHapticTokens.DRAG_START),
    /** Переключатель включён. */
    TOGGLE_ON(DsHapticTokens.TOGGLE_ON),
    /** Переключатель выключен. */
    TOGGLE_OFF(DsHapticTokens.TOGGLE_OFF);

    private final int[][] chain;

    Event(int[][] chain) {
      this.chain = chain;
    }

    /** Константа для текущего устройства или {@link HapticChain#NONE}. */
    public int constant() {
      return HapticChain.resolve(chain, Build.VERSION.SDK_INT);
    }
  }

  private Haptics() {}

  /**
   * Воспроизводит отклик события на {@code view}.
   *
   * @return {@code true}, если система приняла запрос
   */
  public static boolean perform(@NonNull View view, @NonNull Event event) {
    int constant = event.constant();
    if (constant == HapticChain.NONE) {
      return false;
    }
    return view.performHapticFeedback(constant);
  }
}
