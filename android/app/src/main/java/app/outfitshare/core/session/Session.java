package app.outfitshare.core.session;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import app.outfitshare.core.net.dto.Dto;
import com.google.gson.Gson;

/** Signed-in state: tokens (encrypted) and the cached own profile. */
public final class Session {
  private static final String ACCESS = "access";
  private static final String REFRESH = "refresh";
  private static final String PROFILE = "profile";

  private final SecureStore store;
  private final Gson gson;
  private final MutableLiveData<Dto.Profile> me = new MutableLiveData<>();
  private final MutableLiveData<Boolean> signedOut = new MutableLiveData<>(false);
  @Nullable private volatile String access;
  @Nullable private volatile String refresh;

  public Session(Context context, Gson gson) {
    this.store = new SecureStore(context);
    this.gson = gson;
    access = store.get(ACCESS);
    refresh = store.get(REFRESH);
    String cached = store.get(PROFILE);
    if (cached != null) {
      try {
        me.setValue(gson.fromJson(cached, Dto.Profile.class));
      } catch (RuntimeException ignored) {
        // Stale format: will be refetched.
      }
    }
  }

  public boolean isSignedIn() {
    return refresh != null;
  }

  @Nullable
  public String access() {
    return access;
  }

  @Nullable
  public String refresh() {
    return refresh;
  }

  public synchronized void setTokens(String access, String refresh) {
    this.access = access;
    this.refresh = refresh;
    store.put(ACCESS, access);
    store.put(REFRESH, refresh);
    signedOut.postValue(false);
  }

  public LiveData<Dto.Profile> me() {
    return me;
  }

  @Nullable
  public Dto.Profile currentMe() {
    return me.getValue();
  }

  public long myId() {
    Dto.Profile p = me.getValue();
    return p == null ? -1 : p.id;
  }

  public boolean isAdmin() {
    Dto.Profile p = me.getValue();
    return p != null && (p.isAdmin || "admin".equals(p.role));
  }

  public void setMe(Dto.Profile profile) {
    me.postValue(profile);
    store.put(PROFILE, gson.toJson(profile));
  }

  /** Emits true when the server rejected the refresh token: the UI returns to login. */
  public LiveData<Boolean> signedOut() {
    return signedOut;
  }

  public synchronized void clear() {
    access = null;
    refresh = null;
    store.clear();
    me.postValue(null);
    signedOut.postValue(true);
  }
}
