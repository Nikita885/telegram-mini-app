package app.outfitshare.feature.constructor;

import android.graphics.Matrix;

/**
 * Layer geometry shared with the server render (backend/mobile/render.py):
 *
 * <ul>
 *   <li>the mannequin canvas is W×H units; x, y are the layer centre in 0..1 of W, H;
 *   <li>a fitted layer's base box is the whole canvas — at x=y=0.5, scale=1, rotation=0 it sits
 *       exactly on the body;
 *   <li>a free layer's base box is {@link #FREE_BASE_WIDTH}·W wide, height from the image aspect;
 *   <li>the box is mirrored if flipped, scaled, rotated clockwise around its centre.
 * </ul>
 */
public final class OutfitGeometry {
  public static final float FREE_BASE_WIDTH = 0.4f;

  private OutfitGeometry() {}

  /** Base box size in canvas units. */
  public static float[] baseSize(boolean fitted, int canvasW, int canvasH, int imageW, int imageH) {
    if (fitted) {
      return new float[] {canvasW, canvasH};
    }
    float w = FREE_BASE_WIDTH * canvasW;
    float h = imageW > 0 ? w * imageH / imageW : w;
    return new float[] {w, h};
  }

  /**
   * Matrix mapping image pixels to canvas units.
   *
   * @param imageW bitmap width in pixels (the bitmap is stretched to the base box)
   */
  public static void layerToCanvas(
      Matrix out,
      float x,
      float y,
      float scale,
      float rotation,
      boolean flipped,
      float baseW,
      float baseH,
      int imageW,
      int imageH,
      int canvasW,
      int canvasH) {
    out.reset();
    out.postScale(baseW / Math.max(imageW, 1), baseH / Math.max(imageH, 1));
    out.postTranslate(-baseW / 2f, -baseH / 2f);
    if (flipped) {
      out.postScale(-1f, 1f);
    }
    out.postScale(scale, scale);
    out.postRotate(rotation);
    out.postTranslate(x * canvasW, y * canvasH);
  }

  /** Fit-center mapping of the canvas into a view: {scale, offsetX, offsetY}. */
  public static float[] fitCenter(int canvasW, int canvasH, int viewW, int viewH) {
    float k = Math.min(viewW / (float) canvasW, viewH / (float) canvasH);
    return new float[] {k, (viewW - canvasW * k) / 2f, (viewH - canvasH * k) / 2f};
  }

  /** Whether a fitted layer is close enough to its body position to snap back onto it. */
  public static boolean nearFittedPose(
      float x, float y, float scale, float rotation, float tolerance) {
    return Math.abs(x - 0.5f) < tolerance
        && Math.abs(y - 0.5f) < tolerance
        && Math.abs(scale - 1f) < 0.15f
        && Math.abs(normalizeAngle(rotation)) < 10f;
  }

  public static float normalizeAngle(float deg) {
    float a = deg % 360f;
    if (a > 180f) {
      a -= 360f;
    } else if (a < -180f) {
      a += 360f;
    }
    return a;
  }
}
