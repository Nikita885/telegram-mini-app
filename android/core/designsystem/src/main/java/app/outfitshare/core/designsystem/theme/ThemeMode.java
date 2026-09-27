package app.outfitshare.core.designsystem.theme;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/** Выбор темы в настройках: как в системе, светлая, тёмная. */
public enum ThemeMode {
  SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
  LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
  DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

  private final String key;
  private final int nightMode;

  ThemeMode(String key, int nightMode) {
    this.key = key;
    this.nightMode = nightMode;
  }

  /** Ключ для хранения выбора в настройках. */
  @NonNull
  public String key() {
    return key;
  }

  /** Применяет режим ко всем Activity приложения (пересоздаёт их при смене). */
  public void apply() {
    AppCompatDelegate.setDefaultNightMode(nightMode);
  }

  /** Режим по сохранённому ключу; неизвестный ключ — {@link #SYSTEM}. */
  @NonNull
  public static ThemeMode fromKey(String key) {
    for (ThemeMode mode : values()) {
      if (mode.key.equals(key)) {
        return mode;
      }
    }
    return SYSTEM;
  }
}
