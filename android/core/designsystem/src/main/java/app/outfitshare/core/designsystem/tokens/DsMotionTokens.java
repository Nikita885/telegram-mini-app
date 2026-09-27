/*
 * СГЕНЕРИРОВАНО tools/designsystem/generate_tokens.py из docs/design/tokens/tokens.json.
 * Не редактировать вручную: правьте tokens.json и перезапустите генератор.
 */

package app.outfitshare.core.designsystem.tokens;

/** Константы движения из tokens.json: пружины и безразмерные масштабы. */
public final class DsMotionTokens {

  private DsMotionTokens() {}

  /** Вещь «падает» на манекен и садится с лёгким отскоком. */
  public static final float SPRING_DROP_STIFFNESS = 450f;
  public static final float SPRING_DROP_DAMPING_RATIO = 0.55f;

  /** Слой примагничивается к опорным точкам. */
  public static final float SPRING_SNAP_STIFFNESS = 800f;
  public static final float SPRING_SNAP_DAMPING_RATIO = 0.7f;

  /** Пружинка сердца при лайке. */
  public static final float SPRING_HEART_STIFFNESS = 1200f;
  public static final float SPRING_HEART_DAMPING_RATIO = 0.4f;

  /** Сжатие карточки/кнопки при нажатии. */
  public static final float SPRING_PRESS_STIFFNESS = 1500f;
  public static final float SPRING_PRESS_DAMPING_RATIO = 1f;

  /** Доводка bottom sheet и перетаскиваемых панелей. */
  public static final float SPRING_SHEET_STIFFNESS = 600f;
  public static final float SPRING_SHEET_DAMPING_RATIO = 0.9f;

  public static final float PRESS_SCALE = 0.97f;
  public static final float HEART_PEAK_SCALE = 1.25f;
  public static final float HEART_BURST_SCALE = 1.6f;
  public static final float DROP_START_SCALE = 1.04f;

  public static final long DURATION_MICRO_MS = 100L;
  public static final long DURATION_SHORT_MS = 150L;
  public static final long DURATION_MEDIUM_MS = 250L;
  public static final long DURATION_LONG_MS = 350L;
  public static final long DURATION_EXTRA_LONG_MS = 500L;
  public static final long DURATION_SHIMMER_MS = 1400L;
  public static final long DURATION_HEART_BURST_MS = 700L;
  public static final long DURATION_STAGGER_MS = 30L;
  public static final long DURATION_UNDO_WINDOW_MS = 5000L;
  public static final long DURATION_SNACKBAR_MS = 4000L;
  public static final long DURATION_SPLASH_MIN_MS = 600L;
}
