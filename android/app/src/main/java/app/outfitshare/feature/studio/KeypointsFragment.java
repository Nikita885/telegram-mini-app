package app.outfitshare.feature.studio;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.button.DsButton;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.chip.Chip;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Drag garment keypoints; «Готово» re-runs the fit on the server with the adjusted points. */
public class KeypointsFragment extends BaseFragment {
  private static final String ARG_ID = "id";

  private final Deque<Map<String, float[]>> undo = new ArrayDeque<>();
  private final Deque<Map<String, float[]>> redo = new ArrayDeque<>();
  private KeypointEditorView editor;
  private Map<String, float[]> original = new LinkedHashMap<>();
  @Nullable private Dto.GarmentJob job;
  private View undoBtn;
  private View redoBtn;
  private TextView hint;

  public static KeypointsFragment newInstance(long jobId) {
    KeypointsFragment f = new KeypointsFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_ID, jobId);
    f.setArguments(args);
    return f;
  }

  public KeypointsFragment() {
    super(R.layout.fragment_keypoints);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    editor = view.findViewById(R.id.editor);
    undoBtn = view.findViewById(R.id.undo);
    redoBtn = view.findViewById(R.id.redo);
    hint = view.findViewById(R.id.hint);
    view.findViewById(R.id.close).setOnClickListener(v -> nav().back());
    undoBtn.setOnClickListener(v -> step(undo, redo));
    redoBtn.setOnClickListener(v -> step(redo, undo));
    view.findViewById(R.id.reset)
        .setOnClickListener(
            v -> {
              push();
              editor.setPoints(original);
            });
    view.findViewById(R.id.done).setOnClickListener(v -> submit());
    Chip mannequin = view.findViewById(R.id.mannequin);
    ImageView preview = view.findViewById(R.id.preview);
    mannequin.setOnCheckedChangeListener(
        (c, checked) -> {
          preview.setVisibility(checked ? View.VISIBLE : View.GONE);
          if (checked && job != null) {
            Images.photo(
                preview, job.preview.female != null ? job.preview.female : job.preview.male);
          }
        });

    editor.setListener(
        new KeypointEditorView.Listener() {
          @Override
          public void onDragStart() {
            push();
          }

          @Override
          public void onPointMoved(String name, float x, float y) {
            hint.setText(
                getString(
                    R.string.kp_position,
                    KeypointEditorView.longLabel(name),
                    Math.round(x),
                    Math.round(y)));
          }

          @Override
          public void onDragEnd() {
            hint.setText(R.string.studio_keypoints_hint);
          }
        });

    Calls.run(
        api().job(requireArguments().getLong(ARG_ID)),
        r -> {
          if (!isAdded() || !r.ok() || r.data == null) {
            showError(r.error, null);
            return;
          }
          job = r.data;
          original = toMap(job.keypoints);
          editor.setPoints(original);
          Glide.with(this)
              .asBitmap()
              .load(job.cutoutUrl)
              .disallowHardwareConfig()
              .into(
                  new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(
                        @NonNull Bitmap resource, @Nullable Transition<? super Bitmap> t) {
                      editor.setBitmap(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                  });
        });
  }

  private static Map<String, float[]> toMap(@Nullable Map<String, List<Float>> kp) {
    Map<String, float[]> out = new LinkedHashMap<>();
    if (kp != null) {
      for (Map.Entry<String, List<Float>> e : kp.entrySet()) {
        if (e.getValue() != null && e.getValue().size() == 2) {
          out.put(e.getKey(), new float[] {e.getValue().get(0), e.getValue().get(1)});
        }
      }
    }
    return out;
  }

  private void push() {
    undo.push(KeypointEditorView.copy(editor.points()));
    redo.clear();
    refreshButtons();
  }

  private void step(Deque<Map<String, float[]>> from, Deque<Map<String, float[]>> to) {
    if (from.isEmpty()) {
      return;
    }
    to.push(KeypointEditorView.copy(editor.points()));
    editor.setPoints(from.pop());
    refreshButtons();
  }

  private void refreshButtons() {
    undoBtn.setEnabled(!undo.isEmpty());
    redoBtn.setEnabled(!redo.isEmpty());
  }

  private void submit() {
    if (job == null) {
      return;
    }
    Map<String, Object> kp = new HashMap<>();
    for (Map.Entry<String, float[]> e : editor.points().entrySet()) {
      List<Float> xy = new ArrayList<>();
      xy.add(e.getValue()[0]);
      xy.add(e.getValue()[1]);
      kp.put(e.getKey(), xy);
    }
    Map<String, Object> body = new HashMap<>();
    body.put("keypoints", kp);
    DsButton done = requireView().findViewById(R.id.done);
    done.setLoading(true);
    Calls.run(
        api().refitJob(job.id, body),
        r -> {
          if (!isAdded()) {
            return;
          }
          done.setLoading(false);
          if (r.ok()) {
            nav().back();
          } else {
            showError(r.error, this::submit);
          }
        });
  }
}
