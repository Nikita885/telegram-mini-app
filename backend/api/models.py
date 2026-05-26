from django.contrib.auth.models import AbstractUser
from django.db import models


class CustomUser(AbstractUser):
    email = models.EmailField(unique=True)
    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["username"]

    def __str__(self):
        return self.email


class TelegramUser(models.Model):
    telegram_id = models.BigIntegerField(unique=True, verbose_name="Telegram ID")
    username = models.CharField(max_length=255, blank=True, null=True)
    first_name = models.CharField(max_length=255, blank=True, null=True)
    last_name = models.CharField(max_length=255, blank=True, null=True)
    language_code = models.CharField(max_length=10, blank=True, null=True)
    avatar = models.ImageField(upload_to='avatars/', blank=True, null=True)
    avatar_random_color = models.CharField(max_length=7, blank=True, null=True)

    def __str__(self):
        return self.username or self.first_name or str(self.telegram_id)


class Follow(models.Model):
    follower = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='following')
    following = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='followers')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('follower', 'following')


class Post(models.Model):
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='posts')
    text = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)


class Dialog(models.Model):
    """
    Диалог между двумя пользователями.
    Для уникальности всегда храним: user1.telegram_id < user2.telegram_id.
    """
    user1 = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='dialogs_as_user1')
    user2 = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='dialogs_as_user2')
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)  # обновляется при каждом новом сообщении
    pinned = models.BooleanField(default=False)

    class Meta:
        unique_together = ('user1', 'user2')
        ordering = ['-pinned', '-updated_at']  # Закрепленные сверху

    def get_other_user(self, current_user):
        return self.user2 if self.user1 == current_user else self.user1

    def __str__(self):
        return f"Dialog({self.user1} ↔ {self.user2})"


class Message(models.Model):
    dialog = models.ForeignKey(Dialog, on_delete=models.CASCADE, related_name='messages')
    sender = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='sent_messages')
    text = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    is_read = models.BooleanField(default=False)
    edited = models.BooleanField(default=False)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f"[{self.dialog_id}] {self.sender}: {self.text[:40]}"
    
# Добавить в конец файла models.py после класса Message:

class Mannequin(models.Model):
    GENDER_CHOICES = [
        ('male', 'Мужской'),
        ('female', 'Женский'),
    ]
    gender = models.CharField(max_length=10, choices=GENDER_CHOICES, unique=True)
    image = models.ImageField(upload_to='mannequins/')

    class Meta:
        ordering = ['gender']

    def __str__(self):
        return self.get_gender_display()


class ClothingCategory(models.Model):
    """Категории одежды"""
    CATEGORY_CHOICES = [
        ('top', 'Верхняя одежда'),
        ('shirt', 'Рубашки и футболки'),
        ('pants', 'Брюки'),
        ('dress', 'Платья'),
        ('skirt', 'Юбки'),
        ('shoes', 'Обувь'),
        ('accessories', 'Аксессуары'),
        ('hat', 'Головные уборы'),
    ]
    
    name = models.CharField(max_length=50, choices=CATEGORY_CHOICES, unique=True)
    icon_class = models.CharField(max_length=50, default='ri-shirt-line', blank=True)
    icon_svg = models.FileField(upload_to='category_icons/', blank=True, null=True, verbose_name='SVG иконка')
    order = models.IntegerField(default=0)
    
    class Meta:
        ordering = ['order']
    
    def __str__(self):
        return self.get_name_display()


class ClothingItem(models.Model):
    """Предмет одежды"""
    GENDER_CHOICES = [
        ('male', 'Мужское'),
        ('female', 'Женское'),
        ('unisex', 'Унисекс'),
    ]
    
    STYLE_CHOICES = [
        ('casual', 'Casual'),
        ('formal', 'Formal'),
        ('sport', 'Sport'),
        ('street', 'Street'),
        ('business', 'Business'),
    ]
    
    category = models.ForeignKey(ClothingCategory, on_delete=models.CASCADE, related_name='items')
    name = models.CharField(max_length=255)
    image = models.ImageField(upload_to='clothing/')

    gender = models.CharField(max_length=10, choices=GENDER_CHOICES)
    style = models.CharField(max_length=20, choices=STYLE_CHOICES, blank=True)
    color = models.CharField(max_length=50, blank=True)
    tags = models.CharField(max_length=500, blank=True)
    item_description = models.TextField(blank=True, verbose_name='Описание')
    buy_link = models.URLField(max_length=500, blank=True, null=True, verbose_name='Где купить (ссылка)')

    created_at = models.DateTimeField(auto_now_add=True)
    
    class Meta:
        ordering = ['-created_at']
    
    def __str__(self):
        return f"{self.name} ({self.get_gender_display()})"


class Hashtag(models.Model):
    """Хештеги"""
    tag = models.CharField(max_length=100, unique=True)
    usage_count = models.IntegerField(default=0)
    
    def __str__(self):
        return f"#{self.tag}"


class OutfitPost(models.Model):
    """Пост с образом"""
    MANNEQUIN_CHOICES = [
        ('male', 'Мужской'),
        ('female', 'Женский'),
    ]
    
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='outfit_posts')
    mannequin_type = models.CharField(max_length=10, choices=MANNEQUIN_CHOICES)
    description = models.TextField(blank=True)
    hashtags = models.ManyToManyField(Hashtag, blank=True, related_name='posts')
    final_image = models.ImageField(upload_to='outfits/', blank=True, null=True)
    
    created_at = models.DateTimeField(auto_now_add=True)
    likes_count = models.IntegerField(default=0)
    
    class Meta:
        ordering = ['-created_at']
    
    def __str__(self):
        return f"Outfit by {self.user} - {self.created_at.strftime('%Y-%m-%d')}"


class PostClothingItem(models.Model):
    """Одежда в посте с трансформацией"""
    post = models.ForeignKey(OutfitPost, on_delete=models.CASCADE, related_name='items')
    clothing = models.ForeignKey(ClothingItem, on_delete=models.CASCADE)

    position_x = models.FloatField(default=0)
    position_y = models.FloatField(default=0)
    scale = models.FloatField(default=1.0)
    rotation = models.FloatField(default=0)
    z_index = models.IntegerField(default=0)

    class Meta:
        ordering = ['z_index']

    def __str__(self):
        return f"{self.clothing.name} in {self.post}"


class PostLike(models.Model):
    post = models.ForeignKey(OutfitPost, on_delete=models.CASCADE, related_name='likes')
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='liked_posts')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('post', 'user')

    def __str__(self):
        return f"{self.user} liked {self.post}"


class PostComment(models.Model):
    post = models.ForeignKey(OutfitPost, on_delete=models.CASCADE, related_name='comments')
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='post_comments')
    text = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    likes_count = models.IntegerField(default=0)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f"{self.user}: {self.text[:40]}"


class CommentLike(models.Model):
    comment = models.ForeignKey(PostComment, on_delete=models.CASCADE, related_name='likes')
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='comment_likes')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('comment', 'user')

    def __str__(self):
        return f"{self.user} liked comment {self.comment_id}"


class Notification(models.Model):
    NOTIF_TYPES = [
        ('like', 'Лайк'),
        ('comment', 'Комментарий'),
        ('follow', 'Подписка'),
    ]
    recipient = models.ForeignKey(
        TelegramUser, on_delete=models.CASCADE, related_name='notifications'
    )
    sender = models.ForeignKey(
        TelegramUser, on_delete=models.CASCADE, related_name='sent_notifications'
    )
    notif_type = models.CharField(max_length=20, choices=NOTIF_TYPES)
    post = models.ForeignKey(
        'OutfitPost', on_delete=models.CASCADE, null=True, blank=True,
        related_name='notifications'
    )
    comment = models.ForeignKey(
        'PostComment', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='notifications'
    )
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Notif[{self.notif_type}] → {self.recipient}"