package app.outfitshare.core.designsystem.component.avatar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Инициалы аватара без фото. */
public class InitialsTest {

  @Test
  public void firstAndLastWord() {
    assertEquals("АП", Initials.of("Анна Петрова"));
    assertEquals("АС", Initials.of("анна мария сергеевна"));
  }

  @Test
  public void singleWordGivesOneLetter() {
    assertEquals("Л", Initials.of("Лиза"));
  }

  @Test
  public void usernameSeparatorsAreWordBreaks() {
    assertEquals("NS", Initials.of("@nikita_style"));
    assertEquals("OS", Initials.of("outfit.share"));
  }

  @Test
  public void emojiAndSymbolsAreSkipped() {
    assertEquals("F", Initials.of("🦊 Fox"));
    assertEquals("M", Initials.of("✨mila✨"));
  }

  @Test
  public void emptyWhenNoLetters() {
    assertEquals("", Initials.of(null));
    assertEquals("", Initials.of("   "));
    assertEquals("", Initials.of("🔥🔥"));
  }

  @Test
  public void supplementaryLettersAreNotSplit() {
    String gothic = new String(Character.toChars(0x10330));
    assertEquals(gothic, Initials.of(gothic + "abc"));
  }
}
