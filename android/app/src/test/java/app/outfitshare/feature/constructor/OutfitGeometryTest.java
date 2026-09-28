package app.outfitshare.feature.constructor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OutfitGeometryTest {

  @Test
  public void fittedLayerCoversWholeCanvas() {
    assertArrayEquals(new float[] {750f, 1000f}, OutfitGeometry.baseSize(true, 750, 1000, 750, 1000), 0.001f);
  }

  @Test
  public void freeLayerKeepsImageAspect() {
    float[] size = OutfitGeometry.baseSize(false, 750, 1000, 200, 100);
    assertEquals(300f, size[0], 0.001f);
    assertEquals(150f, size[1], 0.001f);
  }

  @Test
  public void fitCenterLetterboxesTallCanvas() {
    float[] fit = OutfitGeometry.fitCenter(750, 1000, 360, 400);
    assertEquals(0.4f, fit[0], 0.0001f);
    assertEquals(30f, fit[1], 0.001f);
    assertEquals(0f, fit[2], 0.001f);
  }

  @Test
  public void snapToleranceMatchesServerFittedPose() {
    assertTrue(OutfitGeometry.nearFittedPose(0.52f, 0.49f, 1.05f, 4f, 0.05f));
    assertFalse(OutfitGeometry.nearFittedPose(0.7f, 0.5f, 1f, 0f, 0.05f));
    assertFalse(OutfitGeometry.nearFittedPose(0.5f, 0.5f, 1.4f, 0f, 0.05f));
    assertTrue(OutfitGeometry.nearFittedPose(0.5f, 0.5f, 1f, 355f, 0.05f));
  }

  @Test
  public void normalizesAngles() {
    assertEquals(-10f, OutfitGeometry.normalizeAngle(350f), 0.001f);
    assertEquals(170f, OutfitGeometry.normalizeAngle(-190f), 0.001f);
  }
}
