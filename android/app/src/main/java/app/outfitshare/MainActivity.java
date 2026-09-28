package app.outfitshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import app.outfitshare.core.AppContainer;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.ui.Navigator;
import app.outfitshare.feature.auth.LoginFragment;
import app.outfitshare.feature.auth.OnboardingFragment;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.profile.UserFragment;
import app.outfitshare.feature.shell.ShellFragment;
import java.util.List;

/** Single activity: splash → onboarding/login or the tabbed shell; everything else is fragments. */
public class MainActivity extends AppCompatActivity {
  private Navigator navigator;
  private AppContainer container;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    SplashScreen.installSplashScreen(this);
    super.onCreate(savedInstanceState);
    SystemBars.enableEdgeToEdge(getWindow());
    setContentView(R.layout.activity_main);
    container = App.get().container();
    navigator = new Navigator(getSupportFragmentManager(), R.id.root_container);

    if (savedInstanceState == null) {
      showStart();
      handleDeepLink(getIntent());
    }
    container
        .session
        .signedOut()
        .observe(
            this,
            out -> {
              if (Boolean.TRUE.equals(out)
                  && getSupportFragmentManager().findFragmentByTag("root")
                      instanceof ShellFragment) {
                navigator.setRoot(new LoginFragment());
              }
            });
  }

  public Navigator navigator() {
    return navigator;
  }

  private void showStart() {
    if (container.session.isSignedIn()) {
      navigator.setRoot(new ShellFragment());
    } else if (!container.prefs.onboarded()) {
      navigator.setRoot(new OnboardingFragment());
    } else {
      navigator.setRoot(new LoginFragment());
    }
  }

  /** After a successful login. */
  public void onSignedIn() {
    container.realtime.start();
    container.counters.refresh();
    navigator.setRoot(new ShellFragment());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleDeepLink(intent);
  }

  /**
   * outfitshare://outfit/42, outfitshare://user/7 and https://<server>/o/42, /u/7 (shared links).
   */
  private void handleDeepLink(@Nullable Intent intent) {
    Uri uri = intent == null ? null : intent.getData();
    if (uri == null || !container.session.isSignedIn()) {
      return;
    }
    String kind = linkKind(uri);
    long id;
    try {
      id = Long.parseLong(uri.getLastPathSegment());
    } catch (NumberFormatException | NullPointerException e) {
      return;
    }
    if ("outfit".equals(kind)) {
      getWindow().getDecorView().post(() -> navigator.push(OutfitFragment.newInstance(id)));
    } else if ("user".equals(kind)) {
      getWindow().getDecorView().post(() -> navigator.push(UserFragment.newInstance(id)));
    }
  }

  @Nullable
  private static String linkKind(Uri uri) {
    if ("outfitshare".equals(uri.getScheme())) {
      return uri.getHost();
    }
    List<String> path = uri.getPathSegments();
    if (path.size() != 2) {
      return null;
    }
    return "o".equals(path.get(0)) ? "outfit" : "u".equals(path.get(0)) ? "user" : null;
  }
}
