package app.outfitshare.feature.studio;

import androidx.annotation.Nullable;
import app.outfitshare.core.net.dto.Dto;
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
        return "Вырезаем фон";
      case 2:
        return "Выравниваем контур";
      case 3:
        return "Ищем опорные точки";
      case 4:
        return "Подгоняем к манекену";
      case 5:
        return "Готовим превью";
      default:
        return "В очереди";
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
        return "Мужское";
      case "female":
        return "Женское";
      default:
        return "Унисекс";
    }
  }

  static String zoneLabel(@Nullable String zone) {
    if (zone == null) {
      return "";
    }
    switch (zone) {
      case "head":
        return "Голова";
      case "upper":
        return "Плечи · торс";
      case "lower":
        return "Талия · ноги";
      case "full":
        return "Всё тело";
      case "feet":
        return "Стопы";
      default:
        return "Свободно";
    }
  }
}
