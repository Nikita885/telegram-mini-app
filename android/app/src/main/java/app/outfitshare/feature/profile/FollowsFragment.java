package app.outfitshare.feature.profile;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Paging;
import app.outfitshare.core.ui.States;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import java.util.List;

/** Mockup «Подписки и подписчики»: two tabs with counts, search within the list. */
public class FollowsFragment extends BaseFragment {
  private static final String ARG_ID = "id";
  private static final String ARG_HANDLE = "handle";
  private static final String ARG_FOLLOWERS = "followers";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private PeopleAdapter adapter;
  private StateView stateView;
  private Paging<Dto.User> paging;
  private String kind;
  private String query = "";

  public static FollowsFragment newInstance(long userId, @Nullable String handle, boolean followers) {
    FollowsFragment f = new FollowsFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_ID, userId);
    args.putString(ARG_HANDLE, handle);
    args.putBoolean(ARG_FOLLOWERS, followers);
    f.setArguments(args);
    return f;
  }

  public FollowsFragment() {
    super(R.layout.fragment_follows);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    Bundle args = requireArguments();
    long userId = args.getLong(ARG_ID);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setTitle(args.getString(ARG_HANDLE));
    toolbar.setNavigationOnClickListener(v -> nav().back());
    stateView = view.findViewById(R.id.state);
    stateView.setOnActionClickListener(v -> paging.refresh());

    RecyclerView list = view.findViewById(R.id.list);
    LinearLayoutManager lm = new LinearLayoutManager(requireContext());
    list.setLayoutManager(lm);
    adapter = new PeopleAdapter(u -> nav().push(UserFragment.newInstance(u.id)));
    list.setAdapter(adapter);
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
        if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
          paging.loadMore();
        }
      }
    });

    TabLayout tabs = view.findViewById(R.id.tabs);
    tabs.addTab(tabs.newTab().setText(R.string.follows_followers));
    tabs.addTab(tabs.newTab().setText(R.string.follows_following));
    Calls.run(api().user(userId), r -> {
      if (isAdded() && r.ok() && r.data != null) {
        setTabText(tabs, 0, getString(R.string.follows_followers), r.data.followersCount);
        setTabText(tabs, 1, getString(R.string.follows_following), r.data.followingCount);
      }
    });

    kind = args.getBoolean(ARG_FOLLOWERS) ? "followers" : "following";
    paging = new Paging<>(cursor -> api().connections(userId, kind, query.isEmpty() ? null : query, cursor),
        new Paging.Listener<Dto.User>() {
          @Override
          public void onPage(List<Dto.User> all, boolean reset, boolean fromCache) {
            adapter.submit(all);
            stateView.setState(all.isEmpty()
                ? States.empty(app.outfitshare.core.designsystem.R.drawable.ds_illustration_people,
                    query.isEmpty() ? "Пока никого" : "Никого не нашли",
                    query.isEmpty() ? "Поделитесь профилем — подписчики появятся здесь." : null, null)
                : ScreenState.content());
          }

          @Override
          public void onError(ApiError error, boolean firstPage) {
            if (firstPage) {
              stateView.setState(States.failure(error, "Список не загрузился"));
            }
          }
        });
    TabLayout.Tab initial = tabs.getTabAt(args.getBoolean(ARG_FOLLOWERS) ? 0 : 1);
    if (initial != null) {
      initial.select();
    }
    tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab t) {
        kind = t.getPosition() == 0 ? "followers" : "following";
        reload();
      }

      @Override
      public void onTabUnselected(TabLayout.Tab t) {}

      @Override
      public void onTabReselected(TabLayout.Tab t) {}
    });

    EditText search = view.findViewById(R.id.search_input);
    search.setHint(R.string.search_in_list);
    View clear = view.findViewById(R.id.search_clear);
    clear.setOnClickListener(v -> search.setText(""));
    search.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

      @Override
      public void onTextChanged(CharSequence s, int a, int b, int c) {}

      @Override
      public void afterTextChanged(Editable s) {
        clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
          query = s.toString().trim();
          reload();
        }, 300);
      }
    });
    reload();
  }

  private static void setTabText(TabLayout tabs, int index, String label, int count) {
    TabLayout.Tab t = tabs.getTabAt(index);
    if (t != null) {
      t.setText(label + "  " + Formats.count(count));
    }
  }

  private void reload() {
    stateView.setState(ScreenState.loading());
    paging.refresh();
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }
}
