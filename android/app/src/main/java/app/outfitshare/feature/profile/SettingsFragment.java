package app.outfitshare.feature.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.BuildConfig;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.dialog.ConfirmDialog;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.designsystem.theme.ThemeMode;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.feature.auth.LoginFragment;
import app.outfitshare.feature.studio.StudioQueueFragment;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Mockup «Настройки». A toggle row is one touch target; logout only through a confirmation. */
public class SettingsFragment extends BaseFragment {
  private static final String THEME = "theme";

  public SettingsFragment() {
    super(R.layout.fragment_settings);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> nav().back());

    Dto.Profile me = container().session.currentMe();
    ListRowView meRow = view.findViewById(R.id.me);
    if (me != null) {
      AvatarView avatar = new AvatarView(requireContext());
      avatar.setAvatarSize(2);
      Images.avatar(avatar, me);
      meRow.setLeadingView(avatar);
      meRow.setTitle(me.name);
      meRow.setSubtitle(Formats.handle(me.username) + " · вход через Telegram");
    }
    meRow.setOnClickListener(v -> nav().push(new EditProfileFragment()));

    ListRowView theme = view.findViewById(R.id.theme);
    theme.setValue(themeLabel(container().prefs.theme()));
    theme.setOnClickListener(
        v -> {
          ThemeMode current = container().prefs.theme();
          ArrayList<ActionSheet.Row> rows = new ArrayList<>();
          rows.add(
              new ActionSheet.Row(
                  ThemeMode.SYSTEM.key(),
                  0,
                  getString(R.string.settings_theme_system),
                  "Переключается вместе с телефоном",
                  false,
                  current == ThemeMode.SYSTEM));
          rows.add(
              new ActionSheet.Row(
                  ThemeMode.LIGHT.key(),
                  0,
                  getString(R.string.settings_theme_light),
                  "Молочный фон, графитовый текст",
                  false,
                  current == ThemeMode.LIGHT));
          rows.add(
              new ActionSheet.Row(
                  ThemeMode.DARK.key(),
                  0,
                  getString(R.string.settings_theme_dark),
                  "Графитовый фон — бережёт глаза вечером",
                  false,
                  current == ThemeMode.DARK));
          ActionSheet.show(
              getChildFragmentManager(), THEME, getString(R.string.settings_theme), rows);
        });
    getChildFragmentManager()
        .setFragmentResultListener(
            THEME,
            getViewLifecycleOwner(),
            (k, r) -> {
              ThemeMode mode = ThemeMode.fromKey(r.getString(ActionSheet.RESULT_ID));
              container().prefs.setTheme(mode);
              theme.setValue(themeLabel(mode));
              mode.apply();
            });

    SharedPreferences notif =
        requireContext().getSharedPreferences("notif_settings", Context.MODE_PRIVATE);
    bindToggle(view.findViewById(R.id.notif_likes), notif, "likes");
    bindToggle(view.findViewById(R.id.notif_follows), notif, "follows");
    bindToggle(view.findViewById(R.id.notif_messages), notif, "messages");

    ListRowView studio = view.findViewById(R.id.studio);
    studio.setVisibility(container().session.isAdmin() ? View.VISIBLE : View.GONE);
    studio.setOnClickListener(v -> nav().push(new StudioQueueFragment()));

    ListRowView server = view.findViewById(R.id.server);
    server.setValue(
        container().prefs.server().replace("https://", "").replace("http://", "").replace("/", ""));

    view.findViewById(R.id.logout)
        .setOnClickListener(
            v ->
                ConfirmDialog.with(requireContext())
                    .title(getString(R.string.settings_logout_title))
                    .message(getString(R.string.settings_logout_text))
                    .confirm(getString(R.string.settings_logout))
                    .cancel("Отмена")
                    .destructive()
                    .onConfirm(this::logout)
                    .show());

    ((TextView) view.findViewById(R.id.version))
        .setText("Outfit Share " + BuildConfig.VERSION_NAME);
  }

  private void bindToggle(ListRowView row, SharedPreferences prefs, String key) {
    row.setToggle(
        prefs.getBoolean(key, true), (r, checked) -> prefs.edit().putBoolean(key, checked).apply());
  }

  private String themeLabel(ThemeMode mode) {
    switch (mode) {
      case LIGHT:
        return getString(R.string.settings_theme_light);
      case DARK:
        return getString(R.string.settings_theme_dark);
      default:
        return getString(R.string.settings_theme_system);
    }
  }

  private void logout() {
    Map<String, Object> body = new HashMap<>();
    String refresh = container().session.refresh();
    if (refresh != null) {
      body.put("refresh", refresh);
      Calls.run(api().logout(body), r -> {});
    }
    container().signOut();
    nav().setRoot(new LoginFragment());
  }
}
