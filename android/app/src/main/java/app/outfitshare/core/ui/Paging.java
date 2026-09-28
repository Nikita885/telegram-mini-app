package app.outfitshare.core.ui;

import androidx.annotation.Nullable;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;

/** Cursor pagination for a list screen: first page, next pages, refresh; one request at a time. */
public final class Paging<T> {
  public interface Source<T> {
    Call<Dto.Page<T>> page(@Nullable String cursor);
  }

  public interface Listener<T> {
    /** `reset` — the list was replaced (first page / refresh). */
    void onPage(List<T> all, boolean reset, boolean fromCache);

    void onError(ApiError error, boolean firstPage);
  }

  private final Source<T> source;
  private final Listener<T> listener;
  private final List<T> items = new ArrayList<>();
  @Nullable private String next;
  private boolean loading;
  private boolean loadedOnce;
  private boolean ended;
  @Nullable private Call<?> inFlight;

  public Paging(Source<T> source, Listener<T> listener) {
    this.source = source;
    this.listener = listener;
  }

  public List<T> items() {
    return items;
  }

  public boolean loadedOnce() {
    return loadedOnce;
  }

  public boolean isLoading() {
    return loading;
  }

  public void refresh() {
    if (inFlight != null) {
      inFlight.cancel();
    }
    loading = false;
    next = null;
    ended = false;
    load(true);
  }

  public void loadMore() {
    if (!loading && !ended && loadedOnce) {
      load(false);
    }
  }

  private void load(boolean reset) {
    loading = true;
    inFlight =
        Calls.run(
            source.page(reset ? null : next),
            r -> {
              loading = false;
              inFlight = null;
              if (!r.ok() || r.data == null) {
                listener.onError(r.error != null ? r.error : ApiError.offline(), reset);
                return;
              }
              if (reset) {
                items.clear();
              }
              items.addAll(r.data.results);
              next = r.data.next;
              ended = next == null;
              loadedOnce = true;
              listener.onPage(items, reset, r.fromCache);
            });
  }
}
