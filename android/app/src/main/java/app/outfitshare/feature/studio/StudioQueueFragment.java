package app.outfitshare.feature.studio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.realtime.Realtime;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.States;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mockup «Студия: очередь»: uploads with %, pipeline steps, ready with fit score, errors with action. */
public class StudioQueueFragment extends BaseFragment {
  private static final String[] GROUPS = {"active", "review", "failed"};
  private static final long POLL_MS = 2500;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<Object> rows = new ArrayList<>();
  private final List<WorkInfo> uploads = new ArrayList<>();
  private final List<Dto.GarmentJob> jobs = new ArrayList<>();
  private Adapter adapter;
  private StateView stateView;
  private SwipeRefreshLayout refresh;
  private TabLayout tabs;
  private int tab;

  private final Realtime.Listener realtime = (event, payload) -> {
    if ("studio.job".equals(event)) {
      load();
    }
  };

  public StudioQueueFragment() {
    super(R.layout.fragment_studio_queue);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> nav().back());
    stateView = view.findViewById(R.id.state);
    refresh = view.findViewById(R.id.refresh);
    OfflineBanner offline = view.findViewById(R.id.offline);
    boolean online = container().api.isOnline();
    offline.setOffline(!online);
    offline.setVisibility(online ? View.GONE : View.VISIBLE);

    if (!container().session.isAdmin()) {
      stateView.setState(States.locked("Студия — для администраторов каталога",
          "Здесь фотографируют и публикуют вещи. Если вы стилист или магазин — напишите нам.", null));
      view.findViewById(R.id.shoot).setVisibility(View.GONE);
      return;
    }

    RecyclerView list = view.findViewById(R.id.list);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    adapter = new Adapter();
    list.setAdapter(adapter);
    refresh.setOnRefreshListener(this::load);
    stateView.setOnActionClickListener(v -> nav().present(new StudioCameraFragment()));
    view.findViewById(R.id.shoot).setOnClickListener(v -> nav().present(new StudioCameraFragment()));

    tabs = view.findViewById(R.id.tabs);
    tabs.addTab(tabs.newTab().setText(R.string.studio_tab_active));
    tabs.addTab(tabs.newTab().setText(R.string.studio_tab_ready));
    tabs.addTab(tabs.newTab().setText(R.string.studio_tab_failed));
    tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
      @Override
      public void onTabSelected(TabLayout.Tab t) {
        tab = t.getPosition();
        stateView.setState(ScreenState.loading());
        load();
      }

      @Override
      public void onTabUnselected(TabLayout.Tab t) {}

      @Override
      public void onTabReselected(TabLayout.Tab t) {}
    });

    WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData(UploadWorker.TAG).observe(getViewLifecycleOwner(), infos -> {
      uploads.clear();
      for (WorkInfo i : infos) {
        if (!i.getState().isFinished()) {
          uploads.add(i);
        } else if (i.getState() == WorkInfo.State.SUCCEEDED) {
          load();
        }
      }
      rebuild();
    });

    stateView.setState(ScreenState.loading());
    load();
  }

  @Override
  public void onStart() {
    super.onStart();
    container().realtime.addListener(realtime);
    handler.postDelayed(this::poll, POLL_MS);
  }

  @Override
  public void onStop() {
    container().realtime.removeListener(realtime);
    handler.removeCallbacksAndMessages(null);
    super.onStop();
  }

  /** While something is processing the list refreshes itself (WebSocket may be down). */
  private void poll() {
    boolean busy = false;
    for (Dto.GarmentJob j : jobs) {
      if ("queued".equals(j.status) || "processing".equals(j.status)) {
        busy = true;
      }
    }
    if (busy && isResumed()) {
      load();
    }
    handler.postDelayed(this::poll, POLL_MS);
  }

  private void load() {
    if (tabs == null) {
      return;
    }
    Calls.run(api().jobs(GROUPS[tab]), r -> {
      if (!isAdded()) {
        return;
      }
      refresh.setRefreshing(false);
      if (!r.ok() || r.data == null) {
        if (jobs.isEmpty() && uploads.isEmpty()) {
          stateView.setState(States.failure(r.error, "Очередь не загрузилась"));
        }
        return;
      }
      jobs.clear();
      jobs.addAll(r.data.results);
      if (r.data.totals != null) {
        setCount(0, getString(R.string.studio_tab_active), total(r.data.totals, "active") + uploads.size());
        setCount(1, getString(R.string.studio_tab_ready), total(r.data.totals, "review"));
        setCount(2, getString(R.string.studio_tab_failed), total(r.data.totals, "failed"));
      }
      rebuild();
    });
  }

  private static int total(Map<String, Integer> totals, String key) {
    Integer v = totals.get(key);
    return v == null ? 0 : v;
  }

  private void setCount(int index, String label, int count) {
    TabLayout.Tab t = tabs.getTabAt(index);
    if (t != null) {
      t.setText(label + "  " + count);
    }
  }

  private void rebuild() {
    if (adapter == null) {
      return;
    }
    rows.clear();
    if (tab == 0) {
      rows.addAll(uploads);
    }
    rows.addAll(jobs);
    adapter.notifyDataSetChanged();
    if (rows.isEmpty()) {
      stateView.setState(States.empty(app.outfitshare.core.designsystem.R.drawable.ds_illustration_camera,
          tab == 2 ? "Ошибок нет" : "Очередь пуста",
          tab == 2 ? null : "Сфотографируйте вещь — через пару минут она сядет на манекен.",
          tab == 2 ? null : "Открыть камеру"));
    } else {
      stateView.setState(ScreenState.content());
    }
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }

  private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_job, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
      Object o = rows.get(position);
      h.progress.setVisibility(View.GONE);
      h.steps.setVisibility(View.GONE);
      h.chevron.setVisibility(View.GONE);
      h.action.setVisibility(View.GONE);
      h.status.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
      h.status.setTextColor(DsTheme.color(requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorOnSurfaceVariant));
      h.itemView.setOnClickListener(null);
      if (o instanceof WorkInfo) {
        bindUpload(h, (WorkInfo) o);
      } else {
        bindJob(h, (Dto.GarmentJob) o);
      }
    }

    private void bindUpload(Holder h, WorkInfo info) {
      String path = null;
      String name = "Новая вещь";
      for (String tag : info.getTags()) {
        if (tag.startsWith("file:")) {
          path = tag.substring(5);
        } else if (tag.startsWith("name:")) {
          name = tag.substring(5);
        }
      }
      h.title.setText(name);
      if (path != null) {
        com.bumptech.glide.Glide.with(h.thumb).load(new File(path)).centerCrop().into(h.thumb);
      }
      int progress = info.getProgress().getInt(UploadWorker.KEY_PROGRESS, 0);
      long sent = info.getProgress().getLong(UploadWorker.KEY_SENT, 0);
      long total = info.getProgress().getLong(UploadWorker.KEY_TOTAL, 0);
      if (info.getState() == WorkInfo.State.ENQUEUED && progress == 0) {
        h.status.setText(container().api.isOnline() ? "Ждёт отправки" : "Загрузка на паузе — нет сети");
      } else {
        h.status.setText(String.format(Locale.forLanguageTag("ru"), "Загрузка · %d %% · %.1f из %.1f МБ",
            progress, sent / 1048576f, total / 1048576f));
      }
      h.progress.setVisibility(View.VISIBLE);
      h.progress.setProgressCompat(progress, true);
    }

    private void bindJob(Holder h, Dto.GarmentJob j) {
      Images.photo(h.thumb, j.sourceUrl);
      h.title.setText(j.categoryName);
      switch (j.status) {
        case "queued":
        case "processing": {
          int step = StudioText.step(j.stage);
          h.status.setText(StudioText.stageLabel(j.stage) + " · шаг " + Math.max(1, step) + " из " + StepsView.COUNT);
          h.steps.setVisibility(View.VISIBLE);
          h.steps.setDone(Math.max(0, step - 1));
          h.itemView.setOnClickListener(v -> nav().push(StudioReviewFragment.newInstance(j.id)));
          break;
        }
        case "review": {
          boolean low = StudioText.lowFit(j);
          int color = DsTheme.color(requireContext(), low
              ? app.outfitshare.core.designsystem.R.attr.dsColorWarning : app.outfitshare.core.designsystem.R.attr.dsColorSuccess);
          h.status.setText((low ? "Низкая посадка · " : "Готово к проверке · посадка ") + StudioText.fitPercent(j) + " %");
          h.status.setTextColor(color);
          h.status.setCompoundDrawablesRelativeWithIntrinsicBounds(low ? R.drawable.badge_warning : R.drawable.badge_check_small, 0, 0, 0);
          androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(h.status, android.content.res.ColorStateList.valueOf(color));
          h.chevron.setVisibility(View.VISIBLE);
          h.itemView.setOnClickListener(v -> nav().push(StudioReviewFragment.newInstance(j.id)));
          break;
        }
        case "failed": {
          int color = DsTheme.color(requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorDanger);
          h.status.setText(j.error == null || j.error.isEmpty() ? "Не удалось обработать" : j.error);
          h.status.setTextColor(color);
          h.status.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.badge_error, 0, 0, 0);
          androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(h.status, android.content.res.ColorStateList.valueOf(color));
          h.action.setVisibility(View.VISIBLE);
          boolean reshoot = j.attributes != null && j.attributes.get("error_code") != null
              && !"crash".equals(String.valueOf(j.attributes.get("error_code")));
          h.action.setText(reshoot ? "Переснять" : "Повторить");
          h.action.setOnClickListener(v -> {
            if (reshoot) {
              Calls.run(api().deleteJob(j.id), r -> load());
              nav().present(new StudioCameraFragment());
            } else {
              Calls.run(api().retryJob(j.id), r -> load());
            }
          });
          break;
        }
        default:
          h.status.setText("Опубликовано");
      }
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
      final ImageView thumb;
      final TextView title;
      final TextView status;
      final LinearProgressIndicator progress;
      final StepsView steps;
      final View chevron;
      final MaterialButton action;

      Holder(View v) {
        super(v);
        thumb = v.findViewById(R.id.thumb);
        title = v.findViewById(R.id.title);
        status = v.findViewById(R.id.status);
        progress = v.findViewById(R.id.progress);
        steps = v.findViewById(R.id.steps);
        chevron = v.findViewById(R.id.chevron);
        action = v.findViewById(R.id.action);
      }
    }
  }
}
