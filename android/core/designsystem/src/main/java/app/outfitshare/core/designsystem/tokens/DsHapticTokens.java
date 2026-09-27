/*
 * СГЕНЕРИРОВАНО tools/designsystem/generate_tokens.py из docs/design/tokens/tokens.json.
 * Не редактировать вручную: правьте tokens.json и перезапустите генератор.
 */

package app.outfitshare.core.designsystem.tokens;

import android.annotation.SuppressLint;
import android.view.HapticFeedbackConstants;

/**
 * Хаптика ключевых событий: для каждого события — цепочка {константа, минимальный API}.
 * Берётся первая константа, доступная на устройстве. См. {@code Haptics}.
 */
@SuppressLint("InlinedApi")
public final class DsHapticTokens {

  private DsHapticTokens() {}

  public static final int[][] SNAP = {
    {HapticFeedbackConstants.SEGMENT_TICK, 34},
    {HapticFeedbackConstants.CLOCK_TICK, 21},
  };

  public static final int[][] LIKE = {
    {HapticFeedbackConstants.CONFIRM, 30},
    {HapticFeedbackConstants.VIRTUAL_KEY, 21},
  };

  public static final int[][] PUBLISH = {
    {HapticFeedbackConstants.CONFIRM, 30},
    {HapticFeedbackConstants.LONG_PRESS, 21},
  };

  public static final int[][] SHUTTER = {
    {HapticFeedbackConstants.CONFIRM, 30},
    {HapticFeedbackConstants.VIRTUAL_KEY, 21},
  };

  public static final int[][] REJECT = {
    {HapticFeedbackConstants.REJECT, 30},
    {HapticFeedbackConstants.LONG_PRESS, 21},
  };

  public static final int[][] DRAG_START = {
    {HapticFeedbackConstants.DRAG_START, 34},
    {HapticFeedbackConstants.GESTURE_START, 30},
    {HapticFeedbackConstants.LONG_PRESS, 21},
  };

  public static final int[][] TOGGLE_ON = {
    {HapticFeedbackConstants.TOGGLE_ON, 34},
    {HapticFeedbackConstants.CLOCK_TICK, 21},
  };

  public static final int[][] TOGGLE_OFF = {
    {HapticFeedbackConstants.TOGGLE_OFF, 34},
    {HapticFeedbackConstants.CLOCK_TICK, 21},
  };
}
