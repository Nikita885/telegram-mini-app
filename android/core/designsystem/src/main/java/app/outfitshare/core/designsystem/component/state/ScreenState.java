package app.outfitshare.core.designsystem.component.state;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * Состояние экрана для {@link StateView}. Каждый экран проектируется в шести состояниях:
 *
 * <ul>
 *   <li>{@link Kind#CONTENT} — данные есть, StateView скрыт;
 *   <li>{@link Kind#LOADING} — скелетон формы будущего контента;
 *   <li>{@link Kind#EMPTY} — иллюстрация, объяснение и следующий шаг;
 *   <li>{@link Kind#ERROR} — ошибка сети/сервера и «Повторить»;
 *   <li>{@link Kind#OFFLINE} — нет сети и нет кэша (с кэшем — баннер {@code OfflineBanner});
 *   <li>{@link Kind#NO_PERMISSION} — нет прав (камера, приватный профиль, Студия не для админа).
 * </ul>
 */
public final class ScreenState {

  /** Вид состояния. */
  public enum Kind {
    CONTENT,
    LOADING,
    EMPTY,
    ERROR,
    OFFLINE,
    NO_PERMISSION
  }

  private static final int NO_RES = 0;
  private static final ScreenState CONTENT = new Builder(Kind.CONTENT).build();
  private static final ScreenState LOADING = new Builder(Kind.LOADING).build();

  private final Kind kind;
  @DrawableRes private final int illustration;
  @Nullable private final CharSequence title;
  @Nullable private final CharSequence message;
  @Nullable private final CharSequence actionLabel;
  @Nullable private final CharSequence secondaryActionLabel;

  private ScreenState(Builder builder) {
    kind = builder.kind;
    illustration = builder.illustration;
    title = builder.title;
    message = builder.message;
    actionLabel = builder.actionLabel;
    secondaryActionLabel = builder.secondaryActionLabel;
  }

  @NonNull
  public static ScreenState content() {
    return CONTENT;
  }

  @NonNull
  public static ScreenState loading() {
    return LOADING;
  }

  /** Ошибка со стандартными текстами и действием «Повторить». */
  @NonNull
  public static ScreenState error() {
    return new Builder(Kind.ERROR).build();
  }

  /** Нет сети и нет кэша: стандартные тексты и «Повторить». */
  @NonNull
  public static ScreenState offline() {
    return new Builder(Kind.OFFLINE).build();
  }

  /** Конструктор произвольного состояния. */
  @NonNull
  public static Builder builder(@NonNull Kind kind) {
    return new Builder(kind);
  }

  @NonNull
  public Kind getKind() {
    return kind;
  }

  @DrawableRes
  public int getIllustration() {
    return illustration;
  }

  public boolean hasIllustration() {
    return illustration != NO_RES;
  }

  @Nullable
  public CharSequence getTitle() {
    return title;
  }

  @Nullable
  public CharSequence getMessage() {
    return message;
  }

  @Nullable
  public CharSequence getActionLabel() {
    return actionLabel;
  }

  @Nullable
  public CharSequence getSecondaryActionLabel() {
    return secondaryActionLabel;
  }

  /** Показывает ли состояние сообщение с иллюстрацией (а не контент или загрузку). */
  public boolean isMessage() {
    return kind != Kind.CONTENT && kind != Kind.LOADING;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ScreenState)) {
      return false;
    }
    ScreenState that = (ScreenState) other;
    return kind == that.kind
        && illustration == that.illustration
        && Objects.equals(str(title), str(that.title))
        && Objects.equals(str(message), str(that.message))
        && Objects.equals(str(actionLabel), str(that.actionLabel))
        && Objects.equals(str(secondaryActionLabel), str(that.secondaryActionLabel));
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, illustration, str(title), str(message), str(actionLabel));
  }

  @Override
  public String toString() {
    return "ScreenState{" + kind + ", title=" + title + "}";
  }

  @Nullable
  private static String str(@Nullable CharSequence text) {
    return text == null ? null : text.toString();
  }

  /** Сборка состояния. Не заданные тексты StateView подставит по умолчанию для вида. */
  public static final class Builder {
    private final Kind kind;
    @DrawableRes private int illustration = NO_RES;
    @Nullable private CharSequence title;
    @Nullable private CharSequence message;
    @Nullable private CharSequence actionLabel;
    @Nullable private CharSequence secondaryActionLabel;

    private Builder(Kind kind) {
      this.kind = Objects.requireNonNull(kind);
    }

    @NonNull
    public Builder illustration(@DrawableRes int illustration) {
      this.illustration = illustration;
      return this;
    }

    @NonNull
    public Builder title(@Nullable CharSequence title) {
      this.title = title;
      return this;
    }

    @NonNull
    public Builder message(@Nullable CharSequence message) {
      this.message = message;
      return this;
    }

    /** Главное действие: «Повторить», «Найти людей», «Открыть настройки». */
    @NonNull
    public Builder action(@Nullable CharSequence label) {
      this.actionLabel = label;
      return this;
    }

    @NonNull
    public Builder secondaryAction(@Nullable CharSequence label) {
      this.secondaryActionLabel = label;
      return this;
    }

    @NonNull
    public ScreenState build() {
      return new ScreenState(this);
    }
  }
}
