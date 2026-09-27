package app.outfitshare.core.designsystem.component.badge;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import app.outfitshare.core.designsystem.R;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.navigation.NavigationBarView;

/**
 * Счётчики на нижней навигации: непрочитанные диалоги и уведомления.
 *
 * <p>Цвет — акцент (стиль {@code Widget.Ds.Badge}), максимум «99+». TalkBack читает «3
 * непрочитанных» вместе с названием вкладки. Текстовые бейджи («Новое», «Ремикс») — стили {@code
 * Widget.Ds.TextBadge.*} на обычном TextView.
 */
public final class Badges {

  private Badges() {}

  /** Показывает число или убирает бейдж при {@code count <= 0}. */
  public static void setCount(@NonNull NavigationBarView bar, @IdRes int itemId, int count) {
    if (count <= 0) {
      bar.removeBadge(itemId);
      return;
    }
    BadgeDrawable badge = bar.getOrCreateBadge(itemId);
    badge.setNumber(count);
    badge.setContentDescriptionQuantityStringsResource(R.plurals.ds_a11y_unread_count);
    badge.setVisible(true);
  }

  /** Точка без числа: «есть новое». */
  public static void setDot(@NonNull NavigationBarView bar, @IdRes int itemId, boolean visible) {
    if (!visible) {
      bar.removeBadge(itemId);
      return;
    }
    BadgeDrawable badge = bar.getOrCreateBadge(itemId);
    badge.clearNumber();
    badge.setVisible(true);
  }
}
