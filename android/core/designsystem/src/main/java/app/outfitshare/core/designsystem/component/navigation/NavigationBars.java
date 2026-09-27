package app.outfitshare.core.designsystem.component.navigation;

import androidx.annotation.NonNull;
import app.outfitshare.core.designsystem.a11y.A11y;
import com.google.android.material.navigation.NavigationBarView;

/**
 * Нижняя навигация приложения: пять вкладок (Лента, Поиск, Создать, Диалоги, Профиль).
 *
 * <p>При крупном шрифте (≥ 150 %) подписи пяти вкладок не помещаются в 360 dp: навигация переходит
 * на иконки. Выбранная вкладка по-прежнему отличается не цветом, а пилюлей-индикатором и залитой
 * иконкой; название читает TalkBack и показывает подсказка по долгому нажатию.
 */
public final class NavigationBars {

  private NavigationBars() {}

  /** Применяет правила дизайн-системы к нижней навигации. Вызывать после inflate. */
  public static void applyDesignSystemRules(@NonNull NavigationBarView bar) {
    bar.setLabelVisibilityMode(
        A11y.isLargeFontScale(bar.getContext())
            ? NavigationBarView.LABEL_VISIBILITY_UNLABELED
            : NavigationBarView.LABEL_VISIBILITY_LABELED);
  }
}
