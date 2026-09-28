package app.outfitshare.feature.studio;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.button.DsButton;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import app.outfitshare.feature.constructor.PublishedFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mockup «Студия: публикация вещи». Attributes come prefilled from the pipeline; the colour is a
 * ring-selected swatch (shape, not only colour). After publishing the item is in the constructor.
 */
public class StudioPublishFragment extends BaseFragment {
  private static final String ARG_JOB = "job";
  private static final String GENDER = "gender";
  private static final String STYLE = "style";
  // Style names are the catalog's own labels (Latin in both languages); "" is "no style".
  private static final String[][] STYLES = {
    {"", ""},
    {"casual", "Casual"},
    {"formal", "Formal"},
    {"sport", "Sport"},
    {"street", "Street"},
    {"business", "Business"}
  };

  private Dto.GarmentJob job;
  private String gender;
  private String style = "";
  @Nullable private String colorName;
  private final List<Dto.Category> categories = new ArrayList<>();

  public static StudioPublishFragment newInstance(Dto.GarmentJob job) {
    StudioPublishFragment f = new StudioPublishFragment();
    Bundle args = new Bundle();
    args.putString(ARG_JOB, app.outfitshare.App.get().container().gson.toJson(job));
    f.setArguments(args);
    return f;
  }

  public StudioPublishFragment() {
    super(R.layout.fragment_studio_publish);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    job = container().gson.fromJson(requireArguments().getString(ARG_JOB), Dto.GarmentJob.class);
    gender = job.gender;
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> nav().back());
    Images.cutout((ImageView) view.findViewById(R.id.cutout), job.cutoutUrl);
    String color = StudioText.colorName(job);
    ((EditText) view.findViewById(R.id.name)).setText(defaultName(job.categoryName, color));

    ListRowView layer = view.findViewById(R.id.layer);
    layer.setValue(StudioText.zoneLabel(job.zone));
    ListRowView genderRow = view.findViewById(R.id.gender);
    genderRow.setValue(StudioText.genderLabel(gender));
    genderRow.setOnClickListener(
        v -> {
          ArrayList<ActionSheet.Row> rows = new ArrayList<>();
          for (String g : new String[] {"unisex", "female", "male"}) {
            rows.add(
                new ActionSheet.Row(
                    g, 0, StudioText.genderLabel(g), null, false, g.equals(gender)));
          }
          ActionSheet.show(
              getChildFragmentManager(), GENDER, getString(R.string.studio_gender), rows);
        });
    getChildFragmentManager()
        .setFragmentResultListener(
            GENDER,
            getViewLifecycleOwner(),
            (k, r) -> {
              gender = r.getString(ActionSheet.RESULT_ID, gender);
              genderRow.setValue(StudioText.genderLabel(gender));
            });
    ListRowView styleRow = view.findViewById(R.id.style);
    styleRow.setValue(styleLabel(STYLES[0]));
    styleRow.setOnClickListener(
        v -> {
          ArrayList<ActionSheet.Row> rows = new ArrayList<>();
          for (String[] s : STYLES) {
            rows.add(
                new ActionSheet.Row(
                    s[0].isEmpty() ? "none" : s[0],
                    0,
                    styleLabel(s),
                    null,
                    false,
                    s[0].equals(style)));
          }
          ActionSheet.show(
              getChildFragmentManager(), STYLE, getString(R.string.studio_style), rows);
        });
    getChildFragmentManager()
        .setFragmentResultListener(
            STYLE,
            getViewLifecycleOwner(),
            (k, r) -> {
              String id = r.getString(ActionSheet.RESULT_ID, "none");
              style = "none".equals(id) ? "" : id;
              for (String[] s : STYLES) {
                if (s[0].equals(style)) {
                  styleRow.setValue(styleLabel(s));
                }
              }
            });

    bindSwatches(view);
    loadCategories(view.findViewById(R.id.categories));
    view.findViewById(R.id.publish).setOnClickListener(v -> publish());
  }

  private static String defaultName(String category, @Nullable String color) {
    if (color == null || color.isEmpty()) {
      return category;
    }
    return category + ", " + color;
  }

  @SuppressWarnings("unchecked")
  private void bindSwatches(View view) {
    LinearLayout row = view.findViewById(R.id.swatches);
    TextView name = view.findViewById(R.id.color_name);
    Object raw = job.attributes == null ? null : job.attributes.get("colors");
    List<Map<String, Object>> colors =
        raw instanceof List ? (List<Map<String, Object>>) raw : new ArrayList<>();
    int size = getResources().getDimensionPixelSize(R.dimen.swatch_size);
    int gap =
        getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_3);
    int ring =
        DsTheme.color(requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorOnSurface);
    int background =
        DsTheme.color(requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorBackground);
    List<View> swatches = new ArrayList<>();
    for (int i = 0; i < colors.size(); i++) {
      Map<String, Object> c = colors.get(i);
      String hex = String.valueOf(c.get("hex"));
      String label = String.valueOf(c.get("name"));
      View swatch = new View(requireContext());
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size + 8, size + 8);
      lp.setMarginEnd(gap);
      swatch.setLayoutParams(lp);
      swatch.setContentDescription(getString(R.string.studio_color_description, label));
      int fill;
      try {
        fill = Color.parseColor(hex);
      } catch (IllegalArgumentException e) {
        fill = Color.GRAY;
      }
      final int f = fill;
      swatch.setOnClickListener(
          v -> {
            colorName = label;
            name.setText(label);
            for (View s : swatches) {
              s.setBackground(swatchDrawable((Integer) s.getTag(), s == v, ring, background));
              s.setSelected(s == v);
            }
          });
      swatch.setTag(f);
      swatches.add(swatch);
      row.addView(swatch);
    }
    for (int i = 0; i < swatches.size(); i++) {
      swatches
          .get(i)
          .setBackground(
              swatchDrawable((Integer) swatches.get(i).getTag(), i == 0, ring, background));
    }
    if (!colors.isEmpty()) {
      colorName = String.valueOf(colors.get(0).get("name"));
      name.setText(colorName);
    }
  }

  /** Selected swatch: 2 dp background gap + 2 dp ring in the text colour (mockup swatch). */
  private GradientDrawable swatchDrawable(int fill, boolean selected, int ring, int background) {
    GradientDrawable d = new GradientDrawable();
    d.setShape(GradientDrawable.OVAL);
    d.setColor(fill);
    float density = getResources().getDisplayMetrics().density;
    if (selected) {
      d.setStroke((int) (3 * density), ring);
    } else {
      d.setStroke((int) (1 * density), Color.argb(30, 0, 0, 0));
    }
    return d;
  }

  private void loadCategories(ChipGroup group) {
    Calls.run(
        api().categories(),
        r -> {
          if (!isAdded() || !r.ok() || r.data == null) {
            return;
          }
          categories.clear();
          categories.addAll(r.data.results);
          group.removeAllViews();
          for (Dto.Category c : categories) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(c.name);
            chip.setTag(c.slug);
            chip.setCheckable(true);
            group.addView(chip);
            if (c.slug.equals(job.category)) {
              chip.setChecked(true);
            }
          }
        });
  }

  private void publish() {
    View v = requireView();
    String name = ((EditText) v.findViewById(R.id.name)).getText().toString().trim();
    TextInputLayout nameLayout = v.findViewById(R.id.name_layout);
    if (name.isEmpty()) {
      nameLayout.setError(getString(R.string.studio_name_required));
      return;
    }
    nameLayout.setError(null);
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    ChipGroup group = v.findViewById(R.id.categories);
    View checked = group.findViewById(group.getCheckedChipId());
    if (checked != null) {
      body.put("category", checked.getTag());
    }
    body.put("gender", gender);
    body.put("style", style);
    if (colorName != null) {
      body.put("color", colorName);
    }
    String brand = ((EditText) v.findViewById(R.id.brand)).getText().toString().trim();
    String price =
        ((EditText) v.findViewById(R.id.price)).getText().toString().trim().replace(',', '.');
    String link = ((EditText) v.findViewById(R.id.link)).getText().toString().trim();
    String description = ((EditText) v.findViewById(R.id.description)).getText().toString().trim();
    body.put("brand", brand);
    if (!price.isEmpty()) {
      body.put("price", price);
    }
    if (!link.isEmpty()) {
      body.put("buy_link", link.startsWith("http") ? link : "https://" + link);
    }
    body.put("description", description);

    DsButton button = v.findViewById(R.id.publish);
    button.setLoading(true);
    button.setText(R.string.publish_in_progress);
    Calls.run(
        api().publishJob(job.id, body),
        r -> {
          if (!isAdded()) {
            return;
          }
          button.setLoading(false);
          button.setText(R.string.studio_publish_button);
          if (!r.ok() || r.data == null) {
            String field = r.error == null ? null : r.error.fieldMessage("buy_link");
            if (field != null) {
              ((TextInputLayout) v.findViewById(R.id.link_layout))
                  .setError(getString(R.string.studio_link_invalid));
            } else {
              showError(r.error, this::publish);
            }
            return;
          }
          Haptics.perform(v, Haptics.Event.PUBLISH);
          Dto.GarmentJob done = r.data.job;
          String preview = done.preview.female != null ? done.preview.female : done.preview.male;
          nav().popInclusive(StudioReviewFragment.class.getSimpleName());
          nav()
              .push(
                  PublishedFragment.studioItem(
                      r.data.item.id,
                      preview != null ? preview : done.cutoutUrl,
                      r.data.item.name));
        });
  }

  private String styleLabel(String[] style) {
    return style[0].isEmpty() ? getString(R.string.studio_no_style) : style[1];
  }
}
