package app.outfitshare.core.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.outfitshare.App;
import app.outfitshare.MainActivity;
import app.outfitshare.R;
import app.outfitshare.core.AppContainer;
import app.outfitshare.core.designsystem.component.snackbar.DsSnackbar;
import app.outfitshare.core.net.Api;
import app.outfitshare.core.net.ApiError;

/** Shared plumbing for every screen. */
public abstract class BaseFragment extends Fragment {

  protected BaseFragment(@LayoutRes int layout) {
    super(layout);
  }

  protected AppContainer container() {
    return App.get().container();
  }

  protected Api api() {
    return container().api.api();
  }

  protected Navigator nav() {
    return ((MainActivity) requireActivity()).navigator();
  }

  protected long myId() {
    return container().session.myId();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    // Pushed screens are stacked with add(): block touches from falling through to the screen
    // below.
    view.setClickable(true);
  }

  protected void toast(CharSequence message) {
    View v = getView();
    if (v != null) {
      DsSnackbar.message(v, message);
    }
  }

  protected void showError(@Nullable ApiError error, @Nullable Runnable retry) {
    View v = getView();
    if (v == null || error == null) {
      return;
    }
    DsSnackbar.error(
        v, error.isOffline() ? getString(R.string.error_offline) : error.message, retry);
  }
}
