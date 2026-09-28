package app.outfitshare.feature.studio;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import app.outfitshare.App;
import app.outfitshare.core.net.dto.Dto;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import retrofit2.Response;

/** Uploads a Studio photo in the background; survives leaving the screen, waits for the network. */
public class UploadWorker extends Worker {
  public static final String TAG = "studio_upload";
  public static final String KEY_FILE = "file";
  public static final String KEY_CATEGORY = "category";
  public static final String KEY_CATEGORY_NAME = "category_name";
  public static final String KEY_GENDER = "gender";
  public static final String KEY_PROGRESS = "progress";
  public static final String KEY_SENT = "sent";
  public static final String KEY_TOTAL = "total";
  public static final String KEY_JOB_ID = "job_id";

  public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  public static void enqueue(
      Context context, File photo, String category, String categoryName, String gender) {
    Data input =
        new Data.Builder()
            .putString(KEY_FILE, photo.getAbsolutePath())
            .putString(KEY_CATEGORY, category)
            .putString(KEY_CATEGORY_NAME, categoryName)
            .putString(KEY_GENDER, gender)
            .build();
    OneTimeWorkRequest request =
        new OneTimeWorkRequest.Builder(UploadWorker.class)
            .setInputData(input)
            .addTag(TAG)
            .addTag("file:" + photo.getAbsolutePath())
            .addTag("name:" + categoryName)
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build();
    WorkManager.getInstance(context).enqueue(request);
  }

  @NonNull
  @Override
  public Result doWork() {
    File file = new File(getInputData().getString(KEY_FILE));
    if (!file.exists()) {
      return Result.failure();
    }
    long total = file.length();
    RequestBody raw = RequestBody.create(file, MediaType.get("image/jpeg"));
    RequestBody counting =
        new CountingBody(
            raw,
            sent ->
                setProgressAsync(
                    new Data.Builder()
                        .putInt(KEY_PROGRESS, (int) (100 * sent / Math.max(total, 1)))
                        .putLong(KEY_SENT, sent)
                        .putLong(KEY_TOTAL, total)
                        .build()));
    MultipartBody.Part photo = MultipartBody.Part.createFormData("photo", file.getName(), counting);
    RequestBody category =
        RequestBody.create(getInputData().getString(KEY_CATEGORY), MediaType.get("text/plain"));
    RequestBody gender =
        RequestBody.create(getInputData().getString(KEY_GENDER), MediaType.get("text/plain"));
    try {
      Response<Dto.GarmentJob> response =
          App.get().container().api.api().createJob(photo, category, gender).execute();
      if (response.isSuccessful() && response.body() != null) {
        file.delete();
        return Result.success(new Data.Builder().putLong(KEY_JOB_ID, response.body().id).build());
      }
      if (response.code() >= 500 || response.code() == 429) {
        return Result.retry();
      }
      return Result.failure(
          new Data.Builder().putString("error", "http_" + response.code()).build());
    } catch (IOException e) {
      return Result.retry();
    }
  }

  interface Progress {
    void onBytes(long sent);
  }

  /** Reports upload progress while OkHttp writes the body. */
  static final class CountingBody extends RequestBody {
    private final RequestBody delegate;
    private final Progress progress;

    CountingBody(RequestBody delegate, Progress progress) {
      this.delegate = delegate;
      this.progress = progress;
    }

    @Override
    public MediaType contentType() {
      return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
      return delegate.contentLength();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
      final long[] sent = {0};
      final long[] lastReport = {0};
      BufferedSink counting =
          Okio.buffer(
              new ForwardingSink(sink) {
                @Override
                public void write(@NonNull Buffer source, long byteCount) throws IOException {
                  super.write(source, byteCount);
                  sent[0] += byteCount;
                  if (sent[0] - lastReport[0] > 64 * 1024) {
                    lastReport[0] = sent[0];
                    progress.onBytes(sent[0]);
                  }
                }
              });
      delegate.writeTo(counting);
      counting.flush();
      progress.onBytes(sent[0]);
    }
  }
}
