package app.outfitshare.core.ui;

import android.widget.ImageView;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.net.dto.Dto;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/** Glide wrappers: photos cross-fade in, avatars fall back to initials. */
public final class Images {
  private Images() {}

  public static void photo(ImageView view, @Nullable String url) {
    if (url == null) {
      Glide.with(view).clear(view);
      view.setImageDrawable(null);
      return;
    }
    Glide.with(view)
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .transition(DrawableTransitionOptions.withCrossFade(150))
        .into(view);
  }

  /** Transparent garment layers and cutouts: no cross-fade (it would flash the background). */
  public static void cutout(ImageView view, @Nullable String url) {
    if (url == null) {
      Glide.with(view).clear(view);
      view.setImageDrawable(null);
      return;
    }
    Glide.with(view).load(url).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(view);
  }

  public static void avatar(AvatarView view, @Nullable Dto.User user) {
    Glide.with(view).clear(view);
    view.setImageDrawable(null);
    if (user == null) {
      view.setUser(0, null);
      return;
    }
    view.setUser(user.id, user.name != null && !user.name.isEmpty() ? user.name : user.username);
    if (user.avatarUrl != null) {
      Glide.with(view).load(user.avatarUrl).circleCrop().into(view);
    }
  }
}
