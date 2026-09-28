package app.outfitshare.feature.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.avatar.AvatarView;
import app.outfitshare.core.designsystem.component.chip.FilterChipGroup;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.component.state.ScreenState;
import app.outfitshare.core.designsystem.component.state.StateView;
import app.outfitshare.core.designsystem.theme.DsTheme;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.net.ApiError;
import app.outfitshare.core.net.Calls;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.Images;
import app.outfitshare.core.ui.Spacing;
import app.outfitshare.core.ui.States;
import app.outfitshare.feature.feed.OutfitAdapter;
import app.outfitshare.feature.outfit.CommentsSheet;
import app.outfitshare.feature.outfit.ItemSheet;
import app.outfitshare.feature.outfit.ItemsAdapter;
import app.outfitshare.feature.outfit.OutfitFragment;
import app.outfitshare.feature.profile.PeopleAdapter;
import app.outfitshare.feature.profile.UserFragment;
import app.outfitshare.feature.shell.ShellFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

/** Before typing: recent, trends, people. After: filter chips and results. */
public class SearchFragment extends BaseFragment implements ShellFragment.Reselectable {
  private static final String ARG_QUERY = "query";
  private static final String F_OUTFITS = "outfits";
  private static final String F_PEOPLE = "people";
  private static final String F_ITEMS = "items";
  private static final String F_TAGS = "tags";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private EditText input;
  private StateView stateView;
  private RecyclerView results;
  private View initial;
  private LinearLayout initialContent;
  private FilterChipGroup filters;
  private String filter = F_OUTFITS;
  private String query = "";
  private OutfitAdapter outfitAdapter;
  private PeopleAdapter peopleAdapter;
  private ItemsAdapter itemsAdapter;
  private retrofit2.Call<?> inFlight;

  public SearchFragment() {
    super(R.layout.fragment_search);
  }

  /** Pushed search with a query (hashtag tap, «Найти людей»). */
  public static SearchFragment forQuery(String q) {
    SearchFragment f = new SearchFragment();
    Bundle args = new Bundle();
    args.putString(ARG_QUERY, q);
    f.setArguments(args);
    return f;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    SystemBars.applyPadding(view, true, false);
    input = view.findViewById(R.id.search_input);
    stateView = view.findViewById(R.id.state);
    results = view.findViewById(R.id.results);
    initial = view.findViewById(R.id.initial);
    initialContent = view.findViewById(R.id.initial_content);
    filters = view.findViewById(R.id.filters);
    stateView.setState(ScreenState.content());
    stateView.setOnActionClickListener(v -> run());

    outfitAdapter = new OutfitAdapter(true, new OutfitAdapter.Listener() {
      @Override
      public void onOpen(Dto.Outfit outfit, View photo) {
        nav().push(OutfitFragment.newInstance(outfit.id));
      }

      @Override
      public void onAuthor(Dto.User author) {
        nav().push(UserFragment.newInstance(author.id));
      }

      @Override
      public void onComments(Dto.Outfit outfit) {
        CommentsSheet.show(getChildFragmentManager(), outfit.id, outfit.author.id);
      }

      @Override
      public void onTag(String tag) {
        setQuery("#" + tag);
      }
    });
    peopleAdapter = new PeopleAdapter(u -> nav().push(UserFragment.newInstance(u.id)));
    itemsAdapter = new ItemsAdapter(0, true, item -> ItemSheet.show(getChildFragmentManager(), item));

    List<FilterChipGroup.Filter> list = new ArrayList<>();
    list.add(new FilterChipGroup.Filter(F_OUTFITS, getString(R.string.filter_outfits), 0));
    list.add(new FilterChipGroup.Filter(F_PEOPLE, getString(R.string.filter_people), 0));
    list.add(new FilterChipGroup.Filter(F_ITEMS, getString(R.string.filter_items), 0));
    list.add(new FilterChipGroup.Filter(F_TAGS, getString(R.string.filter_tags), 0));
    filters.setFilters(list, filter);
    filters.setOnFilterSelectedListener(id -> {
      filter = id;
      run();
    });

    View clear = view.findViewById(R.id.search_clear);
    clear.setOnClickListener(v -> input.setText(""));
    input.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

      @Override
      public void onTextChanged(CharSequence s, int a, int b, int c) {}

      @Override
      public void afterTextChanged(Editable s) {
        clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
          query = s.toString().trim();
          run();
        }, 350);
      }
    });
    input.setOnEditorActionListener((v, actionId, e) -> {
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        query = input.getText().toString().trim();
        if (!query.isEmpty()) {
          container().prefs.addRecentSearch(query);
        }
        run();
        return true;
      }
      return false;
    });

    String preset = getArguments() == null ? null : getArguments().getString(ARG_QUERY);
    if (preset != null) {
      View back = view.findViewById(R.id.back);
      back.setVisibility(View.VISIBLE);
      back.setOnClickListener(v -> nav().back());
      view.findViewById(R.id.large_title).setVisibility(View.GONE);
      if ("@".equals(preset)) {
        filter = F_PEOPLE;
        filters.setFilters(list, filter);
        preset = "";
        input.requestFocus();
      }
      setQuery(preset);
      if (preset.isEmpty()) {
        run();
      }
    } else {
      buildInitial();
    }
  }

  private void setQuery(String q) {
    input.setText(q);
    input.setSelection(q.length());
    query = q;
    run();
  }

  private void run() {
    handler.removeCallbacksAndMessages(null);
    if (inFlight != null) {
      inFlight.cancel();
    }
    boolean empty = query.isEmpty() && !F_PEOPLE.equals(filter);
    initial.setVisibility(empty ? View.VISIBLE : View.GONE);
    results.setVisibility(empty ? View.GONE : View.VISIBLE);
    filters.setVisibility(empty && getArguments() == null ? View.GONE : View.VISIBLE);
    requireView().findViewById(R.id.large_title).setVisibility(empty && getArguments() == null ? View.VISIBLE : View.GONE);
    if (empty) {
      stateView.setState(ScreenState.content());
      buildInitial();
      return;
    }
    stateView.setState(ScreenState.loading());
    int gutter = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    int gap = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_grid_gap);
    while (results.getItemDecorationCount() > 0) {
      results.removeItemDecorationAt(0);
    }
    switch (filter) {
      case F_PEOPLE:
        results.setPadding(0, results.getPaddingTop(), 0, results.getPaddingBottom());
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        results.setAdapter(peopleAdapter);
        inFlight = Calls.run(api().searchUsers(query.replace("@", "")), r -> {
          if (r.ok() && r.data != null) {
            peopleAdapter.submit(r.data.results);
          }
          finish(r.error, r.data == null ? 0 : r.data.results.size());
        });
        break;
      case F_ITEMS:
        results.setPadding(gutter, results.getPaddingTop(), gutter, results.getPaddingBottom());
        results.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        results.addItemDecoration(Spacing.grid(gap, gap));
        results.setAdapter(itemsAdapter);
        inFlight = Calls.run(api().items(null, null, query.replace("#", ""), null), r -> {
          if (r.ok() && r.data != null) {
            itemsAdapter.submit(r.data.results);
          }
          finish(r.error, r.data == null ? 0 : r.data.results.size());
        });
        break;
      case F_TAGS:
        results.setPadding(0, results.getPaddingTop(), 0, results.getPaddingBottom());
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        inFlight = Calls.run(api().trendingHashtags(), r -> {
          List<Dto.Hashtag> tags = new ArrayList<>();
          if (r.ok() && r.data != null) {
            String q = query.replace("#", "").toLowerCase();
            for (Dto.Hashtag h : r.data.results) {
              if (h.tag.contains(q)) {
                tags.add(h);
              }
            }
            if (!q.isEmpty() && tags.isEmpty()) {
              Dto.Hashtag h = new Dto.Hashtag();
              h.tag = q;
              tags.add(h);
            }
          }
          results.setAdapter(new TagsAdapter(tags));
          finish(r.error, tags.size());
        });
        break;
      default:
        results.setPadding(gutter, results.getPaddingTop(), gutter, results.getPaddingBottom());
        results.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        results.addItemDecoration(Spacing.grid(getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_4), gap));
        results.setAdapter(outfitAdapter);
        inFlight = Calls.run(api().searchOutfits(query, null), r -> {
          if (r.ok() && r.data != null) {
            outfitAdapter.submit(r.data.results);
          }
          finish(r.error, r.data == null ? 0 : r.data.results.size());
        });
    }
  }

  private void finish(@Nullable ApiError error, int count) {
    if (!isAdded()) {
      return;
    }
    if (error != null) {
      stateView.setState(error.isOffline()
          ? ScreenState.builder(ScreenState.Kind.OFFLINE)
              .illustration(app.outfitshare.core.designsystem.R.drawable.ds_illustration_offline)
              .title("Поиск без сети не работает")
              .message("Недавние запросы и сохранённое доступны в профиле.")
              .action("Повторить").build()
          : States.failure(error, "Поиск не ответил"));
      return;
    }
    stateView.setState(count == 0
        ? States.empty(app.outfitshare.core.designsystem.R.drawable.ds_illustration_empty_search,
            "Ничего не нашлось", "Попробуйте короче или по-другому.", null)
        : ScreenState.content());
  }

  // ── Initial content: recent, trends, people ──────────────────────────────

  private void buildInitial() {
    initialContent.removeAllViews();
    List<String> recent = container().prefs.recentSearches();
    if (!recent.isEmpty()) {
      LinearLayout head = sectionHead(getString(R.string.search_recent), getString(R.string.action_clear), () -> {
        container().prefs.clearRecentSearches();
        buildInitial();
      });
      initialContent.addView(head);
      for (String q : recent) {
        ListRowView row = new ListRowView(requireContext());
        row.setIcon(ContextCompat.getDrawable(requireContext(), app.outfitshare.core.designsystem.R.drawable.ds_ic_history));
        row.setTitle(q);
        com.google.android.material.button.MaterialButton remove = new com.google.android.material.button.MaterialButton(
            requireContext(), null, com.google.android.material.R.attr.materialIconButtonStyle);
        remove.setIconResource(app.outfitshare.core.designsystem.R.drawable.ds_ic_close);
        remove.setContentDescription("Удалить из истории");
        remove.setOnClickListener(v -> {
          container().prefs.removeRecentSearch(q);
          buildInitial();
        });
        row.setTrailingView(remove);
        row.setOnClickListener(v -> setQuery(q));
        initialContent.addView(row);
      }
    }
    initialContent.addView(sectionHead(getString(R.string.search_trending), null, null));
    ChipGroup trends = new ChipGroup(requireContext());
    int gutter = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    trends.setPadding(gutter, 0, gutter, 0);
    initialContent.addView(trends);
    Calls.run(api().trendingHashtags(), r -> {
      if (!isAdded() || !r.ok() || r.data == null) {
        return;
      }
      for (Dto.Hashtag h : r.data.results) {
        Chip chip = new Chip(requireContext());
        chip.setText("#" + h.tag);
        chip.setCheckable(false);
        chip.setOnClickListener(v -> setQuery("#" + h.tag));
        trends.addView(chip);
      }
    });

    initialContent.addView(sectionHead(getString(R.string.search_people), null, null));
    android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(requireContext());
    hs.setHorizontalScrollBarEnabled(false);
    LinearLayout people = new LinearLayout(requireContext());
    people.setPadding(gutter, 0, gutter, 0);
    hs.addView(people);
    initialContent.addView(hs);
    Calls.run(api().searchUsers(""), r -> {
      if (!isAdded() || !r.ok() || r.data == null) {
        return;
      }
      int w = getResources().getDimensionPixelSize(R.dimen.person_tile_width);
      int gap = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_4);
      for (Dto.User u : r.data.results) {
        LinearLayout tile = new LinearLayout(requireContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(gap);
        tile.setLayoutParams(lp);
        AvatarView avatar = new AvatarView(requireContext());
        avatar.setAvatarSize(3);
        Images.avatar(avatar, u);
        tile.addView(avatar);
        TextView name = new TextView(requireContext());
        name.setText(u.name == null ? "" : u.name.split(" ")[0]);
        name.setTextAppearance(app.outfitshare.core.designsystem.R.style.TextAppearance_Ds_BodySmall);
        name.setTextColor(DsTheme.color(requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorOnSurface));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_1), 0, 0);
        tile.addView(name);
        tile.setOnClickListener(v -> nav().push(UserFragment.newInstance(u.id)));
        tile.setContentDescription(u.name);
        people.addView(tile);
      }
    });
  }

  private LinearLayout sectionHead(String title, @Nullable String action, @Nullable Runnable onAction) {
    LinearLayout head = new LinearLayout(requireContext());
    head.setGravity(Gravity.CENTER_VERTICAL);
    int gutter = getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter);
    head.setPadding(gutter, getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_5), gutter,
        getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_2));
    TextView kicker = new TextView(requireContext(), null, 0, app.outfitshare.core.designsystem.R.style.Widget_Ds_Text_Kicker);
    kicker.setText(title);
    head.addView(kicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (action != null && onAction != null) {
      TextView link = new TextView(requireContext());
      link.setText(action);
      link.setTextAppearance(app.outfitshare.core.designsystem.R.style.TextAppearance_Ds_LabelLarge);
      link.setTextColor(DsTheme.color(requireContext(), app.outfitshare.core.designsystem.R.attr.dsColorAccent));
      link.setMinHeight(getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_size_touch_target));
      link.setGravity(Gravity.CENTER_VERTICAL);
      link.setOnClickListener(v -> onAction.run());
      head.addView(link);
    }
    return head;
  }

  @Override
  public void onReselected() {
    input.setText("");
    input.requestFocus();
  }

  @Override
  public void onDestroyView() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroyView();
  }

  private final class TagsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<Dto.Hashtag> tags;

    TagsAdapter(List<Dto.Hashtag> tags) {
      this.tags = tags;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
      ListRowView row = new ListRowView(parent.getContext());
      row.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
      row.setIcon(ContextCompat.getDrawable(parent.getContext(), app.outfitshare.core.designsystem.R.drawable.ds_ic_tag));
      return new RecyclerView.ViewHolder(row) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
      Dto.Hashtag t = tags.get(position);
      ListRowView row = (ListRowView) h.itemView;
      row.setTitle("#" + t.tag);
      row.setSubtitle(t.count > 0 ? getResources().getQuantityString(R.plurals.outfits_count, t.count, t.count) : null);
      row.setOnClickListener(v -> {
        filter = F_OUTFITS;
        List<FilterChipGroup.Filter> list = new ArrayList<>();
        list.add(new FilterChipGroup.Filter(F_OUTFITS, getString(R.string.filter_outfits), 0));
        list.add(new FilterChipGroup.Filter(F_PEOPLE, getString(R.string.filter_people), 0));
        list.add(new FilterChipGroup.Filter(F_ITEMS, getString(R.string.filter_items), 0));
        list.add(new FilterChipGroup.Filter(F_TAGS, getString(R.string.filter_tags), 0));
        filters.setFilters(list, filter);
        setQuery("#" + t.tag);
      });
    }

    @Override
    public int getItemCount() {
      return tags.size();
    }
  }
}
