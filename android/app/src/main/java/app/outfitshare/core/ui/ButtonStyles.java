package app.outfitshare.core.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import com.google.android.material.button.MaterialButton;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.theme.DsTheme;

/**
 * Runtime switch between primary «Подписаться» and secondary «Вы подписаны»: the mockups change the
 * button's kind (shape + fill), not just its colour.
 */
public final class ButtonStyles {
  private ButtonStyles() {}

  public static void primary(MaterialButton b) {
    int bg = DsTheme.color(b.getContext(), app.outfitshare.core.designsystem.R.attr.dsColorPrimary);
    int fg = DsTheme.color(b.getContext(), app.outfitshare.core.designsystem.R.attr.dsColorOnPrimary);
    b.setBackgroundTintList(ColorStateList.valueOf(bg));
    b.setTextColor(fg);
    b.setIconTint(ColorStateList.valueOf(fg));
    b.setStrokeWidth(0);
  }

  public static void secondary(MaterialButton b) {
    int fg = DsTheme.color(b.getContext(), app.outfitshare.core.designsystem.R.attr.dsColorOnSurface);
    b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
    b.setTextColor(fg);
    b.setIconTint(ColorStateList.valueOf(fg));
    b.setStrokeColor(ColorStateList.valueOf(fg));
    b.setStrokeWidth(b.getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_size_stroke_hairline));
  }

  public static void follow(MaterialButton b, boolean following) {
    if (following) {
      secondary(b);
      b.setText("Вы подписаны");
      b.setIconResource(app.outfitshare.core.designsystem.R.drawable.ds_ic_check);
    } else {
      primary(b);
      b.setText("Подписаться");
      b.setIcon(null);
    }
  }
}
