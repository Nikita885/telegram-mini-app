package app.outfitshare.core.designsystem.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import app.outfitshare.core.designsystem.R;
import com.google.android.material.color.MaterialColors;

/** Чтение семантических токенов из текущей темы. */
public final class DsTheme {

  private DsTheme() {}

  /**
   * Цвет атрибута темы, например {@code R.attr.dsColorAccent}.
   *
   * @throws IllegalArgumentException если контекст не использует тему Theme.Ds
   */
  @ColorInt
  public static int color(@NonNull Context context, @AttrRes int attr) {
    return MaterialColors.getColor(context, attr, DsTheme.class.getSimpleName());
  }

  /** Список состояний цвета из ресурса {@code res/color}. */
  @NonNull
  public static ColorStateList colorStateList(@NonNull Context context, @ColorRes int res) {
    ColorStateList list = AppCompatResources.getColorStateList(context, res);
    if (list == null) {
      throw new IllegalArgumentException("Нет ColorStateList " + res);
    }
    return list;
  }

  /** Контекст с тёмной палитрой дизайн-системы — для Студии и полноэкранного фото. */
  @NonNull
  public static Context darkContext(@NonNull Context context) {
    return new ContextThemeWrapper(context, R.style.ThemeOverlay_Ds_Dark);
  }

  /** {@code true}, если сейчас действует тёмная палитра. */
  public static boolean isDark(@NonNull Context context) {
    return !MaterialColors.isColorLight(color(context, R.attr.dsColorBackground));
  }
}
