package app.outfitshare.core.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.annotation.Nullable;
import app.outfitshare.BuildConfig;
import app.outfitshare.core.net.dto.Dto;
import app.outfitshare.core.session.Session;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit/OkHttp setup: bearer token, transparent refresh on 401, and an HTTP cache that serves
 * the last response for GET requests while offline (screens show "офлайн — показываем
 * сохранённое").
 */
public final class ApiClient {
  public static final String FROM_CACHE_HEADER = "X-From-Cache";

  private final Context context;
  private final Session session;
  private final Gson gson;
  private final OkHttpClient http;
  private String baseUrl;
  private Api api;

  public ApiClient(Context context, Session session, Gson gson, String baseUrl) {
    this.context = context.getApplicationContext();
    this.session = session;
    this.gson = gson;
    this.http =
        new OkHttpClient.Builder()
            .cache(new Cache(new File(context.getCacheDir(), "http"), 30L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .addInterceptor(this::authorize)
            .addInterceptor(this::offlineCache)
            .addNetworkInterceptor(ApiClient::storeGetResponses)
            .authenticator(new TokenRefresher())
            .build();
    setBaseUrl(baseUrl);
  }

  public void setBaseUrl(String url) {
    baseUrl = url.endsWith("/") ? url : url + "/";
    api =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(Api.class);
  }

  public Api api() {
    return api;
  }

  public OkHttpClient http() {
    return http;
  }

  public String baseUrl() {
    return baseUrl;
  }

  public Gson gson() {
    return gson;
  }

  public boolean isOnline() {
    ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
    if (cm == null) {
      return true;
    }
    NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
    return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }

  private Response authorize(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    String token = session.access();
    Request.Builder builder =
        request
            .newBuilder()
            .header("User-Agent", "OutfitShare-Android/" + BuildConfig.VERSION_NAME);
    if (token != null && !isPublicAuthPath(request.url().encodedPath())) {
      builder.header("Authorization", "Bearer " + token);
    }
    return chain.proceed(builder.build());
  }

  /** Endpoints that must not carry a (possibly expired) access token. */
  private static boolean isPublicAuthPath(String path) {
    return path.contains("/auth/refresh/")
        || path.contains("/auth/telegram/")
        || path.contains("/auth/dev/")
        || path.contains("/auth/logout/");
  }

  /** GET while offline → serve the cached copy (any age), tagged so the UI can show a banner. */
  private Response offlineCache(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    if (!"GET".equals(request.method())) {
      return chain.proceed(request);
    }
    if (!isOnline()) {
      Request cached =
          request
              .newBuilder()
              .cacheControl(
                  new CacheControl.Builder().onlyIfCached().maxStale(30, TimeUnit.DAYS).build())
              .build();
      Response response = chain.proceed(cached);
      if (response.code() == 504) {
        response.close();
        throw new IOException("offline");
      }
      return response.newBuilder().header(FROM_CACHE_HEADER, "1").build();
    }
    return chain.proceed(request);
  }

  /**
   * The API sends no cache headers: store every successful GET, always revalidated when online.
   * "public" is required because OkHttp skips caching authorized responses otherwise; the cache is
   * private to the app and wiped on sign-out.
   */
  private static Response storeGetResponses(Interceptor.Chain chain) throws IOException {
    Response response = chain.proceed(chain.request());
    if ("GET".equals(chain.request().method())
        && response.isSuccessful()
        && chain.request().url().encodedPath().startsWith("/api/")) {
      return response
          .newBuilder()
          .removeHeader("Pragma")
          .header("Cache-Control", "public, max-age=0")
          .build();
    }
    return response;
  }

  private final class TokenRefresher implements Authenticator {
    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, Response response) throws IOException {
      if (isPublicAuthPath(response.request().url().encodedPath()) || responseCount(response) > 1) {
        return null;
      }
      synchronized (ApiClient.this) {
        String current = session.access();
        String sent = response.request().header("Authorization");
        // Another request already refreshed the token: retry with the new one.
        if (current != null && sent != null && !sent.equals("Bearer " + current)) {
          return response
              .request()
              .newBuilder()
              .header("Authorization", "Bearer " + current)
              .build();
        }
        String refresh = session.refresh();
        if (refresh == null) {
          return null;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("refresh", refresh);
        retrofit2.Response<Dto.Tokens> result = api.refresh(body).execute();
        if (result.isSuccessful() && result.body() != null) {
          session.setTokens(result.body().access, result.body().refresh);
          return response
              .request()
              .newBuilder()
              .header("Authorization", "Bearer " + result.body().access)
              .build();
        }
        if (result.code() == 401 || result.code() == 403) {
          session.clear();
        }
        return null;
      }
    }

    private int responseCount(Response response) {
      int count = 1;
      while ((response = response.priorResponse()) != null) {
        count++;
      }
      return count;
    }
  }
}
