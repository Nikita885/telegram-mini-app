package app.outfitshare.feature.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.R;
import app.outfitshare.core.net.Api;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.ButtonStyles;
import app.outfitshare.feature.messages.ChatFragment;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Mockup «Профиль (чужой)»: «Подписаться» primary → «Вы подписаны» secondary, «Сообщение». */
public class UserFragment extends ProfileScreen {
  private static final String ARG_ID = "id";
  private static final String MORE = "user_more";

  public static UserFragment newInstance(long userId) {
    UserFragment f = new UserFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_ID, userId);
    f.setArguments(args);
    return f;
  }

  @Override
  protected long userId() {
    return requireArguments().getLong(ARG_ID);
  }

  @Override
  protected boolean isOwn() {
    return false;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    if (requireArguments().getLong(ARG_ID) == container().session.myId()) {
      // Own profile opened from a list: show the own layout.
      super.onViewCreated(view, state);
      return;
    }
    super.onViewCreated(view, state);
    getChildFragmentManager()
        .setFragmentResultListener(
            MORE,
            getViewLifecycleOwner(),
            (k, r) -> {
              if ("report".equals(r.getString(ActionSheet.RESULT_ID)) && profile != null) {
                Map<String, Object> body = new HashMap<>();
                body.put("target_type", "user");
                body.put("target_id", profile.id);
                body.put("reason", Api.REPORT_REASON);
                Calls.run(
                    api().report(body),
                    x -> toast(getString(x.ok() ? R.string.report_sent : R.string.report_failed)));
              } else if ("share".equals(r.getString(ActionSheet.RESULT_ID)) && profile != null) {
                shareProfile(profile);
              }
            });
  }

  @Override
  protected void bindAppBar(View root, Dto.Profile p) {
    View back = root.findViewById(R.id.back);
    back.setVisibility(View.VISIBLE);
    back.setOnClickListener(v -> nav().back());
    ((TextView) root.findViewById(R.id.handle_title))
        .setText(p.username == null || p.username.isEmpty() ? p.name : p.username);
    MaterialButton menu = root.findViewById(R.id.menu);
    menu.setIconResource(app.outfitshare.core.designsystem.R.drawable.ds_ic_more_vert);
    menu.setContentDescription(getString(R.string.action_more));
    menu.setOnClickListener(
        v -> {
          ArrayList<ActionSheet.Row> rows = new ArrayList<>();
          rows.add(
              new ActionSheet.Row(
                  "share",
                  app.outfitshare.core.designsystem.R.drawable.ds_ic_share,
                  getString(R.string.profile_share),
                  null,
                  false));
          rows.add(
              new ActionSheet.Row(
                  "report",
                  app.outfitshare.core.designsystem.R.drawable.ds_ic_flag,
                  getString(R.string.action_report),
                  null,
                  true));
          ActionSheet.show(getChildFragmentManager(), MORE, null, rows);
        });
  }

  @Override
  protected void bindButtons(Dto.Profile p, MaterialButton primary, MaterialButton secondary) {
    if (p.isMe) {
      primary.setText(R.string.action_edit);
      primary.setOnClickListener(v -> nav().push(new EditProfileFragment()));
      secondary.setText(R.string.action_share);
      secondary.setOnClickListener(v -> shareProfile(p));
      return;
    }
    ButtonStyles.follow(primary, p.isFollowing);
    primary.setOnClickListener(v -> toggleFollow(p, primary));
    secondary.setText(R.string.action_message);
    secondary.setOnClickListener(v -> nav().push(ChatFragment.withUser(p)));
  }

  private void toggleFollow(Dto.Profile p, MaterialButton button) {
    boolean target = !p.isFollowing;
    p.isFollowing = target;
    ButtonStyles.follow(button, target);
    Calls.run(
        target ? api().follow(p.id) : api().unfollow(p.id),
        r -> {
          if (!isAdded()) {
            return;
          }
          if (r.ok() && r.data != null) {
            p.followersCount = r.data.followersCount;
            ((TextView)
                    requireView()
                        .findViewById(R.id.count_followers)
                        .findViewById(R.id.counter_value))
                .setText(app.outfitshare.core.ui.Formats.count(p.followersCount));
          } else {
            p.isFollowing = !target;
            ButtonStyles.follow(button, !target);
            showError(r.error, null);
          }
        });
  }
}
