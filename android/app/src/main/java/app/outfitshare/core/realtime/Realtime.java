package app.outfitshare.core.realtime;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.net.ApiClient;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.session.Session;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * One WebSocket per signed-in app (ws/v1/): messages, typing, notifications, Studio progress.
 * Reconnects with exponential backoff while the app is in the foreground.
 */
public final class Realtime {
  public interface Listener {
    void onEvent(String event, JsonObject payload);
  }

  private final ApiClient client;
  private final Session session;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
  @Nullable private WebSocket socket;
  private boolean wanted;
  private boolean connected;
  private long backoffMs = 1000;

  public Realtime(ApiClient client, Session session) {
    this.client = client;
    this.session = session;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean isConnected() {
    return connected;
  }

  public void start() {
    wanted = true;
    connect();
  }

  public void stop() {
    wanted = false;
    main.removeCallbacksAndMessages(null);
    if (socket != null) {
      socket.close(1000, "background");
      socket = null;
    }
    connected = false;
  }

  public void sendTyping(long dialogId) {
    WebSocket ws = socket;
    if (ws != null && connected) {
      JsonObject o = new JsonObject();
      o.addProperty("type", "typing");
      o.addProperty("dialog_id", dialogId);
      ws.send(o.toString());
    }
  }

  private void connect() {
    if (!wanted || socket != null || session.access() == null) {
      return;
    }
    String base = client.baseUrl().replaceFirst("^http", "ws");
    // The token goes in a header, never in the URL: URLs end up in proxy access logs.
    Request request =
        new Request.Builder()
            .url(base + "ws/v1/")
            .header("Authorization", "Bearer " + session.access())
            .build();
    socket = client.http().newWebSocket(request, new Callbacks());
  }

  /** Access token rejected: any authorized REST call refreshes it (ApiClient), then reconnect. */
  private void refreshThenReconnect() {
    socket = null;
    connected = false;
    if (!wanted) {
      return;
    }
    client
        .api()
        .counters()
        .enqueue(
            new Callback<Dto.Counters>() {
              @Override
              public void onResponse(
                  @NonNull Call<Dto.Counters> call,
                  @NonNull retrofit2.Response<Dto.Counters> response) {
                main.post(Realtime.this::scheduleReconnect);
              }

              @Override
              public void onFailure(@NonNull Call<Dto.Counters> call, @NonNull Throwable t) {
                main.post(Realtime.this::scheduleReconnect);
              }
            });
  }

  private void scheduleReconnect() {
    socket = null;
    connected = false;
    if (!wanted) {
      return;
    }
    long delay = backoffMs;
    backoffMs = Math.min(backoffMs * 2, 30_000);
    main.postDelayed(this::connect, delay);
  }

  private final class Callbacks extends WebSocketListener {
    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
      main.post(
          () -> {
            connected = true;
            backoffMs = 1000;
          });
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
      JsonObject root;
      try {
        root = JsonParser.parseString(text).getAsJsonObject();
      } catch (RuntimeException e) {
        return;
      }
      String event = root.has("event") ? root.get("event").getAsString() : "";
      JsonObject payload =
          root.has("payload") && root.get("payload").isJsonObject()
              ? root.getAsJsonObject("payload")
              : new JsonObject();
      main.post(
          () -> {
            for (Listener l : listeners) {
              l.onEvent(event, payload);
            }
          });
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
      main.post(
          () -> {
            if (code == 4401) {
              refreshThenReconnect();
              return;
            }
            scheduleReconnect();
          });
    }

    @Override
    public void onFailure(
        @NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
      main.post(Realtime.this::scheduleReconnect);
    }
  }
}
