package app.outfitshare.core.designsystem.haptics;

/**
 * Выбор константы {@code HapticFeedbackConstants} из цепочки фолбэков.
 *
 * <p>Цепочка — массив пар {@code {константа, минимальный API}}, упорядоченный от новой константы к
 * старой (см. сгенерированный {@code DsHapticTokens}). Чистая логика без Android — покрыта
 * JVM-тестами.
 */
public final class HapticChain {

  /** Подходящей константы нет — хаптику не воспроизводим. */
  public static final int NONE = -1;

  private HapticChain() {}

  /**
   * Возвращает первую константу цепочки, доступную на устройстве с {@code sdkInt}.
   *
   * @param chain пары {константа, minApi}
   * @param sdkInt версия Android устройства ({@code Build.VERSION.SDK_INT})
   * @return константа или {@link #NONE}
   */
  public static int resolve(int[][] chain, int sdkInt) {
    for (int[] step : chain) {
      if (step.length == 2 && sdkInt >= step[1]) {
        return step[0];
      }
    }
    return NONE;
  }
}
