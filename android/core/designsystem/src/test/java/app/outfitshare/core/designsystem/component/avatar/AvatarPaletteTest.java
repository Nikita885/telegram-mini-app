package app.outfitshare.core.designsystem.component.avatar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Стабильный и равномерный выбор цвета аватара. */
public class AvatarPaletteTest {

  private static final int PALETTE = 7;

  @Test
  public void indexIsStable() {
    assertEquals(
        AvatarPalette.indexFor(981058756L, PALETTE), AvatarPalette.indexFor(981058756L, PALETTE));
  }

  @Test
  public void indexIsInRangeForNegativeAndHugeIds() {
    for (long id : new long[] {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 1090826509L}) {
      int index = AvatarPalette.indexFor(id, PALETTE);
      assertTrue(index >= 0 && index < PALETTE);
    }
  }

  @Test
  public void sequentialIdsSpreadOverWholePalette() {
    int[] hits = new int[PALETTE];
    for (long id = 1; id <= 7_000; id++) {
      hits[AvatarPalette.indexFor(id, PALETTE)]++;
    }
    for (int count : hits) {
      // Равномерно ±20 %: соседние пользователи не получают один цвет.
      assertTrue("распределение " + count, count > 800 && count < 1200);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyPaletteIsRejected() {
    AvatarPalette.indexFor(1L, 0);
  }
}
