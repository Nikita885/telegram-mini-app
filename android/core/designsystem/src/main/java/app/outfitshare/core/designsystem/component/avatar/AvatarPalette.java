package app.outfitshare.core.designsystem.component.avatar;

/**
 * Стабильный выбор цвета аватара-инициалов по id пользователя.
 *
 * <p>Один и тот же пользователь получает один и тот же цвет на всех устройствах и в веб-макетах: id
 * перемешивается мультипликативным хешем (золотое сечение, 64 бита), затем берётся остаток от
 * размера палитры {@code color.avatar.palette}.
 */
public final class AvatarPalette {

  private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
  private static final int HALF_LONG_BITS = 32;

  private AvatarPalette() {}

  /**
   * Индекс цвета в палитре.
   *
   * @param userId идентификатор пользователя
   * @param paletteSize размер палитры (&gt; 0)
   */
  public static int indexFor(long userId, int paletteSize) {
    if (paletteSize <= 0) {
      throw new IllegalArgumentException("paletteSize must be > 0");
    }
    long mixed = userId * GOLDEN_GAMMA;
    mixed ^= mixed >>> HALF_LONG_BITS;
    return (int) Math.floorMod(mixed, (long) paletteSize);
  }
}
