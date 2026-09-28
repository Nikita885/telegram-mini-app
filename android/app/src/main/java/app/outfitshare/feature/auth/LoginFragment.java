package app.outfitshare.feature.auth;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.MainActivity;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.button.DsButton;
import app.outfitshare.core.designsystem.haptics.Haptics;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Wordmark;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * Sign in through the Telegram bot: the API issues a one-time nonce, the bot confirms it when the
 * user presses Start, and this screen polls until tokens are issued.
 */
public class LoginFragment extends BaseFragment {
  private static final long POLL_MS = 2000;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private DsButton telegram;
  private TextView hint;
  private View reopen;
  private OfflineBanner offline;
  @Nullable private Dto.LoginStart pending;
  private long pendingSince;
  private boolean polling;

  public LoginFragment() {
    super(R.layout.fragment_login);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    TextView wordmark = view.findViewById(R.id.wordmark);
    Wordmark.apply(wordmark);
    wordmark.setOnLongClickListener(
        v -> {
          askServer();
          return true;
        });
    telegram = view.findViewById(R.id.telegram);
    hint = view.findViewById(R.id.hint);
    reopen = view.findViewById(R.id.reopen);
    offline = view.findViewById(R.id.offline);
    offline.setOnRetryClickListener(v -> loadConfig());

    telegram.setOnClickListener(v -> start());
    reopen.setOnClickListener(v -> openBot());
    view.findViewById(R.id.dev).setOnClickListener(v -> askDevLogin());
    loadConfig();
  }

  private void loadConfig() {
    boolean online = container().api.isOnline();
    offline.setOffline(!online);
    offline.setVisibility(online ? View.GONE : View.VISIBLE);
    telegram.setEnabled(online);
    Calls.run(
        api().config(),
        r -> {
          if (!isAdded()) {
            return;
          }
          View dev = requireView().findViewById(R.id.dev);
          dev.setVisibility(r.ok() && r.data != null && r.data.devLogin ? View.VISIBLE : View.GONE);
        });
  }

  private void start() {
    setWaiting(true);
    Calls.run(
        api().telegramStart(),
        r -> {
          if (!isAdded()) {
            return;
          }
          if (!r.ok() || r.data == null) {
            setWaiting(false);
            showError(r.error, this::start);
            return;
          }
          pending = r.data;
          pendingSince = System.currentTimeMillis();
          openBot();
          startPolling();
        });
  }

  private void openBot() {
    if (pending == null) {
      return;
    }
    Uri web = Uri.parse(pending.botUrl);
    // Prefer the Telegram app (tg://resolve), fall back to the t.me link in a browser.
    String bot = web.getLastPathSegment();
    String start = web.getQueryParameter("start");
    Intent tg =
        new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + bot + "&start=" + start));
    try {
      startActivity(tg);
    } catch (ActivityNotFoundException e) {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, web));
      } catch (ActivityNotFoundException ignored) {
        toast("Установите Telegram, чтобы войти");
      }
    }
  }

  private void startPolling() {
    if (polling) {
      return;
    }
    polling = true;
    handler.postDelayed(this::poll, POLL_MS);
  }

  private void poll() {
    if (!isAdded() || pending == null) {
      polling = false;
      return;
    }
    if (System.currentTimeMillis() - pendingSince > pending.expiresIn * 1000L) {
      polling = false;
      pending = null;
      setWaiting(false);
      toast("Время на подтверждение вышло — нажмите ещё раз");
      return;
    }
    Map<String, Object> body = new HashMap<>();
    body.put("nonce", pending.nonce);
    Calls.run(
        api().telegramPoll(body),
        r -> {
          if (!isAdded()) {
            polling = false;
            return;
          }
          if (r.ok() && r.data != null && r.data.access != null) {
            polling = false;
            onTokens(r.data);
          } else if (r.error != null && r.error.kind == ApiError.Kind.NOT_FOUND) {
            polling = false;
            pending = null;
            setWaiting(false);
            showError(r.error, this::start);
          } else {
            handler.postDelayed(this::poll, POLL_MS);
          }
        });
  }

  private void onTokens(Dto.AuthResult result) {
    container().session.setTokens(result.access, result.refresh);
    Haptics.perform(requireView(), Haptics.Event.TOGGLE_ON);
    Calls.run(
        api().me(),
        r -> {
          if (r.ok() && r.data != null) {
            container().session.setMe(r.data);
          }
          if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onSignedIn();
          }
        });
  }

  private void setWaiting(boolean waiting) {
    telegram.setLoading(waiting);
    telegram.setText(waiting ? R.string.login_waiting : R.string.login_telegram);
    hint.setVisibility(waiting ? View.VISIBLE : View.GONE);
    reopen.setVisibility(waiting ? View.VISIBLE : View.GONE);
  }

  @Override
  public void onResume() {
    super.onResume();
    // Back from Telegram: check right away instead of waiting for the next tick.
    if (pending != null) {
      handler.removeCallbacksAndMessages(null);
      polling = true;
      poll();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    handler.removeCallbacksAndMessages(null);
    polling = false;
    if (pending != null) {
      // Keep polling in the background briefly: some launchers keep us paused while Telegram is on
      // top.
      polling = true;
      handler.postDelayed(this::poll, POLL_MS);
    }
  }

  private void askServer() {
    EditText input = new EditText(requireContext());
    input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
    input.setText(container().prefs.server());
    FrameLayout box = new FrameLayout(requireContext());
    int pad =
        getResources()
            .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    box.setPadding(pad, 0, pad, 0);
    box.addView(input);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.login_server)
        .setView(box)
        .setPositiveButton(
            R.string.action_done,
            (d, w) -> {
              container().changeServer(input.getText().toString());
              loadConfig();
            })
        .setNegativeButton(R.string.action_close, null)
        .show();
  }

  private void askDevLogin() {
    EditText input = new EditText(requireContext());
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    input.setHint("Telegram ID");
    FrameLayout box = new FrameLayout(requireContext());
    int pad =
        getResources()
            .getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    box.setPadding(pad, 0, pad, 0);
    box.addView(input);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.login_dev)
        .setView(box)
        .setPositiveButton(
            R.string.action_next,
            (d, w) -> {
              Map<String, Object> body = new HashMap<>();
              try {
                body.put("telegram_id", Long.parseLong(input.getText().toString().trim()));
              } catch (NumberFormatException e) {
                return;
              }
              Calls.run(
                  api().devLogin(body),
                  r -> {
                    if (r.ok() && r.data != null) {
                      onTokens(r.data);
                    } else {
                      showError(r.error, null);
                    }
                  });
            })
        .setNegativeButton(R.string.action_close, null)
        .show();
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }
}
