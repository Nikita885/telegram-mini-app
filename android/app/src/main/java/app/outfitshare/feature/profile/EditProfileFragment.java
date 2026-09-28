package app.outfitshare.feature.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.button.DsButton;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import com.google.android.material.textfield.TextInputLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** «Готово» is enabled only with changes; username errors show under the field. */
public class EditProfileFragment extends BaseFragment {
  private static final String PHOTO = "photo";

  private EditText firstName;
  private EditText lastName;
  private EditText username;
  private EditText bio;
  private DsButton done;
  private AvatarView avatar;
  @Nullable private Dto.Profile me;
  private ActivityResultLauncher<PickVisualMediaRequest> picker;

  public EditProfileFragment() {
    super(R.layout.fragment_edit_profile);
  }

  @Override
  public void onCreate(@Nullable Bundle state) {
    super.onCreate(state);
    picker =
        registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
              if (uri != null) {
                upload(uri);
              }
            });
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    me = container().session.currentMe();
    firstName = view.findViewById(R.id.first_name);
    lastName = view.findViewById(R.id.last_name);
    username = view.findViewById(R.id.username);
    bio = view.findViewById(R.id.bio);
    done = view.findViewById(R.id.done);
    avatar = view.findViewById(R.id.avatar);
    OfflineBanner offline = view.findViewById(R.id.offline);
    offline.setVisibility(container().api.isOnline() ? View.GONE : View.VISIBLE);
    offline.setOffline(!container().api.isOnline());

    if (me != null) {
      firstName.setText(me.firstName);
      lastName.setText(me.lastName);
      username.setText(me.username);
      bio.setText(me.bio);
      Images.avatar(avatar, me);
    }
    TextWatcher watcher =
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void afterTextChanged(Editable s) {
            ((TextInputLayout) requireView().findViewById(R.id.username_layout)).setError(null);
            done.setEnabled(changed());
          }
        };
    for (EditText e : new EditText[] {firstName, lastName, username, bio}) {
      e.addTextChangedListener(watcher);
    }
    view.findViewById(R.id.close).setOnClickListener(v -> nav().back());
    done.setOnClickListener(v -> save());
    view.findViewById(R.id.change_photo).setOnClickListener(v -> photoSheet());
    avatar.setOnClickListener(v -> photoSheet());
    getChildFragmentManager()
        .setFragmentResultListener(
            PHOTO,
            getViewLifecycleOwner(),
            (k, r) -> onPhotoAction(r.getString(ActionSheet.RESULT_ID)));
  }

  private boolean changed() {
    if (me == null) {
      return true;
    }
    return !text(firstName).equals(nz(me.firstName))
        || !text(lastName).equals(nz(me.lastName))
        || !text(username).equals(nz(me.username))
        || !text(bio).equals(nz(me.bio));
  }

  private static String text(EditText e) {
    return e.getText().toString().trim();
  }

  private static String nz(@Nullable String s) {
    return s == null ? "" : s;
  }

  private void save() {
    Map<String, Object> body = new HashMap<>();
    body.put("first_name", text(firstName));
    body.put("last_name", text(lastName));
    body.put("username", text(username));
    body.put("bio", text(bio));
    done.setLoading(true);
    Calls.run(
        api().updateMe(body),
        r -> {
          if (!isAdded()) {
            return;
          }
          done.setLoading(false);
          if (r.ok() && r.data != null) {
            container().session.setMe(r.data);
            nav().back();
            return;
          }
          TextInputLayout layout = requireView().findViewById(R.id.username_layout);
          if (r.error != null
              && ("username_taken".equals(r.error.code)
                  || r.error.fieldMessage("username") != null)) {
            String msg = r.error.fieldMessage("username");
            layout.setError(msg != null ? msg : "Имя @" + text(username) + " уже занято");
            done.setEnabled(false);
          } else {
            showError(r.error, this::save);
          }
        });
  }

  private void photoSheet() {
    ArrayList<ActionSheet.Row> rows = new ArrayList<>();
    rows.add(
        new ActionSheet.Row(
            "telegram",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_send,
            getString(R.string.profile_photo_telegram),
            null,
            false));
    rows.add(
        new ActionSheet.Row(
            "gallery",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_gallery,
            getString(R.string.profile_photo_gallery),
            null,
            false));
    if (me != null && me.avatarUrl != null) {
      rows.add(
          new ActionSheet.Row(
              "delete",
              app.outfitshare.core.designsystem.R.drawable.ds_ic_delete,
              getString(R.string.profile_photo_delete),
              null,
              true));
    }
    ActionSheet.show(
        getChildFragmentManager(), PHOTO, getString(R.string.profile_photo_sheet), rows);
  }

  private void onPhotoAction(@Nullable String id) {
    if ("gallery".equals(id)) {
      picker.launch(
          new PickVisualMediaRequest.Builder()
              .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
              .build());
    } else if ("telegram".equals(id)) {
      Map<String, Object> body = new HashMap<>();
      body.put("source", "telegram");
      Calls.run(
          api().avatarFromTelegram(body),
          r -> onAvatar(r.ok() && r.data != null ? r.data.avatarUrl : null, r.error));
    } else if ("delete".equals(id)) {
      Calls.run(api().deleteAvatar(), r -> onAvatar(null, r.error));
    }
  }

  private void upload(Uri uri) {
    try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in);
      if (bitmap == null) {
        toast("Не удалось прочитать фото");
        return;
      }
      int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
      float k = Math.min(1f, 1024f / side);
      Bitmap scaled =
          Bitmap.createScaledBitmap(
              bitmap, Math.round(bitmap.getWidth() * k), Math.round(bitmap.getHeight() * k), true);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
      MultipartBody.Part part =
          MultipartBody.Part.createFormData(
              "avatar",
              "avatar.jpg",
              RequestBody.create(out.toByteArray(), MediaType.get("image/jpeg")));
      Calls.run(
          api().uploadAvatar(part),
          r -> onAvatar(r.ok() && r.data != null ? r.data.avatarUrl : null, r.error));
    } catch (Exception e) {
      toast("Не удалось прочитать фото");
    }
  }

  private void onAvatar(@Nullable String url, @Nullable app.outfitshare.core.net.ApiError error) {
    if (!isAdded()) {
      return;
    }
    if (error != null) {
      showError(error, null);
      return;
    }
    if (me != null) {
      me.avatarUrl = url;
      container().session.setMe(me);
      Images.avatar(avatar, me);
    }
  }
}
