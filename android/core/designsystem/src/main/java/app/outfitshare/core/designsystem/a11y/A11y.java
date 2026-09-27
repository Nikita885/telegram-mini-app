package app.outfitshare.core.designsystem.a11y;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;

/**
 * Помощники доступности. Правила дизайн-системы:
 *
 * <ul>
 *   <li>любой интерактивный элемент ≥ 48×48 dp (размеры задают стили компонентов);
 *   <li>у иконок-кнопок и фото — contentDescription, у декоративных картинок — {@code
 *       importantForAccessibility="no"};
 *   <li>жесты (двойной тап, свайп) дублируются доступными действиями;
 *   <li>смена состояния экрана объявляется live region.
 * </ul>
 */
public final class A11y {

  /**
   * Масштаб шрифта, начиная с которого компоненты перестраиваются: нижняя навигация переходит на
   * иконки, однострочные имена получают вторую строку, кнопки в ряд встают друг под другом.
   */
  public static final float LARGE_FONT_SCALE = 1.5f;

  private A11y() {}

  /** Включён ли крупный системный шрифт (≥ {@link #LARGE_FONT_SCALE}). */
  public static boolean isLargeFontScale(@NonNull Context context) {
    return context.getResources().getConfiguration().fontScale >= LARGE_FONT_SCALE;
  }

  /** Помечает заголовок для навигации TalkBack по заголовкам. */
  public static void setHeading(@NonNull View view) {
    ViewCompat.setAccessibilityHeading(view, true);
  }

  /** Состояние элемента словами («отмечено», «загрузка»), читается после названия. */
  public static void setStateDescription(@NonNull View view, @Nullable CharSequence state) {
    ViewCompat.setStateDescription(view, state);
  }

  /** Объявлять изменения текста элемента, не прерывая текущую речь. */
  public static void announceChanges(@NonNull View view) {
    ViewCompat.setAccessibilityLiveRegion(view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
  }

  /**
   * Добавляет доступное действие — альтернативу жесту.
   *
   * @return идентификатор действия для последующего {@link #removeAction}
   */
  public static int addAction(
      @NonNull View view, @NonNull CharSequence label, @NonNull Runnable action) {
    AccessibilityViewCommand command =
        (target, arguments) -> {
          action.run();
          return true;
        };
    return ViewCompat.addAccessibilityAction(view, label, command);
  }

  /** Удаляет действие, добавленное {@link #addAction}. */
  public static void removeAction(@NonNull View view, int actionId) {
    if (actionId != View.NO_ID) {
      ViewCompat.removeAccessibilityAction(view, actionId);
    }
  }

  /**
   * Отмечает узел доступности как отмеченный/неотмеченный. На Android 16+ используется
   * трёхсостояний API, на старых версиях — прежний булев.
   */
  @SuppressWarnings("deprecation")
  public static void setChecked(@NonNull AccessibilityNodeInfo info, boolean checked) {
    info.setCheckable(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
      info.setChecked(
          checked
              ? AccessibilityNodeInfo.CHECKED_STATE_TRUE
              : AccessibilityNodeInfo.CHECKED_STATE_FALSE);
    } else {
      info.setChecked(checked);
    }
  }

  /** Включён ли TalkBack или другой сервис исследования касанием. */
  public static boolean isTouchExplorationEnabled(@NonNull Context context) {
    AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
    return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
  }
}
