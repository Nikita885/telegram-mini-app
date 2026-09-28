package app.outfitshare.feature.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The device name the Telegram bot shows next to the sign-in code. */
public class DeviceNameTest {
  @Test
  public void addsMakerWhenModelLacksIt() {
    assertEquals(
        "Samsung SM-S911B · Android 14", LoginFragment.deviceName("samsung", "SM-S911B", "14"));
  }

  @Test
  public void doesNotRepeatMakerAlreadyInModel() {
    assertEquals(
        "Google Pixel 8 · Android 15", LoginFragment.deviceName("Google", "Google Pixel 8", "15"));
  }

  @Test
  public void survivesMissingBuildFields() {
    assertEquals("Android", LoginFragment.deviceName(null, null, null));
    assertEquals("Pixel 8 · Android", LoginFragment.deviceName("", "Pixel 8", ""));
  }
}
