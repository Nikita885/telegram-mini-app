package app.outfitshare.core.designsystem.haptics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Выбор константы хаптики из цепочки фолбэков. */
public class HapticChainTest {

  private static final int SEGMENT_TICK = 27;
  private static final int CLOCK_TICK = 4;
  private static final int[][] SNAP = {{SEGMENT_TICK, 34}, {CLOCK_TICK, 21}};

  @Test
  public void newestAvailableConstantWins() {
    assertEquals(SEGMENT_TICK, HapticChain.resolve(SNAP, 35));
    assertEquals(SEGMENT_TICK, HapticChain.resolve(SNAP, 34));
  }

  @Test
  public void fallsBackOnOlderDevices() {
    assertEquals(CLOCK_TICK, HapticChain.resolve(SNAP, 33));
    assertEquals(CLOCK_TICK, HapticChain.resolve(SNAP, 26));
  }

  @Test
  public void noneWhenNothingFits() {
    assertEquals(HapticChain.NONE, HapticChain.resolve(SNAP, 20));
    assertEquals(HapticChain.NONE, HapticChain.resolve(new int[0][], 34));
  }
}
