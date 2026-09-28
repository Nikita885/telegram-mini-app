package app.outfitshare.core.ui;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import app.outfitshare.core.designsystem.R;

/** «Outfit <em>Share</em>»: display medium + display italic, as in the mockups' .wordmark. */
public final class Wordmark {
  private Wordmark() {}

  public static void apply(TextView view) {
    Typeface medium = ResourcesCompat.getFont(view.getContext(), app.outfitshare.core.designsystem.R.font.ds_display_medium);
    Typeface italic = ResourcesCompat.getFont(view.getContext(), app.outfitshare.core.designsystem.R.font.ds_display_italic);
    SpannableStringBuilder sb = new SpannableStringBuilder("Outfit Share");
    sb.setSpan(new FontSpan(medium), 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    sb.setSpan(new FontSpan(italic), 7, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    view.setText(sb);
    view.setContentDescription("Outfit Share");
  }

  /** Multi-line cover variant: "Outfit" / "Share" (splash cover). */
  public static void applyCover(TextView view) {
    Typeface italic = ResourcesCompat.getFont(view.getContext(), app.outfitshare.core.designsystem.R.font.ds_display_italic);
    SpannableStringBuilder sb = new SpannableStringBuilder("Outfit\nShare");
    sb.setSpan(new FontSpan(italic), 7, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    view.setText(sb);
    view.setContentDescription("Outfit Share");
  }

  static final class FontSpan extends MetricAffectingSpan {
    private final Typeface typeface;

    FontSpan(Typeface typeface) {
      this.typeface = typeface;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint paint) {
      if (typeface != null) {
        paint.setTypeface(typeface);
      }
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint paint) {
      if (typeface != null) {
        paint.setTypeface(typeface);
      }
    }
  }
}
