package app.outfitshare.feature.feed;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.motion.SharedElements;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Paging;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.core.ui.States;
import app.outfitshare.core.ui.Wordmark;
import app.outfitshare.feature.notifications.NotificationsFragment;
import app.outfitshare.feature.outfit.CommentsSheet;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.profile.UserFragment;
import app.outfitshare.feature.search.SearchFragment;
import app.outfitshare.feature.shell.ShellFragment;
import com.google.android.material.tabs.TabLayout;
import java.util.List;

/** «Подписки / Для вас». Double tap on a photo likes, tap opens the outfit (shared element). */
public class FeedFragment extends BaseFragment implements ShellFragment.Reselectable {
  private static final int TAB_FOLLOWING = 0;
  private static final int TAB_FOR_YOU = 1;
  private static final long NEW_POSTS_CHECK_MS = 60_000;

  @SuppressWarnings("unchecked")
  private final Paging<Dto.Outfit>[] pagings = new Paging[2];

  private final Handler handler = new Handler(Looper.getMainLooper());
  private OutfitAdapter adapter;
  private StateView stateView;
  private SwipeRefreshLayout refresh;
  private OfflineBanner offline;
  private RecyclerView list;
  private View newPosts;
  private int tab = TAB_FOLLOWING;

  public FeedFragment() {
    super(R.layout.fragment_feed);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    Wordmark.apply(view.findViewById(R.id.wordmark));
    stateView = view.findViewById(R.id.state);
    refresh = view.findViewById(R.id.refresh);
    offline = view.findViewById(R.id.offline);
    list = view.findViewById(R.id.list);
    newPosts = view.findViewById(R.id.new_posts);

    View bell = view.findViewById(R.id.notifications);
    View dot = view.findViewById(R.id.notifications_dot);
    bell.setOnClickListener(v -> nav().push(new NotificationsFragment()));
    container()
        .counters
        .counters()
        .observe(
            getViewLifecycleOwner(),
            c -> {
              dot.setVisibility(c.notifications > 0 ? View.VISIBLE : View.GONE);
              bell.setContentDescription(
                  c.notifications > 0 ? "Уведомления, есть новые" : "Уведомления");
            });

    adapter =
        new OutfitAdapter(
            false,
            new OutfitAdapter.Listener() {
              @Override
              public void onOpen(Dto.Outfit outfit, View photo) {
                SharedElements.holdSource(FeedFragment.this);
                nav()
                    .push(
                        OutfitFragment.newInstance(outfit.id),
                        photo,
                        SharedElements.outfitPhoto(outfit.id));
              }

              @Override
              public void onAuthor(Dto.User author) {
                nav().push(UserFragment.newInstance(author.id));
              }

              @Override
              public void onComments(Dto.Outfit outfit) {
                CommentsSheet.show(getChildFragmentManager(), outfit.id, outfit.author.id);
              }

              @Override
              public void onTag(String tag) {
                nav().push(SearchFragment.forQuery("#" + tag));
              }
            });
    LinearLayoutManager lm = new LinearLayoutManager(requireContext());
    list.setLayoutManager(lm);
    list.setAdapter(adapter);
    list.addItemDecoration(
        Spacing.vertical(
            getResources()
                .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_8)));
    list.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
              pagings[tab].loadMore();
            }
          }
        });

    pagings[TAB_FOLLOWING] = paging("following");
    pagings[TAB_FOR_YOU] = paging("for_you");

    TabLayout tabs = view.findViewById(R.id.tabs);
    tabs.addTab(tabs.newTab().setText(R.string.tab_following));
    tabs.addTab(tabs.newTab().setText(R.string.tab_for_you));
    tabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab t) {
            switchTab(t.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab t) {}

          @Override
          public void onTabReselected(TabLayout.Tab t) {
            onReselected();
          }
        });

    refresh.setOnRefreshListener(() -> pagings[tab].refresh());
    stateView.setOnActionClickListener(
        v -> {
          if (tab == TAB_FOLLOWING && stateView.getState().getKind() == ScreenState.Kind.EMPTY) {
            tabs.selectTab(tabs.getTabAt(TAB_FOR_YOU));
          } else {
            stateView.setState(ScreenState.loading());
            pagings[tab].refresh();
          }
        });
    stateView.setOnSecondaryActionClickListener(v -> nav().push(SearchFragment.forQuery("@")));
    offline.setOnRetryClickListener(v -> pagings[tab].refresh());
    newPosts.setOnClickListener(
        v -> {
          newPosts.setVisibility(View.GONE);
          list.scrollToPosition(0);
          pagings[tab].refresh();
        });

    container()
        .outfitBus
        .changes()
        .observe(
            getViewLifecycleOwner(),
            c -> {
              if (c.liked != null) {
                adapter.applyLike(c.outfitId, c.liked, c.likesCount);
              }
              if (c.deleted || c.created) {
                pagings[tab].refresh();
              }
            });

    switchTab(TAB_FOLLOWING);
    handler.postDelayed(this::checkNewPosts, NEW_POSTS_CHECK_MS);
  }

  private Paging<Dto.Outfit> paging(String kind) {
    return new Paging<>(
        cursor ->
            "for_you".equals(kind)
                ? api().feed(kind, cursor, null)
                : api().feed(kind, null, cursor),
        new Paging.Listener<Dto.Outfit>() {
          @Override
          public void onPage(List<Dto.Outfit> all, boolean reset, boolean fromCache) {
            if (pagings[tab] == null
                || !kind.equals(tab == TAB_FOR_YOU ? "for_you" : "following")) {
              return;
            }
            render(all, fromCache);
          }

          @Override
          public void onError(ApiError error, boolean firstPage) {
            refresh.setRefreshing(false);
            if (firstPage && adapter.getItemCount() == 0) {
              stateView.setState(States.failure(error, "Лента не загрузилась"));
            } else {
              showError(error, () -> pagings[tab].refresh());
            }
          }
        });
  }

  private void switchTab(int index) {
    tab = index;
    Paging<Dto.Outfit> p = pagings[index];
    if (p.loadedOnce()) {
      render(p.items(), false);
    } else {
      adapter.submit(java.util.Collections.emptyList());
      stateView.setState(ScreenState.loading());
      p.refresh();
    }
  }

  private void render(List<Dto.Outfit> items, boolean fromCache) {
    refresh.setRefreshing(false);
    offline.setOffline(fromCache);
    offline.setVisibility(fromCache ? View.VISIBLE : View.GONE);
    adapter.submit(items);
    if (items.isEmpty()) {
      if (tab == TAB_FOLLOWING) {
        stateView.setState(
            States.empty(
                app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_feed,
                "Здесь будут образы тех, на кого вы подпишетесь",
                "Загляните в рекомендации или найдите друзей из Telegram.",
                "Смотреть «Для вас»",
                "Найти людей"));
      } else {
        stateView.setState(
            States.empty(
                app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_feed,
                "Пока нет образов",
                "Соберите первый — он появится у всех в ленте.",
                null));
      }
    } else {
      stateView.setState(ScreenState.content());
    }
  }

  @Override
  public void onReselected() {
    if (list != null) {
      list.smoothScrollToPosition(0);
    }
  }

  private void checkNewPosts() {
    handler.postDelayed(this::checkNewPosts, NEW_POSTS_CHECK_MS);
    List<Dto.Outfit> items = pagings[TAB_FOR_YOU].items();
    if (!isResumed() || isHidden() || tab != TAB_FOR_YOU || items.isEmpty()) {
      return;
    }
    long max = 0;
    for (Dto.Outfit o : items) {
      max = Math.max(max, o.id);
    }
    // `since` returns only a count: a cheap poll that doesn't reshuffle the visible feed.
    Calls.run(
        api().feedNewCount("for_you", max),
        r -> {
          if (r.ok() && r.data != null && r.data.newCount > 0 && newPosts != null) {
            newPosts.setVisibility(View.VISIBLE);
          }
        });
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }
}
