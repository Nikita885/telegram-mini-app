package app.outfitshare.feature.studio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.outfitshare.core.net.dto.Dto;
import org.junit.Test;

/** Backend pipeline stages mapped onto the five steps shown in the Studio. */
public class StudioTextTest {
  @Test
  public void stagesMapOntoFiveSteps() {
    assertEquals(0, StudioText.step(null));
    assertEquals(1, StudioText.step("quality"));
    assertEquals(1, StudioText.step("background"));
    assertEquals(2, StudioText.step("normalize"));
    assertEquals(2, StudioText.step("attributes"));
    assertEquals(3, StudioText.step("keypoints"));
    assertEquals(4, StudioText.step("fit"));
    assertEquals(5, StudioText.step("preview"));
    assertEquals(0, StudioText.step("something_new"));
  }

  @Test
  public void fitScoreAgainstThreshold() {
    Dto.GarmentJob job = new Dto.GarmentJob();
    assertEquals(0, StudioText.fitPercent(job));
    assertFalse(StudioText.lowFit(job));
    job.fitScore = 0.868f;
    assertEquals(87, StudioText.fitPercent(job));
    assertFalse(StudioText.lowFit(job));
    job.fitScore = 0.41f;
    assertTrue(StudioText.lowFit(job));
  }
}
