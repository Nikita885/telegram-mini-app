package app.outfitshare.core.designsystem.component.card;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.outfitshare.core.designsystem.R;
import app.outfitshare.core.designsystem.a11y.A11y;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.image.AspectRatioImageView;
import app.outfitshare.core.designsystem.component.like.LikeButton;
import app.outfitshare.core.designsystem.motion.HeartBurst;

/**
 * Карточка образа для ленты, профиля, поиска и коллекций.
 *
 * <p>Жесты: тап — открыть образ (обычный {@link #setOnClickListener}); двойной тап по фото — лайк с
 * большим сердцем. Пока двойной тап включён ({@link #setDoubleTapToLike}), одиночный тап по фото
 * подтверждается с задержкой системного таймаута двойного тапа.
 *
 * <p>Доступность: карточка читается одной фразой «Образ от …, подпись», лайк — отдельная
 * кнопка-переключатель, жест двойного тапа и переход к комментариям продублированы доступными
 * действиями.
 */
public class OutfitCardView extends LinearLayout {

  private final AspectRatioImageView photo;
  private final View photoContainer;
  private final ImageView heartBurst;
  private final TextView badge;
  private final AvatarView avatar;
  private final TextView author;
  private final TextView meta;
  private final LikeButton like;
  private final TextView caption;
  private final boolean showCaption;
  private final GestureDetector photoGestures;
  private final Rect photoBounds = new Rect();

  private boolean doubleTapToLike;
  private boolean touchOnPhoto;
  @Nullable private CharSequence authorName;
  @Nullable private Runnable openComments;
  private int likeActionId = View.NO_ID;
  private int commentsActionId = View.NO_ID;

  public OutfitCardView(@NonNull Context context) {
    this(context, null);
  }

  public OutfitCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsOutfitCardStyle);
  }

  public OutfitCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr, R.style.Widget_Ds_OutfitCard);
    setOrientation(VERTICAL);
    setClickable(true);
    setFocusable(true);
    setStateListAnimator(
        AnimatorInflater.loadStateListAnimator(context, R.animator.ds_press_scale));
    LayoutInflater.from(context).inflate(R.layout.ds_view_outfit_card, this, true);

    photo = findViewById(R.id.ds_outfit_photo);
    photoContainer = findViewById(R.id.ds_outfit_photo_container);
    heartBurst = findViewById(R.id.ds_outfit_heart_burst);
    badge = findViewById(R.id.ds_outfit_badge);
    avatar = findViewById(R.id.ds_outfit_avatar);
    author = findViewById(R.id.ds_outfit_author);
    meta = findViewById(R.id.ds_outfit_meta);
    like = findViewById(R.id.ds_outfit_like);
    caption = findViewById(R.id.ds_outfit_caption);

    boolean showAuthor;
    TypedArray a =
        context.obtainStyledAttributes(
            attrs, R.styleable.OutfitCardView, defStyleAttr, R.style.Widget_Ds_OutfitCard);
    try {
      showCaption = a.getBoolean(R.styleable.OutfitCardView_dsShowCaption, true);
      showAuthor = a.getBoolean(R.styleable.OutfitCardView_dsShowAuthor, true);
    } finally {
      a.recycle();
    }
    if (!showAuthor) {
      avatar.setVisibility(GONE);
      author.setVisibility(GONE);
      meta.setVisibility(GONE);
    }

    if (A11y.isLargeFontScale(context)) {
      // При крупном шрифте имя и мета переносятся, а не обрезаются до «Анна …».
      author.setMaxLines(2);
      meta.setMaxLines(2);
    }

    photoGestures = new GestureDetector(context, new PhotoGestureListener());
    like.setOnLikeChangeListener((button, liked) -> updateAccessibilityActions());
    updateAccessibilityActions();
  }

  /** Фото образа — для загрузчика изображений и shared element перехода. */
  @NonNull
  public AspectRatioImageView getPhotoView() {
    return photo;
  }

  @NonNull
  public AvatarView getAvatarView() {
    return avatar;
  }

  @NonNull
  public LikeButton getLikeButton() {
    return like;
  }

  /**
   * Автор образа.
   *
   * @param meta вторичная строка: «2 ч · 7 вещей», «Ремикс образа @anna»
   */
  public void setAuthor(long userId, @NonNull CharSequence name, @Nullable CharSequence meta) {
    authorName = name;
    avatar.setUser(userId, name);
    author.setText(name);
    this.meta.setText(meta);
    this.meta.setVisibility(TextUtils.isEmpty(meta) ? GONE : VISIBLE);
    updateDescription();
  }

  /** Подпись автора к образу (до двух строк). */
  public void setCaption(@Nullable CharSequence text) {
    caption.setText(text);
    caption.setVisibility(showCaption && !TextUtils.isEmpty(text) ? VISIBLE : GONE);
    updateDescription();
  }

  /** Бейдж поверх фото: «Новое», «Ремикс». {@code null} — скрыть. */
  public void setBadge(@Nullable CharSequence text) {
    badge.setText(text);
    badge.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    updateDescription();
  }

  /** Состояние лайка с сервера. */
  public void setLiked(boolean liked, long count) {
    like.setLiked(liked, count);
    updateAccessibilityActions();
  }

  /** Слушатель лайка — и кнопкой, и двойным тапом по фото. */
  public void setOnLikeChangeListener(@Nullable LikeButton.OnLikeChangeListener listener) {
    like.setOnLikeChangeListener(
        (button, liked) -> {
          updateAccessibilityActions();
          if (listener != null) {
            listener.onLikeChanged(button, liked);
          }
        });
  }

  /** Включает двойной тап по фото для лайка. */
  public void setDoubleTapToLike(boolean enabled) {
    doubleTapToLike = enabled;
  }

  /** Доступное действие «Комментарии» (кнопка комментариев есть на экране просмотра). */
  public void setOnOpenComments(@Nullable Runnable action) {
    openComments = action;
    updateAccessibilityActions();
  }

  /** transitionName фото для shared element перехода (см. {@code SharedElements}). */
  public void setSharedElementName(@Nullable String name) {
    ViewCompat.setTransitionName(photo, name);
  }

  /** Сбрасывает временные состояния перед переиспользованием в списке. */
  public void recycle() {
    HeartBurst.cancel(heartBurst);
    photo.setImageDrawable(null);
    setSharedElementName(null);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      photoContainer.getHitRect(photoBounds);
      touchOnPhoto =
          doubleTapToLike && photoBounds.contains((int) event.getX(), (int) event.getY());
    }
    return super.onInterceptTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!touchOnPhoto) {
      return super.onTouchEvent(event);
    }
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      setPressed(true);
    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      setPressed(false);
    }
    photoGestures.onTouchEvent(event);
    return true;
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  private void updateDescription() {
    StringBuilder text = new StringBuilder();
    if (authorName != null) {
      text.append(getResources().getString(R.string.ds_a11y_outfit_by, authorName));
    }
    appendSentence(text, badge.getVisibility() == VISIBLE ? badge.getText() : null);
    appendSentence(text, caption.getVisibility() == VISIBLE ? caption.getText() : null);
    appendSentence(text, meta.getVisibility() == VISIBLE ? meta.getText() : null);
    setContentDescription(text.toString());
  }

  private static void appendSentence(StringBuilder text, @Nullable CharSequence part) {
    if (TextUtils.isEmpty(part)) {
      return;
    }
    if (text.length() > 0) {
      text.append(". ");
    }
    text.append(part);
  }

  private void updateAccessibilityActions() {
    A11y.removeAction(this, likeActionId);
    A11y.removeAction(this, commentsActionId);
    CharSequence likeLabel =
        getResources()
            .getString(like.isChecked() ? R.string.ds_a11y_unlike : R.string.ds_a11y_like);
    likeActionId = A11y.addAction(this, likeLabel, like::performClick);
    commentsActionId =
        openComments == null
            ? View.NO_ID
            : A11y.addAction(
                this, getResources().getString(R.string.ds_a11y_open_comments), openComments);
  }

  private final class PhotoGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDown(@NonNull MotionEvent event) {
      return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
      performClick();
      return true;
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent event) {
      like.likeFromGesture();
      updateAccessibilityActions();
      HeartBurst.play(heartBurst);
      return true;
    }
  }
}
