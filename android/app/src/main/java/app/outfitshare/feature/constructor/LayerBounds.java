package app.outfitshare.feature.constructor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import java.util.Map;
import java.util.WeakHashMap;

/** Opaque-pixel helpers for garment bitmaps (hit testing, selection frame). Results are cached. */
final class LayerBounds {
  private static final int ALPHA_MIN = 24;
  private static final Map<Bitmap, RectF> CACHE = new WeakHashMap<>();

  private LayerBounds() {}

  static RectF opaqueBounds(Bitmap b) {
    RectF cached = CACHE.get(b);
    if (cached != null) {
      return cached;
    }
    int w = b.getWidth();
    int h = b.getHeight();
    int step = Math.max(1, Math.max(w, h) / 200);
    int minX = w;
    int minY = h;
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < h; y += step) {
      for (int x = 0; x < w; x += step) {
        if (Color.alpha(b.getPixel(x, y)) > ALPHA_MIN) {
          if (x < minX) {
            minX = x;
          }
          if (y < minY) {
            minY = y;
          }
          if (x > maxX) {
            maxX = x;
          }
          if (y > maxY) {
            maxY = y;
          }
        }
      }
    }
    RectF r =
        maxX < 0
            ? new RectF(0, 0, w, h)
            : new RectF(minX, minY, Math.min(w, maxX + step), Math.min(h, maxY + step));
    CACHE.put(b, r);
    return r;
  }

  /** Is there an opaque pixel within `radius` of (x, y)? */
  static boolean isOpaqueNear(Bitmap b, int x, int y, int radius) {
    int w = b.getWidth();
    int h = b.getHeight();
    if (x < -radius || y < -radius || x >= w + radius || y >= h + radius) {
      return false;
    }
    int step = Math.max(1, radius / 2);
    for (int dy = -radius; dy <= radius; dy += step) {
      for (int dx = -radius; dx <= radius; dx += step) {
        int px = x + dx;
        int py = y + dy;
        if (px >= 0 && py >= 0 && px < w && py < h && Color.alpha(b.getPixel(px, py)) > ALPHA_MIN) {
          return true;
        }
      }
    }
    return false;
  }
}
