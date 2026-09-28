package app.outfitshare.feature.feed;

import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.card.OutfitCardView;
import app.outfitshare.core.designsystem.motion.SharedElements;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.OutfitActions;
import app.outfitshare.core.ui.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * Outfit cards: full width in the feed, compact in 2-column grids (search, similar, collections).
 */
public class OutfitAdapter extends ListAdapter<Dto.Outfit, OutfitAdapter.Holder> {

  public interface Listener {
    void onOpen(Dto.Outfit outfit, View photo);

    void onAuthor(Dto.User author);

    void onComments(Dto.Outfit outfit);

    void onTag(String tag);
  }

  private final boolean compact;
  private final Listener listener;

  public OutfitAdapter(boolean compact, Listener listener) {
    super(DIFF);
    this.compact = compact;
    this.listener = listener;
    setHasStableIds(true);
  }

  public void submit(List<Dto.Outfit> items) {
    submitList(new ArrayList<>(items));
  }

  @Override
  public long getItemId(int position) {
    return getItem(position).id;
  }

  /** Apply a change broadcast on the OutfitBus to the visible card. */
  public void applyLike(long outfitId, boolean liked, int count) {
    for (int i = 0; i < getItemCount(); i++) {
      Dto.Outfit o = getItem(i);
      if (o.id == outfitId) {
        o.isLiked = liked;
        o.likesCount = count;
        notifyItemChanged(i, "like");
      }
    }
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    OutfitCardView card = new OutfitCardView(parent.getContext());
    card.setLayoutParams(
        new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return new Holder(card);
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
    if (payloads.contains("like")) {
      Dto.Outfit o = getItem(position);
      h.card.setLiked(o.isLiked, o.likesCount);
      return;
    }
    super.onBindViewHolder(h, position, payloads);
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Dto.Outfit o = getItem(position);
    OutfitCardView card = h.card;
    Images.photo(card.getPhotoView(), o.imageUrl);
    Images.avatar(card.getAvatarView(), o.author);
    String meta;
    if (o.remixOf != null && o.remixOf.author != null) {
      meta = Formats.ago(o.createdAt) + " · ремикс " + Formats.handle(o.remixOf.author.username);
    } else {
      int n = o.items.size();
      meta =
          Formats.ago(o.createdAt)
              + " · "
              + card.getResources().getQuantityString(R.plurals.items_count, n, n);
    }
    card.setAuthor(o.author.id, displayName(o.author), compact ? null : meta);
    card.setBadge(o.remixOf != null ? "Ремикс" : null);
    card.setLiked(o.isLiked, o.likesCount);
    card.setSharedElementName(SharedElements.outfitPhoto(o.id));
    if (compact) {
      card.setCaption(null);
    } else {
      card.setCaption(Text.caption(card.getContext(), o.description, o.hashtags, listener::onTag));
      TextView caption =
          card.findViewById(app.outfitshare.core.designsystem.R.id.ds_outfit_caption);
      if (caption != null) {
        caption.setMovementMethod(LinkMovementMethod.getInstance());
      }
    }
    card.setOnLikeChangeListener((button, liked) -> OutfitActions.setLiked(card, o, liked));
    card.setOnClickListener(v -> listener.onOpen(o, card.getPhotoView()));
    card.setOnOpenComments(() -> listener.onComments(o));
    card.getAvatarView().setOnClickListener(v -> listener.onAuthor(o.author));
  }

  static String displayName(Dto.User u) {
    return u.name != null && !u.name.isEmpty() ? u.name : Formats.handle(u.username);
  }

  @Override
  public void onViewRecycled(@NonNull Holder holder) {
    holder.card.recycle();
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final OutfitCardView card;

    Holder(OutfitCardView card) {
      super(card);
      this.card = card;
    }
  }

  private static final DiffUtil.ItemCallback<Dto.Outfit> DIFF =
      new DiffUtil.ItemCallback<Dto.Outfit>() {
        @Override
        public boolean areItemsTheSame(@NonNull Dto.Outfit a, @NonNull Dto.Outfit b) {
          return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Dto.Outfit a, @NonNull Dto.Outfit b) {
          return a.likesCount == b.likesCount
              && a.isLiked == b.isLiked
              && a.commentsCount == b.commentsCount
              && a.isSaved == b.isSaved;
        }
      };
}
