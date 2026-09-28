package app.outfitshare.core.net;

import app.outfitshare.core.net.dto.Dto;
import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Mobile API v1 (backend/mobile/urls.py). */
public interface Api {

  // Auth
  @GET("api/v1/config/")
  Call<Dto.AppConfig> config();

  @POST("api/v1/auth/telegram/start/")
  Call<Dto.LoginStart> telegramStart();

  @POST("api/v1/auth/telegram/poll/")
  Call<Dto.AuthResult> telegramPoll(@Body Map<String, Object> body);

  @POST("api/v1/auth/dev/")
  Call<Dto.AuthResult> devLogin(@Body Map<String, Object> body);

  @POST("api/v1/auth/refresh/")
  Call<Dto.Tokens> refresh(@Body Map<String, Object> body);

  @POST("api/v1/auth/logout/")
  Call<Void> logout(@Body Map<String, Object> body);

  // Me & users
  @GET("api/v1/me/")
  Call<Dto.Profile> me();

  @PATCH("api/v1/me/")
  Call<Dto.Profile> updateMe(@Body Map<String, Object> body);

  @Multipart
  @POST("api/v1/me/avatar/")
  Call<Dto.AvatarResult> uploadAvatar(@Part MultipartBody.Part avatar);

  @POST("api/v1/me/avatar/")
  Call<Dto.AvatarResult> avatarFromTelegram(@Body Map<String, Object> body);

  @DELETE("api/v1/me/avatar/")
  Call<Void> deleteAvatar();

  @GET("api/v1/users/search/")
  Call<Dto.Page<Dto.User>> searchUsers(@Query("q") String q);

  @GET("api/v1/users/{id}/")
  Call<Dto.Profile> user(@Path("id") long id);

  @GET("api/v1/users/{id}/outfits/")
  Call<Dto.Page<Dto.Outfit>> userOutfits(@Path("id") long id, @Query("before") String before);

  @GET("api/v1/users/{id}/{kind}/")
  Call<Dto.Page<Dto.User>> connections(
      @Path("id") long id, @Path("kind") String kind, @Query("q") String q, @Query("before") String before);

  @POST("api/v1/users/{id}/follow/")
  Call<Dto.FollowResult> follow(@Path("id") long id);

  @DELETE("api/v1/users/{id}/follow/")
  Call<Dto.FollowResult> unfollow(@Path("id") long id);

  // Feed & outfits
  @GET("api/v1/feed/")
  Call<Dto.Page<Dto.Outfit>> feed(
      @Query("tab") String tab, @Query("page") String page, @Query("before") String before);

  @GET("api/v1/feed/")
  Call<Dto.NewCount> feedNewCount(@Query("tab") String tab, @Query("since") long since);

  @POST("api/v1/outfits/")
  Call<Dto.Outfit> createOutfit(@Body Map<String, Object> body);

  @GET("api/v1/outfits/search/")
  Call<Dto.Page<Dto.Outfit>> searchOutfits(@Query("q") String q, @Query("before") String before);

  @GET("api/v1/outfits/{id}/")
  Call<Dto.Outfit> outfit(@Path("id") long id);

  @DELETE("api/v1/outfits/{id}/")
  Call<Void> deleteOutfit(@Path("id") long id);

  @GET("api/v1/outfits/{id}/similar/")
  Call<Dto.Page<Dto.Outfit>> similarOutfits(@Path("id") long id);

  @POST("api/v1/outfits/{id}/like/")
  Call<Dto.LikeResult> like(@Path("id") long id);

  @DELETE("api/v1/outfits/{id}/like/")
  Call<Dto.LikeResult> unlike(@Path("id") long id);

  @POST("api/v1/outfits/{id}/save/")
  Call<Dto.SaveResult> save(@Path("id") long id, @Body Map<String, Object> body);

  @DELETE("api/v1/outfits/{id}/save/")
  Call<Dto.SaveResult> unsave(@Path("id") long id);

  @GET("api/v1/outfits/{id}/comments/")
  Call<Dto.CommentsPage> comments(@Path("id") long id);

  @POST("api/v1/outfits/{id}/comments/")
  Call<Dto.Comment> addComment(@Path("id") long id, @Body Map<String, Object> body);

  @DELETE("api/v1/comments/{id}/")
  Call<Void> deleteComment(@Path("id") long id);

  @POST("api/v1/comments/{id}/like/")
  Call<Dto.LikeResult> likeComment(@Path("id") long id);

  @DELETE("api/v1/comments/{id}/like/")
  Call<Dto.LikeResult> unlikeComment(@Path("id") long id);

  @GET("api/v1/hashtags/trending/")
  Call<Dto.Page<Dto.Hashtag>> trendingHashtags();

  // Catalog
  @GET("api/v1/catalog/mannequins/")
  Call<Dto.Page<Dto.Mannequin>> mannequins();

  @GET("api/v1/catalog/categories/")
  Call<Dto.Page<Dto.Category>> categories();

  @GET("api/v1/catalog/items/")
  Call<Dto.Page<Dto.Item>> items(
      @Query("category") String category,
      @Query("gender") String gender,
      @Query("q") String q,
      @Query("before") String before);

  @GET("api/v1/catalog/items/{id}/similar/")
  Call<Dto.Page<Dto.Item>> similarItems(@Path("id") long id);

  // Collections
  @GET("api/v1/collections/")
  Call<Dto.Page<Dto.Collection>> collections(@Query("user_id") Long userId);

  @POST("api/v1/collections/")
  Call<Dto.Collection> createCollection(@Body Map<String, Object> body);

  @GET("api/v1/collections/{id}/")
  Call<Dto.CollectionPage> collection(@Path("id") long id, @Query("before") String before);

  @DELETE("api/v1/collections/{id}/")
  Call<Void> deleteCollection(@Path("id") long id);

  // Messaging
  @GET("api/v1/dialogs/")
  Call<Dto.Page<Dto.Dialog>> dialogs();

  @POST("api/v1/dialogs/")
  Call<Dto.Dialog> openDialog(@Body Map<String, Object> body);

  @DELETE("api/v1/dialogs/{id}/")
  Call<Void> deleteDialog(@Path("id") long id);

  @POST("api/v1/dialogs/{id}/pin/")
  Call<Dto.PinResult> pinDialog(@Path("id") long id, @Body Map<String, Object> body);

  @POST("api/v1/dialogs/{id}/read/")
  Call<Void> readDialog(@Path("id") long id);

  @GET("api/v1/dialogs/{id}/messages/")
  Call<Dto.MessagesPage> messages(
      @Path("id") long id, @Query("before") String before, @Query("after") String after);

  @POST("api/v1/dialogs/{id}/messages/")
  Call<Dto.Message> sendMessage(@Path("id") long id, @Body Map<String, Object> body);

  @PATCH("api/v1/messages/{id}/")
  Call<Dto.Message> editMessage(@Path("id") long id, @Body Map<String, Object> body);

  @DELETE("api/v1/messages/{id}/")
  Call<Void> deleteMessage(@Path("id") long id);

  // Notifications
  @GET("api/v1/notifications/")
  Call<Dto.Page<Dto.Notification>> notifications(@Query("before") String before);

  @POST("api/v1/notifications/read/")
  Call<Void> readNotifications();

  @GET("api/v1/counters/")
  Call<Dto.Counters> counters();

  @POST("api/v1/reports/")
  Call<Void> report(@Body Map<String, Object> body);

  // Studio
  @GET("api/v1/studio/jobs/")
  Call<Dto.JobsPage> jobs(@Query("status") String status);

  @Multipart
  @POST("api/v1/studio/jobs/")
  Call<Dto.GarmentJob> createJob(
      @Part MultipartBody.Part photo,
      @Part("category") RequestBody category,
      @Part("gender") RequestBody gender);

  @GET("api/v1/studio/jobs/{id}/")
  Call<Dto.GarmentJob> job(@Path("id") long id);

  @DELETE("api/v1/studio/jobs/{id}/")
  Call<Void> deleteJob(@Path("id") long id);

  @POST("api/v1/studio/jobs/{id}/retry/")
  Call<Dto.GarmentJob> retryJob(@Path("id") long id);

  @POST("api/v1/studio/jobs/{id}/refit/")
  Call<Dto.GarmentJob> refitJob(@Path("id") long id, @Body Map<String, Object> body);

  @POST("api/v1/studio/jobs/{id}/publish/")
  Call<Dto.PublishResult> publishJob(@Path("id") long id, @Body Map<String, Object> body);

  @HTTP(method = "DELETE", path = "api/v1/outfits/{id}/save/", hasBody = true)
  Call<Dto.SaveResult> unsaveFrom(@Path("id") long id, @Body Map<String, Object> body);
}
