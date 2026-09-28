package app.outfitshare.core.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.outfitshare.core.designsystem.theme.DsTheme;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Caption styling: #hashtags in the accent colour, clickable. */
public final class Text {
  private static final Pattern HASHTAG = Pattern.compile("#[\\wа-яёА-ЯЁ]{2,40}");

  private Text() {}

  public static CharSequence caption(
      Context context,
      @Nullable String text,
      @Nullable List<String> extraTags,
      @Nullable Consumer<String> onTag) {
    SpannableStringBuilder sb = new SpannableStringBuilder(text == null ? "" : text);
    if (extraTags != null) {
      for (String tag : extraTags) {
        if (sb.toString().toLowerCase().contains("#" + tag.toLowerCase())) {
          continue;
        }
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append('#').append(tag);
      }
    }
    int accent = DsTheme.color(context, app.outfitshare.core.designsystem.R.attr.dsColorAccent);
    Matcher m = HASHTAG.matcher(sb);
    while (m.find()) {
      final String tag = m.group().substring(1);
      if (onTag != null) {
        sb.setSpan(
            new ClickableSpan() {
              @Override
              public void onClick(@NonNull View widget) {
                onTag.accept(tag);
              }

              @Override
              public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(accent);
                ds.setUnderlineText(false);
              }
            },
            m.start(),
            m.end(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else {
        sb.setSpan(
            new ForegroundColorSpan(accent), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return sb;
  }

  /** "<b>name</b> rest" with the name in semibold (notifications). */
  public static CharSequence boldLead(String lead, String rest) {
    SpannableStringBuilder sb = new SpannableStringBuilder(lead);
    sb.setSpan(
        new StyleSpan(android.graphics.Typeface.BOLD),
        0,
        lead.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    sb.append(rest);
    return sb;
  }
}
