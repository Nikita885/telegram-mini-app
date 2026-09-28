package app.outfitshare.core.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import app.outfitshare.App;
import app.outfitshare.R;
import app.outfitshare.core.AppContainer;
import app.outfitshare.core.designsystem.component.snackbar.DsSnackbar;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import java.util.HashMap;

/** Like / save / share for an outfit, optimistic and broadcast on the OutfitBus. */
public final class OutfitActions {
  private OutfitActions() {}

  public static void setLiked(View anchor, Dto.Outfit outfit, boolean liked) {
    AppContainer c = App.get().container();
    int before = outfit.likesCount;
    boolean wasLiked = outfit.isLiked;
    outfit.isLiked = liked;
    outfit.likesCount = Math.max(0, before + (liked == wasLiked ? 0 : liked ? 1 : -1));
    c.outfitBus.liked(outfit.id, liked, outfit.likesCount);
    Calls.run(
        liked ? c.api.api().like(outfit.id) : c.api.api().unlike(outfit.id),
        r -> {
          if (r.ok() && r.data != null) {
            outfit.likesCount = r.data.likesCount;
            c.outfitBus.liked(outfit.id, r.data.isLiked, r.data.likesCount);
          } else {
            outfit.isLiked = wasLiked;
            outfit.likesCount = before;
            c.outfitBus.liked(outfit.id, wasLiked, before);
            DsSnackbar.error(
                anchor,
                Res.str(R.string.outfit_like_failed),
                () -> setLiked(anchor, outfit, liked));
          }
        });
  }

  public static void toggleSaved(View anchor, Dto.Outfit outfit, Runnable onChanged) {
    AppContainer c = App.get().container();
    boolean target = !outfit.isSaved;
    outfit.isSaved = target;
    c.outfitBus.saved(outfit.id, target);
    onChanged.run();
    Calls.run(
        target ? c.api.api().save(outfit.id, new HashMap<>()) : c.api.api().unsave(outfit.id),
        r -> {
          if (!r.ok()) {
            outfit.isSaved = !target;
            c.outfitBus.saved(outfit.id, !target);
            onChanged.run();
            DsSnackbar.error(
                anchor,
                Res.str(R.string.outfit_save_failed),
                () -> toggleSaved(anchor, outfit, onChanged));
          } else {
            DsSnackbar.message(
                anchor, Res.str(target ? R.string.outfit_saved : R.string.outfit_unsaved));
          }
        });
  }

  public static String link(long outfitId) {
    return App.get().container().prefs.server() + "o/" + outfitId;
  }

  /** System share sheet with the outfit link. */
  public static void share(Context context, Dto.Outfit outfit) {
    Intent send = new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    String author = outfit.author != null ? outfit.author.name : "";
    send.putExtra(
        Intent.EXTRA_TEXT, context.getString(R.string.outfit_share_text, author, link(outfit.id)));
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.outfit_share_title)));
  }
}
