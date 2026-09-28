package app.outfitshare.core.ui;

import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Gaps between list/grid items (token spacing, no dividers). */
public final class Spacing extends RecyclerView.ItemDecoration {
  private final int vertical;
  private final int horizontal;

  private Spacing(int vertical, int horizontal) {
    this.vertical = vertical;
    this.horizontal = horizontal;
  }

  public static Spacing vertical(int px) {
    return new Spacing(px, 0);
  }

  public static Spacing grid(int rowGapPx, int columnGapPx) {
    return new Spacing(rowGapPx, columnGapPx);
  }

  @Override
  public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    int pos = parent.getChildAdapterPosition(view);
    if (pos == RecyclerView.NO_POSITION) {
      return;
    }
    RecyclerView.LayoutManager lm = parent.getLayoutManager();
    if (lm instanceof GridLayoutManager) {
      GridLayoutManager glm = (GridLayoutManager) lm;
      int span = glm.getSpanCount();
      GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) view.getLayoutParams();
      if (lp.getSpanSize() == span) {
        out.top = pos == 0 ? 0 : vertical;
        return;
      }
      int col = lp.getSpanIndex();
      out.left = col * horizontal / span;
      out.right = horizontal - (col + 1) * horizontal / span;
      int row = glm.getSpanSizeLookup().getSpanGroupIndex(pos, span);
      out.top = row == 0 ? 0 : vertical;
    } else if (pos > 0) {
      out.top = vertical;
    }
  }
}
