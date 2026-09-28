package app.outfitshare.feature.outfit;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.core.designsystem.component.card.ItemCardView;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Images;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Garment cards: horizontal rail on the outfit screen, grids in search and the constructor. */
public class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.Holder> {
  private final List<Dto.Item> items = new ArrayList<>();
  private final Consumer<Dto.Item> onClick;
  private final int fixedWidthPx;
  private final boolean showText;
  private final java.util.Set<Long> selected = new java.util.HashSet<>();

  /** @param fixedWidthPx card width for a horizontal rail, 0 to fill the grid cell */
  public ItemsAdapter(int fixedWidthPx, boolean showText, Consumer<Dto.Item> onClick) {
    this.fixedWidthPx = fixedWidthPx;
    this.showText = showText;
    this.onClick = onClick;
  }

  public void submit(List<Dto.Item> list) {
    items.clear();
    items.addAll(list);
    notifyDataSetChanged();
  }

  public void append(List<Dto.Item> list) {
    int start = items.size();
    items.addAll(list);
    notifyItemRangeInserted(start, list.size());
  }

  public void setSelected(java.util.Collection<Long> ids) {
    selected.clear();
    selected.addAll(ids);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ItemCardView card = new ItemCardView(parent.getContext());
    card.setLayoutParams(new RecyclerView.LayoutParams(
        fixedWidthPx > 0 ? fixedWidthPx : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    card.setCheckable(true);
    return new Holder(card);
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Dto.Item item = items.get(position);
    Images.cutout(h.card.getImageView(), item.imageUrl);
    h.card.setTitle(showText ? item.name : null);
    h.card.setSubtitle(showText ? subtitle(item) : null);
    h.card.setChecked(selected.contains(item.id));
    h.card.setContentDescription(item.name);
    h.card.setOnClickListener(v -> onClick.accept(item));
  }

  static String subtitle(Dto.Item item) {
    StringBuilder sb = new StringBuilder(item.categoryName == null ? "" : item.categoryName);
    if (item.brand != null && !item.brand.isEmpty()) {
      sb.append(" · ").append(item.brand);
    } else if (item.color != null && !item.color.isEmpty()) {
      sb.append(" · ").append(item.color);
    }
    return sb.toString();
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final ItemCardView card;

    Holder(ItemCardView card) {
      super(card);
      this.card = card;
    }
  }
}
