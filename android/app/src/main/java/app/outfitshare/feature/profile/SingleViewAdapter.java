package app.outfitshare.feature.profile;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** A RecyclerView adapter showing one prepared view (drafts tab). */
final class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final View view;

  SingleViewAdapter(View view) {
    this.view = view;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (view.getParent() instanceof ViewGroup) {
      ((ViewGroup) view.getParent()).removeView(view);
    }
    view.setLayoutParams(
        new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return new RecyclerView.ViewHolder(view) {};
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}

  @Override
  public int getItemCount() {
    return 1;
  }
}
