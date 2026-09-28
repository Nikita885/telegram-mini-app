package app.outfitshare.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.theme.DsTheme;

/** Mockup `.pager`: 6 dp dots, the current one a 20 dp pill. */
public class PagerDots extends View {
  private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final float dot;
  private final float wide;
  private final float gap;
  private int count = 3;
  private int current;

  public PagerDots(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    float d = getResources().getDisplayMetrics().density;
    dot = 6 * d;
    wide = 20 * d;
    gap = 6 * d;
    on.setColor(DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorOnSurface));
    off.setColor(
        DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorOutlineVariant));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  public void set(int count, int current) {
    this.count = count;
    this.current = current;
    setContentDescription("Шаг " + (current + 1) + " из " + count);
    invalidate();
  }

  @Override
  protected void onMeasure(int w, int h) {
    int width = (int) (wide + (count - 1) * (dot + gap));
    setMeasuredDimension(resolveSize(width, w), resolveSize((int) dot, h));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float x = 0;
    float cy = getHeight() / 2f;
    for (int i = 0; i < count; i++) {
      float w = i == current ? wide : dot;
      rect.set(x, cy - dot / 2, x + w, cy + dot / 2);
      canvas.drawRoundRect(rect, dot / 2, dot / 2, i == current ? on : off);
      x += w + gap;
    }
  }
}
