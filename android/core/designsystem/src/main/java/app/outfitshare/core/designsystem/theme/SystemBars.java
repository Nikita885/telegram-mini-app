package app.outfitshare.core.designsystem.theme;

import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge: контент рисуется под системными барами (обязательно с targetSdk 35), а отступы
 * добавляются инсетами. Фото в ленте и просмотре образа уходят под статус-бар — журнальная подача.
 */
public final class SystemBars {

  private SystemBars() {}

  /** Включает отрисовку под системными барами для окна Activity. */
  public static void enableEdgeToEdge(@NonNull Window window) {
    WindowCompat.setDecorFitsSystemWindows(window, false);
  }

  /**
   * Добавляет к исходным паддингам {@code view} размеры системных баров и выреза.
   *
   * @param top учитывать статус-бар
   * @param bottom учитывать навигационную панель и клавиатуру
   */
  public static void applyPadding(@NonNull View view, boolean top, boolean bottom) {
    final int start = view.getPaddingLeft();
    final int topPadding = view.getPaddingTop();
    final int end = view.getPaddingRight();
    final int bottomPadding = view.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(
        view,
        (v, windowInsets) -> {
          Insets bars =
              windowInsets.getInsets(
                  WindowInsetsCompat.Type.systemBars()
                      | WindowInsetsCompat.Type.displayCutout()
                      | WindowInsetsCompat.Type.ime());
          v.setPadding(
              start + bars.left,
              topPadding + (top ? bars.top : 0),
              end + bars.right,
              bottomPadding + (bottom ? bars.bottom : 0));
          return windowInsets;
        });
    ViewCompat.requestApplyInsets(view);
  }
}
