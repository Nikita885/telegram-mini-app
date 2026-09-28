package app.outfitshare.feature.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.dialog.ConfirmDialog;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.feed.OutfitAdapter;
import app.outfitshare.feature.outfit.CommentsSheet;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.search.SearchFragment;
import com.google.android.material.appbar.MaterialToolbar;

/** A collection: kicker with the count, display title, 2-column outfit grid. */
public class CollectionFragment extends BaseFragment {
  private static final String ARG_ID = "id";

  private OutfitAdapter adapter;
  private HeaderAdapter header;
  private StateView stateView;

  public static CollectionFragment newInstance(long id) {
    CollectionFragment f = new CollectionFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_ID, id);
    f.setArguments(args);
    return f;
  }

  public CollectionFragment() {
    super(R.layout.fragment_collection);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> nav().back());
    stateView = view.findViewById(R.id.state);
    stateView.setOnActionClickListener(v -> load());

    RecyclerView list = view.findViewById(R.id.list);
    GridLayoutManager lm = new GridLayoutManager(requireContext(), 2);
    header = new HeaderAdapter();
    adapter =
        new OutfitAdapter(
            true,
            new OutfitAdapter.Listener() {
              @Override
              public void onOpen(Dto.Outfit outfit, View photo) {
                nav().push(OutfitFragment.newInstance(outfit.id));
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
    lm.setSpanSizeLookup(
        new GridLayoutManager.SpanSizeLookup() {
          @Override
          public int getSpanSize(int position) {
            return position == 0 ? 2 : 1;
          }
        });
    list.setLayoutManager(lm);
    list.setAdapter(new ConcatAdapter(header, adapter));
    int gap =
        getResources()
            .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_grid_gap);
    list.addItemDecoration(Spacing.grid(gap, gap));
    stateView.setState(ScreenState.loading());
    load();
  }

  private void load() {
    long id = requireArguments().getLong(ARG_ID);
    Calls.run(
        api().collection(id, null),
        r -> {
          if (!isAdded()) {
            return;
          }
          if (!r.ok() || r.data == null) {
            stateView.setState(States.failure(r.error, getString(R.string.collection_failed)));
            return;
          }
          Dto.Collection c = r.data.collection;
          header.bind(c);
          adapter.submit(r.data.results);
          stateView.setState(
              r.data.results.isEmpty()
                  ? States.empty(
                      app.outfitshare.core.designsystem.R.drawable
                          .ds_illustration_empty_collections,
                      getString(R.string.collection_empty_title),
                      getString(R.string.collection_empty_text),
                      null)
                  : ScreenState.content());
          MaterialToolbar toolbar = requireView().findViewById(R.id.toolbar);
          toolbar.getMenu().clear();
          if (!c.isDefault) {
            toolbar
                .getMenu()
                .add(R.string.collection_delete)
                .setOnMenuItemClickListener(
                    item -> {
                      ConfirmDialog.with(requireContext())
                          .title(getString(R.string.collection_delete_title))
                          .message(getString(R.string.collection_delete_text))
                          .confirm(getString(R.string.action_delete))
                          .cancel(getString(R.string.action_cancel))
                          .destructive()
                          .onConfirm(
                              () ->
                                  Calls.run(
                                      api().deleteCollection(id),
                                      x -> {
                                        if (x.ok()) {
                                          nav().back();
                                        } else {
                                          showError(x.error, null);
                                        }
                                      }))
                          .show();
                      return true;
                    });
          }
        });
  }

  private final class HeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    @Nullable private Dto.Collection collection;

    void bind(Dto.Collection c) {
      collection = c;
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View v =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.view_large_title, parent, false);
      return new RecyclerView.ViewHolder(v) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
      if (collection == null) {
        return;
      }
      int n = collection.count;
      ((TextView) h.itemView.findViewById(R.id.kicker))
          .setText(
              getString(
                  collection.isPrivate
                      ? R.string.collection_kicker_private
                      : R.string.collection_kicker,
                  getResources().getQuantityString(R.plurals.outfits_count, n, n)));
      ((TextView) h.itemView.findViewById(R.id.title)).setText(collection.title);
    }

    @Override
    public int getItemCount() {
      return collection == null ? 0 : 1;
    }
  }
}
