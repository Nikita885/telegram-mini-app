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

/** Dates, counts and plurals as used across the mockups, in the app's current language. */
public final class Formats {
  private Formats() {}

  // Built per call: the language can change while the process lives (per-app language setting).
  private static DateTimeFormatter pattern(String pattern) {
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault());
  }

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

  /** "сейчас", "5 мин", "2 ч", "3 дн", "12 окт" (en: "now", "5m", "2h", "3d", "Oct 12"). */
  public static String ago(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    if (t == null) {
      return "";
    }
    long minutes = ChronoUnit.MINUTES.between(t.toInstant(), Instant.now());
    if (minutes < 1) {
      return Res.str(R.string.time_now);
    }
    if (minutes < 60) {
      return Res.str(R.string.time_minutes_short, minutes);
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return Res.str(R.string.time_hours_short, hours);
    }
    long days = hours / 24;
    if (days < 7) {
      return Res.str(R.string.time_days_short, days);
    }
    return pattern(Res.str(R.string.pattern_day_month_short)).format(t).replace(".", "");
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
      return pattern("HH:mm").format(t);
    }
    if (day.equals(today.minusDays(1))) {
      return Res.str(R.string.time_yesterday_lower);
    }
    if (ChronoUnit.DAYS.between(day, today) < 7) {
      return pattern("EE").format(t).replace(".", "");
    }
    return pattern(Res.str(R.string.pattern_day_month_short)).format(t).replace(".", "");
  }

  public static String time(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    return t == null ? "" : pattern("HH:mm").format(t);
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
      return Res.str(R.string.time_today);
    }
    if (day.equals(today.minusDays(1))) {
      return Res.str(R.string.time_yesterday);
    }
    return pattern(
            Res.str(
                day.getYear() == today.getYear()
                    ? R.string.pattern_day_month
                    : R.string.pattern_day_month_year))
        .format(t);
  }

  public static String dayMonth(@Nullable String iso) {
    ZonedDateTime t = parse(iso);
    return t == null ? "" : pattern(Res.str(R.string.pattern_day_month)).format(t);
  }

  public static boolean sameDay(@Nullable String a, @Nullable String b) {
    ZonedDateTime x = parse(a);
    ZonedDateTime y = parse(b);
    return x != null && y != null && x.toLocalDate().equals(y.toLocalDate());
  }

  /** 950 → "950", 1200 → "1,2 тыс." (en "1.2K"), 12400 → "12 тыс.", 1 200 000 → "1,2 млн". */
  public static String count(long n) {
    if (n < 1000) {
      return String.valueOf(n);
    }
    if (n < 1_000_000) {
      return Res.str(R.string.count_thousands, compact(n / 1000.0));
    }
    return Res.str(R.string.count_millions, compact(n / 1_000_000.0));
  }

  private static String compact(double v) {
    if (v >= 10) {
      return String.valueOf((long) Math.floor(v));
    }
    double r = Math.floor(v * 10) / 10.0;
    return r == Math.floor(r)
        ? String.valueOf((long) r)
        : String.format(Locale.getDefault(), "%.1f", r);
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
