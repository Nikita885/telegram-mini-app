package app.outfitshare.feature.outfit;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import app.outfitshare.App;
import app.outfitshare.MainActivity;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.sheet.DsBottomSheetDialogFragment;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Images;
import app.outfitshare.feature.constructor.ConstructorFragment;

/** A garment of an outfit: photo, attributes, «Где купить» and «Собрать образ с этой вещью». */
public class ItemSheet extends DsBottomSheetDialogFragment {
  private static final String ARG_ITEM = "item";

  public static void show(FragmentManager fm, Dto.Item item) {
    ItemSheet sheet = new ItemSheet();
    Bundle args = new Bundle();
    args.putString(ARG_ITEM, App.get().container().gson.toJson(item));
    sheet.setArguments(args);
    sheet.show(fm, "item");
  }

  @NonNull
  @Override
  protected View onCreateSheetContent(
      @NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
    View v = inflater.inflate(R.layout.sheet_item, container, false);
    Dto.Item item =
        App.get().container().gson.fromJson(requireArguments().getString(ARG_ITEM), Dto.Item.class);
    Images.cutout((ImageView) v.findViewById(R.id.image), item.imageUrl);
    ((TextView) v.findViewById(R.id.name)).setText(item.name);
    StringBuilder meta = new StringBuilder(item.categoryName == null ? "" : item.categoryName);
    for (String part : new String[] {item.color, item.styleName, item.brand}) {
      if (!TextUtils.isEmpty(part)) {
        meta.append(" · ").append(part);
      }
    }
    ((TextView) v.findViewById(R.id.meta)).setText(meta);
    TextView price = v.findViewById(R.id.price);
    if (!TextUtils.isEmpty(item.price)) {
      price.setText(getString(R.string.price_rub, item.price.replace(".00", "")));
      price.setVisibility(View.VISIBLE);
    }
    TextView description = v.findViewById(R.id.description);
    if (!TextUtils.isEmpty(item.description)) {
      description.setText(item.description);
      description.setVisibility(View.VISIBLE);
    }
    View buy = v.findViewById(R.id.buy);
    buy.setVisibility(TextUtils.isEmpty(item.buyLink) ? View.GONE : View.VISIBLE);
    buy.setOnClickListener(
        x -> {
          try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.buyLink)));
          } catch (ActivityNotFoundException ignored) {
            // No browser installed.
          }
        });
    v.findViewById(R.id.try_on)
        .setOnClickListener(
            x -> {
              dismiss();
              if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity())
                    .navigator()
                    .present(ConstructorFragment.withItem(item));
              }
            });
    return v;
  }
}
