package app.outfitshare.feature.profile;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.constructor.ConstructorFragment;
import app.outfitshare.feature.shell.ShellFragment;
import app.outfitshare.feature.studio.StudioQueueFragment;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;

/** Mockup «Профиль (свой)»: tabs «Образы · Коллекции · Черновики», Studio entry for admins. */
public class ProfileFragment extends ProfileScreen implements ShellFragment.Reselectable {

  @Override
  protected long userId() {
    return -1;
  }

  @Override
  protected boolean isOwn() {
    return true;
  }

  @Override
  protected void bindAppBar(View root, Dto.Profile p) {
    ((TextView) root.findViewById(R.id.handle_title))
        .setText(p.username == null || p.username.isEmpty() ? p.name : p.username);
    View add = root.findViewById(R.id.add);
    add.setVisibility(View.VISIBLE);
    add.setOnClickListener(v -> nav().present(new ConstructorFragment()));
    View studio = root.findViewById(R.id.studio);
    studio.setVisibility(p.isAdmin || "admin".equals(p.role) ? View.VISIBLE : View.GONE);
    studio.setOnClickListener(v -> nav().push(new StudioQueueFragment()));
    root.findViewById(R.id.menu).setOnClickListener(v -> nav().push(new SettingsFragment()));
  }

  @Override
  protected void bindButtons(Dto.Profile p, MaterialButton primary, MaterialButton secondary) {
    primary.setText(R.string.action_edit);
    primary.setOnClickListener(v -> nav().push(new EditProfileFragment()));
    secondary.setText(R.string.action_share);
    secondary.setOnClickListener(v -> shareProfile(p));
  }

  @Override
  protected ScreenState emptyOutfitsState() {
    return States.empty(
        app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_outfits,
        getString(R.string.profile_empty_title),
        getString(R.string.profile_empty_text),
        getString(R.string.action_open_constructor));
  }

  @Override
  public void onViewCreated(
      @androidx.annotation.NonNull View view,
      @androidx.annotation.Nullable android.os.Bundle state) {
    super.onViewCreated(view, state);
    tabState.setOnActionClickListener(
        v -> {
          if (tab == TAB_OUTFITS) {
            nav().present(new ConstructorFragment());
          } else {
            showTab(tab);
          }
        });
    container()
        .session
        .me()
        .observe(
            getViewLifecycleOwner(),
            me -> {
              if (me != null
                  && profile != null
                  && me.id == profile.id
                  && (me.name != null && !me.name.equals(profile.name)
                      || me.bio != null && !me.bio.equals(profile.bio)
                      || me.avatarUrl != null && !me.avatarUrl.equals(profile.avatarUrl))) {
                reload();
              }
            });
  }

  /** Local draft kept by the constructor (not yet published). */
  @Override
  protected void showDrafts() {
    grid.setAdapter(null);
    String json =
        requireContext()
            .getSharedPreferences("constructor_draft", Context.MODE_PRIVATE)
            .getString("draft", null);
    int count = 0;
    if (json != null) {
      try {
        count = new JSONObject(json).getJSONArray("layers").length();
      } catch (Exception ignored) {
        count = 0;
      }
    }
    if (count == 0) {
      tabState.setState(
          States.empty(
              app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_outfits,
              getString(R.string.drafts_empty_title),
              getString(R.string.drafts_empty_text),
              getString(R.string.action_open_constructor)));
      return;
    }
    tabState.setState(ScreenState.content());
    ListRowView row = new ListRowView(requireContext());
    row.setIcon(
        ContextCompat.getDrawable(
            requireContext(), app.outfitshare.core.designsystem.R.drawable.ds_ic_draft));
    row.setTitle(getString(R.string.draft_untitled));
    row.setSubtitle(
        getString(
            R.string.draft_subtitle,
            getResources().getQuantityString(R.plurals.items_count, count, count)));
    row.setShowChevron(true);
    row.setOnClickListener(v -> nav().present(new ConstructorFragment()));
    gridManager.setSpanCount(1);
    grid.setAdapter(new SingleViewAdapter(row));
    grid.setPadding(0, 0, 0, 0);
  }

  @Override
  public void onReselected() {
    reload();
  }
}
