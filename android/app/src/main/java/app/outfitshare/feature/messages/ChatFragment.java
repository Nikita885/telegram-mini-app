package app.outfitshare.feature.messages;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.realtime.Realtime;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Formats;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.profile.UserFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mockup «Чат»: incoming on a tonal surface, outgoing in primary, shared outfits as cards. */
public class ChatFragment extends BaseFragment {
  private static final String ARG_DIALOG = "dialog";
  private static final String ARG_USER = "user";
  private static final String MESSAGE_ACTIONS = "message_actions";

  private final List<Dto.Message> messages = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Adapter adapter;
  private RecyclerView list;
  private LinearLayoutManager lm;
  private StateView stateView;
  private EditText field;
  private MaterialButton send;
  private TextView status;
  private long dialogId;
  private Dto.User peer;
  @Nullable private String olderCursor;
  private boolean loadingOlder;
  private long lastTypingSent;
  private long localIds = -1;
  @Nullable private Dto.Message actionTarget;

  private final Realtime.Listener realtime =
      (event, payload) -> {
        long d = payload.has("dialog_id") ? payload.get("dialog_id").getAsLong() : 0;
        if (d != dialogId || dialogId == 0) {
          return;
        }
        switch (event) {
          case "message.new":
            if (payload.get("sender_id").getAsLong() != myId()) {
              fetchNewer();
            }
            break;
          case "message.edited":
            long id = payload.get("id").getAsLong();
            for (int i = 0; i < messages.size(); i++) {
              if (messages.get(i).id == id) {
                messages.get(i).text = payload.get("text").getAsString();
                messages.get(i).edited = true;
                adapter.notifyItemChanged(i);
              }
            }
            break;
          case "message.deleted":
            long removed = payload.get("id").getAsLong();
            for (int i = 0; i < messages.size(); i++) {
              if (messages.get(i).id == removed) {
                messages.remove(i);
                adapter.notifyItemRemoved(i);
                break;
              }
            }
            break;
          case "dialog.read":
            for (Dto.Message m : messages) {
              if (m.isMine) {
                m.isRead = true;
              }
            }
            adapter.notifyDataSetChanged();
            break;
          case "typing":
            showTyping();
            break;
          default:
            break;
        }
      };

  public static ChatFragment forDialog(Dto.Dialog dialog) {
    ChatFragment f = new ChatFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_DIALOG, dialog.id);
    args.putString(ARG_USER, app.outfitshare.App.get().container().gson.toJson(dialog.user));
    f.setArguments(args);
    return f;
  }

  public static ChatFragment withUser(Dto.User user) {
    ChatFragment f = new ChatFragment();
    Bundle args = new Bundle();
    args.putString(
        ARG_USER, app.outfitshare.App.get().container().gson.toJson(user, Dto.User.class));
    f.setArguments(args);
    return f;
  }

  public ChatFragment() {
    super(R.layout.fragment_chat);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    Bundle args = requireArguments();
    dialogId = args.getLong(ARG_DIALOG, 0);
    peer = container().gson.fromJson(args.getString(ARG_USER), Dto.User.class);

    view.findViewById(R.id.back).setOnClickListener(v -> nav().back());
    AvatarView avatar = view.findViewById(R.id.avatar);
    Images.avatar(avatar, peer);
    ((TextView) view.findViewById(R.id.name)).setText(peer.name);
    status = view.findViewById(R.id.status);
    status.setText(Formats.handle(peer.username));
    view.findViewById(R.id.peer)
        .setOnClickListener(v -> nav().push(UserFragment.newInstance(peer.id)));
    view.findViewById(R.id.more)
        .setOnClickListener(v -> nav().push(UserFragment.newInstance(peer.id)));
    OfflineBanner offline = view.findViewById(R.id.offline);
    boolean online = container().api.isOnline();
    offline.setOffline(!online);
    offline.setVisibility(online ? View.GONE : View.VISIBLE);

    stateView = view.findViewById(R.id.state);
    stateView.setOnActionClickListener(v -> loadInitial());
    list = view.findViewById(R.id.messages);
    lm = new LinearLayoutManager(requireContext());
    lm.setStackFromEnd(true);
    list.setLayoutManager(lm);
    adapter = new Adapter();
    list.setAdapter(adapter);
    list.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            if (lm.findFirstVisibleItemPosition() <= 3) {
              loadOlder();
            }
          }
        });

    // Composer: hanger button shares one of my outfits, avatar is hidden in chats.
    view.findViewById(R.id.composer_avatar).setVisibility(View.GONE);
    View attach = view.findViewById(R.id.composer_attach);
    attach.setVisibility(View.VISIBLE);
    attach.setOnClickListener(v -> MyOutfitsSheet.show(getChildFragmentManager()));
    getChildFragmentManager()
        .setFragmentResultListener(
            MyOutfitsSheet.RESULT,
            getViewLifecycleOwner(),
            (k, r) -> sendOutfit(r.getLong(MyOutfitsSheet.RESULT_ID)));
    getChildFragmentManager()
        .setFragmentResultListener(
            MESSAGE_ACTIONS,
            getViewLifecycleOwner(),
            (k, r) -> onMessageAction(r.getString(ActionSheet.RESULT_ID)));
    field = view.findViewById(R.id.composer_field);
    field.setHint(R.string.chat_hint);
    send = view.findViewById(R.id.composer_send);
    field.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void afterTextChanged(Editable s) {
            send.setEnabled(s.toString().trim().length() > 0);
            long now = System.currentTimeMillis();
            if (dialogId != 0 && s.length() > 0 && now - lastTypingSent > 3000) {
              lastTypingSent = now;
              container().realtime.sendTyping(dialogId);
            }
          }
        });
    send.setOnClickListener(v -> sendText());

    if (dialogId == 0) {
      showNewDialog();
    } else {
      stateView.setState(ScreenState.loading());
      loadInitial();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    container().realtime.addListener(realtime);
  }

  @Override
  public void onStop() {
    container().realtime.removeListener(realtime);
    super.onStop();
  }

  // ── Loading ──────────────────────────────────────────────────────────────

  private void loadInitial() {
    Calls.run(
        api().messages(dialogId, null, null),
        r -> {
          if (!isAdded()) {
            return;
          }
          if (!r.ok() || r.data == null) {
            stateView.setState(States.failure(r.error, "Сообщения не загрузились"));
            return;
          }
          messages.clear();
          List<Dto.Message> page = new ArrayList<>(r.data.results);
          java.util.Collections.reverse(page);
          messages.addAll(page);
          olderCursor = r.data.next;
          adapter.notifyDataSetChanged();
          list.scrollToPosition(Math.max(0, messages.size() - 1));
          stateView.setState(ScreenState.content());
          if (messages.isEmpty()) {
            showNewDialog();
          }
          markRead();
        });
  }

  private void loadOlder() {
    if (loadingOlder || olderCursor == null || dialogId == 0) {
      return;
    }
    loadingOlder = true;
    Calls.run(
        api().messages(dialogId, olderCursor, null),
        r -> {
          loadingOlder = false;
          if (!isAdded() || !r.ok() || r.data == null) {
            return;
          }
          List<Dto.Message> page = new ArrayList<>(r.data.results);
          java.util.Collections.reverse(page);
          messages.addAll(0, page);
          olderCursor = r.data.next;
          adapter.notifyItemRangeInserted(0, page.size());
        });
  }

  private void fetchNewer() {
    long last = 0;
    for (Dto.Message m : messages) {
      last = Math.max(last, m.id);
    }
    Calls.run(
        api().messages(dialogId, null, String.valueOf(last)),
        r -> {
          if (!isAdded() || !r.ok() || r.data == null) {
            return;
          }
          for (Dto.Message m : r.data.results) {
            if (!contains(m.id)) {
              messages.add(m);
              adapter.notifyItemInserted(messages.size() - 1);
            }
          }
          hideTyping();
          list.smoothScrollToPosition(Math.max(0, messages.size() - 1));
          markRead();
        });
  }

  private boolean contains(long id) {
    for (Dto.Message m : messages) {
      if (m.id == id) {
        return true;
      }
    }
    return false;
  }

  private void markRead() {
    if (dialogId != 0) {
      Calls.run(api().readDialog(dialogId), r -> container().counters.refresh());
    }
  }

  // ── Sending ──────────────────────────────────────────────────────────────

  private void sendText() {
    String text = field.getText().toString().trim();
    if (text.isEmpty()) {
      return;
    }
    field.setText("");
    Dto.Message local = localMessage();
    local.text = text;
    enqueue(local);
  }

  private void sendOutfit(long outfitId) {
    Dto.Message local = localMessage();
    local.text = "";
    local.outfit = new Dto.OutfitPreview();
    local.outfit.id = outfitId;
    enqueue(local);
  }

  private Dto.Message localMessage() {
    Dto.Message m = new Dto.Message();
    m.localId = localIds--;
    m.id = m.localId;
    m.isMine = true;
    m.pending = true;
    m.senderId = myId();
    m.createdAt = java.time.OffsetDateTime.now().toString();
    return m;
  }

  private void enqueue(Dto.Message local) {
    requireView().findViewById(R.id.suggestions_box).setVisibility(View.GONE);
    stateView.setState(ScreenState.content());
    messages.add(local);
    adapter.notifyItemInserted(messages.size() - 1);
    list.scrollToPosition(messages.size() - 1);
    if (dialogId == 0) {
      Map<String, Object> body = new HashMap<>();
      body.put("user_id", peer.id);
      Calls.run(
          api().openDialog(body),
          r -> {
            if (r.ok() && r.data != null) {
              dialogId = r.data.id;
              post(local);
            } else {
              fail(local);
            }
          });
    } else {
      post(local);
    }
  }

  private void post(Dto.Message local) {
    Map<String, Object> body = new HashMap<>();
    if (local.outfit != null) {
      body.put("outfit_id", local.outfit.id);
    } else {
      body.put("text", local.text);
    }
    local.pending = true;
    local.failed = false;
    Calls.run(
        api().sendMessage(dialogId, body),
        r -> {
          int i = messages.indexOf(local);
          if (i < 0 || !isAdded()) {
            return;
          }
          if (r.ok() && r.data != null) {
            messages.set(i, r.data);
            adapter.notifyItemChanged(i);
          } else {
            fail(local);
          }
        });
  }

  private void fail(Dto.Message local) {
    local.pending = false;
    local.failed = true;
    int i = messages.indexOf(local);
    if (i >= 0) {
      adapter.notifyItemChanged(i);
    }
  }

  // ── Typing / new dialog ──────────────────────────────────────────────────

  private void showTyping() {
    SpannableString s = new SpannableString(getString(R.string.chat_typing));
    s.setSpan(
        new ForegroundColorSpan(
            DsTheme.color(
                requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorAccent)),
        0,
        s.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    status.setText(s);
    handler.removeCallbacksAndMessages(null);
    handler.postDelayed(this::hideTyping, 4000);
  }

  private void hideTyping() {
    if (status != null) {
      status.setText(Formats.handle(peer.username));
    }
  }

  private void showNewDialog() {
    stateView.setState(
        ScreenState.builder(ScreenState.Kind.EMPTY)
            .title(peer.name)
            .message(Formats.handle(peer.username))
            .build());
    View box = requireView().findViewById(R.id.suggestions_box);
    ChipGroup group = requireView().findViewById(R.id.suggestions);
    group.removeAllViews();
    Chip hello = new Chip(requireContext());
    hello.setText("👋 Привет!");
    hello.setCheckable(false);
    hello.setOnClickListener(
        v -> {
          field.setText("Привет!");
          sendText();
        });
    Chip share = new Chip(requireContext());
    share.setText(R.string.action_share_outfit);
    share.setCheckable(false);
    share.setChipIconResource(app.outfitshare.core.designsystem.R.drawable.ds_ic_hanger);
    share.setChipIconVisible(true);
    share.setOnClickListener(v -> MyOutfitsSheet.show(getChildFragmentManager()));
    group.addView(hello);
    group.addView(share);
    box.setVisibility(View.VISIBLE);
  }

  // ── Actions on a message ─────────────────────────────────────────────────

  private void showActions(Dto.Message m) {
    actionTarget = m;
    ArrayList<ActionSheet.Row> rows = new ArrayList<>();
    if (m.text != null && !m.text.isEmpty()) {
      rows.add(
          new ActionSheet.Row(
              "copy",
              app.outfitshare.core.designsystem.R.drawable.ds_ic_copy,
              "Скопировать",
              null,
              false));
    }
    if (m.isMine && m.id > 0) {
      if (m.outfit == null) {
        rows.add(
            new ActionSheet.Row(
                "edit",
                app.outfitshare.core.designsystem.R.drawable.ds_ic_edit,
                "Изменить",
                null,
                false));
      }
      rows.add(
          new ActionSheet.Row(
              "delete",
              app.outfitshare.core.designsystem.R.drawable.ds_ic_delete,
              "Удалить",
              null,
              true));
    }
    if (!rows.isEmpty()) {
      ActionSheet.show(getChildFragmentManager(), MESSAGE_ACTIONS, null, rows);
    }
  }

  private void onMessageAction(@Nullable String id) {
    Dto.Message m = actionTarget;
    if (m == null || id == null) {
      return;
    }
    switch (id) {
      case "copy":
        ClipboardManager cm = requireContext().getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("message", m.text));
        toast("Скопировано");
        break;
      case "edit":
        EditText input = new EditText(requireContext());
        input.setInputType(
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(m.text);
        FrameLayout box = new FrameLayout(requireContext());
        int pad =
            getResources()
                .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Изменить сообщение")
            .setView(box)
            .setPositiveButton(
                R.string.action_done,
                (d, w) -> {
                  Map<String, Object> body = new HashMap<>();
                  body.put("text", input.getText().toString());
                  Calls.run(
                      api().editMessage(m.id, body),
                      r -> {
                        if (r.ok() && r.data != null) {
                          m.text = r.data.text;
                          m.edited = true;
                          adapter.notifyItemChanged(messages.indexOf(m));
                        } else {
                          showError(r.error, null);
                        }
                      });
                })
            .setNegativeButton(R.string.action_close, null)
            .show();
        break;
      case "delete":
        int i = messages.indexOf(m);
        messages.remove(m);
        adapter.notifyItemRemoved(i);
        Calls.run(
            api().deleteMessage(m.id),
            r -> {
              if (!r.ok()) {
                messages.add(Math.min(i, messages.size()), m);
                adapter.notifyDataSetChanged();
                showError(r.error, null);
              }
            });
        break;
      default:
        break;
    }
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }

  // ── Adapter ──────────────────────────────────────────────────────────────

  private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    @Override
    public int getItemViewType(int position) {
      return messages.get(position).isMine ? 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      int layout = viewType == 1 ? R.layout.item_message_out : R.layout.item_message_in;
      return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
      Dto.Message m = messages.get(position);
      boolean newDay =
          position == 0 || !Formats.sameDay(messages.get(position - 1).createdAt, m.createdAt);
      h.day.setVisibility(newDay ? View.VISIBLE : View.GONE);
      h.day.setText(Formats.daySeparator(m.createdAt));

      boolean hasText = m.text != null && !m.text.isEmpty();
      h.text.setVisibility(hasText ? View.VISIBLE : View.GONE);
      h.text.setText(m.text);
      String time = Formats.time(m.createdAt) + (m.edited ? " · изм." : "");
      h.time.setText(time);
      if (m.isMine) {
        int icon =
            m.pending ? R.drawable.badge_schedule : (m.isRead ? R.drawable.badge_done_all : 0);
        h.time.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, m.failed ? 0 : icon, 0);
        if (h.failed != null) {
          h.failed.setVisibility(m.failed ? View.VISIBLE : View.GONE);
          h.failed.setOnClickListener(v -> post(m));
        }
      }

      if (m.outfit != null) {
        h.card.setVisibility(View.VISIBLE);
        Images.photo(h.cardPhoto, m.outfit.imageUrl);
        h.cardTitle.setText(
            m.outfit.description == null || m.outfit.description.isEmpty()
                ? "Образ"
                : m.outfit.description);
        h.cardSub.setText(
            m.outfit.author != null ? "Образ " + Formats.handle(m.outfit.author.username) : "");
        int color =
            DsTheme.color(
                requireContext(),
                m.isMine
                    ? app.outfitshare.core.designsystem.R.attr.dsColorOnPrimary
                    : app.outfitshare.core.designsystem.R.attr.dsColorOnSurface);
        h.cardTitle.setTextColor(color);
        h.cardSub.setTextColor(color);
        h.card.setOnClickListener(v -> nav().push(OutfitFragment.newInstance(m.outfit.id)));
        int pad =
            getResources()
                .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_1);
        h.bubble.setPadding(
            pad,
            pad,
            pad,
            getResources()
                .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_2));
      } else {
        h.card.setVisibility(View.GONE);
        int hp =
            getResources()
                .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_3);
        int vp =
            getResources()
                .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_2);
        h.bubble.setPadding(hp, vp, hp, vp);
      }
      h.bubble.setOnLongClickListener(
          v -> {
            showActions(m);
            return true;
          });
      h.bubble.setContentDescription(
          (m.isMine ? "Вы: " : peer.name + ": ") + (hasText ? m.text : "образ") + ", " + time);
    }

    @Override
    public int getItemCount() {
      return messages.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
      final TextView day;
      final View bubble;
      final TextView text;
      final TextView time;
      @Nullable final TextView failed;
      final View card;
      final ImageView cardPhoto;
      final TextView cardTitle;
      final TextView cardSub;

      Holder(View v) {
        super(v);
        day = v.findViewById(R.id.day);
        bubble = v.findViewById(R.id.bubble);
        text = v.findViewById(R.id.text);
        time = v.findViewById(R.id.time);
        failed = v.findViewById(R.id.failed);
        card = v.findViewById(R.id.card);
        cardPhoto = v.findViewById(R.id.card_photo);
        cardTitle = v.findViewById(R.id.card_title);
        cardSub = v.findViewById(R.id.card_sub);
      }
    }
  }
}
