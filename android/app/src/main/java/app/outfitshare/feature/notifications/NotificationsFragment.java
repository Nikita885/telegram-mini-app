package app.outfitshare.feature.notifications;

import android.os.Bundle;
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
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.ButtonStyles;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.Paging;
import app.outfitshare.core.ui.Res;
import app.outfitshare.core.ui.States;
import app.outfitshare.core.ui.Text;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.profile.UserFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Mockup «Уведомления»: groups by recency, unread dot, outfit thumbnail or follow-back button. */
public class NotificationsFragment extends BaseFragment {
  private final List<Object> rows = new ArrayList<>();
  private Adapter adapter;
  private StateView stateView;
  private SwipeRefreshLayout refresh;
  private OfflineBanner offline;
  private Paging<Dto.Notification> paging;

  public NotificationsFragment() {
    super(R.layout.fragment_list);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.notifications_title);
    toolbar.setNavigationOnClickListener(v -> nav().back());
    toolbar
        .getMenu()
        .add(R.string.action_read_all)
        .setIcon(app.outfitshare.core.designsystem.R.drawable.ds_ic_done_all)
        .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
    toolbar.setOnMenuItemClickListener(
        item -> {
          markAllRead();
          return true;
        });
    stateView = view.findViewById(R.id.state);
    refresh = view.findViewById(R.id.refresh);
    offline = view.findViewById(R.id.offline);
    RecyclerView list = view.findViewById(R.id.list);
    LinearLayoutManager lm = new LinearLayoutManager(requireContext());
    list.setLayoutManager(lm);
    adapter = new Adapter();
    list.setAdapter(adapter);
    list.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 4) {
              paging.loadMore();
            }
          }
        });
    stateView.setOnActionClickListener(v -> paging.refresh());
    offline.setOnRetryClickListener(v -> paging.refresh());
    refresh.setOnRefreshListener(() -> paging.refresh());

    paging =
        new Paging<>(
            cursor -> api().notifications(cursor),
            new Paging.Listener<Dto.Notification>() {
              @Override
              public void onPage(List<Dto.Notification> all, boolean reset, boolean fromCache) {
                refresh.setRefreshing(false);
                offline.setOffline(fromCache);
                offline.setVisibility(fromCache ? View.VISIBLE : View.GONE);
                group(all);
                stateView.setState(
                    all.isEmpty()
                        ? States.empty(
                            app.outfitshare.core.designsystem.R.drawable
                                .ds_illustration_empty_notifications,
                            getString(R.string.notifications_empty_title),
                            getString(R.string.notifications_empty_text),
                            null)
                        : ScreenState.content());
                if (reset && !fromCache) {
                  // Seen: the bell dot goes away, the unread dots stay until the next visit.
                  Calls.run(
                      api().readNotifications(), r -> container().counters.clearNotifications());
                }
              }

              @Override
              public void onError(ApiError error, boolean firstPage) {
                refresh.setRefreshing(false);
                if (firstPage) {
                  stateView.setState(
                      States.failure(error, getString(R.string.notifications_failed)));
                }
              }
            });
    stateView.setState(ScreenState.loading());
    paging.refresh();
  }

  private void markAllRead() {
    for (Dto.Notification n : paging.items()) {
      n.isRead = true;
    }
    group(paging.items());
    Calls.run(api().readNotifications(), r -> container().counters.clearNotifications());
  }

  /** «Новые» = unread, then «На этой неделе», then «Раньше». */
  private void group(List<Dto.Notification> all) {
    rows.clear();
    List<Dto.Notification> fresh = new ArrayList<>();
    List<Dto.Notification> week = new ArrayList<>();
    List<Dto.Notification> older = new ArrayList<>();
    for (Dto.Notification n : all) {
      ZonedDateTime t = Formats.parse(n.createdAt);
      long days = t == null ? 0 : ChronoUnit.DAYS.between(t.toInstant(), java.time.Instant.now());
      if (!n.isRead) {
        fresh.add(n);
      } else if (days < 7) {
        week.add(n);
      } else {
        older.add(n);
      }
    }
    addGroup(getString(R.string.notifications_new), fresh);
    addGroup(getString(R.string.notifications_week), week);
    addGroup(getString(R.string.notifications_earlier), older);
    adapter.notifyDataSetChanged();
  }

  private void addGroup(String title, List<Dto.Notification> items) {
    if (!items.isEmpty()) {
      rows.add(title);
      rows.addAll(items);
    }
  }

  private static String describe(Dto.Notification n) {
    switch (n.type) {
      case "like":
        return " " + Res.str(R.string.notif_like);
      case "comment":
        return " " + Res.str(R.string.notif_comment, n.comment != null ? n.comment.text : "");
      case "reply":
        return " " + Res.str(R.string.notif_reply, n.comment != null ? n.comment.text : "");
      case "follow":
        return " " + Res.str(R.string.notif_follow);
      case "remix":
        return " " + Res.str(R.string.notif_remix);
      case "studio":
        return Res.str(R.string.notif_studio);
      default:
        return "";
    }
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
      return new Row(inf.inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
      Object o = rows.get(position);
      if (o instanceof String) {
        ((TextView) h.itemView).setText((String) o);
        return;
      }
      Dto.Notification n = (Dto.Notification) o;
      Row r = (Row) h;
      r.unread.setVisibility(n.isRead ? View.INVISIBLE : View.VISIBLE);
      Images.avatar(r.avatar, n.actor);
      String who =
          n.actor.username != null && !n.actor.username.isEmpty() ? n.actor.username : n.actor.name;
      r.text.setText(Text.boldLead(who, describe(n)));
      r.time.setText(Formats.ago(n.createdAt));
      boolean hasOutfit = n.outfit != null && n.outfit.imageUrl != null;
      r.thumb.setVisibility(hasOutfit ? View.VISIBLE : View.GONE);
      if (hasOutfit) {
        Images.photo(r.thumb, n.outfit.imageUrl);
      }
      boolean follow = "follow".equals(n.type);
      r.action.setVisibility(follow ? View.VISIBLE : View.GONE);
      if (follow) {
        ButtonStyles.follow(r.action, n.actor.isFollowing);
        r.action.setOnClickListener(
            v -> {
              boolean target = !n.actor.isFollowing;
              n.actor.isFollowing = target;
              ButtonStyles.follow(r.action, target);
              Calls.run(target ? api().follow(n.actor.id) : api().unfollow(n.actor.id), x -> {});
            });
      }
      r.avatar.setOnClickListener(v -> nav().push(UserFragment.newInstance(n.actor.id)));
      r.itemView.setOnClickListener(
          v -> {
            if (n.outfit != null) {
              nav().push(OutfitFragment.newInstance(n.outfit.id));
            } else {
              nav().push(UserFragment.newInstance(n.actor.id));
            }
          });
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }
  }

  static final class Row extends RecyclerView.ViewHolder {
    final View unread;
    final AvatarView avatar;
    final TextView text;
    final TextView time;
    final ImageView thumb;
    final MaterialButton action;

    Row(View v) {
      super(v);
      unread = v.findViewById(R.id.unread);
      avatar = v.findViewById(R.id.avatar);
      text = v.findViewById(R.id.text);
      time = v.findViewById(R.id.time);
      thumb = v.findViewById(R.id.thumb);
      action = v.findViewById(R.id.action);
    }
  }
}
