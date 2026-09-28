package app.outfitshare.feature.messages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;
import app.outfitshare.App;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.component.sheet.DsBottomSheetDialogFragment;
import app.outfitshare.core.designsystem.component.snackbar.DsSnackbar;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import java.util.HashMap;
import java.util.Map;

/** «Отправить в диалог»: pick a conversation, the outfit goes as a card message. */
public class ShareToDialogSheet extends DsBottomSheetDialogFragment {
  private static final String ARG_OUTFIT = "outfit";

  public static void show(FragmentManager fm, long outfitId) {
    ShareToDialogSheet s = new ShareToDialogSheet();
    Bundle args = new Bundle();
    args.putLong(ARG_OUTFIT, outfitId);
    s.setArguments(args);
    s.show(fm, "share_to_dialog");
  }

  @Nullable
  @Override
  protected CharSequence getSheetTitle() {
    return "Отправить в диалог";
  }

  @NonNull
  @Override
  protected View onCreateSheetContent(@NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
    NestedScrollView scroll = new NestedScrollView(requireContext());
    LinearLayout list = new LinearLayout(requireContext());
    list.setOrientation(LinearLayout.VERTICAL);
    int pad = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_5);
    list.setPadding(0, 0, 0, pad);
    scroll.addView(list);
    TextView loading = new TextView(requireContext());
    loading.setText(app.outfitshare.core.designsystem.R.string.ds_state_loading);
    int gutter = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    loading.setPadding(gutter, pad, gutter, pad);
    list.addView(loading);

    long outfitId = requireArguments().getLong(ARG_OUTFIT);
    Calls.run(App.get().container().api.api().dialogs(), r -> {
      if (!isAdded()) {
        return;
      }
      list.removeAllViews();
      if (!r.ok() || r.data == null || r.data.results.isEmpty()) {
        loading.setText(r.ok() ? "Пока нет диалогов — напишите кому-нибудь из профиля" : "Диалоги не загрузились");
        list.addView(loading);
        return;
      }
      for (Dto.Dialog d : r.data.results) {
        ListRowView row = new ListRowView(requireContext());
        AvatarView avatar = new AvatarView(requireContext());
        avatar.setAvatarSize(1);
        Images.avatar(avatar, d.user);
        row.setLeadingView(avatar);
        row.setTitle(d.user.name);
        row.setSubtitle(Formats.handle(d.user.username));
        row.setOnClickListener(v -> send(d, outfitId));
        list.addView(row);
      }
    });
    return scroll;
  }

  private void send(Dto.Dialog d, long outfitId) {
    Map<String, Object> body = new HashMap<>();
    body.put("outfit_id", outfitId);
    View anchor = requireParentFragment().getView();
    dismiss();
    Calls.run(App.get().container().api.api().sendMessage(d.id, body), r -> {
      if (anchor != null) {
        DsSnackbar.message(anchor, r.ok() ? "Отправлено: " + d.user.name : "Не удалось отправить");
      }
    });
  }
}
