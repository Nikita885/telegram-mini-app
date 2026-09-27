package app.outfitshare.core.designsystem.component.like;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

/** Компактные счётчики лайков и подписчиков. */
public class CountFormatterTest {

  private final CountFormatter ru =
      new CountFormatter("%1$s тыс.", "%1$s млн", Locale.forLanguageTag("ru-RU"));

  @Test
  public void smallNumbersAsIs() {
    assertEquals("0", ru.format(0));
    assertEquals("999", ru.format(999));
    assertEquals("0", ru.format(-5));
  }

  @Test
  public void thousandsWithOneDecimalRoundedDown() {
    assertEquals("1 тыс.", ru.format(1_000));
    assertEquals("1,2 тыс.", ru.format(1_234));
    assertEquals("1,9 тыс.", ru.format(1_999));
    assertEquals("12 тыс.", ru.format(12_999));
    assertEquals("999 тыс.", ru.format(999_999));
  }

  @Test
  public void millions() {
    assertEquals("1,2 млн", ru.format(1_250_000));
    assertEquals("25 млн", ru.format(25_900_000));
  }

  @Test
  public void englishDecimalSeparator() {
    CountFormatter en = new CountFormatter("%1$sK", "%1$sM", Locale.ENGLISH);
    assertEquals("1.2K", en.format(1_234));
  }
}
