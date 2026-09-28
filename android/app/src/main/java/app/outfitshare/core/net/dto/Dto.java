package app.outfitshare.core.net.dto;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * API models. Field names map to snake_case JSON via Gson's LOWER_CASE_WITH_UNDERSCORES policy.
 * Mutable on purpose: screens update counters in place after optimistic actions.
 */
public final class Dto {
  private Dto() {}

  public static class Page<T> {
    public List<T> results = new ArrayList<>();
    @Nullable public String next;
  }

  public static class ErrorEnvelope {
    public ApiErrorBody error;
  }

  public static class ApiErrorBody {
    public String code;
    public String message;
    @Nullable public Map<String, Object> fields;
  }

  public static class AppConfig {
    public String botUsername;
    public boolean devLogin;
    public List<Integer> mannequinCanvas;
    public float freeLayerWidth = 0.4f;
  }

  public static class LoginStart {
    public String nonce;
    public String botUrl;
    public int expiresIn;
  }

  public static class Tokens {
    public String access;
    public String refresh;
    public String accessExpiresAt;
  }

  public static class AuthResult extends Tokens {
    public String status;
    public User user;
  }

  public static class User {
    public long id;
    public String username;
    public String name;
    @Nullable public String avatarUrl;
    public String avatarColor;
    public String role;
    // Present in lists of people.
    public boolean isFollowing;
    public boolean followsYou;
    public boolean isMe;
  }

  public static class Profile extends User {
    public String firstName;
    public String lastName;
    public String bio;
    public int outfitsCount;
    public int followersCount;
    public int followingCount;
    public boolean isAdmin;
  }

  public static class Item {
    public long id;
    public String name;
    public String category;
    public String categoryName;
    public String zone;
    public int defaultLayer;
    public String gender;
    public String style;
    public String styleName;
    public String color;
    public String season;
    public String brand;
    @Nullable public String price;
    public String buyLink;
    public String description;
    @Nullable public String imageUrl;
    public Fitted fitted = new Fitted();
    @Nullable public Float fitScore;

    @Nullable
    public String fittedFor(String gender) {
      return "female".equals(gender) ? fitted.female : fitted.male;
    }
  }

  public static class Fitted {
    @Nullable public String male;
    @Nullable public String female;
  }

  public static class Layer {
    public long itemId;
    public float x = 0.5f;
    public float y = 0.5f;
    public float scale = 1f;
    public float rotation;
    public int z;
    public boolean flipped;
    public boolean fitted;
  }

  public static class RemixRef {
    public long id;
    public User author;
  }

  public static class Outfit {
    public long id;
    public User author;
    @Nullable public String imageUrl;
    public String description;
    public List<String> hashtags = new ArrayList<>();
    public String mannequin;
    public String visibility;
    public int likesCount;
    public int commentsCount;
    public boolean isLiked;
    public boolean isSaved;
    public boolean isMine;
    public boolean authorFollowed;
    public String createdAt;
    @Nullable public RemixRef remixOf;
    public List<Item> items = new ArrayList<>();
    public List<Layer> layers = new ArrayList<>();
  }

  public static class OutfitPreview {
    public long id;
    @Nullable public String imageUrl;
    public User author;
    public String description;
  }

  public static class Comment {
    public long id;
    public long outfitId;
    @Nullable public Long parentId;
    public User author;
    public String text;
    public int likesCount;
    public boolean isLiked;
    public boolean isPostAuthor;
    public boolean canDelete;
    public String createdAt;
    // Local-only: a comment that failed to send.
    public transient boolean failed;
  }

  public static class CommentsPage {
    public List<Comment> results = new ArrayList<>();
    public int count;
  }

  public static class Mannequin {
    public String gender;
    public String name;
    @Nullable public String canvasUrl;
    public int width;
    public int height;
    public Map<String, List<Float>> anchors;
  }

  public static class Category {
    public long id;
    public String slug;
    public String name;
    public String zone;
    public int defaultLayer;
    public int order;
  }

  public static class LastMessage {
    public String text;
    public boolean isMine;
    public boolean isOutfit;
    public boolean isRead;
    public String createdAt;
  }

  public static class Dialog {
    public long id;
    public User user;
    @Nullable public LastMessage lastMessage;
    public int unreadCount;
    public boolean pinned;
    public String updatedAt;
    // Local-only: the other side is typing.
    public transient boolean typing;
  }

  public static class Message {
    public long id;
    public long dialogId;
    public long senderId;
    public boolean isMine;
    public String text;
    @Nullable public OutfitPreview outfit;
    public String createdAt;
    public boolean edited;
    public boolean isRead;
    // Local-only states for optimistic sending.
    public transient boolean pending;
    public transient boolean failed;
    public transient long localId;
  }

  public static class MessagesPage extends Page<Message> {
    @Nullable public User user;
  }

  public static class Notification {
    public long id;
    public String type;
    public User actor;
    @Nullable public OutfitPreview outfit;
    @Nullable public CommentRef comment;
    public boolean isRead;
    public String createdAt;
  }

  public static class CommentRef {
    public long id;
    public String text;
  }

  public static class Collection {
    public long id;
    public String title;
    public boolean isDefault;
    public boolean isPrivate;
    public int count;
    public List<String> covers = new ArrayList<>();
    public String updatedAt;
  }

  public static class CollectionPage extends Page<Outfit> {
    public Collection collection;
  }

  public static class Hashtag {
    public String tag;
    public int count;
  }

  public static class NewCount {
    public int newCount;
  }

  public static class Counters {
    public int notifications;
    public int messages;
  }

  public static class LikeResult {
    public boolean isLiked;
    public int likesCount;
  }

  public static class FollowResult {
    public boolean isFollowing;
    public int followersCount;
  }

  public static class SaveResult {
    public boolean isSaved;
    @Nullable public Long collectionId;
  }

  public static class PinResult {
    public boolean pinned;
  }

  public static class AvatarResult {
    @Nullable public String avatarUrl;
  }

  public static class JobsPage extends Page<GarmentJob> {
    public Map<String, Integer> totals;
  }

  public static class GarmentJob {
    public long id;
    public String status;
    public String stage;
    public List<String> stages = new ArrayList<>();
    public int progress;
    public String error;
    public String category;
    public String categoryName;
    public String zone;
    public String gender;
    @Nullable public String sourceUrl;
    @Nullable public String cutoutUrl;
    public Fitted fitted = new Fitted();
    public Fitted preview = new Fitted();
    public Map<String, Object> attributes;
    public Map<String, List<Float>> keypoints;
    @Nullable public Float fitScore;
    public float fitThreshold = 0.6f;
    @Nullable public Long itemId;
    public String createdAt;
  }

  public static class PublishResult {
    public GarmentJob job;
    public Item item;
  }
}
