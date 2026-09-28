package app.outfitshare.core.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import app.outfitshare.core.net.ApiClient;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.realtime.Realtime;

/** Unread notifications and messages for the navigation badges; refreshed by realtime events. */
public final class CountersStore {
  private final ApiClient client;
  private final MutableLiveData<Dto.Counters> counters = new MutableLiveData<>(new Dto.Counters());

  public CountersStore(ApiClient client, Realtime realtime) {
    this.client = client;
    realtime.addListener(
        (event, payload) -> {
          switch (event) {
            case "notification.new":
            case "message.new":
            case "message.deleted":
            case "dialog.read":
            case "ready":
              refresh();
              break;
            default:
              break;
          }
        });
  }

  public LiveData<Dto.Counters> counters() {
    return counters;
  }

  public void refresh() {
    Calls.run(
        client.api().counters(),
        r -> {
          if (r.ok() && r.data != null) {
            counters.setValue(r.data);
          }
        });
  }

  public void clearNotifications() {
    Dto.Counters c = copy();
    c.notifications = 0;
    counters.setValue(c);
  }

  private Dto.Counters copy() {
    Dto.Counters src = counters.getValue();
    Dto.Counters c = new Dto.Counters();
    if (src != null) {
      c.notifications = src.notifications;
      c.messages = src.messages;
    }
    return c;
  }
}
