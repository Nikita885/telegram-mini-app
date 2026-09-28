package app.outfitshare.feature.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import app.outfitshare.R;
import app.outfitshare.core.designsystem.component.button.DsButton;
import app.outfitshare.core.designsystem.theme.SystemBars;
import app.outfitshare.core.ui.BaseFragment;
import app.outfitshare.core.ui.PagerDots;

/** Three spreads; «Пропустить» always available, swipe and «Далее» are equivalent. */
public class OnboardingFragment extends BaseFragment {
  private static final int[] PHOTOS = {
    R.drawable.hero_trench, R.drawable.hero_shirt, R.drawable.hero_dress
  };
  private static final int[] TITLES = {
    R.string.onboarding_1_title, R.string.onboarding_2_title, R.string.onboarding_3_title
  };
  private static final int[] TEXTS = {
    R.string.onboarding_1_text, R.string.onboarding_2_text, R.string.onboarding_3_text
  };

  public OnboardingFragment() {
    super(R.layout.fragment_onboarding);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    ViewPager2 pager = view.findViewById(R.id.pager);
    PagerDots dots = view.findViewById(R.id.dots);
    DsButton next = view.findViewById(R.id.next);
    View skip = view.findViewById(R.id.skip);
    SystemBars.applyPadding(view.findViewById(R.id.bottom_row), false, true);
    SystemBars.applyPadding(skip, true, false);

    pager.setAdapter(new PagesAdapter());
    pager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            boolean last = position == PHOTOS.length - 1;
            dots.set(PHOTOS.length, position);
            next.setText(last ? R.string.onboarding_start : R.string.action_next);
            skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
          }
        });
    dots.set(PHOTOS.length, 0);
    next.setOnClickListener(
        v -> {
          int i = pager.getCurrentItem();
          if (i < PHOTOS.length - 1) {
            pager.setCurrentItem(i + 1);
          } else {
            finish();
          }
        });
    skip.setOnClickListener(v -> finish());
  }

  private void finish() {
    container().prefs.setOnboarded();
    nav().setRoot(new LoginFragment());
  }

  private static final class PagesAdapter extends RecyclerView.Adapter<PagesAdapter.Holder> {
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new Holder(
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.item_onboarding_page, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
      h.photo.setImageResource(PHOTOS[position]);
      h.kicker.setText(String.format("0%d / 03", position + 1));
      h.title.setText(TITLES[position]);
      h.text.setText(TEXTS[position]);
    }

    @Override
    public int getItemCount() {
      return PHOTOS.length;
    }

    static final class Holder extends RecyclerView.ViewHolder {
      final ImageView photo;
      final TextView kicker;
      final TextView title;
      final TextView text;

      Holder(View v) {
        super(v);
        photo = v.findViewById(R.id.photo);
        kicker = v.findViewById(R.id.kicker);
        title = v.findViewById(R.id.title);
        text = v.findViewById(R.id.text);
      }
    }
  }
}
