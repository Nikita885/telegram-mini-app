package app.outfitshare.feature.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.App;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.snackbar.DsSnackbar;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ButtonStyles;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** People rows with a follow button that changes kind (primary → secondary). */
public class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.Holder> {
  private final List<Dto.User> items = new ArrayList<>();
  private final Consumer<Dto.User> onOpen;

  public PeopleAdapter(Consumer<Dto.User> onOpen) {
    this.onOpen = onOpen;
  }

  public void submit(List<Dto.User> list) {
    items.clear();
    items.addAll(list);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_person, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Dto.User u = items.get(position);
    Images.avatar(h.avatar, u);
    h.title.setText(u.name);
    String sub = Formats.handle(u.username);
    if (u.isMe) {
      sub += " · это вы";
    } else if (u.isFollowing && u.followsYou) {
      sub += " · взаимно";
    } else if (u.followsYou) {
      sub += " · подписан(а) на вас";
    }
    h.sub.setText(sub);
    h.action.setVisibility(u.isMe ? View.GONE : View.VISIBLE);
    ButtonStyles.follow(h.action, u.isFollowing);
    h.action.setOnClickListener(v -> toggle(u, h));
    h.itemView.setOnClickListener(v -> onOpen.accept(u));
  }

  private void toggle(Dto.User u, Holder h) {
    boolean target = !u.isFollowing;
    u.isFollowing = target;
    ButtonStyles.follow(h.action, target);
    Calls.run(
        target ? App.get().container().api.api().follow(u.id) : App.get().container().api.api().unfollow(u.id),
        r -> {
          if (!r.ok()) {
            u.isFollowing = !target;
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
              notifyItemChanged(pos);
            }
            DsSnackbar.error(h.itemView, r.error != null ? r.error.message : "Не получилось", null);
          }
        });
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final AvatarView avatar;
    final TextView title;
    final TextView sub;
    final MaterialButton action;

    Holder(View v) {
      super(v);
      avatar = v.findViewById(R.id.avatar);
      title = v.findViewById(R.id.title);
      sub = v.findViewById(R.id.sub);
      action = v.findViewById(R.id.action);
    }
  }
}
