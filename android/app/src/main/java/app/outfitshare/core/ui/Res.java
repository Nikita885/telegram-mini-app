package app.outfitshare.core.ui;

import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import app.outfitshare.App;

/**
 * Localized strings for code without a Context at hand (helpers, adapters, static formatters). The
 * application context follows the system and per-app language, so this is safe for UI text.
 */
public final class Res {
  private Res() {}

  public static String str(@StringRes int id) {
    return App.get().getString(id);
  }

  public static String str(@StringRes int id, Object... args) {
    return App.get().getString(id, args);
  }

  public static String plural(@PluralsRes int id, int quantity) {
    return App.get().getResources().getQuantityString(id, quantity, quantity);
  }

  public static String plural(@PluralsRes int id, int quantity, Object... args) {
    return App.get().getResources().getQuantityString(id, quantity, args);
  }
}
