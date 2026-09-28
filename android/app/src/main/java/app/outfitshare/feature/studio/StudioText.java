package app.outfitshare.feature.studio;

import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Res;
import java.util.List;
import java.util.Map;

/** Human wording for the pipeline, mapped onto the mockup's five steps. */
final class StudioText {
  private StudioText() {}

  /** Backend stage → mockup step (1..5). */
  static int step(@Nullable String stage) {
    if (stage == null) {
      return 0;
    }
    switch (stage) {
      case "quality":
      case "background":
        return 1;
      case "normalize":
      case "attributes":
        return 2;
      case "keypoints":
        return 3;
      case "fit":
        return 4;
      case "preview":
        return 5;
      default:
        return 0;
    }
  }

  static String stageLabel(@Nullable String stage) {
    switch (step(stage)) {
      case 1:
        return Res.str(R.string.studio_stage_background);
      case 2:
        return Res.str(R.string.studio_stage_normalize);
      case 3:
        return Res.str(R.string.studio_stage_keypoints);
      case 4:
        return Res.str(R.string.studio_stage_fit);
      case 5:
        return Res.str(R.string.studio_stage_preview);
      default:
        return Res.str(R.string.studio_stage_queued);
    }
  }

  static int fitPercent(Dto.GarmentJob job) {
    return job.fitScore == null ? 0 : Math.round(job.fitScore * 100);
  }

  static boolean lowFit(Dto.GarmentJob job) {
    return job.fitScore != null && job.fitScore < job.fitThreshold;
  }

  /** Main colour name found by the pipeline, e.g. «кремовый». */
  @Nullable
  @SuppressWarnings("unchecked")
  static String colorName(Dto.GarmentJob job) {
    if (job.attributes == null) {
      return null;
    }
    Object colors = job.attributes.get("colors");
    if (colors instanceof List
        && !((List<?>) colors).isEmpty()
        && ((List<?>) colors).get(0) instanceof Map) {
      Object name = ((Map<String, Object>) ((List<?>) colors).get(0)).get("name");
      return name == null ? null : String.valueOf(name);
    }
    return null;
  }

  static String genderLabel(String gender) {
    switch (gender) {
      case "male":
        return Res.str(R.string.studio_g_male);
      case "female":
        return Res.str(R.string.studio_g_female);
      default:
        return Res.str(R.string.studio_g_unisex);
    }
  }

  static String zoneLabel(@Nullable String zone) {
    if (zone == null) {
      return "";
    }
    switch (zone) {
      case "head":
        return Res.str(R.string.zone_head);
      case "upper":
        return Res.str(R.string.zone_upper);
      case "lower":
        return Res.str(R.string.zone_lower);
      case "full":
        return Res.str(R.string.zone_full);
      case "feet":
        return Res.str(R.string.zone_feet);
      default:
        return Res.str(R.string.zone_free);
    }
  }
}
