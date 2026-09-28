package app.outfitshare;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import app.outfitshare.core.AppContainer;

public class App extends Application {
  private static App instance;
  private AppContainer container;

  public static App get() {
    return instance;
  }

  public AppContainer container() {
    return container;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    container = new AppContainer(this);
    container.prefs.theme().apply();

    // The WebSocket lives while the app is visible.
    ProcessLifecycleOwner.get()
        .getLifecycle()
        .addObserver(
            new DefaultLifecycleObserver() {
              @Override
              public void onStart(@NonNull LifecycleOwner owner) {
                if (container.session.isSignedIn()) {
                  container.realtime.start();
                  container.counters.refresh();
                }
              }

              @Override
              public void onStop(@NonNull LifecycleOwner owner) {
                container.realtime.stop();
              }
            });
  }
}
