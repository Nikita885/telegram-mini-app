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

# ══════════════════════════════════════════════════════════════════════════════
#  ОДЕЖДА И ТЕГИ
# ══════════════════════════════════════════════════════════════════════════════

class ClothingCategory(models.Model):
    """Категории одежды: верх, низ, обувь, аксессуары"""
    name = models.CharField(max_length=100, unique=True)
    slug = models.SlugField(unique=True)
    order = models.IntegerField(default=0)  # порядок отображения
    icon = models.CharField(max_length=50, blank=True)  # иконка RemixIcon
    
    class Meta:
        ordering = ['order', 'name']
        verbose_name = 'Категория одежды'
        verbose_name_plural = 'Категории одежды'
    
    def __str__(self):
        return self.name


class ClothingTag(models.Model):
    """Теги для одежды: цвет, стиль, сезон и т.д."""
    TAG_TYPES = [
        ('color', 'Цвет'),
        ('material', 'Материал'),
        ('style', 'Стиль'),
        ('season', 'Сезон'),
        ('brand', 'Бренд'),
        ('occasion', 'Повод'),
    ]
    
    name = models.CharField(max_length=100)
    slug = models.SlugField()
    tag_type = models.CharField(max_length=20, choices=TAG_TYPES)
    color_hex = models.CharField(max_length=7, blank=True, null=True)  # для цветов
    
    class Meta:
        unique_together = ['slug', 'tag_type']
        ordering = ['tag_type', 'name']
        verbose_name = 'Тег'
        verbose_name_plural = 'Теги'
    
    def __str__(self):
        return f"{self.get_tag_type_display()}: {self.name}"


class ClothingItem(models.Model):
    """Предмет одежды в базе данных"""
    name = models.CharField(max_length=255)
    category = models.ForeignKey(ClothingCategory, on_delete=models.CASCADE, related_name='items')
    
    # Изображение одежды (PNG с прозрачным фоном!)
    image = models.ImageField(upload_to='clothing/')
    thumbnail = models.ImageField(upload_to='clothing/thumbs/', blank=True, null=True)
    
    # Теги (автоматические от нейронки + ручные)
    tags = models.ManyToManyField(ClothingTag, related_name='items', blank=True)
    
    # Для правильного позиционирования на манекене
    default_z_index = models.IntegerField(default=1)  # слой по умолчанию
    
    # Метаданные
    created_at = models.DateTimeField(auto_now_add=True)
    is_active = models.BooleanField(default=True)
    
    class Meta:
        ordering = ['-created_at']
        verbose_name = 'Предмет одежды'
        verbose_name_plural = 'Предметы одежды'
    
    def __str__(self):
        return f"{self.category.name}: {self.name}"


# ══════════════════════════════════════════════════════════════════════════════
#  ОБРАЗЫ (OUTFITS)
# ══════════════════════════════════════════════════════════════════════════════

class Outfit(models.Model):
    """Образ, созданный пользователем"""
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='outfits')
    
    # Название и описание
    title = models.CharField(max_length=255, blank=True)
    description = models.TextField(blank=True)
    
    # Превью образа (screenshot конструктора)
    preview_image = models.ImageField(upload_to='outfits/previews/', blank=True, null=True)
    
    # Статистика
    likes_count = models.IntegerField(default=0)
    comments_count = models.IntegerField(default=0)
    views_count = models.IntegerField(default=0)
    
    # Метаданные
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    is_public = models.BooleanField(default=True)
    
    # Для рекомендаций
    tags = models.ManyToManyField(ClothingTag, related_name='outfits', blank=True)
    
    class Meta:
        ordering = ['-created_at']
        verbose_name = 'Образ'
        verbose_name_plural = 'Образы'
    
    def __str__(self):
        return f"{self.user.username or self.user.telegram_id}: {self.title or 'Образ #' + str(self.id)}"


class OutfitItem(models.Model):
    """Предмет одежды в составе образа"""
    outfit = models.ForeignKey(Outfit, on_delete=models.CASCADE, related_name='items')
    clothing_item = models.ForeignKey(ClothingItem, on_delete=models.CASCADE)
    
    # Позиция на канвасе (JSON)
    # Формат: {"x": 100, "y": 200, "scale": 1.2, "rotation": 0, "z_index": 3}
    position_data = models.JSONField(default=dict)
    
    # Порядок добавления
    order = models.IntegerField(default=0)
    
    class Meta:
        ordering = ['order']
        verbose_name = 'Предмет в образе'
        verbose_name_plural = 'Предметы в образе'
    
    def __str__(self):
        return f"{self.outfit.title}: {self.clothing_item.name}"


# ══════════════════════════════════════════════════════════════════════════════
#  СОЦИАЛЬНЫЕ ВЗАИМОДЕЙСТВИЯ
# ══════════════════════════════════════════════════════════════════════════════

class OutfitLike(models.Model):
    """Лайк на образ"""
    outfit = models.ForeignKey(Outfit, on_delete=models.CASCADE, related_name='likes')
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='outfit_likes')
    created_at = models.DateTimeField(auto_now_add=True)
    
    class Meta:
        unique_together = ['outfit', 'user']
        verbose_name = 'Лайк'
        verbose_name_plural = 'Лайки'
    
    def __str__(self):
        return f"{self.user.username} → {self.outfit.title}"


class OutfitComment(models.Model):
    """Комментарий к образу"""
    outfit = models.ForeignKey(Outfit, on_delete=models.CASCADE, related_name='comments')
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='outfit_comments')
    text = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    
    # Для вложенных комментариев
    parent = models.ForeignKey('self', on_delete=models.CASCADE, null=True, blank=True, related_name='replies')
    
    class Meta:
        ordering = ['created_at']
        verbose_name = 'Комментарий'
        verbose_name_plural = 'Комментарии'
    
    def __str__(self):
        return f"{self.user.username}: {self.text[:50]}"


class OutfitView(models.Model):
    """Просмотр образа (для аналитики и рекомендаций)"""
    outfit = models.ForeignKey(Outfit, on_delete=models.CASCADE, related_name='outfit_views')
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='viewed_outfits')
    viewed_at = models.DateTimeField(auto_now_add=True)
    duration_seconds = models.IntegerField(default=0)  # сколько смотрел
    
    class Meta:
        verbose_name = 'Просмотр'
        verbose_name_plural = 'Просмотры'
    
    def __str__(self):
        return f"{self.user.username} → {self.outfit.title}"


# ══════════════════════════════════════════════════════════════════════════════
#  КОЛЛЕКЦИИ (опционально)
# ══════════════════════════════════════════════════════════════════════════════

class OutfitCollection(models.Model):
    """Коллекция образов (типа Pinterest board)"""
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='collections')
    name = models.CharField(max_length=255)
    description = models.TextField(blank=True)
    outfits = models.ManyToManyField(Outfit, related_name='collections', blank=True)
    
    is_public = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    
    class Meta:
        ordering = ['-created_at']
        verbose_name = 'Коллекция'
        verbose_name_plural = 'Коллекции'
    
    def __str__(self):
        return f"{self.user.username}: {self.name}"
    

class OutfitAnalytics(models.Model):
    """Аналитика образов"""
    outfit = models.OneToOneField(Outfit, on_delete=models.CASCADE, related_name='analytics')
    
    # Engagement метрики
    engagement_rate = models.FloatField(default=0)  # (likes + comments) / views
    avg_view_duration = models.FloatField(default=0)  # средняя продолжительность просмотра
    
    # Вирусность
    shares_count = models.IntegerField(default=0)
    saves_count = models.IntegerField(default=0)
    
    # Демография
    top_viewer_age_group = models.CharField(max_length=20, blank=True)
    top_viewer_country = models.CharField(max_length=2, blank=True)
    
    updated_at = models.DateTimeField(auto_now=True)