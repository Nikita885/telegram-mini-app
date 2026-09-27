package app.outfitshare.core.designsystem.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Правила дизайн-системы, проверяемые на самих ресурсах модуля.
 *
 * <ul>
 *   <li>контраст основных пар цветов ≥ WCAG AA в обеих темах;
 *   <li>в разметке и стилях нет «магических» dp/sp/px — только токены;
 *   <li>интерактивные компоненты не меньше 48 dp;
 *   <li>текст задаётся в sp, отступы — по сетке 4 dp.
 * </ul>
 */
public class DesignSystemResourcesTest {

  private static final Pattern LITERAL_SIZE =
      Pattern.compile("\"(-?\\d+(\\.\\d+)?)(dp|sp|px|dip|pt|in|mm)\"");
  private static final double TEXT_AA = 4.5;
  private static final double UI_AA = 3.0;
  private static final float TOUCH_TARGET_DP = 48f;

  private final Map<String, String> colors =
      ResourceFiles.values("values/ds_tokens_colors.xml", "color");
  private final Map<String, String> dimens =
      ResourceFiles.values("values/ds_tokens_dimens.xml", "dimen");

  @Test
  public void coreColorPairsPassWcagAaInBothThemes() {
    String[][] text = {
      {"on_background", "background"},
      {"on_surface", "surface"},
      {"on_surface_variant", "background"},
      {"on_surface_variant", "surface_sunken"},
      {"on_primary", "primary"},
      {"on_accent", "accent"},
      {"accent", "background"},
      {"danger", "background"},
      {"inverse_on_surface", "inverse_surface"},
      {"inverse_accent", "inverse_surface"},
    };
    String[][] ui = {
      {"outline", "background"}, {"outline", "surface_sunken"}, {"like", "background"}
    };
    for (String theme : new String[] {"light", "dark"}) {
      for (String[] pair : text) {
        assertContrast(theme, pair, TEXT_AA);
      }
      for (String[] pair : ui) {
        assertContrast(theme, pair, UI_AA);
      }
    }
  }

  @Test
  public void layoutsAndStylesUseTokensInsteadOfLiteralSizes() throws IOException {
    List<File> checked = new ArrayList<>();
    checked.addAll(ResourceFiles.files("layout", ""));
    checked.addAll(ResourceFiles.files("values", "styles"));
    checked.addAll(ResourceFiles.files("values", "themes"));
    checked.addAll(ResourceFiles.files("values", "shapes"));
    checked.addAll(ResourceFiles.files("values-v27", ""));
    checked.addAll(ResourceFiles.files("values-v28", ""));
    checked.addAll(ResourceFiles.files("values-v29", ""));
    checked.addAll(ResourceFiles.files("drawable", "ds_bg_"));
    checked.addAll(ResourceFiles.files("animator", ""));
    checked.addAll(ResourceFiles.files("color", ""));
    assertTrue("нечего проверять", checked.size() > 20);

    List<String> violations = new ArrayList<>();
    for (File file : checked) {
      String[] lines =
          new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n");
      for (int i = 0; i < lines.length; i++) {
        Matcher m = LITERAL_SIZE.matcher(lines[i]);
        while (m.find()) {
          boolean weightedZero =
              Float.parseFloat(m.group(1)) == 0f && lines[i].contains("android:layout_");
          if (!weightedZero) {
            violations.add(file.getName() + ":" + (i + 1) + " " + lines[i].trim());
          }
        }
      }
    }
    assertEquals("Литеральные размеры вместо токенов: " + violations, 0, violations.size());
  }

  @Test
  public void interactiveComponentsMeetTouchTarget() {
    Map<String, Map<String, String>> styles = ResourceFiles.styles();
    String[] interactive = {
      "Widget.Ds.Button.Primary",
      "Widget.Ds.Button.Primary.Small",
      "Widget.Ds.Button.Secondary",
      "Widget.Ds.Button.Ghost",
      "Widget.Ds.Button.Icon",
      "Widget.Ds.Button.Danger",
      "Widget.Ds.LikeButton",
      "Widget.Ds.Switch",
      "Widget.Ds.CheckBox",
      "Widget.Ds.Snackbar.Button",
      "Widget.Ds.OfflineBanner",
    };
    for (String name : interactive) {
      Map<String, String> items = styles.get(name);
      assertTrue("нет стиля " + name, items != null);
      String minHeight = items.get("android:minHeight");
      assertTrue(name + ": не задан minHeight", minHeight != null);
      assertTrue(
          name + ": minHeight < 48 dp", ResourceFiles.dimen(minHeight, dimens) >= TOUCH_TARGET_DP);
    }
    for (String chip :
        new String[] {
          "Widget.Ds.Chip.Filter", "Widget.Ds.Chip.Input", "Widget.Ds.Chip.Suggestion"
        }) {
      Map<String, String> items = styles.get(chip);
      assertEquals(chip, "true", items.get("ensureMinTouchTargetSize"));
      assertTrue(
          chip,
          ResourceFiles.dimen(items.get("chipMinTouchTargetSize"), dimens) >= TOUCH_TARGET_DP);
    }
  }

  @Test
  public void textSizesAreScalable() {
    for (Map.Entry<String, String> e : dimens.entrySet()) {
      if (e.getKey().startsWith("ds_type_")) {
        assertTrue(e.getKey() + " должен быть в sp", e.getValue().endsWith("sp"));
      }
    }
    Map<String, Map<String, String>> styles = ResourceFiles.styles();
    for (Map.Entry<String, Map<String, String>> style : styles.entrySet()) {
      if (style.getKey().startsWith("TextAppearance.Ds.")) {
        String size = style.getValue().get("android:textSize");
        assertTrue(style.getKey(), size != null && size.startsWith("@dimen/ds_type_"));
      }
    }
  }

  @Test
  public void spacingFollowsFourDpGrid() {
    for (Map.Entry<String, String> e : dimens.entrySet()) {
      if (e.getKey().startsWith("ds_space_") && !e.getKey().equals("ds_space_0_5")) {
        float dp = ResourceFiles.dimen(e.getValue(), dimens);
        assertEquals(e.getKey(), 0f, dp % 4f, 0f);
      }
    }
  }

  private void assertContrast(String theme, String[] pair, double minimum) {
    String fg = colors.get("ds_" + theme + "_" + pair[0]);
    String bg = colors.get("ds_" + theme + "_" + pair[1]);
    if (fg == null || bg == null) {
      fail("нет цвета " + theme + " " + pair[0] + "/" + pair[1]);
    }
    double ratio = contrast(fg, bg);
    assertTrue(theme + ": " + pair[0] + " на " + pair[1] + " = " + ratio, ratio >= minimum);
  }

  private static double contrast(String argbA, String argbB) {
    double a = luminance(argbA);
    double b = luminance(argbB);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }

  private static double luminance(String argb) {
    String hex = argb.substring(argb.length() - 6);
    double r = channel(Integer.parseInt(hex.substring(0, 2), 16));
    double g = channel(Integer.parseInt(hex.substring(2, 4), 16));
    double b = channel(Integer.parseInt(hex.substring(4, 6), 16));
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double channel(int value) {
    double s = value / 255.0;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  }
}
