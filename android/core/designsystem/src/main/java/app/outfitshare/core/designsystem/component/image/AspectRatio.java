package app.outfitshare.core.designsystem.component.image;

/** Разбор пропорций вида {@code "4:5"} из токенов {@code aspectRatio.*}. */
public final class AspectRatio {

  private AspectRatio() {}

  /**
   * Отношение высоты к ширине.
   *
   * @param spec строка «ширина:высота», например {@code "4:5"}
   * @return {@code высота / ширина} или 0, если строка пустая или некорректная
   */
  public static float heightToWidth(String spec) {
    if (spec == null) {
      return 0f;
    }
    String[] parts = spec.trim().split(":");
    if (parts.length != 2) {
      return 0f;
    }
    try {
      float width = Float.parseFloat(parts[0].trim());
      float height = Float.parseFloat(parts[1].trim());
      if (width <= 0f || height <= 0f || Float.isNaN(width) || Float.isNaN(height)) {
        return 0f;
      }
      return height / width;
    } catch (NumberFormatException e) {
      return 0f;
    }
  }
}
