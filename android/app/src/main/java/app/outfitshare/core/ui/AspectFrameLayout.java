package app.outfitshare.core.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import app.outfitshare.R;

/** FrameLayout with a fixed width/height ratio; clips children to its rounded background. */
public class AspectFrameLayout extends FrameLayout {
  private float ratio;

  public AspectFrameLayout(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AspectFrameLayout);
    ratio = a.getFloat(R.styleable.AspectFrameLayout_aspectRatio, 1f);
    a.recycle();
    setClipToOutline(true);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    int height = Math.round(width / ratio);
    super.onMeasure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
  }

  public void setRatio(float ratio) {
    this.ratio = ratio;
    requestLayout();
  }
}
