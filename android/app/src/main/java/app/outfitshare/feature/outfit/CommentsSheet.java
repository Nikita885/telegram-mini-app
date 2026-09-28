package app.outfitshare.feature.outfit;

import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.App;
import app.outfitshare.MainActivity;
import app.outfitshare.R;
import app.outfitshare.core.AppContainer;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.sheet.DsBottomSheetDialogFragment;
import app.outfitshare.core.designsystem.component.snackbar.DsSnackbar;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.profile.UserFragment;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mockup «Комментарии»: sheet over the outfit, replies indented, author badge, undo on delete. */
public class CommentsSheet extends DsBottomSheetDialogFragment {
  private static final String ARG_OUTFIT = "outfit";
  private static final String ARG_AUTHOR = "author";

  private final List<Dto.Comment> comments = new ArrayList<>();
  private Adapter adapter;
  private StateView stateView;
  private EditText field;
  private MaterialButton send;
  private TextView replying;
  @Nullable private Dto.Comment replyTo;
  private long outfitId;
  private long localIds = -1;

  public static void show(FragmentManager fm, long outfitId, long authorId) {
    CommentsSheet sheet = new CommentsSheet();
    Bundle args = new Bundle();
    args.putLong(ARG_OUTFIT, outfitId);
    args.putLong(ARG_AUTHOR, authorId);
    sheet.setArguments(args);
    sheet.show(fm, "comments");
  }

  private AppContainer c() {
    return App.get().container();
  }

  @Nullable
  @Override
  protected CharSequence getSheetTitle() {
    return getString(R.string.comments_title);
  }

  @NonNull
  @Override
  protected View onCreateSheetContent(
      @NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
    outfitId = requireArguments().getLong(ARG_OUTFIT);
    View v = inflater.inflate(R.layout.sheet_comments, container, false);
    stateView = v.findViewById(R.id.state);
    RecyclerView list = v.findViewById(R.id.list);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    adapter = new Adapter();
    list.setAdapter(adapter);

    AvatarView me = v.findViewById(R.id.composer_avatar);
    Images.avatar(me, c().session.currentMe());
    field = v.findViewById(R.id.composer_field);
    send = v.findViewById(R.id.composer_send);
    replying = v.findViewById(R.id.replying);
    replying.setOnClickListener(x -> setReplyTo(null));
    field.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void afterTextChanged(Editable s) {
            send.setEnabled(s.toString().trim().length() > 0);
          }
        });
    send.setOnClickListener(x -> submit());
    stateView.setOnActionClickListener(x -> load());
    stateView.setState(ScreenState.loading());
    load();
    return v;
  }

  private void updateTitle() {
    View root = getView();
    if (root == null) {
      return;
    }
    TextView title = root.findViewById(app.outfitshare.core.designsystem.R.id.ds_sheet_title);
    SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.comments_title));
    int start = sb.length();
    int count = 0;
    for (Dto.Comment cm : comments) {
      if (!cm.failed) {
        count++;
      }
    }
    sb.append("  ").append(String.valueOf(count));
    sb.setSpan(
        new ForegroundColorSpan(
            DsTheme.color(
                requireContext(),
                app.outfitshare.core.designsystem.R.attr.dsColorOnSurfaceVariant)),
        start,
        sb.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    sb.setSpan(
        new android.text.style.RelativeSizeSpan(0.6f),
        start,
        sb.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    title.setText(sb);
    c().outfitBus.commented(outfitId, count);
  }

  private void load() {
    Calls.run(
        c().api.api().comments(outfitId),
        r -> {
          if (!isAdded()) {
            return;
          }
          if (!r.ok() || r.data == null) {
            stateView.setState(States.failure(r.error, "Не загрузились"));
            return;
          }
          comments.clear();
          comments.addAll(threaded(r.data.results));
          adapter.notifyDataSetChanged();
          updateTitle();
          showEmptyIfNeeded();
        });
  }

  /** Replies right after their thread root, in time order. */
  private static List<Dto.Comment> threaded(List<Dto.Comment> flat) {
    List<Dto.Comment> out = new ArrayList<>();
    Map<Long, List<Dto.Comment>> replies = new HashMap<>();
    for (Dto.Comment cm : flat) {
      if (cm.parentId != null) {
        replies.computeIfAbsent(cm.parentId, k -> new ArrayList<>()).add(cm);
      }
    }
    for (Dto.Comment cm : flat) {
      if (cm.parentId == null) {
        out.add(cm);
        List<Dto.Comment> rs = replies.get(cm.id);
        if (rs != null) {
          out.addAll(rs);
        }
      }
    }
    return out;
  }

  private void showEmptyIfNeeded() {
    if (comments.isEmpty()) {
      stateView.setState(
          States.empty(
              app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_comments,
              "Пока тихо",
              "Напишите первым — автор увидит уведомление.",
              null));
    } else {
      stateView.setState(ScreenState.content());
    }
  }

  private void setReplyTo(@Nullable Dto.Comment comment) {
    replyTo = comment;
    if (comment == null) {
      replying.setVisibility(View.GONE);
      return;
    }
    replying.setText("Ответ " + Formats.handle(comment.author.username));
    replying.setVisibility(View.VISIBLE);
    field.requestFocus();
  }

  private void submit() {
    String text = field.getText().toString().trim();
    if (text.isEmpty()) {
      return;
    }
    Dto.Comment local = new Dto.Comment();
    local.id = localIds--;
    local.text = text;
    local.author = c().session.currentMe();
    local.createdAt = null;
    local.parentId =
        replyTo != null ? (replyTo.parentId != null ? replyTo.parentId : replyTo.id) : null;
    int insertAt = comments.size();
    if (local.parentId != null) {
      for (int i = 0; i < comments.size(); i++) {
        Dto.Comment cm = comments.get(i);
        if (cm.id == local.parentId
            || (cm.parentId != null && cm.parentId.equals(local.parentId))) {
          insertAt = i + 1;
        }
      }
    }
    comments.add(insertAt, local);
    adapter.notifyItemInserted(insertAt);
    field.setText("");
    setReplyTo(null);
    showEmptyIfNeeded();
    post(local);
  }

  private void post(Dto.Comment local) {
    Map<String, Object> body = new HashMap<>();
    body.put("text", local.text);
    if (local.parentId != null) {
      body.put("parent_id", local.parentId);
    }
    local.failed = false;
    Calls.run(
        c().api.api().addComment(outfitId, body),
        r -> {
          int index = comments.indexOf(local);
          if (index < 0) {
            return;
          }
          if (r.ok() && r.data != null) {
            comments.set(index, r.data);
          } else {
            local.failed = true;
          }
          adapter.notifyItemChanged(index);
          updateTitle();
        });
  }

  private void delete(Dto.Comment comment) {
    int index = comments.indexOf(comment);
    if (index < 0) {
      return;
    }
    // Removed from the list at once; the server delete happens when the undo window closes.
    List<Dto.Comment> removed = new ArrayList<>();
    removed.add(comment);
    for (Dto.Comment cm : new ArrayList<>(comments)) {
      if (cm.parentId != null && cm.parentId == comment.id) {
        removed.add(cm);
      }
    }
    comments.removeAll(removed);
    adapter.notifyDataSetChanged();
    updateTitle();
    showEmptyIfNeeded();
    DsSnackbar.undo(
        requireView(),
        "Комментарий удалён",
        () -> {
          comments.addAll(Math.min(index, comments.size()), removed);
          adapter.notifyDataSetChanged();
          updateTitle();
          showEmptyIfNeeded();
        },
        () -> Calls.run(c().api.api().deleteComment(comment.id), r -> {}));
  }

  private void toggleLike(Dto.Comment comment, int position) {
    boolean target = !comment.isLiked;
    comment.isLiked = target;
    comment.likesCount = Math.max(0, comment.likesCount + (target ? 1 : -1));
    adapter.notifyItemChanged(position);
    Calls.run(
        target ? c().api.api().likeComment(comment.id) : c().api.api().unlikeComment(comment.id),
        r -> {
          if (r.ok() && r.data != null) {
            comment.likesCount = r.data.likesCount;
          } else {
            comment.isLiked = !target;
            comment.likesCount = Math.max(0, comment.likesCount + (target ? -1 : 1));
          }
          int i = comments.indexOf(comment);
          if (i >= 0) {
            adapter.notifyItemChanged(i);
          }
        });
  }

  private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new Holder(
          LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
      Dto.Comment cm = comments.get(position);
      boolean reply = cm.parentId != null;
      int gutter =
          getResources()
              .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
      h.itemView.setPaddingRelative(
          reply ? getResources().getDimensionPixelSize(R.dimen.reply_indent) : gutter,
          h.itemView.getPaddingTop(),
          h.itemView.getPaddingEnd(),
          h.itemView.getPaddingBottom());
      h.avatar.setAvatarSize(reply ? 0 : 1);
      Images.avatar(h.avatar, cm.author);
      String handle =
          cm.author != null
              ? (cm.author.username != null && !cm.author.username.isEmpty()
                  ? cm.author.username
                  : cm.author.name)
              : "";
      SpannableStringBuilder head = new SpannableStringBuilder(handle);
      head.setSpan(
          new StyleSpan(android.graphics.Typeface.BOLD),
          0,
          handle.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      head.setSpan(
          new ForegroundColorSpan(
              DsTheme.color(
                  requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorOnSurface)),
          0,
          handle.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      head.append("  ").append(cm.createdAt == null ? "сейчас" : Formats.ago(cm.createdAt));
      h.head.setText(head);
      h.badge.setVisibility(cm.isPostAuthor ? View.VISIBLE : View.GONE);
      h.text.setText(cm.text);
      h.failed.setVisibility(cm.failed ? View.VISIBLE : View.GONE);
      h.failed.setOnClickListener(
          x -> {
            post(cm);
            notifyItemChanged(h.getBindingAdapterPosition());
          });
      boolean sent = cm.id > 0;
      h.reply.setVisibility(sent && !cm.failed ? View.VISIBLE : View.GONE);
      h.reply.setOnClickListener(x -> setReplyTo(cm));
      h.like.setVisibility(sent ? View.VISIBLE : View.INVISIBLE);
      h.like.setChecked(cm.isLiked);
      h.like.setText(cm.likesCount > 0 ? String.valueOf(cm.likesCount) : "");
      h.like.setContentDescription(cm.isLiked ? "Убрать отметку «Нравится»" : "Нравится");
      h.like.setOnClickListener(x -> toggleLike(cm, h.getBindingAdapterPosition()));
      h.avatar.setOnClickListener(x -> openAuthor(cm));
      h.itemView.setOnLongClickListener(
          x -> {
            if (cm.canDelete && sent) {
              delete(cm);
              return true;
            }
            return false;
          });
    }

    @Override
    public int getItemCount() {
      return comments.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
      final AvatarView avatar;
      final TextView head;
      final TextView badge;
      final TextView text;
      final TextView reply;
      final TextView failed;
      final MaterialButton like;

      Holder(View v) {
        super(v);
        avatar = v.findViewById(R.id.avatar);
        head = v.findViewById(R.id.head);
        badge = v.findViewById(R.id.author_badge);
        text = v.findViewById(R.id.text);
        reply = v.findViewById(R.id.reply);
        failed = v.findViewById(R.id.failed);
        like = v.findViewById(R.id.like);
      }
    }
  }

  private void openAuthor(Dto.Comment cm) {
    if (cm.author == null || !(getActivity() instanceof MainActivity)) {
      return;
    }
    dismiss();
    ((MainActivity) getActivity()).navigator().push(UserFragment.newInstance(cm.author.id));
  }
}
