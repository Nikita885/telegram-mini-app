package app.outfitshare.feature.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.ui.Res;

/** Mockup `.steps`: five segments of the pipeline, finished ones in the accent colour. */
public class StepsView extends View {
  public static final int COUNT = 5;
  private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final float gap;
  private int done;

  public StepsView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    on.setColor(DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorAccent));
    off.setColor(
        DsTheme.color(
            context, app.outfitshare.core.designsystem.R.attr.dsColorSurfaceContainerHighest));
    gap = 4 * getResources().getDisplayMetrics().density;
  }

  public void setDone(int done) {
    this.done = done;
    setContentDescription(
        Res.str(app.outfitshare.R.string.step_of, Math.min(done + 1, COUNT), COUNT));
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    float w = (getWidth() - gap * (COUNT - 1)) / COUNT;
    float r = getHeight() / 2f;
    for (int i = 0; i < COUNT; i++) {
      float x = i * (w + gap);
      rect.set(x, 0, x + w, getHeight());
      c.drawRoundRect(rect, r, r, i < done ? on : off);
    }
  }
}
