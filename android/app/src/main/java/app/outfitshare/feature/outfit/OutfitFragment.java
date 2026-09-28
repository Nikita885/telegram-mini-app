package app.outfitshare.feature.outfit;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.dialog.ConfirmDialog;
import app.outfitshare.core.designsystem.component.like.LikeButton;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.motion.HeartBurst;
import app.outfitshare.core.designsystem.motion.SharedElements;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.OutfitActions;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.core.ui.States;
import app.outfitshare.core.ui.Text;
import app.outfitshare.feature.constructor.ConstructorFragment;
import app.outfitshare.feature.feed.OutfitAdapter;
import app.outfitshare.feature.messages.ShareToDialogSheet;
import app.outfitshare.feature.profile.UserFragment;
import app.outfitshare.feature.search.SearchFragment;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OutfitFragment extends BaseFragment {
  private static final String ARG_ID = "id";
  private static final String MORE = "outfit_more";

  private long outfitId;
  @Nullable private Dto.Outfit outfit;
  private StateView stateView;
  private ItemsAdapter itemsAdapter;
  private OutfitAdapter similarAdapter;

  public static OutfitFragment newInstance(long id) {
    OutfitFragment f = new OutfitFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_ID, id);
    f.setArguments(args);
    return f;
  }

  public OutfitFragment() {
    super(R.layout.fragment_outfit);
  }

  @Override
  public void onCreate(@Nullable Bundle state) {
    super.onCreate(state);
    outfitId = requireArguments().getLong(ARG_ID);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SharedElements.setUpDestination(this, R.id.root_container);
    view.findViewById(R.id.photo).setTransitionName(SharedElements.outfitPhoto(outfitId));
    SystemBars.applyPadding(view.findViewById(R.id.top_buttons), true, false);
    stateView = view.findViewById(R.id.state);
    stateView.setOnActionClickListener(v -> load());

    view.findViewById(R.id.back).setOnClickListener(v -> nav().back());
    view.findViewById(R.id.more).setOnClickListener(v -> showMore());

    RecyclerView items = view.findViewById(R.id.items);
    items.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
    itemsAdapter = new ItemsAdapter(getResources().getDimensionPixelSize(R.dimen.item_rail_width), true,
        item -> ItemSheet.show(getChildFragmentManager(), item));
    items.setAdapter(itemsAdapter);
    items.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets(@NonNull android.graphics.Rect out, @NonNull View child, @NonNull RecyclerView parent,
          @NonNull RecyclerView.State s) {
        if (parent.getChildAdapterPosition(child) > 0) {
          out.left = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_3);
        }
      }
    });

    RecyclerView similar = view.findViewById(R.id.similar);
    similar.setLayoutManager(new GridLayoutManager(requireContext(), 2));
    int gap = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_grid_gap);
    similar.addItemDecoration(Spacing.grid(gap, gap));
    similarAdapter = new OutfitAdapter(true, new OutfitAdapter.Listener() {
      @Override
      public void onOpen(Dto.Outfit o, View photo) {
        nav().push(OutfitFragment.newInstance(o.id));
      }

      @Override
      public void onAuthor(Dto.User author) {
        nav().push(UserFragment.newInstance(author.id));
      }

      @Override
      public void onComments(Dto.Outfit o) {
        CommentsSheet.show(getChildFragmentManager(), o.id, o.author.id);
      }

      @Override
      public void onTag(String tag) {
        nav().push(SearchFragment.forQuery("#" + tag));
      }
    });
    similar.setAdapter(similarAdapter);

    getChildFragmentManager().setFragmentResultListener(MORE, getViewLifecycleOwner(), (key, result) ->
        onMoreAction(result.getString(ActionSheet.RESULT_ID)));

    container().outfitBus.changes().observe(getViewLifecycleOwner(), c -> {
      if (outfit != null && c.outfitId == outfit.id && c.commentsCount != null) {
        outfit.commentsCount = c.commentsCount;
        ((MaterialButton) requireView().findViewById(R.id.comments)).setText(String.valueOf(c.commentsCount));
      }
    });

    stateView.setState(ScreenState.loading());
    load();
  }

  private void load() {
    Calls.run(api().outfit(outfitId), r -> {
      if (!isAdded()) {
        return;
      }
      if (!r.ok() || r.data == null) {
        stateView.setState(States.failure(r.error, "Образ не загрузился"));
        if (r.error != null && r.error.kind == app.outfitshare.core.net.ApiError.Kind.FORBIDDEN) {
          stateView.setState(States.locked("Образ недоступен", "Автор скрыл его или открыл только для подписчиков.", null));
        }
        return;
      }
      outfit = r.data;
      bind(r.data);
      stateView.setState(ScreenState.content());
      loadSimilar();
    });
  }

  private void bind(Dto.Outfit o) {
    View v = requireView();
    ImageView photo = v.findViewById(R.id.photo);
    Images.photo(photo, o.imageUrl);
    photo.setContentDescription("Образ " + (o.author != null ? o.author.name : ""));
    TextView badge = v.findViewById(R.id.items_badge);
    int n = o.items.size();
    badge.setText(getResources().getQuantityString(R.plurals.items_count, n, n));

    AvatarView avatar = v.findViewById(R.id.avatar);
    Images.avatar(avatar, o.author);
    ((TextView) v.findViewById(R.id.author_name)).setText(o.author.name);
    ((TextView) v.findViewById(R.id.author_handle)).setText(Formats.handle(o.author.username));
    v.findViewById(R.id.author_row).setOnClickListener(x -> nav().push(UserFragment.newInstance(o.author.id)));

    MaterialButton follow = v.findViewById(R.id.follow);
    follow.setVisibility(o.isMine ? View.GONE : View.VISIBLE);
    bindFollow(follow, o.authorFollowed);
    follow.setOnClickListener(x -> toggleFollow(follow));

    LikeButton like = v.findViewById(R.id.like);
    like.setLiked(o.isLiked, o.likesCount);
    like.setOnLikeChangeListener((b, liked) -> OutfitActions.setLiked(v, o, liked));

    // Double tap on the photo likes, with the big heart (mockup state «Двойной тап: лайк»).
    ImageView heart = v.findViewById(R.id.heart);
    GestureDetector detector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDoubleTap(@NonNull MotionEvent e) {
        HeartBurst.play(heart);
        Haptics.perform(heart, Haptics.Event.LIKE);
        if (!o.isLiked) {
          like.likeFromGesture();
        }
        return true;
      }

      @Override
      public boolean onDown(@NonNull MotionEvent e) {
        return true;
      }
    });
    photo.setOnTouchListener((pv, ev) -> {
      detector.onTouchEvent(ev);
      if (ev.getAction() == MotionEvent.ACTION_UP) {
        pv.performClick();
      }
      return true;
    });

    MaterialButton comments = v.findViewById(R.id.comments);
    comments.setText(String.valueOf(o.commentsCount));
    comments.setOnClickListener(x -> CommentsSheet.show(getChildFragmentManager(), o.id, o.author.id));
    MaterialButton remix = v.findViewById(R.id.remix);
    remix.setText("");
    remix.setOnClickListener(x -> remix(o));
    MaterialButton save = v.findViewById(R.id.save);
    bindSaved(save, o.isSaved);
    save.setOnClickListener(x -> OutfitActions.toggleSaved(v, o, () -> bindSaved(save, o.isSaved)));
    v.findViewById(R.id.share).setOnClickListener(x -> OutfitActions.share(requireContext(), o));

    TextView caption = v.findViewById(R.id.caption);
    caption.setText(Text.caption(requireContext(), o.description, o.hashtags,
        tag -> nav().push(SearchFragment.forQuery("#" + tag))));
    caption.setMovementMethod(LinkMovementMethod.getInstance());
    caption.setVisibility(caption.length() == 0 ? View.GONE : View.VISIBLE);
    String date = Formats.dayMonth(o.createdAt);
    if (o.remixOf != null && o.remixOf.author != null) {
      date += " · ремикс образа " + Formats.handle(o.remixOf.author.username);
    }
    ((TextView) v.findViewById(R.id.date)).setText(date);

    ((TextView) v.findViewById(R.id.items_count)).setText(n > 0 ? "Все " + n : "");
    v.findViewById(R.id.items_section).setVisibility(n > 0 ? View.VISIBLE : View.GONE);
    itemsAdapter.submit(o.items);
  }

  private void bindFollow(MaterialButton follow, boolean following) {
    follow.setText(following ? R.string.action_following : R.string.action_follow);
    follow.setIconResource(following ? app.outfitshare.core.designsystem.R.drawable.ds_ic_check : 0);
  }

  private void bindSaved(MaterialButton save, boolean saved) {
    save.setIconResource(saved ? app.outfitshare.core.designsystem.R.drawable.ds_ic_bookmark_filled
        : app.outfitshare.core.designsystem.R.drawable.ds_ic_bookmark);
    save.setContentDescription(saved ? "Убрать из сохранённого" : getString(R.string.action_save));
  }

  private void toggleFollow(MaterialButton follow) {
    if (outfit == null) {
      return;
    }
    boolean target = !outfit.authorFollowed;
    outfit.authorFollowed = target;
    bindFollow(follow, target);
    Calls.run(target ? api().follow(outfit.author.id) : api().unfollow(outfit.author.id), r -> {
      if (!r.ok() && outfit != null) {
        outfit.authorFollowed = !target;
        bindFollow(follow, !target);
        showError(r.error, null);
      }
    });
  }

  private void loadSimilar() {
    Calls.run(api().similarOutfits(outfitId), r -> {
      if (!isAdded() || !r.ok() || r.data == null) {
        return;
      }
      similarAdapter.submit(r.data.results);
      requireView().findViewById(R.id.similar_section).setVisibility(r.data.results.isEmpty() ? View.GONE : View.VISIBLE);
    });
  }

  private void remix(Dto.Outfit o) {
    nav().present(ConstructorFragment.remix(o));
  }

  private void showMore() {
    if (outfit == null) {
      return;
    }
    ArrayList<ActionSheet.Row> rows = new ArrayList<>();
    int dr = 0;
    rows.add(new ActionSheet.Row("save", app.outfitshare.core.designsystem.R.drawable.ds_ic_bookmark,
        outfit.isSaved ? "Убрать из сохранённого" : "Сохранить в коллекцию", null, false));
    rows.add(new ActionSheet.Row("remix", app.outfitshare.core.designsystem.R.drawable.ds_ic_remix,
        "Сделать ремикс", "Откроется конструктор с этими вещами", false));
    rows.add(new ActionSheet.Row("link", app.outfitshare.core.designsystem.R.drawable.ds_ic_link, "Скопировать ссылку", null, false));
    rows.add(new ActionSheet.Row("send", app.outfitshare.core.designsystem.R.drawable.ds_ic_share, "Отправить в диалог", null, false));
    if (outfit.isMine) {
      rows.add(new ActionSheet.Row("delete", app.outfitshare.core.designsystem.R.drawable.ds_ic_delete, "Удалить образ", null, true));
    } else {
      rows.add(new ActionSheet.Row("report", app.outfitshare.core.designsystem.R.drawable.ds_ic_flag, "Пожаловаться", null, true));
    }
    ActionSheet.show(getChildFragmentManager(), MORE, null, rows);
  }

  private void onMoreAction(@Nullable String id) {
    if (outfit == null || id == null) {
      return;
    }
    View v = requireView();
    switch (id) {
      case "save":
        OutfitActions.toggleSaved(v, outfit, () -> bindSaved(v.findViewById(R.id.save), outfit.isSaved));
        break;
      case "remix":
        remix(outfit);
        break;
      case "link":
        ClipboardManager cm = requireContext().getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("Outfit Share", OutfitActions.link(outfit.id)));
        toast("Ссылка скопирована");
        break;
      case "send":
        ShareToDialogSheet.show(getChildFragmentManager(), outfit.id);
        break;
      case "delete":
        ConfirmDialog.with(requireContext())
            .title("Удалить образ?")
            .message("Образ исчезнет из ленты и профиля. Это действие нельзя отменить.")
            .confirm("Удалить")
            .cancel("Отмена")
            .destructive()
            .onConfirm(this::delete)
            .show();
        break;
      case "report":
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", "post");
        body.put("target_id", outfit.id);
        body.put("reason", "Жалоба из приложения");
        Calls.run(api().report(body), r -> toast(r.ok() ? "Спасибо, модераторы посмотрят" : "Не удалось отправить жалобу"));
        break;
      default:
        break;
    }
  }

  private void delete() {
    Calls.run(api().deleteOutfit(outfitId), r -> {
      if (r.ok()) {
        container().outfitBus.deleted(outfitId);
        nav().back();
      } else {
        showError(r.error, this::delete);
      }
    });
  }
}
