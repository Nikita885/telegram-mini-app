package app.outfitshare.core.designsystem.component.image;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * Фото с фиксированными пропорциями ({@code app:dsAspectRatio="@string/ds_aspect_outfit"}) и формой
 * из токенов. Высота вычисляется из ширины, поэтому сетка ленты не «прыгает» до загрузки.
 *
 * <p>Плейсхолдер — утопленная поверхность ({@code surfaceSunken}); загрузчик изображений приложения
 * кладёт картинку через {@link #setImageDrawable}.
 */
public class AspectRatioImageView extends ShapeableImageView {

  private float heightRatio;

  public AspectRatioImageView(@NonNull Context context) {
    this(context, null);
  }

  public AspectRatioImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsPhotoStyle);
  }

  public AspectRatioImageView(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray a =
        context.obtainStyledAttributes(attrs, R.styleable.AspectRatioImageView, defStyleAttr, 0);
    try {
      heightRatio =
          AspectRatio.heightToWidth(a.getString(R.styleable.AspectRatioImageView_dsAspectRatio));
    } finally {
      a.recycle();
    }
  }

  /** Задаёт пропорции строкой «ширина:высота»; пустая строка — обычное измерение. */
  public void setAspectRatio(@Nullable String spec) {
    float ratio = AspectRatio.heightToWidth(spec);
    if (ratio != heightRatio) {
      heightRatio = ratio;
      requestLayout();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    if (heightRatio > 0f && widthMode != MeasureSpec.UNSPECIFIED) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = Math.round(width * heightRatio);
      super.onMeasure(
          MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
      return;
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
