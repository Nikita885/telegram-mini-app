package app.outfitshare.core.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import app.outfitshare.core.designsystem.component.list.ListRowView;
import app.outfitshare.core.designsystem.component.sheet.DsBottomSheetDialogFragment;
import com.google.android.material.divider.MaterialDivider;
import java.util.ArrayList;

/**
 * List of actions in a bottom sheet (mockup «Шторка «Ещё»», «Фото профиля», «Тема»). The chosen row
 * id is delivered through the Fragment Result API under {@code requestKey}.
 */
public class ActionSheet extends DsBottomSheetDialogFragment {
  public static final String RESULT_ID = "id";

  private static final String ARG_KEY = "key";
  private static final String ARG_TITLE = "title";
  private static final String ARG_ROWS = "rows";

  /** One row; `danger` rows are separated by a divider and use the danger colour. */
  public static final class Row implements java.io.Serializable {
    final String id;
    @DrawableRes final int icon;
    final String title;
    @Nullable final String subtitle;
    final boolean danger;
    final boolean checked;

    public Row(
        String id, @DrawableRes int icon, String title, @Nullable String subtitle, boolean danger) {
      this(id, icon, title, subtitle, danger, false);
    }

    public Row(
        String id,
        @DrawableRes int icon,
        String title,
        @Nullable String subtitle,
        boolean danger,
        boolean checked) {
      this.id = id;
      this.icon = icon;
      this.title = title;
      this.subtitle = subtitle;
      this.danger = danger;
      this.checked = checked;
    }
  }

  public static void show(
      FragmentManager fm, String requestKey, @Nullable String title, ArrayList<Row> rows) {
    ActionSheet sheet = new ActionSheet();
    Bundle args = new Bundle();
    args.putString(ARG_KEY, requestKey);
    args.putString(ARG_TITLE, title);
    args.putSerializable(ARG_ROWS, rows);
    sheet.setArguments(args);
    sheet.show(fm, requestKey);
  }

  @Nullable
  @Override
  protected CharSequence getSheetTitle() {
    return requireArguments().getString(ARG_TITLE);
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  protected View onCreateSheetContent(
      @NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
    LinearLayout list = new LinearLayout(requireContext());
    list.setOrientation(LinearLayout.VERTICAL);
    int bottom =
        getResources().getDimensionPixelSize(app.outfitshare.core.designsystem.R.dimen.ds_space_5);
    list.setPadding(0, 0, 0, bottom);
    ArrayList<Row> rows = (ArrayList<Row>) requireArguments().getSerializable(ARG_ROWS);
    boolean dividerAdded = false;
    if (rows != null) {
      for (Row row : rows) {
        if (row.danger && !dividerAdded && list.getChildCount() > 0) {
          MaterialDivider divider = new MaterialDivider(requireContext());
          divider.setDividerInsetStart(
              getResources()
                  .getDimensionPixelSize(
                      app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter));
          divider.setDividerInsetEnd(
              getResources()
                  .getDimensionPixelSize(
                      app.outfitshare.core.designsystem.R.dimen.ds_layout_gutter));
          list.addView(divider);
          dividerAdded = true;
        }
        ListRowView v = new ListRowView(requireContext());
        if (row.icon != 0) {
          v.setIcon(ContextCompat.getDrawable(requireContext(), row.icon));
        }
        v.setTitle(row.title);
        v.setSubtitle(row.subtitle);
        v.setDestructive(row.danger);
        if (row.checked) {
          v.setTrailingView(checkMark());
        }
        v.setOnClickListener(
            x -> {
              Bundle result = new Bundle();
              result.putString(RESULT_ID, row.id);
              getParentFragmentManager()
                  .setFragmentResult(requireArguments().getString(ARG_KEY), result);
              dismiss();
            });
        list.addView(v);
      }
    }
    return list;
  }

  private View checkMark() {
    android.widget.ImageView iv = new android.widget.ImageView(requireContext());
    iv.setImageResource(app.outfitshare.core.designsystem.R.drawable.ds_ic_check);
    iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return iv;
  }
}
