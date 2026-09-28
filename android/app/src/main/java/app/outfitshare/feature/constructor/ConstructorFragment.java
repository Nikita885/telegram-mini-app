package app.outfitshare.feature.constructor;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.banner.OfflineBanner;
import app.outfitshare.core.designsystem.component.dialog.ConfirmDialog;
import app.outfitshare.core.designsystem.component.snackbar.DsSnackbar;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.ActionSheet;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Paging;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.outfit.ItemSheet;
import app.outfitshare.feature.outfit.ItemsAdapter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mockup «Конструктор». A garment from the catalog drops onto the mannequin and sits on its anchor
 * points (fitted layer from the Studio pipeline); dragging it away and back snaps it home.
 */
public class ConstructorFragment extends BaseFragment implements LayersSheet.Host {
  private static final String ARG_ITEM = "item";
  private static final String ARG_REMIX = "remix";
  private static final String LAYER_ACTIONS = "layer_actions";
  private static final String DRAFT_PREFS = "constructor_draft";

  /** Catalog tabs → category slugs (comma-separated for the API). */
  private static final String[] TAB_TITLES = {
    "Все", "Верх", "Низ", "Платья", "Обувь", "Аксессуары"
  };

  private static final String[] TAB_CATEGORIES = {
    null, "top,shirt", "pants,skirt", "dress", "shoes", "accessories,hat"
  };

  private final List<Layer> layers = new ArrayList<>();
  private final Map<Long, Dto.Item> knownItems = new HashMap<>();
  private final Map<String, Bitmap> bitmaps = new HashMap<>();
  private final Map<String, Dto.Mannequin> mannequins = new HashMap<>();
  private final Deque<List<Layer.State>> undo = new ArrayDeque<>();
  private final Deque<List<Layer.State>> redo = new ArrayDeque<>();

  @SuppressWarnings("unchecked")
  private final Paging<Dto.Item>[] catalog = new Paging[TAB_TITLES.length];

  private OutfitCanvasView canvas;
  private ItemsAdapter adapter;
  private StateView catalogState;
  private OfflineBanner offline;
  private Chip mannequinChip;
  private Chip layersChip;
  private View emptyHint;
  private MaterialButton undoBtn;
  private MaterialButton redoBtn;
  private MaterialButton next;
  private String gender;
  private int tab;
  @Nullable private Long remixOf;
  private String title = "Образ";

  public ConstructorFragment() {
    super(R.layout.fragment_constructor);
  }

  public static ConstructorFragment withItem(Dto.Item item) {
    ConstructorFragment f = new ConstructorFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ITEM, app.outfitshare.App.get().container().gson.toJson(item));
    f.setArguments(args);
    return f;
  }

  public static ConstructorFragment remix(Dto.Outfit outfit) {
    ConstructorFragment f = new ConstructorFragment();
    Bundle args = new Bundle();
    args.putString(ARG_REMIX, app.outfitshare.App.get().container().gson.toJson(outfit));
    f.setArguments(args);
    return f;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, true);
    gender = container().prefs.mannequin();
    canvas = view.findViewById(R.id.canvas);
    catalogState = view.findViewById(R.id.catalog_state);
    offline = view.findViewById(R.id.offline);
    mannequinChip = view.findViewById(R.id.mannequin_chip);
    layersChip = view.findViewById(R.id.layers_chip);
    emptyHint = view.findViewById(R.id.empty_hint);
    undoBtn = view.findViewById(R.id.undo);
    redoBtn = view.findViewById(R.id.redo);
    next = view.findViewById(R.id.next);

    view.findViewById(R.id.close).setOnClickListener(v -> close());
    undoBtn.setOnClickListener(v -> undo());
    redoBtn.setOnClickListener(v -> redo());
    next.setOnClickListener(v -> openPublish());
    mannequinChip.setOnClickListener(v -> setGender("female".equals(gender) ? "male" : "female"));
    layersChip.setOnClickListener(v -> LayersSheet.show(getChildFragmentManager()));

    canvas.setListener(
        new OutfitCanvasView.Listener() {
          @Override
          public void onBeforeChange() {
            pushUndo();
          }

          @Override
          public void onChanged() {
            refreshChrome();
          }

          @Override
          public void onSelectionChanged(@Nullable Layer layer) {}

          @Override
          public void onLongPress(Layer layer) {
            showLayerActions(layer);
          }
        });

    RecyclerView grid = view.findViewById(R.id.catalog);
    grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
    int gap =
        getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_2);
    grid.addItemDecoration(Spacing.grid(gap, gap));
    adapter = new ItemsAdapter(0, false, this::toggleItem);
    grid.setAdapter(adapter);
    grid.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
            if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 6) {
              catalog[tab].loadMore();
            }
          }
        });

    TabLayout tabs = view.findViewById(R.id.categories);
    for (String t : TAB_TITLES) {
      tabs.addTab(tabs.newTab().setText(t));
    }
    for (int i = 0; i < TAB_TITLES.length; i++) {
      catalog[i] = catalogPaging(i);
    }
    tabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab t) {
            showTab(t.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab t) {}

          @Override
          public void onTabReselected(TabLayout.Tab t) {}
        });
    catalogState.setOnActionClickListener(v -> catalog[tab].refresh());
    offline.setOnRetryClickListener(v -> catalog[tab].refresh());

    getChildFragmentManager()
        .setFragmentResultListener(
            LAYER_ACTIONS,
            getViewLifecycleOwner(),
            (k, r) -> onLayerAction(r.getString(ActionSheet.RESULT_ID)));

    loadMannequins();
    restoreInitialLayers();
    showTab(0);
    refreshChrome();
  }

  // ── Catalog ──────────────────────────────────────────────────────────────

  private Paging<Dto.Item> catalogPaging(int index) {
    return new Paging<>(
        cursor -> api().items(TAB_CATEGORIES[index], gender, null, cursor),
        new Paging.Listener<Dto.Item>() {
          @Override
          public void onPage(List<Dto.Item> all, boolean reset, boolean fromCache) {
            for (Dto.Item i : all) {
              knownItems.put(i.id, i);
            }
            if (index != tab) {
              return;
            }
            offline.setOffline(fromCache);
            offline.setVisibility(fromCache ? View.VISIBLE : View.GONE);
            adapter.submit(all);
            syncSelection();
            catalogState.setState(
                all.isEmpty()
                    ? States.empty(
                        app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_search,
                        "Пока пусто",
                        "Вещи этой категории скоро появятся в каталоге.",
                        null)
                    : ScreenState.content());
          }

          @Override
          public void onError(ApiError error, boolean firstPage) {
            if (index == tab && firstPage) {
              catalogState.setState(States.failure(error, "Каталог не загрузился"));
            }
          }
        });
  }

  private void showTab(int index) {
    tab = index;
    Paging<Dto.Item> p = catalog[index];
    if (p.loadedOnce()) {
      adapter.submit(p.items());
      syncSelection();
      catalogState.setState(ScreenState.content());
    } else {
      adapter.submit(new ArrayList<>());
      catalogState.setState(ScreenState.loading());
      p.refresh();
    }
  }

  private void syncSelection() {
    List<Long> ids = new ArrayList<>();
    for (Layer l : layers) {
      ids.add(l.item.id);
    }
    adapter.setSelected(ids);
  }

  // ── Mannequin ────────────────────────────────────────────────────────────

  private void loadMannequins() {
    Calls.run(
        api().mannequins(),
        r -> {
          if (!isAdded() || !r.ok() || r.data == null) {
            return;
          }
          for (Dto.Mannequin m : r.data.results) {
            mannequins.put(m.gender, m);
          }
          applyMannequin();
        });
  }

  private void setGender(String g) {
    if (g.equals(gender)) {
      return;
    }
    pushUndo();
    gender = g;
    container().prefs.setMannequin(g);
    for (Layer l : layers) {
      boolean canFit = l.canFit(g);
      if (l.fitted && !canFit) {
        l.fitted = false;
      } else if (canFit && isAtHome(l)) {
        l.fitted = true;
      }
      loadBitmap(l, false);
    }
    applyMannequin();
    for (Paging<Dto.Item> p : catalog) {
      p.refresh();
    }
    refreshChrome();
  }

  private static boolean isAtHome(Layer l) {
    return l.x == 0.5f && l.y == 0.5f && l.scale == 1f && l.rotation == 0f;
  }

  private void applyMannequin() {
    mannequinChip.setText(
        "female".equals(gender) ? R.string.constructor_female : R.string.constructor_male);
    Dto.Mannequin m = mannequins.get(gender);
    if (m == null || m.canvasUrl == null) {
      return;
    }
    Glide.with(this)
        .asBitmap()
        .load(m.canvasUrl)
        .disallowHardwareConfig()
        .into(
            new CustomTarget<Bitmap>() {
              @Override
              public void onResourceReady(
                  @NonNull Bitmap resource, @Nullable Transition<? super Bitmap> t) {
                canvas.setMannequin(resource, m.width, m.height, m.anchors, gender);
              }

              @Override
              public void onLoadCleared(@Nullable Drawable placeholder) {}
            });
  }

  // ── Layers ───────────────────────────────────────────────────────────────

  private void toggleItem(Dto.Item item) {
    knownItems.put(item.id, item);
    Layer existing = find(item.id);
    if (existing != null) {
      pushUndo();
      layers.remove(existing);
      commitLayers();
      DsSnackbar.undo(requireView(), item.name + " убрана из образа", this::undo, null);
      return;
    }
    pushUndo();
    List<Layer> replaced = replacedBy(item);
    layers.removeAll(replaced);
    Layer layer = new Layer(item);
    layer.fitted = layer.canFit(gender);
    layer.z = item.defaultLayer + layers.size() % 5;
    if (!layer.fitted) {
      // Free cutouts start at the body zone they belong to.
      layer.y = defaultY(layer.zone());
    }
    layers.add(layer);
    canvas.select(layer);
    commitLayers();
    loadBitmap(layer, true);
    if (!replaced.isEmpty()) {
      DsSnackbar.undo(
          requireView(), replaced.get(0).item.name + " убрана из образа", this::undo, null);
    }
  }

  /**
   * Zone rules: one head, one pair of shoes, one bottom; a dress replaces the bottom; tops stack.
   */
  private List<Layer> replacedBy(Dto.Item item) {
    List<Layer> out = new ArrayList<>();
    String zone = item.zone == null ? "accessory" : item.zone;
    for (Layer l : layers) {
      String z = l.zone();
      boolean replace;
      switch (zone) {
        case "head":
        case "feet":
          replace = z.equals(zone);
          break;
        case "lower":
          replace = z.equals("lower") || z.equals("full");
          break;
        case "full":
          replace = z.equals("full") || z.equals("lower") || "shirt".equals(l.item.category);
          break;
        case "upper":
          replace =
              (item.category != null && item.category.equals(l.item.category))
                  || ("shirt".equals(item.category) && z.equals("full"));
          break;
        default:
          replace = false;
      }
      if (replace) {
        out.add(l);
      }
    }
    return out;
  }

  private static float defaultY(String zone) {
    switch (zone) {
      case "head":
        return 0.1f;
      case "upper":
      case "full":
        return 0.36f;
      case "lower":
        return 0.66f;
      case "feet":
        return 0.93f;
      default:
        return 0.45f;
    }
  }

  @Nullable
  private Layer find(long itemId) {
    for (Layer l : layers) {
      if (l.item.id == itemId) {
        return l;
      }
    }
    return null;
  }

  private void loadBitmap(Layer layer, boolean drop) {
    String url = layer.imageUrl(gender);
    if (url == null) {
      return;
    }
    Bitmap cached = bitmaps.get(url);
    if (cached != null) {
      layer.bitmap = cached;
      canvas.invalidate();
      if (drop) {
        canvas.dropIn(layer);
      }
      return;
    }
    int max = layer.fitted ? canvas.canvasHeight() : 900;
    Glide.with(this)
        .asBitmap()
        .load(url)
        .disallowHardwareConfig()
        .override(max, max)
        .into(
            new CustomTarget<Bitmap>() {
              @Override
              public void onResourceReady(
                  @NonNull Bitmap resource, @Nullable Transition<? super Bitmap> t) {
                bitmaps.put(url, resource);
                if (url.equals(layer.imageUrl(gender))) {
                  layer.bitmap = resource;
                  if (drop) {
                    canvas.dropIn(layer);
                  }
                  canvas.invalidate();
                }
              }

              @Override
              public void onLoadCleared(@Nullable Drawable placeholder) {}

              @Override
              public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (isAdded()) {
                  DsSnackbar.error(
                      requireView(), "Не удалось загрузить вещь", () -> loadBitmap(layer, drop));
                }
              }
            });
  }

  private void commitLayers() {
    canvas.setLayers(layers);
    syncSelection();
    refreshChrome();
  }

  private void refreshChrome() {
    int n = 0;
    for (Layer l : layers) {
      if (!l.hidden) {
        n++;
      }
    }
    layersChip.setText(getString(R.string.constructor_layers) + " · " + layers.size());
    layersChip.setVisibility(layers.isEmpty() ? View.GONE : View.VISIBLE);
    emptyHint.setVisibility(layers.isEmpty() ? View.VISIBLE : View.GONE);
    next.setEnabled(n > 0);
    undoBtn.setEnabled(!undo.isEmpty());
    redoBtn.setEnabled(!redo.isEmpty());
  }

  // ── Undo / redo ──────────────────────────────────────────────────────────

  private List<Layer.State> snapshot() {
    List<Layer.State> s = new ArrayList<>();
    for (Layer l : layers) {
      s.add(l.state());
    }
    return s;
  }

  private void pushUndo() {
    undo.push(snapshot());
    if (undo.size() > 50) {
      undo.removeLast();
    }
    redo.clear();
    refreshChrome();
  }

  private void undo() {
    if (undo.isEmpty()) {
      return;
    }
    redo.push(snapshot());
    restore(undo.pop());
  }

  private void redo() {
    if (redo.isEmpty()) {
      return;
    }
    undo.push(snapshot());
    restore(redo.pop());
  }

  private void restore(List<Layer.State> states) {
    Map<Long, Layer> current = new HashMap<>();
    for (Layer l : layers) {
      current.put(l.item.id, l);
    }
    layers.clear();
    for (Layer.State s : states) {
      Layer l = current.get(s.itemId);
      if (l == null) {
        Dto.Item item = knownItems.get(s.itemId);
        if (item == null) {
          continue;
        }
        l = new Layer(item);
      }
      boolean wasFitted = l.fitted;
      l.apply(s);
      layers.add(l);
      if (l.bitmap == null || wasFitted != l.fitted) {
        loadBitmap(l, false);
      }
    }
    commitLayers();
  }

  // ── Layer actions (long press, layers sheet) ─────────────────────────────

  private void showLayerActions(Layer layer) {
    ArrayList<ActionSheet.Row> rows = new ArrayList<>();
    int icons = 0;
    rows.add(
        new ActionSheet.Row(
            "front",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_layers,
            "Поднять слой",
            null,
            false));
    rows.add(
        new ActionSheet.Row(
            "back",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_layers,
            "Опустить слой",
            null,
            false));
    rows.add(
        new ActionSheet.Row(
            "flip",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_swap,
            "Отразить",
            null,
            false));
    if (layer.canFit(gender)) {
      rows.add(
          new ActionSheet.Row(
              "home",
              app.outfitshare.core.designsystem.R.drawable.ds_ic_magnet,
              "Вернуть на манекен",
              "Сядет по опорным точкам",
              false));
    }
    rows.add(
        new ActionSheet.Row(
            "info",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_info,
            "О вещи",
            null,
            false));
    rows.add(
        new ActionSheet.Row(
            "delete",
            app.outfitshare.core.designsystem.R.drawable.ds_ic_delete,
            "Убрать из образа",
            null,
            true));
    ActionSheet.show(getChildFragmentManager(), LAYER_ACTIONS, layer.item.name, rows);
  }

  private void onLayerAction(@Nullable String id) {
    Layer l = canvas.selected();
    if (l == null || id == null) {
      return;
    }
    switch (id) {
      case "front":
        moveLayer(l, 1);
        break;
      case "back":
        moveLayer(l, -1);
        break;
      case "flip":
        pushUndo();
        l.flipped = !l.flipped;
        commitLayers();
        break;
      case "home":
        pushUndo();
        boolean refit = !l.fitted;
        l.fitted = true;
        if (refit) {
          l.x = 0.5f;
          l.y = 0.5f;
          l.scale = 1f;
          l.rotation = 0f;
          loadBitmap(l, true);
        } else {
          canvas.snapHome(l);
        }
        commitLayers();
        break;
      case "info":
        ItemSheet.show(getChildFragmentManager(), l.item);
        break;
      case "delete":
        removeLayer(l);
        break;
      default:
        break;
    }
  }

  /** Swap z with the neighbour above (+1) or below (-1). */
  private void moveLayer(Layer l, int dir) {
    List<Layer> sorted = new ArrayList<>(layers);
    sorted.sort((a, b) -> Integer.compare(a.z, b.z));
    int i = sorted.indexOf(l);
    int j = i + dir;
    if (j < 0 || j >= sorted.size()) {
      return;
    }
    pushUndo();
    Layer other = sorted.get(j);
    int z = l.z;
    l.z = other.z == z ? z + dir : other.z;
    other.z = z;
    commitLayers();
  }

  // LayersSheet.Host
  @Override
  public List<Layer> layers() {
    List<Layer> sorted = new ArrayList<>(layers);
    sorted.sort((a, b) -> Integer.compare(b.z, a.z));
    return sorted;
  }

  @Override
  public void onReordered(List<Layer> topToBottom) {
    pushUndo();
    int z = topToBottom.size() * 10;
    for (Layer l : topToBottom) {
      l.z = z;
      z -= 10;
    }
    commitLayers();
  }

  @Override
  public void onToggleHidden(Layer layer) {
    pushUndo();
    layer.hidden = !layer.hidden;
    commitLayers();
  }

  @Override
  public void removeLayer(Layer layer) {
    pushUndo();
    layers.remove(layer);
    commitLayers();
    DsSnackbar.undo(requireView(), layer.item.name + " убрана из образа", this::undo, null);
  }

  @Override
  public String gender() {
    return gender;
  }

  // ── Start state: remix / single item / draft ─────────────────────────────

  private void restoreInitialLayers() {
    Bundle args = getArguments();
    if (args != null && args.getString(ARG_REMIX) != null) {
      Dto.Outfit o = container().gson.fromJson(args.getString(ARG_REMIX), Dto.Outfit.class);
      remixOf = o.id;
      title = "Ремикс";
      ((android.widget.TextView) requireView().findViewById(R.id.title)).setText(title);
      if (o.mannequin != null) {
        gender = o.mannequin;
      }
      Map<Long, Dto.Item> byId = new HashMap<>();
      for (Dto.Item i : o.items) {
        byId.put(i.id, i);
        knownItems.put(i.id, i);
      }
      if (!o.layers.isEmpty()) {
        for (Dto.Layer dl : o.layers) {
          Dto.Item item = byId.get(dl.itemId);
          if (item == null) {
            continue;
          }
          Layer l = new Layer(item);
          l.x = dl.x;
          l.y = dl.y;
          l.scale = dl.scale;
          l.rotation = dl.rotation;
          l.z = dl.z;
          l.flipped = dl.flipped;
          l.fitted = dl.fitted && l.canFit(gender);
          layers.add(l);
        }
      } else {
        // Outfits from the Mini App have no normalized layers: seat every item on the body.
        for (Dto.Item item : o.items) {
          Layer l = new Layer(item);
          l.fitted = l.canFit(gender);
          l.z = item.defaultLayer;
          if (!l.fitted) {
            l.y = defaultY(l.zone());
          }
          layers.add(l);
        }
      }
    } else if (args != null && args.getString(ARG_ITEM) != null) {
      Dto.Item item = container().gson.fromJson(args.getString(ARG_ITEM), Dto.Item.class);
      knownItems.put(item.id, item);
      toggleItem(item);
      undo.clear();
      return;
    } else {
      restoreDraft();
    }
    for (Layer l : layers) {
      loadBitmap(l, false);
    }
    commitLayers();
  }

  private void restoreDraft() {
    String json = requireContext().getSharedPreferences(DRAFT_PREFS, 0).getString("draft", null);
    if (json == null) {
      return;
    }
    try {
      Draft d = container().gson.fromJson(json, Draft.class);
      gender = d.gender;
      Map<Long, Dto.Item> byId = new HashMap<>();
      for (Dto.Item i : d.items) {
        byId.put(i.id, i);
        knownItems.put(i.id, i);
      }
      for (Layer.State s : d.layers) {
        Dto.Item item = byId.get(s.itemId);
        if (item != null) {
          Layer l = new Layer(item);
          l.apply(s);
          layers.add(l);
        }
      }
      if (!layers.isEmpty()) {
        requireView().post(() -> DsSnackbar.message(requireView(), "Черновик восстановлен"));
      }
    } catch (RuntimeException ignored) {
      clearDraft();
    }
  }

  private void saveDraft() {
    if (remixOf != null) {
      return;
    }
    if (layers.isEmpty()) {
      clearDraft();
      return;
    }
    Draft d = new Draft();
    d.gender = gender;
    for (Layer l : layers) {
      d.layers.add(l.state());
      d.items.add(l.item);
    }
    requireContext()
        .getSharedPreferences(DRAFT_PREFS, 0)
        .edit()
        .putString("draft", container().gson.toJson(d))
        .apply();
  }

  void clearDraft() {
    requireContext().getSharedPreferences(DRAFT_PREFS, 0).edit().clear().apply();
  }

  static final class Draft {
    String gender;
    List<Layer.State> layers = new ArrayList<>();
    List<Dto.Item> items = new ArrayList<>();
  }

  @Override
  public void onPause() {
    super.onPause();
    saveDraft();
  }

  private void close() {
    if (layers.isEmpty() || remixOf != null) {
      clearDraft();
      nav().back();
      return;
    }
    ConfirmDialog.with(requireContext())
        .title("Сохранить черновик?")
        .message("Образ откроется здесь же в следующий раз.")
        .confirm("Сохранить")
        .cancel("Удалить")
        .onConfirm(
            () -> {
              saveDraft();
              nav().back();
            })
        .onCancel(
            () -> {
              layers.clear();
              clearDraft();
              nav().back();
            })
        .show();
  }

  // ── Publish ──────────────────────────────────────────────────────────────

  private void openPublish() {
    canvas.select(null);
    Bitmap preview = canvas.renderPreview(540);
    List<Dto.Layer> out = new ArrayList<>();
    List<Dto.Item> items = new ArrayList<>();
    for (Layer l : layers) {
      if (l.hidden) {
        continue;
      }
      Dto.Layer dl = new Dto.Layer();
      dl.itemId = l.item.id;
      dl.x = l.x;
      dl.y = l.y;
      dl.scale = l.scale;
      dl.rotation = OutfitGeometry.normalizeAngle(l.rotation);
      dl.z = l.z;
      dl.flipped = l.flipped;
      dl.fitted = l.fitted;
      out.add(dl);
      items.add(l.item);
    }
    nav().push(PublishFragment.newInstance(gender, out, items, remixOf, preview));
  }

  /** Publish succeeded: the draft is done. */
  public void onPublished() {
    layers.clear();
    clearDraft();
  }

  static final java.lang.reflect.Type LAYERS_TYPE = new TypeToken<List<Dto.Layer>>() {}.getType();
}
