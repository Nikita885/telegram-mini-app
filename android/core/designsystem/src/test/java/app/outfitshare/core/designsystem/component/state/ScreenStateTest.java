package app.outfitshare.core.designsystem.component.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Модель состояний экрана. */
public class ScreenStateTest {

  @Test
  public void contentAndLoadingAreSingletons() {
    assertSame(ScreenState.content(), ScreenState.content());
    assertSame(ScreenState.loading(), ScreenState.loading());
    assertFalse(ScreenState.content().isMessage());
    assertFalse(ScreenState.loading().isMessage());
  }

  @Test
  public void messageStates() {
    assertTrue(ScreenState.error().isMessage());
    assertTrue(ScreenState.offline().isMessage());
    assertEquals(ScreenState.Kind.OFFLINE, ScreenState.offline().getKind());
    assertFalse(ScreenState.error().hasIllustration());
  }

  @Test
  public void equalityUsesTextContent() {
    ScreenState a =
        ScreenState.builder(ScreenState.Kind.EMPTY)
            .title(new StringBuilder("Пока пусто"))
            .action("Найти людей")
            .build();
    ScreenState b =
        ScreenState.builder(ScreenState.Kind.EMPTY)
            .title("Пока пусто")
            .action("Найти людей")
            .build();
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, ScreenState.builder(ScreenState.Kind.EMPTY).title("Другое").build());
  }
}
