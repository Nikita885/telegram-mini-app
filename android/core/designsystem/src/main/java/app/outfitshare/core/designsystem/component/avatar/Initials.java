package app.outfitshare.core.designsystem.component.avatar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Инициалы для аватара без фото: первые буквы первого и последнего слова имени.
 *
 * <p>Примеры: «Анна Петрова» → «АП», «@nikita_style» → «NS», «Лиза» → «Л», «🦊 Fox» → «F». Эмодзи и
 * знаки пропускаются, суррогатные пары не разрываются.
 */
public final class Initials {

  private static final int MAX_LETTERS = 2;

  private Initials() {}

  /** Инициалы или пустая строка, если в имени нет букв и цифр. */
  public static String of(String displayName) {
    if (displayName == null) {
      return "";
    }
    List<Integer> letters = new ArrayList<>();
    for (String word : displayName.trim().split("[\\s_.\\-@]+")) {
      int codePoint = firstLetterOrDigit(word);
      if (codePoint != -1) {
        letters.add(codePoint);
      }
    }
    if (letters.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    out.appendCodePoint(letters.get(0));
    if (letters.size() >= MAX_LETTERS) {
      out.appendCodePoint(letters.get(letters.size() - 1));
    }
    return out.toString().toUpperCase(Locale.ROOT);
  }

  private static int firstLetterOrDigit(String word) {
    for (int i = 0; i < word.length(); ) {
      int codePoint = word.codePointAt(i);
      if (Character.isLetterOrDigit(codePoint)) {
        return codePoint;
      }
      i += Character.charCount(codePoint);
    }
    return -1;
  }
}
