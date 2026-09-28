package app.outfitshare.feature.studio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.dialog.ConfirmDialog;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.realtime.Realtime;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

/**
 * Mockup «Студия: проверка»: before/after, fit score as icon + number, a low fit blocks «Далее»
 * until the keypoints are adjusted.
 */
public class StudioReviewFragment extends BaseFragment {
  private static final String ARG_ID = "id";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private long jobId;
  @Nullable private Dto.GarmentJob job;
  private String viewGender = "female";
  private boolean showBefore;

  private final Realtime.Listener realtime = (event, payload) -> {
    if ("studio.job".equals(event) && payload.has("id") && payload.get("id").getAsLong() == jobId) {
      load();
    }
  };

  public static StudioReviewFragment newInstance(long jobId) {
    StudioReviewFragment f = new StudioReviewFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_ID, jobId);
    f.setArguments(args);
    return f;
  }

  public StudioReviewFragment() {
    super(R.layout.fragment_studio_review);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    jobId = requireArguments().getLong(ARG_ID);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> nav().back());
    toolbar.getMenu().add("Переобработать");
    toolbar.getMenu().add("Удалить");
    toolbar.setOnMenuItemClickListener(item -> {
      if ("Удалить".contentEquals(item.getTitle())) {
        ConfirmDialog.with(requireContext()).title("Удалить снимок?").message("Вещь не попадёт в каталог.")
            .confirm("Удалить").cancel("Отмена").destructive()
            .onConfirm(() -> Calls.run(api().deleteJob(jobId), r -> nav().back())).show();
      } else {
        Calls.run(api().retryJob(jobId), r -> load());
      }
      return true;
    });
    MaterialButtonToggleGroup toggle = view.findViewById(R.id.before_after);
    toggle.addOnButtonCheckedListener((g, id, checked) -> {
      if (checked) {
        showBefore = id == R.id.before;
        bindImage();
      }
    });
    view.findViewById(R.id.mannequin).setOnClickListener(v -> {
      viewGender = "female".equals(viewGender) ? "male" : "female";
      bindImage();
    });
    view.findViewById(R.id.keypoints).setOnClickListener(v -> nav().push(KeypointsFragment.newInstance(jobId)));
    view.findViewById(R.id.next).setOnClickListener(v -> {
      if (job != null) {
        nav().push(StudioPublishFragment.newInstance(job));
      }
    });
    load();
  }

  @Override
  public void onStart() {
    super.onStart();
    container().realtime.addListener(realtime);
  }

  @Override
  public void onStop() {
    container().realtime.removeListener(realtime);
    handler.removeCallbacksAndMessages(null);
    super.onStop();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (!hidden) {
      load();
    }
  }

  private void load() {
    Calls.run(api().job(jobId), r -> {
      if (!isAdded()) {
        return;
      }
      if (!r.ok() || r.data == null) {
        showError(r.error, this::load);
        return;
      }
      job = r.data;
      if (!"unisex".equals(job.gender)) {
        viewGender = job.gender;
      }
      bind();
      if ("queued".equals(job.status) || "processing".equals(job.status)) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::load, 2000);
      }
    });
  }

  private void bind() {
    Dto.GarmentJob j = job;
    View v = requireView();
    boolean processing = "queued".equals(j.status) || "processing".equals(j.status);
    boolean failed = "failed".equals(j.status);
    v.findViewById(R.id.processing).setVisibility(processing ? View.VISIBLE : View.GONE);
    TextView title = v.findViewById(R.id.title);
    TextView stepsLabel = v.findViewById(R.id.steps_label);
    StepsView steps = v.findViewById(R.id.steps);
    if (processing) {
      int step = StudioText.step(j.stage);
      title.setText(StudioText.stageLabel(j.stage));
      stepsLabel.setText("Шаг " + Math.max(1, step) + " из " + StepsView.COUNT + " · обычно меньше минуты");
      stepsLabel.setVisibility(View.VISIBLE);
      steps.setVisibility(View.VISIBLE);
      steps.setDone(Math.max(0, step - 1));
    } else {
      title.setText(failed ? "Не удалось обработать" : j.categoryName);
      stepsLabel.setVisibility(failed ? View.VISIBLE : View.GONE);
      stepsLabel.setText(j.error);
      steps.setVisibility(View.GONE);
    }

    boolean low = StudioText.lowFit(j);
    TextView ok = v.findViewById(R.id.fit_ok);
    TextView lowBadge = v.findViewById(R.id.fit_low);
    String fit = "Посадка " + StudioText.fitPercent(j) + " %";
    ok.setText(fit);
    lowBadge.setText(fit);
    ok.setVisibility(j.fitScore != null && !low ? View.VISIBLE : View.GONE);
    lowBadge.setVisibility(j.fitScore != null && low ? View.VISIBLE : View.GONE);
    v.findViewById(R.id.low_banner).setVisibility(low && !processing ? View.VISIBLE : View.GONE);

    ChipGroup chips = v.findViewById(R.id.chips);
    chips.removeAllViews();
    addChip(chips, j.categoryName);
    addChip(chips, StudioText.zoneLabel(j.zone));
    addChip(chips, StudioText.genderLabel(j.gender));
    String color = StudioText.colorName(j);
    if (color != null) {
      addChip(chips, color);
    }
    TextView warnings = v.findViewById(R.id.warnings);
    String w = qualityWarnings(j);
    warnings.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
    warnings.setText(w);

    Chip mannequin = v.findViewById(R.id.mannequin);
    mannequin.setVisibility("unisex".equals(j.gender) && !processing ? View.VISIBLE : View.GONE);
    v.findViewById(R.id.next).setEnabled(!processing && !failed && !low);
    v.findViewById(R.id.keypoints).setEnabled(!processing && !failed && j.keypoints != null && !j.keypoints.isEmpty());
    bindImage();
  }

  @SuppressWarnings("unchecked")
  private static String qualityWarnings(Dto.GarmentJob j) {
    if (j.attributes == null || !(j.attributes.get("quality") instanceof java.util.Map)) {
      return "";
    }
    Object list = ((java.util.Map<String, Object>) j.attributes.get("quality")).get("warnings");
    if (!(list instanceof List) || ((List<?>) list).isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("Замечания к фото: ");
    for (Object o : (List<?>) list) {
      switch (String.valueOf(o)) {
        case "blurry":
          sb.append("нерезко; ");
          break;
        case "dark":
          sb.append("темно; ");
          break;
        case "overexposed":
          sb.append("пересвет; ");
          break;
        case "cropped":
          sb.append("вещь обрезана краем кадра; ");
          break;
        default:
          break;
      }
    }
    return sb.toString().replaceAll("; $", "");
  }

  private void addChip(ChipGroup group, @Nullable String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    Chip chip = new Chip(requireContext());
    chip.setText(text);
    chip.setCheckable(false);
    group.addView(chip);
  }

  private void bindImage() {
    if (job == null || getView() == null) {
      return;
    }
    ImageView image = requireView().findViewById(R.id.image);
    Chip mannequin = requireView().findViewById(R.id.mannequin);
    mannequin.setText("female".equals(viewGender) ? "Женский" : "Мужской");
    String url = showBefore ? job.sourceUrl : ("female".equals(viewGender) ? job.preview.female : job.preview.male);
    if (!showBefore && url == null) {
      url = job.preview.female != null ? job.preview.female : job.preview.male;
    }
    Images.photo(image, url != null ? url : job.cutoutUrl);
  }
}
