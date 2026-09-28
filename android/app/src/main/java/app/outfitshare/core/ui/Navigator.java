package app.outfitshare.core.ui;

import android.view.View;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import app.outfitshare.R;

/**
 * Screen stack on top of the root screen (onboarding/login or the tabbed shell). Pushed screens
 * hide the one below instead of replacing it, so lists keep scroll and state.
 */
public final class Navigator {
  private final FragmentManager fm;
  @IdRes private final int container;

  public Navigator(FragmentManager fm, @IdRes int container) {
    this.fm = fm;
    this.container = container;
  }

  /** Replace everything with a new root (after login/logout). */
  public void setRoot(Fragment root) {
    fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    fm.beginTransaction()
        .setReorderingAllowed(true)
        .setCustomAnimations(R.anim.screen_fade_in, R.anim.screen_fade_out)
        .replace(container, root, "root")
        .commit();
  }

  public void push(Fragment screen) {
    push(screen, null, null);
  }

  /** Push with an optional shared element (outfit photo → outfit screen). */
  public void push(Fragment screen, @Nullable View shared, @Nullable String sharedName) {
    FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
    if (shared != null && sharedName != null) {
      tx.addSharedElement(shared, sharedName);
    } else {
      tx.setCustomAnimations(
          R.anim.screen_enter, R.anim.screen_exit, R.anim.screen_pop_enter, R.anim.screen_pop_exit);
    }
    Fragment top = fm.findFragmentById(container);
    if (top != null) {
      tx.hide(top);
    }
    tx.add(container, screen).addToBackStack(screen.getClass().getSimpleName()).commit();
  }

  /** Open a screen as a modal (constructor, camera): slides up. */
  public void present(Fragment screen) {
    FragmentTransaction tx =
        fm.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.anim.modal_enter,
                R.anim.screen_fade_out,
                R.anim.screen_fade_in,
                R.anim.modal_exit);
    Fragment top = fm.findFragmentById(container);
    if (top != null) {
      tx.hide(top);
    }
    tx.add(container, screen).addToBackStack(screen.getClass().getSimpleName()).commit();
  }

  public boolean back() {
    if (fm.getBackStackEntryCount() > 0) {
      fm.popBackStack();
      return true;
    }
    return false;
  }

  /**
   * Pop everything down to and including the screen pushed under `name` (its class simple name).
   */
  public void popInclusive(String name) {
    fm.popBackStackImmediate(name, FragmentManager.POP_BACK_STACK_INCLUSIVE);
  }

  /** Replace the current top screen (publish → success). */
  public void replaceTop(Fragment screen) {
    fm.popBackStackImmediate();
    push(screen);
  }
}
