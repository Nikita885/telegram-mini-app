package app.outfitshare.feature.studio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.States;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mockup «Студия: камера». Live checks (light, background, framing) run on the preview frames; the
 * shot is queued for background upload and the pipeline takes it from there.
 */
public class StudioCameraFragment extends BaseFragment {
  private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
  private ActivityResultLauncher<String> permission;
  private ActivityResultLauncher<PickVisualMediaRequest> picker;
  @Nullable private ImageCapture capture;
  private StateView stateView;
  private TextView hint;
  private TextView qLight;
  private TextView qBackground;
  private TextView qFrame;
  private ChipGroup categories;
  private MaterialButtonToggleGroup gender;
  private MaterialButton flashToggle;
  private boolean flashOn;
  private long lastAnalysis;

  public StudioCameraFragment() {
    super(R.layout.fragment_studio_camera);
  }

  @Override
  public void onCreate(@Nullable Bundle state) {
    super.onCreate(state);
    permission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
              if (granted) {
                stateView.setState(ScreenState.content());
                startCamera();
              } else {
                showNoPermission();
              }
            });
    picker =
        registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
              if (uri != null) {
                queueFromGallery(uri);
              }
            });
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view.findViewById(R.id.top), true, false);
    SystemBars.applyPadding(view.findViewById(R.id.bottom), false, true);
    stateView = view.findViewById(R.id.state);
    hint = view.findViewById(R.id.hint);
    qLight = view.findViewById(R.id.q_light);
    qBackground = view.findViewById(R.id.q_background);
    qFrame = view.findViewById(R.id.q_frame);
    categories = view.findViewById(R.id.categories);
    gender = view.findViewById(R.id.gender);
    flashToggle = view.findViewById(R.id.flash_toggle);

    view.findViewById(R.id.close).setOnClickListener(v -> nav().back());
    view.findViewById(R.id.queue).setOnClickListener(v -> nav().back());
    view.findViewById(R.id.shutter).setOnClickListener(v -> shoot());
    view.findViewById(R.id.gallery).setOnClickListener(v -> pickFromGallery());
    flashToggle.setOnClickListener(
        v -> {
          flashOn = !flashOn;
          flashToggle.setIconResource(
              flashOn
                  ? app.outfitshare.core.designsystem.R.drawable.ds_ic_flash_on
                  : app.outfitshare.core.designsystem.R.drawable.ds_ic_flash_off);
          if (capture != null) {
            capture.setFlashMode(
                flashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
          }
        });
    stateView.setOnActionClickListener(v -> permission.launch(Manifest.permission.CAMERA));
    stateView.setOnSecondaryActionClickListener(v -> pickFromGallery());

    if (!container().session.isAdmin()) {
      stateView.setState(
          States.locked(
              getString(R.string.studio_locked_title),
              getString(R.string.studio_locked_text),
              null));
      return;
    }
    loadCategories();
    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      startCamera();
    } else {
      permission.launch(Manifest.permission.CAMERA);
    }
  }

  private void showNoPermission() {
    stateView.setState(
        ScreenState.builder(ScreenState.Kind.NO_PERMISSION)
            .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_camera)
            .title(getString(R.string.studio_camera_permission_title))
            .message(getString(R.string.studio_camera_permission_text))
            .action(getString(R.string.action_allow))
            .secondaryAction(getString(R.string.studio_gallery))
            .build());
  }

  private void loadCategories() {
    Calls.run(
        api().categories(),
        r -> {
          if (!isAdded() || !r.ok() || r.data == null) {
            return;
          }
          categories.removeAllViews();
          for (Dto.Category c : r.data.results) {
            Chip chip = new Chip(requireContext());
            chip.setText(c.name);
            chip.setTag(c);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            categories.addView(chip);
            if ("top".equals(c.slug)) {
              chip.setChecked(true);
            }
          }
          if (categories.getCheckedChipId() == View.NO_ID && categories.getChildCount() > 0) {
            ((Chip) categories.getChildAt(0)).setChecked(true);
          }
        });
  }

  @Nullable
  private Dto.Category selectedCategory() {
    int id = categories.getCheckedChipId();
    View chip = id == View.NO_ID ? null : categories.findViewById(id);
    return chip == null ? null : (Dto.Category) chip.getTag();
  }

  private String selectedGender() {
    int id = gender.getCheckedButtonId();
    if (id == R.id.g_female) {
      return "female";
    }
    if (id == R.id.g_male) {
      return "male";
    }
    return "unisex";
  }

  // ── CameraX ──────────────────────────────────────────────────────────────

  private void startCamera() {
    ListenableFuture<ProcessCameraProvider> future =
        ProcessCameraProvider.getInstance(requireContext());
    future.addListener(
        () -> {
          if (!isAdded()) {
            return;
          }
          try {
            ProcessCameraProvider provider = future.get();
            PreviewView view = requireView().findViewById(R.id.preview);
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(view.getSurfaceProvider());
            capture =
                new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(
                        flashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF)
                    .build();
            ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analysis.setAnalyzer(analysisExecutor, this::analyze);
            provider.unbindAll();
            provider.bindToLifecycle(
                getViewLifecycleOwner(),
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
                analysis);
          } catch (Exception e) {
            stateView.setState(
                ScreenState.builder(ScreenState.Kind.ERROR)
                    .illustration(
                        app.outfitshare.core.designsystem.R.drawable.ds_illustration_error)
                    .title(getString(R.string.studio_camera_unavailable_title))
                    .message(getString(R.string.studio_camera_unavailable_text))
                    .action(getString(R.string.action_retry))
                    .secondaryAction(getString(R.string.studio_gallery))
                    .build());
          }
        },
        ContextCompat.getMainExecutor(requireContext()));
  }

  /** Luminance-only checks on the Y plane, twice a second. */
  private void analyze(ImageProxy image) {
    long now = System.currentTimeMillis();
    if (now - lastAnalysis < 500) {
      image.close();
      return;
    }
    lastAnalysis = now;
    ImageProxy.PlaneProxy plane = image.getPlanes()[0];
    ByteBuffer buf = plane.getBuffer();
    int rowStride = plane.getRowStride();
    int w = image.getWidth();
    int h = image.getHeight();
    int step = Math.max(4, Math.min(w, h) / 64);
    double sum = 0;
    int n = 0;
    double borderSum = 0;
    double borderSq = 0;
    int borderN = 0;
    double centerSum = 0;
    int centerN = 0;
    double lap = 0;
    double lapSq = 0;
    int lapN = 0;
    for (int y = step; y < h - step; y += step) {
      for (int x = step; x < w - step; x += step) {
        int v = buf.get(y * rowStride + x) & 0xFF;
        sum += v;
        n++;
        boolean border = x < w * 0.1 || x > w * 0.9 || y < h * 0.1 || y > h * 0.9;
        if (border) {
          borderSum += v;
          borderSq += v * v;
          borderN++;
        } else if (x > w * 0.3 && x < w * 0.7 && y > h * 0.3 && y < h * 0.7) {
          centerSum += v;
          centerN++;
        }
        int l =
            4 * v
                - (buf.get(y * rowStride + x - step) & 0xFF)
                - (buf.get(y * rowStride + x + step) & 0xFF)
                - (buf.get((y - step) * rowStride + x) & 0xFF)
                - (buf.get((y + step) * rowStride + x) & 0xFF);
        lap += l;
        lapSq += (double) l * l;
        lapN++;
      }
    }
    image.close();
    double mean = n == 0 ? 0 : sum / n / 255.0;
    double borderMean = borderN == 0 ? 0 : borderSum / borderN;
    double borderStd =
        borderN == 0 ? 0 : Math.sqrt(Math.max(0, borderSq / borderN - borderMean * borderMean));
    double centerMean = centerN == 0 ? 0 : centerSum / centerN;
    double sharp = lapN == 0 ? 0 : lapSq / lapN - Math.pow(lap / lapN, 2);
    boolean lightOk = mean > 0.22 && mean < 0.93;
    boolean backgroundOk = borderStd < 28;
    boolean frameOk = Math.abs(centerMean - borderMean) > 14 && sharp > 40;
    View root = getView();
    if (root != null) {
      root.post(() -> showChecks(lightOk, backgroundOk, frameOk, mean));
    }
  }

  private void showChecks(boolean light, boolean background, boolean frame, double mean) {
    if (!isAdded()) {
      return;
    }
    setCheck(
        qLight,
        light,
        getString(
            light
                ? R.string.studio_q_light
                : (mean < 0.5 ? R.string.studio_q_dark : R.string.studio_q_overexposed)));
    setCheck(
        qBackground,
        background,
        getString(background ? R.string.studio_q_background : R.string.studio_q_busy_background));
    setCheck(
        qFrame, frame, getString(frame ? R.string.studio_q_frame : R.string.studio_q_not_visible));
    if (!light && mean < 0.5) {
      hint.setText(R.string.studio_hint_light);
    } else if (!background) {
      hint.setText(R.string.studio_hint_background);
    } else {
      hint.setText(R.string.studio_hint);
    }
  }

  private void setCheck(TextView chip, boolean ok, String text) {
    chip.setText(text);
    chip.setBackgroundResource(ok ? R.drawable.bg_quality_ok : R.drawable.bg_quality_warn);
    chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
        ok ? R.drawable.badge_check_small : R.drawable.badge_warning, 0, 0, 0);
    TextViewCompat.setCompoundDrawableTintList(
        chip,
        ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(), ok ? R.color.camera_ok : R.color.camera_warn)));
    chip.setContentDescription(
        getString(ok ? R.string.studio_check_ok : R.string.studio_check_warn, text));
  }

  // ── Shooting ─────────────────────────────────────────────────────────────

  private void shoot() {
    Dto.Category category = selectedCategory();
    if (capture == null || category == null) {
      toast(
          getString(
              category == null ? R.string.studio_pick_category : R.string.studio_camera_not_ready));
      return;
    }
    View shutter = requireView().findViewById(R.id.shutter);
    Haptics.perform(shutter, Haptics.Event.SHUTTER);
    View flash = requireView().findViewById(R.id.flash);
    flash.setAlpha(1f);
    flash.animate().alpha(0f).setDuration(300).start();
    File file = newFile();
    capture.takePicture(
        new ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(requireContext()),
        new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
            queue(file, category);
          }

          @Override
          public void onError(@NonNull ImageCaptureException e) {
            showError(app.outfitshare.core.net.ApiError.offline(), null);
            toast(getString(R.string.studio_shot_failed));
          }
        });
  }

  private File newFile() {
    File dir = new File(requireContext().getFilesDir(), "studio");
    dir.mkdirs();
    return new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
  }

  private void queue(File file, Dto.Category category) {
    UploadWorker.enqueue(requireContext(), file, category.slug, category.name, selectedGender());
    hint.setText(R.string.studio_shot_uploading);
  }

  private void pickFromGallery() {
    picker.launch(
        new PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
            .build());
  }

  /** Gallery photos are downscaled to 2400 px before upload. */
  private void queueFromGallery(Uri uri) {
    Dto.Category category = selectedCategory();
    if (category == null) {
      toast(getString(R.string.studio_pick_category));
      return;
    }
    try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in);
      if (bitmap == null) {
        toast(getString(R.string.photo_read_failed));
        return;
      }
      float k = Math.min(1f, 2400f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
      Bitmap scaled =
          Bitmap.createScaledBitmap(
              bitmap, Math.round(bitmap.getWidth() * k), Math.round(bitmap.getHeight() * k), true);
      File file = newFile();
      try (FileOutputStream out = new FileOutputStream(file)) {
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out);
      }
      queue(file, category);
    } catch (Exception e) {
      toast(getString(R.string.photo_read_failed));
    }
  }

  @Override
  public void onDestroy() {
    analysisExecutor.shutdown();
    super.onDestroy();
  }
}
