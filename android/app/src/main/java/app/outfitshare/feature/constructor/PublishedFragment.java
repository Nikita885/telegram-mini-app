package app.outfitshare.feature.constructor;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.studio.StudioCameraFragment;
import com.google.android.material.button.MaterialButton;

/** Success screen after publishing an outfit (or a Studio item to the catalog). */
public class PublishedFragment extends BaseFragment {
  private static final String ARG_MODE = "mode";
  private static final String ARG_ID = "id";
  private static final String ARG_IMAGE = "image";
  private static final String ARG_NAME = "name";

  public static PublishedFragment outfit(long outfitId, @Nullable String imageUrl) {
    return create("outfit", outfitId, imageUrl, null);
  }

  public static PublishedFragment studioItem(
      long itemId, @Nullable String previewUrl, String name) {
    return create("studio", itemId, previewUrl, name);
  }

  private static PublishedFragment create(
      String mode, long id, @Nullable String image, @Nullable String name) {
    PublishedFragment f = new PublishedFragment();
    Bundle args = new Bundle();
    args.putString(ARG_MODE, mode);
    args.putLong(ARG_ID, id);
    args.putString(ARG_IMAGE, image);
    args.putString(ARG_NAME, name);
    f.setArguments(args);
    return f;
  }

  public PublishedFragment() {
    super(R.layout.fragment_published);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    Bundle args = requireArguments();
    boolean studio = "studio".equals(args.getString(ARG_MODE));
    Images.photo((ImageView) view.findViewById(R.id.image), args.getString(ARG_IMAGE));
    MaterialButton primary = view.findViewById(R.id.primary);
    MaterialButton secondary = view.findViewById(R.id.secondary);
    if (studio) {
      ((TextView) view.findViewById(R.id.kicker)).setText(R.string.studio_published);
      ((TextView) view.findViewById(R.id.headline))
          .setText(getString(R.string.studio_published_headline, args.getString(ARG_NAME)));
      primary.setText(R.string.studio_shoot_next);
      primary.setIconResource(app.outfitshare.core.designsystem.R.drawable.ds_ic_camera);
      secondary.setText(R.string.studio_to_queue);
      primary.setOnClickListener(
          v -> {
            nav().back();
            nav().present(new StudioCameraFragment());
          });
      secondary.setOnClickListener(v -> nav().back());
      view.findViewById(R.id.close).setOnClickListener(v -> nav().back());
    } else {
      long id = args.getLong(ARG_ID);
      primary.setOnClickListener(
          v -> {
            nav().popInclusive(ConstructorFragment.class.getSimpleName());
            nav().push(OutfitFragment.newInstance(id));
          });
      secondary.setOnClickListener(
          v -> {
            nav().popInclusive(ConstructorFragment.class.getSimpleName());
            nav().present(new ConstructorFragment());
          });
      view.findViewById(R.id.close)
          .setOnClickListener(v -> nav().popInclusive(ConstructorFragment.class.getSimpleName()));
    }
  }
}
