package app.outfitshare.feature.constructor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.sheet.DsBottomSheetDialogFragment;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.ui.Images;
import com.google.android.material.button.MaterialButton;
import java.util.Collections;
import java.util.List;

/** Mockup «Шторка слоёв»: drag to reorder, hide or remove a layer. */
public class LayersSheet extends DsBottomSheetDialogFragment {

  public interface Host {
    List<Layer> layers();

    void onReordered(List<Layer> topToBottom);

    void onToggleHidden(Layer layer);

    void removeLayer(Layer layer);

    String gender();
  }

  public static void show(FragmentManager fm) {
    new LayersSheet().show(fm, "layers");
  }

  private Host host() {
    return (Host) requireParentFragment();
  }

  @Nullable
  @Override
  protected CharSequence getSheetTitle() {
    return getString(R.string.constructor_layers);
  }

  @NonNull
  @Override
  protected View onCreateSheetContent(@NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
    LinearLayout root = new LinearLayout(requireContext());
    root.setOrientation(LinearLayout.VERTICAL);
    RecyclerView list = new RecyclerView(requireContext());
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    List<Layer> layers = host().layers();
    Adapter adapter = new Adapter(layers);
    list.setAdapter(adapter);
    ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
      @Override
      public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
        Collections.swap(layers, from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
        adapter.notifyItemMoved(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
        return true;
      }

      @Override
      public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int actionState) {
        if (vh != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
          Haptics.perform(vh.itemView, Haptics.Event.DRAG_START);
        }
      }

      @Override
      public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        host().onReordered(layers);
        adapter.notifyDataSetChanged();
      }

      @Override
      public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}
    });
    helper.attachToRecyclerView(list);
    adapter.helper = helper;
    root.addView(list);

    TextView hint = new TextView(requireContext());
    hint.setText(R.string.constructor_layers_hint);
    hint.setTextAppearance(app.outfitshare.core.designsystem.R.style.TextAppearance_Ds_BodySmall);
    int gutter = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    int pad = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_2);
    hint.setPadding(gutter, pad, gutter, getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_6));
    hint.setTextColor(app.outfitshare.core.designsystem.theme.DsTheme.color(requireContext(),
        app.outfitshare.core.designsystem.R.attr.dsColorOnSurfaceVariant));
    root.addView(hint);
    return root;
  }

  private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    final List<Layer> layers;
    ItemTouchHelper helper;

    Adapter(List<Layer> layers) {
      this.layers = layers;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layer, parent, false));
    }

    @Override
    @SuppressWarnings("ClickableViewAccessibility")
    public void onBindViewHolder(@NonNull Holder h, int position) {
      Layer l = layers.get(position);
      Images.cutout(h.thumb, l.item.imageUrl);
      h.title.setText(l.item.name);
      h.sub.setText((l.item.categoryName == null ? "" : l.item.categoryName) + " · слой " + (layers.size() - position));
      h.itemView.setAlpha(l.hidden ? 0.5f : 1f);
      h.visibility.setIconResource(l.hidden ? app.outfitshare.core.designsystem.R.drawable.ds_ic_visibility_off
          : app.outfitshare.core.designsystem.R.drawable.ds_ic_visibility);
      h.visibility.setContentDescription(l.hidden ? "Показать слой" : "Скрыть слой");
      h.visibility.setOnClickListener(v -> {
        host().onToggleHidden(l);
        notifyItemChanged(h.getBindingAdapterPosition());
      });
      h.delete.setOnClickListener(v -> {
        int i = h.getBindingAdapterPosition();
        host().removeLayer(l);
        layers.remove(i);
        notifyItemRemoved(i);
        if (layers.isEmpty()) {
          dismiss();
        }
      });
      h.handle.setOnTouchListener((v, e) -> {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && helper != null) {
          helper.startDrag(h);
        }
        return false;
      });
    }

    @Override
    public int getItemCount() {
      return layers.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
      final ImageView handle;
      final ImageView thumb;
      final TextView title;
      final TextView sub;
      final MaterialButton visibility;
      final MaterialButton delete;

      Holder(View v) {
        super(v);
        handle = v.findViewById(R.id.handle);
        thumb = v.findViewById(R.id.thumb);
        title = v.findViewById(R.id.title);
        sub = v.findViewById(R.id.sub);
        visibility = v.findViewById(R.id.visibility);
        delete = v.findViewById(R.id.delete);
      }
    }
  }
}
