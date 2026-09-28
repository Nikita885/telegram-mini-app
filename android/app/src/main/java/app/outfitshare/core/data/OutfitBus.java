package app.outfitshare.core.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Broadcasts outfit changes (like, save, delete, new) so every open list stays consistent
 * without refetching: the feed updates when the same outfit is liked on its detail screen.
 */
public final class OutfitBus {
  public static final class Change {
    public final long outfitId;
    public final Boolean liked;
    public final Integer likesCount;
    public final Boolean saved;
    public final Integer commentsCount;
    public final boolean deleted;
    public final boolean created;

    private Change(long outfitId, Boolean liked, Integer likesCount, Boolean saved, Integer commentsCount,
        boolean deleted, boolean created) {
      this.outfitId = outfitId;
      this.liked = liked;
      this.likesCount = likesCount;
      this.saved = saved;
      this.commentsCount = commentsCount;
      this.deleted = deleted;
      this.created = created;
    }
  }

  private final MutableLiveData<Change> changes = new MutableLiveData<>();

  public LiveData<Change> changes() {
    return changes;
  }

  public void liked(long id, boolean liked, int count) {
    changes.setValue(new Change(id, liked, count, null, null, false, false));
  }

  public void saved(long id, boolean saved) {
    changes.setValue(new Change(id, null, null, saved, null, false, false));
  }

  public void commented(long id, int count) {
    changes.setValue(new Change(id, null, null, null, count, false, false));
  }

  public void deleted(long id) {
    changes.setValue(new Change(id, null, null, null, null, true, false));
  }

  public void created(long id) {
    changes.setValue(new Change(id, null, null, null, null, false, true));
  }
}
