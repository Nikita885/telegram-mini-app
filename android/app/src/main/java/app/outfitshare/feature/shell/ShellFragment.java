package app.outfitshare.feature.shell;

import android.os.Bundle;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.badge.Badges;
import app.outfitshare.core.designsystem.component.navigation.NavigationBars;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.feature.constructor.ConstructorFragment;
import app.outfitshare.feature.feed.FeedFragment;
import app.outfitshare.feature.messages.DialogsFragment;
import app.outfitshare.feature.profile.ProfileFragment;
import app.outfitshare.feature.search.SearchFragment;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Bottom navigation: Лента · Поиск · Создать · Диалоги · Профиль. Tabs keep their state. */
public class ShellFragment extends BaseFragment {
  private static final String KEY_TAB = "tab";
  private BottomNavigationView bar;
  @IdRes private int current = R.id.nav_feed;

  public ShellFragment() {
    super(R.layout.fragment_shell);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    bar = view.findViewById(R.id.bottom_nav);
    NavigationBars.applyDesignSystemRules(bar);
    SystemBars.applyPadding(bar, false, true);
    styleCreateItem();

    if (state != null) {
      current = state.getInt(KEY_TAB, R.id.nav_feed);
    }
    bar.setSelectedItemId(current);
    showTab(current);
    bar.setOnItemSelectedListener(
        item -> {
          if (item.getItemId() == R.id.nav_create) {
            nav().present(new ConstructorFragment());
            return false;
          }
          showTab(item.getItemId());
          return true;
        });
    bar.setOnItemReselectedListener(
        item -> {
          Fragment f = getChildFragmentManager().findFragmentByTag(tagFor(item.getItemId()));
          if (f instanceof Reselectable) {
            ((Reselectable) f).onReselected();
          }
        });

    container().counters.counters().observe(getViewLifecycleOwner(), c -> Badges.setCount(bar, R.id.nav_dialogs, c.messages));

    requireActivity().getOnBackPressedDispatcher().addCallback(
        getViewLifecycleOwner(),
        new OnBackPressedCallback(true) {
          @Override
          public void handleOnBackPressed() {
            if (current != R.id.nav_feed && getParentFragmentManager().getBackStackEntryCount() == 0) {
              selectTab(R.id.nav_feed);
            } else {
              setEnabled(false);
              requireActivity().getOnBackPressedDispatcher().onBackPressed();
              setEnabled(true);
            }
          }
        });
  }

  /**
   * «Создать» (mockup .bottomnav__create): the 56×32 icon pill is always filled with primary and the
   * plus is on-primary. The pill is the icon container's background, so the label stays in place.
   */
  private void styleCreateItem() {
    View item = bar.findViewById(R.id.nav_create);
    if (item instanceof BottomNavigationItemView) {
      BottomNavigationItemView v = (BottomNavigationItemView) item;
      v.setIconTintList(null);
      v.setActiveIndicatorEnabled(false);
      View container = v.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_container);
      if (container != null) {
        container.setBackgroundResource(R.drawable.nav_create_pill);
      }
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(KEY_TAB, current);
  }

  public void selectTab(@IdRes int id) {
    if (bar != null) {
      bar.setSelectedItemId(id);
    }
  }

  private void showTab(@IdRes int id) {
    current = id;
    FragmentManager fm = getChildFragmentManager();
    FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
    for (Fragment f : fm.getFragments()) {
      if (!tagFor(id).equals(f.getTag())) {
        tx.hide(f);
      }
    }
    Fragment target = fm.findFragmentByTag(tagFor(id));
    if (target == null) {
      tx.add(R.id.tab_container, create(id), tagFor(id));
    } else {
      tx.show(target);
    }
    tx.commit();
  }

  private static String tagFor(@IdRes int id) {
    return "tab_" + id;
  }

  private static Fragment create(@IdRes int id) {
    if (id == R.id.nav_search) {
      return new SearchFragment();
    }
    if (id == R.id.nav_dialogs) {
      return new DialogsFragment();
    }
    if (id == R.id.nav_profile) {
      return new ProfileFragment();
    }
    return new FeedFragment();
  }

  /** Tapping the active tab again scrolls it to the top. */
  public interface Reselectable {
    void onReselected();
  }
}
