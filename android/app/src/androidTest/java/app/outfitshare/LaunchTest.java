package app.outfitshare;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.outfitshare.core.session.Prefs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Smoke tests on a device: the app starts, onboarding can be skipped, sign-in is offered. */
@RunWith(AndroidJUnit4.class)
public class LaunchTest {
  @Before
  public void signedOut() {
    ApplicationProvider.<App>getApplicationContext().container().signOut();
  }

  @Test
  public void onboardingLeadsToTelegramSignIn() {
    Context context = ApplicationProvider.getApplicationContext();
    context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit();
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.skip)).perform(click());
      onView(withId(R.id.telegram)).check(matches(isDisplayed()));
    }
  }

  @Test
  public void returningUserSeesTelegramSignIn() {
    new Prefs(ApplicationProvider.getApplicationContext()).setOnboarded();
    try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.telegram)).check(matches(isDisplayed()));
    }
  }
}
