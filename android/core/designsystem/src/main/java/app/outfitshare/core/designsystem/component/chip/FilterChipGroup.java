package app.outfitshare.core.designsystem.component.chip;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;
import java.util.Objects;

/**
 * Строка фильтр-чипов с одиночным выбором: «Все · Верх · Низ · Обувь · Аксессуары», «Образы · Люди
 * · Вещи · Теги». Кладётся в {@code HorizontalScrollView}; стиль — {@code Widget.Ds.Chip.Filter}.
 */
public class FilterChipGroup extends ChipGroup {

  /** Выбран фильтр. */
  public interface OnFilterSelectedListener {
    /** {@code id} — идентификатор из {@link Filter}. */
    void onFilterSelected(@NonNull String id);
  }

  /** Описание одного фильтра. */
  public static final class Filter {
    final String id;
    final CharSequence label;
    @DrawableRes final int icon;

    /**
     * Создаёт фильтр.
     *
     * @param icon иконка слева или 0
     */
    public Filter(@NonNull String id, @NonNull CharSequence label, @DrawableRes int icon) {
      this.id = Objects.requireNonNull(id);
      this.label = Objects.requireNonNull(label);
      this.icon = icon;
    }
  }

  private final SparseArray<String> idsByView = new SparseArray<>();
  @Nullable private OnFilterSelectedListener listener;

  public FilterChipGroup(@NonNull Context context) {
    this(context, null);
  }

  public FilterChipGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.dsFilterChipGroupStyle);
  }

  public FilterChipGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setSingleSelection(true);
    setSelectionRequired(true);
    setOnCheckedStateChangeListener(
        (group, checkedIds) -> {
          if (listener == null || checkedIds.isEmpty()) {
            return;
          }
          String id = idsByView.get(checkedIds.get(0));
          if (id != null) {
            listener.onFilterSelected(id);
          }
        });
  }

  public void setOnFilterSelectedListener(@Nullable OnFilterSelectedListener listener) {
    this.listener = listener;
  }

  /**
   * Заменяет набор фильтров.
   *
   * @param selectedId выбранный фильтр; {@code null} — первый
   */
  public void setFilters(@NonNull List<Filter> filters, @Nullable String selectedId) {
    removeAllViews();
    idsByView.clear();
    for (int i = 0; i < filters.size(); i++) {
      Filter filter = filters.get(i);
      Chip chip = new Chip(getContext());
      chip.setId(View.generateViewId());
      chip.setText(filter.label);
      chip.setCheckable(true);
      if (filter.icon != 0) {
        chip.setChipIconResource(filter.icon);
        chip.setChipIconVisible(true);
      }
      idsByView.put(chip.getId(), filter.id);
      addView(chip);
      boolean selected = selectedId == null ? i == 0 : filter.id.equals(selectedId);
      if (selected) {
        check(chip.getId());
      }
    }
  }

  /** Идентификатор выбранного фильтра или {@code null}. */
  @Nullable
  public String getSelectedFilterId() {
    int checked = getCheckedChipId();
    return checked == View.NO_ID ? null : idsByView.get(checked);
  }
}
