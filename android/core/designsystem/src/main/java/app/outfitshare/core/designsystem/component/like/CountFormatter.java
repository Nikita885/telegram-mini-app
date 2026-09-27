package app.outfitshare.core.designsystem.component.like;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Компактные счётчики: 999 → «999», 1 234 → «1,2 тыс.», 12 345 → «12 тыс.», 1 250 000 → «1,2 млн».
 *
 * <p>Округление вниз: 1 999 — это «1,9 тыс.», а не «2 тыс.», чтобы число не «обгоняло» реальность.
 */
public final class CountFormatter {

  private static final long THOUSAND = 1_000L;
  private static final long MILLION = 1_000_000L;
  private static final long DECIMAL_LIMIT = 10L;

  private final String thousandsPattern;
  private final String millionsPattern;
  private final DecimalFormat oneDecimal;
  private final DecimalFormat integer;

  /**
   * Создаёт форматтер.
   *
   * @param thousandsPattern шаблон с {@code %1$s}, например «%1$s тыс.»
   * @param millionsPattern шаблон с {@code %1$s}, например «%1$s млн»
   * @param locale локаль десятичного разделителя
   */
  public CountFormatter(String thousandsPattern, String millionsPattern, Locale locale) {
    this.thousandsPattern = thousandsPattern;
    this.millionsPattern = millionsPattern;
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
    oneDecimal = new DecimalFormat("0.#", symbols);
    oneDecimal.setRoundingMode(RoundingMode.DOWN);
    integer = new DecimalFormat("0", symbols);
    integer.setRoundingMode(RoundingMode.DOWN);
  }

  /** Компактная запись неотрицательного счётчика. */
  public String format(long count) {
    long value = Math.max(0L, count);
    if (value < THOUSAND) {
      return Long.toString(value);
    }
    if (value < MILLION) {
      return String.format(thousandsPattern, compact(value, THOUSAND));
    }
    return String.format(millionsPattern, compact(value, MILLION));
  }

  private String compact(long value, long unit) {
    double scaled = (double) value / unit;
    return scaled < DECIMAL_LIMIT ? oneDecimal.format(scaled) : integer.format(scaled);
  }
}
