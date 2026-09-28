package app.outfitshare.feature.constructor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.motion.Motion;
import app.outfitshare.core.designsystem.theme.DsTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The constructor's studio "paper": mannequin + garment layers. Tap selects, one finger moves, two
 * fingers scale/rotate. A fitted garment released near its body pose snaps onto it (SNAP haptic).
 */
public class OutfitCanvasView extends View {

  public interface Listener {
    /** A gesture is about to change a layer: take an undo snapshot. */
    void onBeforeChange();

    void onChanged();

    void onSelectionChanged(@Nullable Layer layer);

    void onLongPress(Layer layer);
  }

  private static final float SNAP_TOLERANCE = 0.06f;

  private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint mannequinPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint handleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix matrix = new Matrix();
  private final Matrix inverse = new Matrix();
  private final float[] corners = new float[8];
  private final float[] point = new float[2];
  private final Path framePath = new Path();
  private final RectF oval = new RectF();
  private final int canvasColor;
  private final int guideColor;
  private final float handleRadius;

  private final List<Layer> layers = new ArrayList<>();
  private final List<Layer> drawOrder = new ArrayList<>();
  @Nullable private Bitmap mannequin;
  @Nullable private Map<String, List<Float>> anchors;
  private int canvasW = 750;
  private int canvasH = 1000;
  private String gender = "female";
  @Nullable private Layer selected;
  @Nullable private Listener listener;

  // Gesture state
  private final GestureDetector taps;
  private int activePointers;
  private float lastX;
  private float lastY;
  private float lastSpan;
  private float lastAngle;
  private boolean gestureChanged;
  private boolean dragging;

  public OutfitCanvasView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    canvasColor = DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorCanvas);
    int mannequinColor =
        DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorMannequin);
    mannequinPaint.setColorFilter(
        new PorterDuffColorFilter(mannequinColor, PorterDuff.Mode.SRC_IN));
    int selection =
        DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorSelection);
    guideColor = DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorSnapGuide);
    float d = getResources().getDisplayMetrics().density;
    framePaint.setStyle(Paint.Style.STROKE);
    framePaint.setStrokeWidth(
        getResources().getDimension(app.outfitshare.core.designsystem.R.dimen.ds_size_stroke_thin));
    framePaint.setColor(selection);
    handleFill.setColor(
        DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorSurface));
    handleStroke.setStyle(Paint.Style.STROKE);
    handleStroke.setStrokeWidth(
        getResources().getDimension(app.outfitshare.core.designsystem.R.dimen.ds_size_stroke_thin));
    handleStroke.setColor(selection);
    handleRadius = 5 * d;
    setContentDescription(context.getString(app.outfitshare.R.string.canvas_description));
    taps =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(@NonNull MotionEvent e) {
                return true;
              }

              @Override
              public boolean onSingleTapUp(@NonNull MotionEvent e) {
                select(hit(e.getX(), e.getY()));
                performClick();
                return true;
              }

              @Override
              public void onLongPress(@NonNull MotionEvent e) {
                Layer l = hit(e.getX(), e.getY());
                if (l != null && listener != null) {
                  select(l);
                  listener.onLongPress(l);
                }
              }
            });
  }

  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  public void setMannequin(
      @Nullable Bitmap bitmap,
      int width,
      int height,
      @Nullable Map<String, List<Float>> anchors,
      String gender) {
    this.mannequin = bitmap;
    this.canvasW = width;
    this.canvasH = height;
    this.anchors = anchors;
    this.gender = gender;
    invalidate();
  }

  public int canvasWidth() {
    return canvasW;
  }

  public int canvasHeight() {
    return canvasH;
  }

  public void setLayers(List<Layer> list) {
    layers.clear();
    layers.addAll(list);
    if (selected != null && !layers.contains(selected)) {
      selected = null;
    }
    invalidate();
  }

  @Nullable
  public Layer selected() {
    return selected;
  }

  public void select(@Nullable Layer layer) {
    if (selected != layer) {
      selected = layer;
      if (listener != null) {
        listener.onSelectionChanged(layer);
      }
      invalidate();
    }
  }

  /**
   * "Падение" of a newly added layer: from above with a spring overshoot, SNAP haptic on landing.
   */
  public void dropIn(Layer layer) {
    if (!Motion.animationsEnabled()) {
      layer.drop = 1f;
      Haptics.perform(this, Haptics.Event.SNAP);
      invalidate();
      return;
    }
    layer.drop = 0f;
    ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(
        getResources()
            .getInteger(app.outfitshare.core.designsystem.R.integer.ds_motion_duration_long));
    a.setInterpolator(new OvershootInterpolator(1.4f));
    a.addUpdateListener(
        v -> {
          layer.drop = (float) v.getAnimatedValue();
          invalidate();
        });
    postDelayed(() -> Haptics.perform(this, Haptics.Event.SNAP), (long) (a.getDuration() * 0.6f));
    a.start();
  }

  /** Animate a fitted layer back onto the body pose. */
  public void snapHome(Layer layer) {
    float fx = layer.x;
    float fy = layer.y;
    float fs = layer.scale;
    float fr = OutfitGeometry.normalizeAngle(layer.rotation);
    Haptics.perform(this, Haptics.Event.SNAP);
    if (!Motion.animationsEnabled()) {
      layer.x = 0.5f;
      layer.y = 0.5f;
      layer.scale = 1f;
      layer.rotation = 0f;
      invalidate();
      return;
    }
    ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(
        getResources()
            .getInteger(app.outfitshare.core.designsystem.R.integer.ds_motion_duration_medium));
    a.setInterpolator(new OvershootInterpolator(1.1f));
    a.addUpdateListener(
        v -> {
          float t = (float) v.getAnimatedValue();
          layer.x = fx + (0.5f - fx) * t;
          layer.y = fy + (0.5f - fy) * t;
          layer.scale = fs + (1f - fs) * t;
          layer.rotation = fr * (1f - t);
          invalidate();
        });
    a.start();
  }

  // ── Drawing ──────────────────────────────────────────────────────────────

  @Override
  protected void onDraw(@NonNull Canvas c) {
    c.drawColor(canvasColor);
    float[] fit = OutfitGeometry.fitCenter(canvasW, canvasH, getWidth(), getHeight());
    c.save();
    c.translate(fit[1], fit[2]);
    c.scale(fit[0], fit[0]);
    if (mannequin != null) {
      matrix.setScale(
          canvasW / (float) mannequin.getWidth(), canvasH / (float) mannequin.getHeight());
      c.drawBitmap(mannequin, matrix, mannequinPaint);
    }
    if (dragging && selected != null && selected.fitted) {
      drawGuides(c, selected.zone());
    }
    drawOrder.clear();
    drawOrder.addAll(layers);
    Collections.sort(drawOrder, (a, b) -> Integer.compare(a.z, b.z));
    for (Layer l : drawOrder) {
      if (l.hidden || l.bitmap == null) {
        continue;
      }
      layerMatrix(l, matrix);
      if (l.drop < 1f) {
        float offset =
            (1f - l.drop)
                * -getResources()
                    .getDimension(app.outfitshare.core.designsystem.R.dimen.ds_motion_drop_offset)
                / fit[0];
        matrix.postTranslate(0, offset);
        bitmapPaint.setAlpha((int) (255 * Math.min(1f, Math.max(0f, l.drop * 1.5f))));
      } else {
        bitmapPaint.setAlpha(255);
      }
      c.drawBitmap(l.bitmap, matrix, bitmapPaint);
    }
    c.restore();
    if (selected != null && selected.bitmap != null && !selected.hidden) {
      drawFrame(c, selected, fit);
    }
  }

  private void layerMatrix(Layer l, Matrix out) {
    Bitmap b = l.bitmap;
    int iw = b == null ? 1 : b.getWidth();
    int ih = b == null ? 1 : b.getHeight();
    float[] base = OutfitGeometry.baseSize(l.fitted, canvasW, canvasH, iw, ih);
    OutfitGeometry.layerToCanvas(
        out, l.x, l.y, l.scale, l.rotation, l.flipped, base[0], base[1], iw, ih, canvasW, canvasH);
  }

  /**
   * Selection frame around the visible garment (fitted layers: its opaque bounds, not the canvas).
   */
  private void drawFrame(Canvas c, Layer l, float[] fit) {
    Bitmap b = l.bitmap;
    if (b == null) {
      return;
    }
    RectF content = LayerBounds.opaqueBounds(b);
    layerMatrix(l, matrix);
    corners[0] = content.left;
    corners[1] = content.top;
    corners[2] = content.right;
    corners[3] = content.top;
    corners[4] = content.right;
    corners[5] = content.bottom;
    corners[6] = content.left;
    corners[7] = content.bottom;
    matrix.mapPoints(corners);
    for (int i = 0; i < 8; i += 2) {
      corners[i] = corners[i] * fit[0] + fit[1];
      corners[i + 1] = corners[i + 1] * fit[0] + fit[2];
    }
    framePath.reset();
    framePath.moveTo(corners[0], corners[1]);
    framePath.lineTo(corners[2], corners[3]);
    framePath.lineTo(corners[4], corners[5]);
    framePath.lineTo(corners[6], corners[7]);
    framePath.close();
    c.drawPath(framePath, framePaint);
    for (int i = 0; i < 8; i += 2) {
      c.drawCircle(corners[i], corners[i + 1], handleRadius, handleFill);
      c.drawCircle(corners[i], corners[i + 1], handleRadius, handleStroke);
    }
  }

  /** Soft accent zones where the dragged garment attaches (mockup «Перетаскивание: snap»). */
  private void drawGuides(Canvas c, String zone) {
    if (anchors == null) {
      return;
    }
    switch (zone) {
      case "upper":
      case "full":
        guideBetween(c, "shoulder_left", "shoulder_right", 0.035f);
        guideBetween(c, "waist_left", "waist_right", 0.025f);
        break;
      case "lower":
        guideBetween(c, "waist_left", "waist_right", 0.025f);
        guideBetween(c, "hips_left", "hips_right", 0.025f);
        break;
      case "feet":
        guideBetween(c, "feet_left", "feet_right", 0.02f);
        break;
      case "head":
        guideBetween(c, "head_left", "head_right", 0.03f);
        break;
      default:
        break;
    }
  }

  private void guideBetween(Canvas c, String a, String b, float halfHeight) {
    List<Float> p = anchors.get(a);
    List<Float> q = anchors.get(b);
    if (p == null || q == null) {
      return;
    }
    float x0 = p.get(0) * canvasW;
    float x1 = q.get(0) * canvasW;
    float y = (p.get(1) + q.get(1)) / 2f * canvasH;
    float h = halfHeight * canvasH;
    oval.set(Math.min(x0, x1) - h, y - h, Math.max(x0, x1) + h, y + h);
    int base =
        Color.argb(110, Color.red(guideColor), Color.green(guideColor), Color.blue(guideColor));
    guidePaint.setShader(
        new RadialGradient(
            oval.centerX(),
            oval.centerY(),
            Math.max(oval.width(), oval.height()) / 2f,
            new int[] {
              base,
              Color.argb(0, Color.red(guideColor), Color.green(guideColor), Color.blue(guideColor))
            },
            new float[] {0.55f, 1f},
            Shader.TileMode.CLAMP));
    c.save();
    c.scale(1f, oval.height() / oval.width(), oval.centerX(), oval.centerY());
    c.drawCircle(oval.centerX(), oval.centerY(), oval.width() / 2f, guidePaint);
    c.restore();
  }

  // ── Hit testing ──────────────────────────────────────────────────────────

  @Nullable
  private Layer hit(float vx, float vy) {
    float[] fit = OutfitGeometry.fitCenter(canvasW, canvasH, getWidth(), getHeight());
    float cx = (vx - fit[1]) / fit[0];
    float cy = (vy - fit[2]) / fit[0];
    drawOrder.clear();
    drawOrder.addAll(layers);
    Collections.sort(drawOrder, (a, b) -> Integer.compare(b.z, a.z));
    // Touch slop in bitmap space: fingers are wider than a sleeve.
    for (Layer l : drawOrder) {
      Bitmap b = l.bitmap;
      if (l.hidden || b == null) {
        continue;
      }
      layerMatrix(l, matrix);
      if (!matrix.invert(inverse)) {
        continue;
      }
      point[0] = cx;
      point[1] = cy;
      inverse.mapPoints(point);
      if (LayerBounds.isOpaqueNear(
          b, (int) point[0], (int) point[1], Math.max(4, b.getWidth() / 60))) {
        return l;
      }
    }
    return null;
  }

  // ── Gestures ─────────────────────────────────────────────────────────────

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    taps.onTouchEvent(e);
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        {
          Layer l = hit(e.getX(), e.getY());
          if (l != null) {
            select(l);
          }
          activePointers = 1;
          lastX = e.getX();
          lastY = e.getY();
          gestureChanged = false;
          getParent().requestDisallowInterceptTouchEvent(selected != null);
          return true;
        }
      case MotionEvent.ACTION_POINTER_DOWN:
        activePointers = e.getPointerCount();
        if (activePointers >= 2) {
          lastSpan = span(e);
          lastAngle = angle(e);
          lastX = focusX(e);
          lastY = focusY(e);
        }
        return true;
      case MotionEvent.ACTION_MOVE:
        if (selected == null) {
          return true;
        }
        float[] fit = OutfitGeometry.fitCenter(canvasW, canvasH, getWidth(), getHeight());
        float fx = e.getPointerCount() >= 2 ? focusX(e) : e.getX();
        float fy = e.getPointerCount() >= 2 ? focusY(e) : e.getY();
        float dx = fx - lastX;
        float dy = fy - lastY;
        if (!gestureChanged && Math.hypot(dx, dy) < 6 && e.getPointerCount() < 2) {
          return true;
        }
        if (!gestureChanged) {
          gestureChanged = true;
          dragging = true;
          if (listener != null) {
            listener.onBeforeChange();
          }
          Haptics.perform(this, Haptics.Event.DRAG_START);
        }
        selected.x += dx / fit[0] / canvasW;
        selected.y += dy / fit[0] / canvasH;
        if (e.getPointerCount() >= 2) {
          float s = span(e);
          float a = angle(e);
          if (lastSpan > 0) {
            selected.scale = Math.max(0.2f, Math.min(6f, selected.scale * s / lastSpan));
          }
          selected.rotation += a - lastAngle;
          lastSpan = s;
          lastAngle = a;
        }
        lastX = fx;
        lastY = fy;
        invalidate();
        return true;
      case MotionEvent.ACTION_POINTER_UP:
        {
          // Re-anchor on the remaining finger so the layer doesn't jump.
          int up = e.getActionIndex();
          int keep = up == 0 ? 1 : 0;
          lastX = e.getX(keep);
          lastY = e.getY(keep);
          activePointers = e.getPointerCount() - 1;
          return true;
        }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        dragging = false;
        if (gestureChanged && selected != null) {
          if (selected.fitted
              && OutfitGeometry.nearFittedPose(
                  selected.x, selected.y, selected.scale, selected.rotation, SNAP_TOLERANCE)
              && (selected.x != 0.5f
                  || selected.y != 0.5f
                  || selected.scale != 1f
                  || selected.rotation != 0f)) {
            snapHome(selected);
          }
          if (listener != null) {
            listener.onChanged();
          }
        }
        gestureChanged = false;
        activePointers = 0;
        invalidate();
        return true;
      default:
        return true;
    }
  }

  private static float span(MotionEvent e) {
    return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
  }

  private static float angle(MotionEvent e) {
    return (float) Math.toDegrees(Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
  }

  private static float focusX(MotionEvent e) {
    return (e.getX(0) + e.getX(1)) / 2f;
  }

  private static float focusY(MotionEvent e) {
    return (e.getY(0) + e.getY(1)) / 2f;
  }

  /** Render the outfit to a bitmap (publish preview), same geometry as the server. */
  public Bitmap renderPreview(int width) {
    int height = Math.round(width * canvasH / (float) canvasW);
    Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    c.drawColor(canvasColor);
    float k = width / (float) canvasW;
    c.scale(k, k);
    if (mannequin != null) {
      matrix.setScale(
          canvasW / (float) mannequin.getWidth(), canvasH / (float) mannequin.getHeight());
      c.drawBitmap(mannequin, matrix, mannequinPaint);
    }
    drawOrder.clear();
    drawOrder.addAll(layers);
    Collections.sort(drawOrder, (a, b) -> Integer.compare(a.z, b.z));
    bitmapPaint.setAlpha(255);
    for (Layer l : drawOrder) {
      if (!l.hidden && l.bitmap != null) {
        layerMatrix(l, matrix);
        c.drawBitmap(l.bitmap, matrix, bitmapPaint);
      }
    }
    return out;
  }
}
