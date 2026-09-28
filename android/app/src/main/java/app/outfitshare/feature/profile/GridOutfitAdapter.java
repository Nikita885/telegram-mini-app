package app.outfitshare.feature.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Images;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Profile grid 3×N, 4:5 photos with 2 dp gaps, remix marker. */
public class GridOutfitAdapter extends RecyclerView.Adapter<GridOutfitAdapter.Holder> {
  private final List<Dto.Outfit> items = new ArrayList<>();
  private final Consumer<Dto.Outfit> onClick;

  public GridOutfitAdapter(Consumer<Dto.Outfit> onClick) {
    this.onClick = onClick;
  }

  public void submit(List<Dto.Outfit> list) {
    items.clear();
    items.addAll(list);
    notifyDataSetChanged();
  }

  public void remove(long id) {
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).id == id) {
        items.remove(i);
        notifyItemRemoved(i);
        return;
      }
    }
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_outfit, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Dto.Outfit o = items.get(position);
    Images.photo(h.photo, o.imageUrl);
    h.photo.setContentDescription(
        "Образ" + (o.description == null || o.description.isEmpty() ? "" : ": " + o.description));
    h.badge.setVisibility(o.remixOf != null ? View.VISIBLE : View.GONE);
    h.itemView.setOnClickListener(v -> onClick.accept(o));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final ImageView photo;
    final ImageView badge;

    Holder(View v) {
      super(v);
      photo = v.findViewById(R.id.photo);
      badge = v.findViewById(R.id.badge);
    }
  }
}
