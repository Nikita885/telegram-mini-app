package app.outfitshare.feature.constructor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.outfitshare.App;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.button.DsButton;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mockup «Публикация образа»: caption, hashtags with suggestions, collection, visibility. */
public class PublishFragment extends BaseFragment {
  private static final String ARG_GENDER = "gender";
  private static final String ARG_LAYERS = "layers";
  private static final String ARG_ITEMS = "items";
  private static final String ARG_REMIX = "remix";
  private static final String ARG_PREVIEW = "preview";
  private static final String VISIBILITY = "visibility";
  private static final String COLLECTION = "collection";

  private final Set<String> tags = new LinkedHashSet<>();
  private final List<Dto.Collection> collections = new ArrayList<>();
  private String visibility = "all";
  @Nullable private Long collectionId;
  private ChipGroup tagGroup;
  private DsButton publish;

  public static PublishFragment newInstance(String gender, List<Dto.Layer> layers, List<Dto.Item> items,
      @Nullable Long remixOf, Bitmap preview) {
    PublishFragment f = new PublishFragment();
    Bundle args = new Bundle();
    args.putString(ARG_GENDER, gender);
    args.putString(ARG_LAYERS, App.get().container().gson.toJson(layers));
    args.putString(ARG_ITEMS, App.get().container().gson.toJson(items));
    if (remixOf != null) {
      args.putLong(ARG_REMIX, remixOf);
    }
    File dir = new File(App.get().getCacheDir(), "shared");
    dir.mkdirs();
    File file = new File(dir, "outfit_preview.jpg");
    try (FileOutputStream out = new FileOutputStream(file)) {
      preview.compress(Bitmap.CompressFormat.JPEG, 90, out);
    } catch (Exception ignored) {
      // Preview is decorative: publishing still works without it.
    }
    args.putString(ARG_PREVIEW, file.getAbsolutePath());
    f.setArguments(args);
    return f;
  }

  public PublishFragment() {
    super(R.layout.fragment_publish);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> nav().back());
    ImageView preview = view.findViewById(R.id.preview);
    preview.setImageBitmap(BitmapFactory.decodeFile(requireArguments().getString(ARG_PREVIEW)));

    OfflineBanner offline = view.findViewById(R.id.offline);
    boolean online = container().api.isOnline();
    offline.setOffline(!online);
    offline.setVisibility(online ? View.GONE : View.VISIBLE);

    tagGroup = view.findViewById(R.id.tags);
    TextInputEditText tagInput = view.findViewById(R.id.tag_input);
    tagInput.setOnEditorActionListener((v, actionId, e) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        addTag(String.valueOf(tagInput.getText()));
        tagInput.setText("");
        return true;
      }
      return false;
    });
    loadSuggestions(view.findViewById(R.id.suggestions));

    ListRowView collectionRow = view.findViewById(R.id.collection);
    collectionRow.setValue("Не добавлять");
    collectionRow.setOnClickListener(v -> pickCollection());
    ListRowView visibilityRow = view.findViewById(R.id.visibility);
    visibilityRow.setValue("Все");
    visibilityRow.setOnClickListener(v -> pickVisibility());
    ListRowView remixRow = view.findViewById(R.id.allow_remix);
    remixRow.setToggle(true, (row, checked) -> {});

    getChildFragmentManager().setFragmentResultListener(VISIBILITY, getViewLifecycleOwner(), (k, r) -> {
      visibility = r.getString(ActionSheet.RESULT_ID, "all");
      visibilityRow.setValue(visibilityLabel(visibility));
    });
    getChildFragmentManager().setFragmentResultListener(COLLECTION, getViewLifecycleOwner(), (k, r) -> {
      String id = r.getString(ActionSheet.RESULT_ID, "none");
      collectionId = "none".equals(id) ? null : Long.parseLong(id);
      String label = "Не добавлять";
      for (Dto.Collection c : collections) {
        if (collectionId != null && c.id == collectionId) {
          label = c.title;
        }
      }
      collectionRow.setValue(label);
    });
    Calls.run(api().collections(null), r -> {
      if (r.ok() && r.data != null) {
        collections.clear();
        collections.addAll(r.data.results);
      }
    });

    publish = view.findViewById(R.id.publish);
    publish.setOnClickListener(v -> submit());
  }

  private static String visibilityLabel(String v) {
    switch (v) {
      case "followers":
        return "Подписчики";
      case "private":
        return "Только я";
      default:
        return "Все";
    }
  }

  private void pickVisibility() {
    ArrayList<ActionSheet.Row> rows = new ArrayList<>();
    rows.add(new ActionSheet.Row("all", app.outfitshare.core.designsystem.R.drawable.ds_ic_visibility, "Все", "Образ увидят все в ленте «Для вас»", false, "all".equals(visibility)));
    rows.add(new ActionSheet.Row("followers", app.outfitshare.core.designsystem.R.drawable.ds_ic_following, "Подписчики", "Только те, кто на вас подписан", false, "followers".equals(visibility)));
    rows.add(new ActionSheet.Row("private", app.outfitshare.core.designsystem.R.drawable.ds_ic_lock, "Только я", "Образ останется в профиле скрытым", false, "private".equals(visibility)));
    ActionSheet.show(getChildFragmentManager(), VISIBILITY, getString(R.string.publish_visibility), rows);
  }

  private void pickCollection() {
    ArrayList<ActionSheet.Row> rows = new ArrayList<>();
    rows.add(new ActionSheet.Row("none", app.outfitshare.core.designsystem.R.drawable.ds_ic_close, "Не добавлять", null, false, collectionId == null));
    for (Dto.Collection c : collections) {
      rows.add(new ActionSheet.Row(String.valueOf(c.id), c.isPrivate ? app.outfitshare.core.designsystem.R.drawable.ds_ic_lock
          : app.outfitshare.core.designsystem.R.drawable.ds_ic_collections, c.title, null, false, collectionId != null && collectionId == c.id));
    }
    ActionSheet.show(getChildFragmentManager(), COLLECTION, getString(R.string.publish_collection), rows);
  }

  private void loadSuggestions(ChipGroup group) {
    Calls.run(api().trendingHashtags(), r -> {
      if (!isAdded() || !r.ok() || r.data == null) {
        return;
      }
      for (Dto.Hashtag h : r.data.results) {
        Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
        chip.setText("+ #" + h.tag);
        chip.setCheckable(false);
        chip.setOnClickListener(v -> {
          addTag(h.tag);
          group.removeView(chip);
        });
        group.addView(chip);
      }
    });
  }

  private void addTag(String raw) {
    String tag = raw.trim().replace("#", "").replace(" ", "").toLowerCase();
    if (tag.length() < 2 || tags.contains(tag) || tags.size() >= 15) {
      return;
    }
    tags.add(tag);
    Chip chip = new Chip(requireContext());
    chip.setText("#" + tag);
    chip.setCloseIconVisible(true);
    chip.setCheckable(false);
    chip.setCloseIconContentDescription("Убрать #" + tag);
    chip.setOnCloseIconClickListener(v -> {
      tags.remove(tag);
      tagGroup.removeView(chip);
    });
    tagGroup.addView(chip);
  }

  private void submit() {
    Bundle args = requireArguments();
    List<Map<String, Object>> layers = new ArrayList<>();
    List<Dto.Layer> parsed = container().gson.fromJson(args.getString(ARG_LAYERS), new TypeToken<List<Dto.Layer>>() {}.getType());
    for (Dto.Layer l : parsed) {
      Map<String, Object> m = new HashMap<>();
      m.put("item_id", l.itemId);
      m.put("x", l.x);
      m.put("y", l.y);
      m.put("scale", l.scale);
      m.put("rotation", l.rotation);
      m.put("z", l.z);
      m.put("flipped", l.flipped);
      m.put("fitted", l.fitted);
      layers.add(m);
    }
    Map<String, Object> body = new HashMap<>();
    body.put("mannequin", args.getString(ARG_GENDER));
    body.put("description", String.valueOf(((TextInputEditText) requireView().findViewById(R.id.caption)).getText()).trim());
    body.put("hashtags", new ArrayList<>(tags));
    body.put("visibility", visibility);
    body.put("layers", layers);
    body.put("allow_remix", ((ListRowView) requireView().findViewById(R.id.allow_remix)).isToggleChecked());
    if (args.containsKey(ARG_REMIX)) {
      body.put("remix_of", args.getLong(ARG_REMIX));
    }
    publish.setLoading(true);
    publish.setText(R.string.publish_in_progress);
    Calls.run(api().createOutfit(body), r -> {
      if (!isAdded()) {
        return;
      }
      publish.setLoading(false);
      publish.setText(R.string.publish_button);
      if (!r.ok() || r.data == null) {
        showError(r.error, this::submit);
        return;
      }
      Haptics.perform(requireView(), Haptics.Event.PUBLISH);
      Dto.Outfit outfit = r.data;
      if (collectionId != null) {
        Map<String, Object> save = new HashMap<>();
        save.put("collection_id", collectionId);
        Calls.run(api().save(outfit.id, save), x -> {});
      }
      container().outfitBus.created(outfit.id);
      for (Fragment f : getParentFragmentManager().getFragments()) {
        if (f instanceof ConstructorFragment) {
          ((ConstructorFragment) f).onPublished();
        }
      }
      nav().replaceTop(PublishedFragment.outfit(outfit.id, outfit.imageUrl));
    });
  }
}
