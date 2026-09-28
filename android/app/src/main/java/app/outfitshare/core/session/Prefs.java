package app.outfitshare.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import app.outfitshare.BuildConfig;
import app.outfitshare.core.designsystem.theme.ThemeMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Non-secret local settings. */
public final class Prefs {
  private static final String SERVER = "server";
  private static final String ONBOARDED = "onboarded";
  private static final String THEME = "theme";
  private static final String RECENT = "recent_searches";
  private static final String MANNEQUIN = "mannequin";

  private final SharedPreferences prefs;

  public Prefs(Context context) {
    prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
  }

  public String server() {
    String value = prefs.getString(SERVER, BuildConfig.DEFAULT_SERVER);
    return value.endsWith("/") ? value : value + "/";
  }

  public void setServer(String url) {
    prefs.edit().putString(SERVER, url.trim()).apply();
  }

  public boolean onboarded() {
    return prefs.getBoolean(ONBOARDED, false);
  }

  public void setOnboarded() {
    prefs.edit().putBoolean(ONBOARDED, true).apply();
  }

  public ThemeMode theme() {
    return ThemeMode.fromKey(prefs.getString(THEME, null));
  }

  public void setTheme(ThemeMode mode) {
    prefs.edit().putString(THEME, mode.key()).apply();
  }

  public String mannequin() {
    return prefs.getString(MANNEQUIN, "female");
  }

  public void setMannequin(String gender) {
    prefs.edit().putString(MANNEQUIN, gender).apply();
  }

  public List<String> recentSearches() {
    String raw = prefs.getString(RECENT, "");
    List<String> out = new ArrayList<>();
    if (!raw.isEmpty()) {
      out.addAll(Arrays.asList(raw.split("\n")));
    }
    return out;
  }

  public void addRecentSearch(String query) {
    List<String> list = recentSearches();
    list.remove(query);
    list.add(0, query);
    while (list.size() > 8) {
      list.remove(list.size() - 1);
    }
    prefs.edit().putString(RECENT, String.join("\n", list)).apply();
  }

  public void removeRecentSearch(String query) {
    List<String> list = recentSearches();
    list.remove(query);
    prefs.edit().putString(RECENT, String.join("\n", list)).apply();
  }

  public void clearRecentSearches() {
    prefs.edit().remove(RECENT).apply();
  }
}
