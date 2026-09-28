package app.outfitshare.feature.constructor;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import app.outfitshare.core.net.dto.Dto;

/** One garment on the mannequin. Geometry follows {@link OutfitGeometry}. */
public final class Layer {
  public final Dto.Item item;
  public float x = 0.5f;
  public float y = 0.5f;
  public float scale = 1f;
  public float rotation;
  public int z;
  public boolean flipped;

  /** Drawn from the fitted (canvas-sized) image instead of the free cutout. */
  public boolean fitted;

  public boolean hidden;
  @Nullable public transient Bitmap bitmap;

  /** 0 → 1 while the "drop onto the mannequin" animation runs. */
  public transient float drop = 1f;

  public Layer(Dto.Item item) {
    this.item = item;
  }

  public String zone() {
    return item.zone == null ? "accessory" : item.zone;
  }

  public boolean canFit(String gender) {
    return item.fittedFor(gender) != null;
  }

  @Nullable
  public String imageUrl(String gender) {
    return fitted ? item.fittedFor(gender) : item.imageUrl;
  }

  /** Values only (for undo/redo and drafts). */
  public State state() {
    State s = new State();
    s.itemId = item.id;
    s.x = x;
    s.y = y;
    s.scale = scale;
    s.rotation = rotation;
    s.z = z;
    s.flipped = flipped;
    s.fitted = fitted;
    s.hidden = hidden;
    return s;
  }

  public void apply(State s) {
    x = s.x;
    y = s.y;
    scale = s.scale;
    rotation = s.rotation;
    z = s.z;
    flipped = s.flipped;
    fitted = s.fitted;
    hidden = s.hidden;
  }

  public static final class State {
    public long itemId;
    public float x;
    public float y;
    public float scale;
    public float rotation;
    public int z;
    public boolean flipped;
    public boolean fitted;
    public boolean hidden;
  }
}
