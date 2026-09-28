package app.outfitshare.core.ui;

import android.content.Context;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Russian dates, counts and plurals as used across the mockups. */
public final class Formats {
  private static final Locale RU = new Locale("ru");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", RU);
  private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", RU);
  private static final DateTimeFormatter DAY_MONTH_FULL = DateTimeFormatter.ofPattern("d MMMM", RU);
  private static final DateTimeFormatter DAY_MONTH_YEAR =
      DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
  private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EE", RU);

  private Formats() {}

  @Nullable
  public static ZonedDateTime parse(@Nullable String iso) {
    if (iso == null || iso.isEmpty()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault());
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** "сейчас", "5 мин", "2 ч", "3 дн", "12 окт". */
  public static String ago(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    if (t == null) {
      return "";
    }
    long minutes = ChronoUnit.MINUTES.between(t.toInstant(), Instant.now());
    if (minutes < 1) {
      return "сейчас";
    }
    if (minutes < 60) {
      return minutes + " мин";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return hours + " ч";
    }
    long days = hours / 24;
    if (days < 7) {
      return days + " дн";
    }
    return DAY_MONTH.format(t).replace(".", "");
  }

  /** Dialog list: "12:40" today, "вчера", weekday within a week, else "24 сен". */
  public static String dialogTime(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    if (t == null) {
      return "";
    }
    LocalDate day = t.toLocalDate();
    LocalDate today = LocalDate.now();
    if (day.equals(today)) {
      return TIME.format(t);
    }
    if (day.equals(today.minusDays(1))) {
      return "вчера";
    }
    if (ChronoUnit.DAYS.between(day, today) < 7) {
      return WEEKDAY.format(t).replace(".", "");
    }
    return DAY_MONTH.format(t).replace(".", "");
  }

  public static String time(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    return t == null ? "" : TIME.format(t);
  }

  /** Chat date separator: "Сегодня", "Вчера", "12 октября". */
  public static String daySeparator(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    if (t == null) {
      return "";
    }
    LocalDate day = t.toLocalDate();
    LocalDate today = LocalDate.now();
    if (day.equals(today)) {
      return "Сегодня";
    }
    if (day.equals(today.minusDays(1))) {
      return "Вчера";
    }
    return (day.getYear() == today.getYear() ? DAY_MONTH_FULL : DAY_MONTH_YEAR).format(t);
  }

  public static String dayMonth(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    return t == null ? "" : DAY_MONTH_FULL.format(t);
  }

  public static boolean sameDay(@Nullable String a, @Nullable String b) {
    ZonedDateTime x = parse(a);
    ZonedDateTime y = parse(b);
    return x != null && y != null && x.toLocalDate().equals(y.toLocalDate());
  }

  /** 950 → "950", 1200 → "1,2 тыс.", 12400 → "12 тыс.", 1 200 000 → "1,2 млн". */
  public static String count(long n) {
    if (n < 1000) {
      return String.valueOf(n);
    }
    if (n < 1_000_000) {
      return compact(n / 1000.0) + " тыс.";
    }
    return compact(n / 1_000_000.0) + " млн";
  }

  private static String compact(double v) {
    if (v >= 10) {
      return String.valueOf((long) Math.floor(v));
    }
    double r = Math.floor(v * 10) / 10.0;
    return r == Math.floor(r) ? String.valueOf((long) r) : String.format(RU, "%.1f", r);
  }

  public static String plural(Context c, int pluralsRes, int n) {
    return c.getResources().getQuantityString(pluralsRes, n, n);
  }

  public static String outfitsWord(Context c, int n) {
    return c.getResources().getQuantityString(R.plurals.outfits_word, n);
  }

  public static String handle(@Nullable String username) {
    return username == null || username.isEmpty() ? "" : "@" + username;
  }
}
