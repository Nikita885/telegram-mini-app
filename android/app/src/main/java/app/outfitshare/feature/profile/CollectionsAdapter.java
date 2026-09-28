package app.outfitshare.feature.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Images;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Collection covers: 2×2 mosaic, title (lock icon if private), outfit count. */
public class CollectionsAdapter extends RecyclerView.Adapter<CollectionsAdapter.Holder> {
  private final List<Dto.Collection> items = new ArrayList<>();
  private final Consumer<Dto.Collection> onClick;

  public CollectionsAdapter(Consumer<Dto.Collection> onClick) {
    this.onClick = onClick;
  }

  public void submit(List<Dto.Collection> list) {
    items.clear();
    items.addAll(list);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_collection, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Dto.Collection c = items.get(position);
    for (int i = 0; i < 4; i++) {
      Images.photo(h.covers[i], i < c.covers.size() ? c.covers.get(i) : null);
    }
    h.title.setText(c.title);
    h.title.setCompoundDrawablesRelativeWithIntrinsicBounds(
        0, 0, c.isPrivate ? R.drawable.badge_lock : 0, 0);
    h.count.setText(
        h.itemView.getResources().getQuantityString(R.plurals.outfits_count, c.count, c.count));
    h.itemView.setContentDescription(
        c.title + (c.isPrivate ? ", приватная" : "") + ", " + h.count.getText());
    h.itemView.setOnClickListener(v -> onClick.accept(c));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final ImageView[] covers = new ImageView[4];
    final TextView title;
    final TextView count;

    Holder(View v) {
      super(v);
      covers[0] = v.findViewById(R.id.c0);
      covers[1] = v.findViewById(R.id.c1);
      covers[2] = v.findViewById(R.id.c2);
      covers[3] = v.findViewById(R.id.c3);
      title = v.findViewById(R.id.title);
      count = v.findViewById(R.id.count);
    }
  }
}
