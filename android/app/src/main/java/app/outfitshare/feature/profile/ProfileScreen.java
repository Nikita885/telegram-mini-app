package app.outfitshare.feature.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
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
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.Paging;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.outfit.OutfitFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import java.util.List;

/** Shared profile screen: own profile (tab root) and someone else's (pushed). */
public abstract class ProfileScreen extends BaseFragment {
  protected static final int TAB_OUTFITS = 0;
  protected static final int TAB_COLLECTIONS = 1;
  protected static final int TAB_DRAFTS = 2;

  @Nullable protected Dto.Profile profile;
  protected StateView stateView;
  protected StateView tabState;
  protected RecyclerView grid;
  protected SwipeRefreshLayout refresh;
  protected OfflineBanner offline;
  protected GridOutfitAdapter outfitsAdapter;
  protected CollectionsAdapter collectionsAdapter;
  protected Paging<Dto.Outfit> outfits;
  protected int tab = TAB_OUTFITS;
  protected GridLayoutManager gridManager;
  private RecyclerView.ItemDecoration gridSpacing;

  protected ProfileScreen() {
    super(R.layout.fragment_profile);
  }

  /** Whose profile: -1 for the signed-in user. */
  protected abstract long userId();

  protected abstract boolean isOwn();

  protected abstract void bindButtons(
      Dto.Profile p, MaterialButton primary, MaterialButton secondary);

  protected abstract void bindAppBar(View root, Dto.Profile p);

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    stateView = view.findViewById(R.id.state);
    tabState = view.findViewById(R.id.tab_state);
    grid = view.findViewById(R.id.grid);
    refresh = view.findViewById(R.id.refresh);
    offline = view.findViewById(R.id.offline);
    stateView.setOnActionClickListener(v -> reload());
    offline.setOnRetryClickListener(v -> reload());
    refresh.setOnRefreshListener(this::reload);

    gridManager = new GridLayoutManager(requireContext(), 3);
    grid.setLayoutManager(gridManager);
    outfitsAdapter = new GridOutfitAdapter(o -> nav().push(OutfitFragment.newInstance(o.id)));
    collectionsAdapter =
        new CollectionsAdapter(c -> nav().push(CollectionFragment.newInstance(c.id)));

    TabLayout tabs = view.findViewById(R.id.tabs);
    tabs.addTab(tabs.newTab().setText(R.string.profile_outfits));
    tabs.addTab(tabs.newTab().setText(R.string.profile_collections));
    if (isOwn()) {
      tabs.addTab(tabs.newTab().setText(R.string.profile_drafts));
    }
    tabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab t) {
            showTab(t.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab t) {}

          @Override
          public void onTabReselected(TabLayout.Tab t) {}
        });

    NestedScrollView scroll = view.findViewById(R.id.scroll);
    scroll.setOnScrollChangeListener(
        (NestedScrollView.OnScrollChangeListener)
            (v, x, y, ox, oy) -> {
              if (tab == TAB_OUTFITS
                  && outfits != null
                  && y + v.getHeight() >= v.getChildAt(0).getHeight() - v.getHeight() / 2) {
                outfits.loadMore();
              }
            });

    container()
        .outfitBus
        .changes()
        .observe(
            getViewLifecycleOwner(),
            c -> {
              if (c.deleted) {
                outfitsAdapter.remove(c.outfitId);
              } else if (c.created && isOwn()) {
                reload();
              }
            });

    stateView.setState(ScreenState.loading());
    reload();
  }

  protected void reload() {
    long id = userId();
    Calls.run(
        id < 0 ? api().me() : api().user(id),
        r -> {
          if (!isAdded()) {
            return;
          }
          refresh.setRefreshing(false);
          if (!r.ok() || r.data == null) {
            if (profile == null) {
              stateView.setState(States.failure(r.error, getString(R.string.profile_failed)));
            } else {
              showError(r.error, this::reload);
            }
            return;
          }
          offline.setOffline(r.fromCache);
          offline.setVisibility(r.fromCache ? View.VISIBLE : View.GONE);
          profile = r.data;
          if (isOwn()) {
            container().session.setMe(r.data);
          }
          bindHeader(r.data);
          stateView.setState(ScreenState.content());
          outfits =
              new Paging<>(
                  cursor -> api().userOutfits(r.data.id, cursor),
                  new Paging.Listener<Dto.Outfit>() {
                    @Override
                    public void onPage(List<Dto.Outfit> all, boolean reset, boolean fromCache) {
                      if (tab == TAB_OUTFITS) {
                        showOutfits(all);
                      }
                    }

                    @Override
                    public void onError(ApiError error, boolean firstPage) {
                      if (tab == TAB_OUTFITS && firstPage) {
                        tabState.setState(
                            States.failure(error, getString(R.string.outfits_failed)));
                      }
                    }
                  });
          showTab(tab);
        });
  }

  private void bindHeader(Dto.Profile p) {
    View v = requireView();
    bindAppBar(v, p);
    AvatarView avatar = v.findViewById(R.id.avatar);
    Images.avatar(avatar, p);
    bindCounter(
        v.findViewById(R.id.count_outfits),
        p.outfitsCount,
        Formats.outfitsWord(requireContext(), p.outfitsCount));
    bindCounter(
        v.findViewById(R.id.count_followers),
        p.followersCount,
        getResources().getQuantityString(R.plurals.followers_word, p.followersCount));
    bindCounter(
        v.findViewById(R.id.count_following), p.followingCount, getString(R.string.following_word));
    v.findViewById(R.id.count_followers)
        .setOnClickListener(x -> nav().push(FollowsFragment.newInstance(p.id, p.username, true)));
    v.findViewById(R.id.count_following)
        .setOnClickListener(x -> nav().push(FollowsFragment.newInstance(p.id, p.username, false)));
    TextView name = v.findViewById(R.id.name);
    name.setText(p.name);
    boolean staff = "admin".equals(p.role) || "moderator".equals(p.role);
    name.setCompoundDrawablesRelativeWithIntrinsicBounds(
        0, 0, staff ? R.drawable.badge_verified : 0, 0);
    androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
        name,
        android.content.res.ColorStateList.valueOf(
            app.outfitshare.core.designsystem.theme.DsTheme.color(
                requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorAccent)));
    TextView bio = v.findViewById(R.id.bio);
    bio.setText(p.bio);
    bio.setVisibility(p.bio == null || p.bio.isEmpty() ? View.GONE : View.VISIBLE);
    bindButtons(p, v.findViewById(R.id.button_primary), v.findViewById(R.id.button_secondary));
  }

  private static void bindCounter(View counter, int value, String label) {
    ((TextView) counter.findViewById(R.id.counter_value)).setText(Formats.count(value));
    ((TextView) counter.findViewById(R.id.counter_label)).setText(label);
    counter.setContentDescription(value + " " + label);
  }

  protected void showTab(int index) {
    tab = index;
    if (gridSpacing != null) {
      grid.removeItemDecoration(gridSpacing);
    }
    if (index == TAB_OUTFITS) {
      gridManager.setSpanCount(3);
      int gap = getResources().getDimensionPixelSize(R.dimen.profile_grid_gap);
      gridSpacing = Spacing.grid(gap, gap);
      grid.addItemDecoration(gridSpacing);
      grid.setPadding(0, 0, 0, 0);
      grid.setAdapter(outfitsAdapter);
      if (outfits == null) {
        return;
      }
      if (outfits.loadedOnce()) {
        showOutfits(outfits.items());
      } else {
        tabState.setState(ScreenState.loading());
        outfits.refresh();
      }
    } else if (index == TAB_COLLECTIONS) {
      gridManager.setSpanCount(2);
      int gutter =
          getResources()
              .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
      int gap =
          getResources()
              .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_grid_gap);
      gridSpacing =
          Spacing.grid(
              getResources()
                  .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_5),
              gap);
      grid.addItemDecoration(gridSpacing);
      grid.setPadding(
          gutter,
          getResources()
              .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_4),
          gutter,
          0);
      grid.setAdapter(collectionsAdapter);
      loadCollections();
    } else {
      showDrafts();
    }
  }

  protected void showOutfits(List<Dto.Outfit> all) {
    outfitsAdapter.submit(all);
    if (all.isEmpty()) {
      tabState.setState(emptyOutfitsState());
    } else {
      tabState.setState(ScreenState.content());
    }
  }

  protected ScreenState emptyOutfitsState() {
    return States.empty(
        app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_outfits,
        getString(R.string.feed_empty_title),
        null,
        null);
  }

  private void loadCollections() {
    if (profile == null) {
      return;
    }
    tabState.setState(ScreenState.loading());
    Calls.run(
        api().collections(isOwn() ? null : profile.id),
        r -> {
          if (!isAdded() || tab != TAB_COLLECTIONS) {
            return;
          }
          if (!r.ok() || r.data == null) {
            tabState.setState(States.failure(r.error, getString(R.string.collections_failed)));
            return;
          }
          collectionsAdapter.submit(r.data.results);
          tabState.setState(
              r.data.results.isEmpty()
                  ? States.empty(
                      app.outfitshare.core.designsystem.R.drawable
                          .ds_illustration_empty_collections,
                      getString(R.string.collections_empty),
                      null,
                      null)
                  : ScreenState.content());
        });
  }

  protected void showDrafts() {}

  protected void shareProfile(Dto.Profile p) {
    Intent send = new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(
        Intent.EXTRA_TEXT,
        getString(R.string.profile_share_text, p.name, container().prefs.server() + "u/" + p.id));
    startActivity(Intent.createChooser(send, getString(R.string.action_share)));
  }
}
