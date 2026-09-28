package app.outfitshare.feature.studio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.theme.DsTheme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mockup «Студия: доводка точек»: garment keypoints dragged by finger. Marker 28 dp, touch target
 * 48 dp; the active point is accent-coloured with a loupe so the finger never hides the fabric edge.
 */
public class KeypointEditorView extends View {

  public interface Listener {
    void onDragStart();

    void onPointMoved(String name, float x, float y);

    void onDragEnd();
  }

  private final Paint canvasPaint = new Paint();
  private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint markerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint markerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint loupePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint loupeRing = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix imageMatrix = new Matrix();
  private final Matrix loupeMatrix = new Matrix();
  private final RectF rect = new RectF();
  private final float[] pt = new float[2];
  private final int accent;
  private final float markerRadius;
  private final float touchRadius;
  private final float loupeRadius;

  private final Map<String, float[]> points = new LinkedHashMap<>();
  @Nullable private Bitmap bitmap;
  @Nullable private BitmapShader shader;
  @Nullable private String active;
  @Nullable private Listener listener;
  private float scale = 1f;
  private float offsetX;
  private float offsetY;

  public KeypointEditorView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    float d = getResources().getDisplayMetrics().density;
    accent = DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorAccent);
    canvasPaint.setColor(DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorCanvas));
    markerStroke.setStyle(Paint.Style.STROKE);
    markerStroke.setStrokeWidth(2 * d);
    labelBg.setColor(Color.argb(140, 0, 0, 0));
    labelText.setColor(Color.WHITE);
    labelText.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
    labelText.setFakeBoldText(true);
    loupeRing.setStyle(Paint.Style.STROKE);
    markerRadius = getResources().getDimension(app.outfitshare.core.designsystem.R.dimen.ds_size_keypoint_handle) / 2f;
    touchRadius = getResources().getDimension(app.outfitshare.core.designsystem.R.dimen.ds_size_touch_target) / 2f;
    loupeRadius = 58 * d;
    setContentDescription("Опорные точки вещи");
  }

  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  public void setBitmap(@Nullable Bitmap b) {
    bitmap = b;
    shader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    requestLayout();
    invalidate();
  }

  /** Points in bitmap pixel coordinates (the cutout returned by the pipeline). */
  public void setPoints(Map<String, float[]> map) {
    points.clear();
    for (Map.Entry<String, float[]> e : map.entrySet()) {
      points.put(e.getKey(), new float[] {e.getValue()[0], e.getValue()[1]});
    }
    invalidate();
  }

  public Map<String, float[]> points() {
    return points;
  }

  @Nullable
  public String activePoint() {
    return active;
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
  }

  private void layoutImage() {
    if (bitmap == null) {
      return;
    }
    float pad = touchRadius;
    float availW = getWidth() - 2 * pad;
    float availH = getHeight() - 2 * pad;
    scale = Math.min(availW / bitmap.getWidth(), availH / bitmap.getHeight());
    offsetX = (getWidth() - bitmap.getWidth() * scale) / 2f;
    offsetY = (getHeight() - bitmap.getHeight() * scale) / 2f;
    imageMatrix.setScale(scale, scale);
    imageMatrix.postTranslate(offsetX, offsetY);
  }

  @Override
  protected void onDraw(Canvas c) {
    c.drawRect(0, 0, getWidth(), getHeight(), canvasPaint);
    if (bitmap == null) {
      return;
    }
    layoutImage();
    c.drawBitmap(bitmap, imageMatrix, bitmapPaint);
    for (Map.Entry<String, float[]> e : points.entrySet()) {
      boolean isActive = e.getKey().equals(active);
      float x = offsetX + e.getValue()[0] * scale;
      float y = offsetY + e.getValue()[1] * scale;
      markerFill.setColor(isActive ? Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)) : Color.argb(64, 0, 0, 0));
      markerStroke.setColor(isActive ? accent : Color.WHITE);
      dot.setColor(isActive ? accent : Color.WHITE);
      c.drawCircle(x, y, markerRadius, markerFill);
      c.drawCircle(x, y, markerRadius, markerStroke);
      c.drawCircle(x, y, markerRadius * 0.22f, dot);
      String label = label(e.getKey());
      float tw = labelText.measureText(label);
      float ly = y + markerRadius + labelText.getTextSize() + 2;
      rect.set(x - tw / 2 - 6, ly - labelText.getTextSize(), x + tw / 2 + 6, ly + 4);
      c.drawRoundRect(rect, 8, 8, labelBg);
      c.drawText(label, x - tw / 2, ly, labelText);
    }
    if (active != null && shader != null) {
      drawLoupe(c, points.get(active));
    }
  }

  /** 2.5× magnifier in the top-right corner, ring on the active point. */
  private void drawLoupe(Canvas c, @Nullable float[] p) {
    if (p == null) {
      return;
    }
    float cx = getWidth() - loupeRadius - 16 * getResources().getDisplayMetrics().density;
    float cy = loupeRadius + 16 * getResources().getDisplayMetrics().density;
    float zoom = scale * 2.5f;
    loupeMatrix.setScale(zoom, zoom);
    loupeMatrix.postTranslate(cx - p[0] * zoom, cy - p[1] * zoom);
    shader.setLocalMatrix(loupeMatrix);
    loupePaint.setShader(shader);
    c.drawCircle(cx, cy, loupeRadius, canvasPaint);
    c.drawCircle(cx, cy, loupeRadius, loupePaint);
    loupeRing.setColor(Color.WHITE);
    loupeRing.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
    c.drawCircle(cx, cy, loupeRadius, loupeRing);
    loupeRing.setColor(accent);
    loupeRing.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
    c.drawCircle(cx, cy, 9 * getResources().getDisplayMetrics().density, loupeRing);
  }

  public static String label(String key) {
    switch (key) {
      case "neck":
        return "Ворот";
      case "shoulder_left":
        return "Плечо Л";
      case "shoulder_right":
        return "Плечо П";
      case "waist_left":
        return "Талия Л";
      case "waist_right":
        return "Талия П";
      case "hem_left":
        return "Низ Л";
      case "hem_right":
        return "Низ П";
      case "box_left_top":
        return "Угол ↖";
      case "box_right_bottom":
        return "Угол ↘";
      default:
        return key;
    }
  }

  public static String longLabel(String key) {
    switch (key) {
      case "shoulder_left":
        return "Плечо слева";
      case "shoulder_right":
        return "Плечо справа";
      case "waist_left":
        return "Талия слева";
      case "waist_right":
        return "Талия справа";
      case "hem_left":
        return "Низ слева";
      case "hem_right":
        return "Низ справа";
      default:
        return label(key);
    }
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (bitmap == null) {
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        active = nearest(e.getX(), e.getY());
        if (active != null) {
          getParent().requestDisallowInterceptTouchEvent(true);
          Haptics.perform(this, Haptics.Event.DRAG_START);
          if (listener != null) {
            listener.onDragStart();
          }
          invalidate();
          return true;
        }
        return false;
      case MotionEvent.ACTION_MOVE:
        if (active != null) {
          float[] p = points.get(active);
          p[0] = clamp((e.getX() - offsetX) / scale, 0, bitmap.getWidth());
          p[1] = clamp((e.getY() - offsetY) / scale, 0, bitmap.getHeight());
          if (listener != null) {
            listener.onPointMoved(active, p[0], p[1]);
          }
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (active != null) {
          performClick();
          if (listener != null) {
            listener.onDragEnd();
          }
        }
        active = null;
        invalidate();
        return true;
      default:
        return true;
    }
  }

  @Nullable
  private String nearest(float x, float y) {
    String best = null;
    float bestD = touchRadius;
    for (Map.Entry<String, float[]> e : points.entrySet()) {
      float px = offsetX + e.getValue()[0] * scale;
      float py = offsetY + e.getValue()[1] * scale;
      float d = (float) Math.hypot(px - x, py - y);
      if (d <= bestD) {
        bestD = d;
        best = e.getKey();
      }
    }
    return best;
  }

  private static float clamp(float v, float lo, float hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  /** Deep copy for undo snapshots. */
  public static Map<String, float[]> copy(Map<String, float[]> src) {
    Map<String, float[]> out = new LinkedHashMap<>();
    for (Map.Entry<String, float[]> e : src.entrySet()) {
      out.put(e.getKey(), new float[] {e.getValue()[0], e.getValue()[1]});
    }
    return out;
  }

  static List<String> names(Map<String, float[]> m) {
    return new ArrayList<>(m.keySet());
  }
}
