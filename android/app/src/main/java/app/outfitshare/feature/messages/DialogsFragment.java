package app.outfitshare.feature.messages;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.dialog.ConfirmDialog;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.realtime.Realtime;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.search.SearchFragment;
import app.outfitshare.feature.shell.ShellFragment;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mockup «Диалоги»: pinned first, unread counts, shared outfit marker, live «печатает…». */
public class DialogsFragment extends BaseFragment implements ShellFragment.Reselectable {
  private static final String ACTIONS = "dialog_actions";

  private final List<Object> rows = new ArrayList<>();
  private final Map<Long, Long> typingUntil = new HashMap<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Adapter adapter;
  private StateView stateView;
  private SwipeRefreshLayout refresh;
  private OfflineBanner offline;
  private List<Dto.Dialog> dialogs = new ArrayList<>();
  @Nullable private Dto.Dialog actionTarget;
  private RecyclerView list;

  private final Realtime.Listener realtime =
      (event, payload) -> {
        switch (event) {
          case "message.new":
          case "message.edited":
          case "message.deleted":
          case "dialog.read":
            load();
            break;
          case "typing":
            long dialogId = payload.has("dialog_id") ? payload.get("dialog_id").getAsLong() : 0;
            typingUntil.put(dialogId, System.currentTimeMillis() + 4000);
            rebuild();
            handler.postDelayed(this::rebuild, 4100);
            break;
          default:
            break;
        }
      };

  public DialogsFragment() {
    super(R.layout.fragment_list);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationIcon(null);
    toolbar.setTitle(R.string.dialogs_title);
    toolbar.setTitleTextAppearance(
        requireContext(),
        app.outfitshare.core.designsystem.R.style.TextAppearance_Ds_HeadlineMedium);
    toolbar
        .getMenu()
        .add(R.string.action_new_message)
        .setIcon(app.outfitshare.core.designsystem.R.drawable.ds_ic_edit)
        .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
    toolbar.setOnMenuItemClickListener(
        item -> {
          nav().push(SearchFragment.forQuery("@"));
          return true;
        });

    stateView = view.findViewById(R.id.state);
    refresh = view.findViewById(R.id.refresh);
    offline = view.findViewById(R.id.offline);
    list = view.findViewById(R.id.list);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    adapter = new Adapter();
    list.setAdapter(adapter);
    refresh.setOnRefreshListener(this::load);
    stateView.setOnActionClickListener(
        v -> {
          if (stateView.getState().getKind() == ScreenState.Kind.EMPTY) {
            nav().push(SearchFragment.forQuery("@"));
          } else {
            load();
          }
        });
    offline.setOnRetryClickListener(v -> load());
    getChildFragmentManager()
        .setFragmentResultListener(
            ACTIONS,
            getViewLifecycleOwner(),
            (k, r) -> onAction(r.getString(ActionSheet.RESULT_ID)));
    stateView.setState(ScreenState.loading());
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
    Calls.run(
        api().dialogs(),
        r -> {
          if (!isAdded()) {
            return;
          }
          refresh.setRefreshing(false);
          if (!r.ok() || r.data == null) {
            if (dialogs.isEmpty()) {
              stateView.setState(States.failure(r.error, "Диалоги не загрузились"));
            }
            return;
          }
          offline.setOffline(r.fromCache);
          offline.setVisibility(r.fromCache ? View.VISIBLE : View.GONE);
          dialogs = r.data.results;
          rebuild();
          stateView.setState(
              dialogs.isEmpty()
                  ? States.empty(
                      app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_messages,
                      "Пока нет диалогов",
                      "Напишите автору образа или поделитесь своим — прямо из просмотра.",
                      "Найти людей")
                  : ScreenState.content());
        });
  }

  private void rebuild() {
    if (!isAdded()) {
      return;
    }
    rows.clear();
    List<Dto.Dialog> pinned = new ArrayList<>();
    List<Dto.Dialog> rest = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (Dto.Dialog d : dialogs) {
      Long until = typingUntil.get(d.id);
      d.typing = until != null && until > now;
      (d.pinned ? pinned : rest).add(d);
    }
    if (!pinned.isEmpty()) {
      rows.add(getString(R.string.dialogs_pinned));
      rows.addAll(pinned);
      rows.add(getString(R.string.dialogs_all));
    }
    rows.addAll(rest);
    adapter.notifyDataSetChanged();
  }

  private void onAction(@Nullable String id) {
    Dto.Dialog d = actionTarget;
    if (d == null || id == null) {
      return;
    }
    if ("pin".equals(id)) {
      Map<String, Object> body = new HashMap<>();
      body.put("pinned", !d.pinned);
      Calls.run(api().pinDialog(d.id, body), r -> load());
    } else if ("delete".equals(id)) {
      ConfirmDialog.with(requireContext())
          .title("Удалить диалог?")
          .message("Переписка удалится у обоих собеседников.")
          .confirm("Удалить")
          .cancel("Отмена")
          .destructive()
          .onConfirm(() -> Calls.run(api().deleteDialog(d.id), r -> load()))
          .show();
    }
  }

  @Override
  public void onReselected() {
    list.smoothScrollToPosition(0);
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }

  private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    @Override
    public int getItemViewType(int position) {
      return rows.get(position) instanceof String ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inf = LayoutInflater.from(parent.getContext());
      if (viewType == 0) {
        return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_section, parent, false)) {};
      }
      return new Row(inf.inflate(R.layout.item_dialog, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
      Object o = rows.get(position);
      if (o instanceof String) {
        ((TextView) h.itemView).setText((String) o);
        return;
      }
      Dto.Dialog d = (Dto.Dialog) o;
      Row r = (Row) h;
      Images.avatar(r.avatar, d.user);
      r.title.setText(d.user.name);
      if (d.typing) {
        SpannableString s = new SpannableString(getString(R.string.chat_typing));
        s.setSpan(
            new ForegroundColorSpan(
                DsTheme.color(
                    requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorAccent)),
            0,
            s.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        r.sub.setText(s);
        r.sub.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
      } else if (d.lastMessage != null) {
        String prefix = d.lastMessage.isMine ? "Вы: " : "";
        r.sub.setText(prefix + d.lastMessage.text);
        r.sub.setCompoundDrawablesRelativeWithIntrinsicBounds(
            d.lastMessage.isOutfit ? R.drawable.badge_hanger_small : 0, 0, 0, 0);
      } else {
        r.sub.setText("");
      }
      r.time.setText(d.lastMessage != null ? Formats.dialogTime(d.lastMessage.createdAt) : "");
      r.unread.setVisibility(d.unreadCount > 0 ? View.VISIBLE : View.GONE);
      r.unread.setText(String.valueOf(d.unreadCount));
      r.unread.setContentDescription(d.unreadCount + " непрочитанных");
      r.read.setVisibility(
          d.unreadCount == 0
                  && d.lastMessage != null
                  && d.lastMessage.isMine
                  && d.lastMessage.isRead
              ? View.VISIBLE
              : View.GONE);
      r.itemView.setOnClickListener(v -> nav().push(ChatFragment.forDialog(d)));
      r.itemView.setOnLongClickListener(
          v -> {
            actionTarget = d;
            ArrayList<ActionSheet.Row> actions = new ArrayList<>();
            actions.add(
                new ActionSheet.Row(
                    "pin",
                    app.outfitshare.core.designsystem.R.drawable.ds_ic_north_west,
                    d.pinned ? "Открепить" : "Закрепить",
                    null,
                    false));
            actions.add(
                new ActionSheet.Row(
                    "delete",
                    app.outfitshare.core.designsystem.R.drawable.ds_ic_delete,
                    "Удалить диалог",
                    null,
                    true));
            ActionSheet.show(getChildFragmentManager(), ACTIONS, d.user.name, actions);
            return true;
          });
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }
  }

  static final class Row extends RecyclerView.ViewHolder {
    final AvatarView avatar;
    final TextView title;
    final TextView sub;
    final TextView time;
    final TextView unread;
    final ImageView read;

    Row(View v) {
      super(v);
      avatar = v.findViewById(R.id.avatar);
      title = v.findViewById(R.id.title);
      sub = v.findViewById(R.id.sub);
      time = v.findViewById(R.id.time);
      unread = v.findViewById(R.id.unread);
      read = v.findViewById(R.id.read);
    }
  }
}
