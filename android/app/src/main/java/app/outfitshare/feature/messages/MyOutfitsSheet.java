package app.outfitshare.feature.messages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.App;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.sheet.DsBottomSheetDialogFragment;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.feature.profile.GridOutfitAdapter;

/** Pick one of my outfits to send in a chat. Result: {@link #RESULT} with {@link #RESULT_ID}. */
public class MyOutfitsSheet extends DsBottomSheetDialogFragment {
  public static final String RESULT = "my_outfit_picked";
  public static final String RESULT_ID = "outfit_id";

  public static void show(FragmentManager fm) {
    new MyOutfitsSheet().show(fm, "my_outfits");
  }

  @Nullable
  @Override
  protected CharSequence getSheetTitle() {
    return getString(R.string.action_share_outfit);
  }

  @NonNull
  @Override
  protected View onCreateSheetContent(
      @NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
    RecyclerView grid = new RecyclerView(requireContext());
    grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
    int gap = getResources().getDimensionPixelSize(R.dimen.profile_grid_gap);
    grid.addItemDecoration(Spacing.grid(gap, gap));
    grid.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.profile_tab_min_height));
    GridOutfitAdapter adapter =
        new GridOutfitAdapter(
            o -> {
              Bundle result = new Bundle();
              result.putLong(RESULT_ID, o.id);
              getParentFragmentManager().setFragmentResult(RESULT, result);
              dismiss();
            });
    grid.setAdapter(adapter);
    long me = App.get().container().session.myId();
    Calls.run(
        App.get().container().api.api().userOutfits(me, null),
        r -> {
          if (isAdded() && r.ok() && r.data != null) {
            adapter.submit(r.data.results);
          }
        });
    return grid;
  }
}
