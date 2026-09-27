package app.outfitshare.core.designsystem.component.image;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Разбор пропорций медиа из токенов. */
public class AspectRatioTest {

  private static final float DELTA = 1e-6f;

  @Test
  public void parsesTokenFormats() {
    assertEquals(1.25f, AspectRatio.heightToWidth("4:5"), DELTA);
    assertEquals(1f, AspectRatio.heightToWidth("1:1"), DELTA);
    assertEquals(4f / 3f, AspectRatio.heightToWidth(" 3 : 4 "), DELTA);
  }

  @Test
  public void invalidSpecsDisableRatio() {
    assertEquals(0f, AspectRatio.heightToWidth(null), DELTA);
    assertEquals(0f, AspectRatio.heightToWidth(""), DELTA);
    assertEquals(0f, AspectRatio.heightToWidth("4x5"), DELTA);
    assertEquals(0f, AspectRatio.heightToWidth("0:5"), DELTA);
    assertEquals(0f, AspectRatio.heightToWidth("a:b"), DELTA);
  }
}
