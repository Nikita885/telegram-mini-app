package app.outfitshare.core;

import android.content.Context;
import app.outfitshare.core.data.CountersStore;
import app.outfitshare.core.data.OutfitBus;
import app.outfitshare.core.net.ApiClient;
import app.outfitshare.core.realtime.Realtime;
import app.outfitshare.core.session.Prefs;
import app.outfitshare.core.session.Session;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Manual dependency container: one instance per process, created in App.onCreate(). */
public final class AppContainer {
  public final Prefs prefs;
  public final Gson gson;
  public final Session session;
  public final ApiClient api;
  public final Realtime realtime;
  public final CountersStore counters;
  public final OutfitBus outfitBus;

  public AppContainer(Context context) {
    prefs = new Prefs(context);
    gson =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .serializeNulls()
            .create();
    session = new Session(context, gson);
    api = new ApiClient(context, session, gson, prefs.server());
    realtime = new Realtime(api, session);
    counters = new CountersStore(api, realtime);
    outfitBus = new OutfitBus();
  }

  /** Point the app at another server (login screen, long press on the logo). */
  public void changeServer(String url) {
    prefs.setServer(url);
    api.setBaseUrl(prefs.server());
  }

  public void signOut() {
    realtime.stop();
    try {
      api.http().cache().evictAll();
    } catch (Exception ignored) {
      // Cache may be closed; nothing to wipe.
    }
    session.clear();
  }
}
