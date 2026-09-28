from django.contrib.auth.models import AbstractUser
from django.db import models
from django.utils import timezone
from django.core.files.base import ContentFile
import io
import os


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

    ROLE_USER = 'user'
    ROLE_MODERATOR = 'moderator'
    ROLE_ADMIN = 'admin'
    ROLE_CHOICES = [
        (ROLE_USER, 'Пользователь'),
        (ROLE_MODERATOR, 'Модератор'),
        (ROLE_ADMIN, 'Администратор'),
    ]
    role = models.CharField(max_length=16, choices=ROLE_CHOICES, default=ROLE_USER)
    bio = models.CharField(max_length=150, blank=True, default='')
    is_banned = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True, null=True)

    @property
    def is_admin(self):
        return self.role == self.ROLE_ADMIN

    @property
    def is_moderator(self):
        return self.role in (self.ROLE_ADMIN, self.ROLE_MODERATOR)

    @property
    def display_name(self):
        full = ' '.join(p for p in (self.first_name, self.last_name) if p)
        return full or self.username or f'id{self.telegram_id}'

    def __str__(self):
        return self.username or self.first_name or str(self.telegram_id)

    @classmethod
    def from_telegram(cls, data):
        """Create or refresh a user from Telegram-verified data (bot update or WebApp initData).

        A Telegram @username is proven by Telegram, so its owner always gets it: anyone who picked
        the same name in the app (possibly to impersonate them) loses it at that moment. Empty values
        from Telegram never erase what the user set in the app.
        """
        import random

        from django.db import transaction

        tg_username = (data.get('username') or '').strip() or None
        with transaction.atomic():
            user, created = cls.objects.get_or_create(
                telegram_id=int(data['id']),
                defaults={
                    'first_name': data.get('first_name'),
                    'last_name': data.get('last_name'),
                    'language_code': data.get('language_code'),
                    'avatar_random_color': '#{:06x}'.format(random.randint(0x404040, 0xC0C0C0)),
                },
            )
            if tg_username:
                cls.objects.filter(username__iexact=tg_username).exclude(pk=user.pk).update(username=None)
            changed = []
            for field, value in (
                ('username', tg_username),
                ('first_name', data.get('first_name')),
                ('last_name', data.get('last_name')),
                ('language_code', data.get('language_code')),
            ):
                if value and getattr(user, field) != value:
                    setattr(user, field, value)
                    changed.append(field)
            if changed:
                user.save(update_fields=changed)
        return user


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

    Закрепление и удаление — у каждого участника своё: «удалить диалог» скрывает переписку только
    у того, кто удалил (userN_cleared_at), собеседник её по-прежнему видит. Сообщения стираются из
    базы, когда их скрыли оба.
    """
    user1 = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='dialogs_as_user1')
    user2 = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='dialogs_as_user2')
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)  # обновляется при каждом новом сообщении
    user1_pinned = models.BooleanField(default=False)
    user2_pinned = models.BooleanField(default=False)
    user1_cleared_at = models.DateTimeField(null=True, blank=True)
    user2_cleared_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        unique_together = ('user1', 'user2')
        ordering = ['-updated_at']

    def get_other_user(self, current_user):
        return self.user2 if self.user1 == current_user else self.user1

    def _side(self, user):
        user_id = getattr(user, 'pk', user)
        if user_id == self.user1_id:
            return 'user1'
        if user_id == self.user2_id:
            return 'user2'
        raise ValueError(f'{user} is not a participant of {self}')

    def pinned_for(self, user):
        return getattr(self, f'{self._side(user)}_pinned')

    def set_pinned(self, user, pinned):
        field = f'{self._side(user)}_pinned'
        setattr(self, field, bool(pinned))
        # update() keeps updated_at (the "last activity" order) untouched.
        Dialog.objects.filter(pk=self.pk).update(**{field: bool(pinned)})

    def cleared_at_for(self, user):
        return getattr(self, f'{self._side(user)}_cleared_at')

    def visible_messages(self, user):
        """Messages this participant still sees (newer than their last «удалить диалог»)."""
        qs = self.messages.all()
        cleared = self.cleared_at_for(user)
        return qs.filter(created_at__gt=cleared) if cleared else qs

    def clear_for(self, user):
        """«Удалить диалог» for one participant; the other one keeps the conversation."""
        side = self._side(user)
        now = timezone.now()
        setattr(self, f'{side}_cleared_at', now)
        setattr(self, f'{side}_pinned', False)
        Dialog.objects.filter(pk=self.pk).update(**{f'{side}_cleared_at': now, f'{side}_pinned': False})
        if self.user1_cleared_at and self.user2_cleared_at:
            # Nobody can see these any more: remove them for good.
            self.messages.filter(created_at__lte=min(self.user1_cleared_at, self.user2_cleared_at)).delete()

    def __str__(self):
        return f"Dialog({self.user1} ↔ {self.user2})"


class Message(models.Model):
    dialog = models.ForeignKey(Dialog, on_delete=models.CASCADE, related_name='messages')
    sender = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='sent_messages')
    text = models.TextField(blank=True)
    # Shared outfit attached to the message (mobile app); Mini App encodes it inside text.
    post = models.ForeignKey(
        'OutfitPost', on_delete=models.SET_NULL, null=True, blank=True, related_name='shares'
    )
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
    # Normalized canvas with the mannequin centred on it, all garment layers share its coordinates.
    canvas = models.ImageField(upload_to='mannequins/canvas/', blank=True, null=True)
    # Anchor points {name: [x, y]} in normalized canvas coordinates (0..1).
    anchors = models.JSONField(default=dict, blank=True)

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
    
    ZONE_CHOICES = [
        ('head', 'Голова'),
        ('upper', 'Верх'),
        ('lower', 'Низ'),
        ('full', 'Всё тело'),
        ('feet', 'Стопы'),
        ('accessory', 'Аксессуар'),
    ]

    name = models.CharField(max_length=50, choices=CATEGORY_CHOICES, unique=True)
    zone = models.CharField(max_length=16, choices=ZONE_CHOICES, default='upper')
    default_layer = models.IntegerField(default=30)
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
    image = models.ImageField(upload_to='clothing/', max_length=500)

    gender = models.CharField(max_length=10, choices=GENDER_CHOICES)
    style = models.CharField(max_length=20, choices=STYLE_CHOICES, blank=True)
    color = models.CharField(max_length=50, blank=True)
    tags = models.CharField(max_length=500, blank=True)
    item_description = models.TextField(blank=True, verbose_name='Описание')
    buy_link = models.URLField(max_length=500, blank=True, null=True, verbose_name='Где купить (ссылка)')
    brand = models.CharField(max_length=120, blank=True, default='')
    price = models.DecimalField(max_digits=10, decimal_places=2, blank=True, null=True)
    SEASON_CHOICES = [
        ('all', 'Всесезон'),
        ('summer', 'Лето'),
        ('demi', 'Демисезон'),
        ('winter', 'Зима'),
    ]
    season = models.CharField(max_length=10, choices=SEASON_CHOICES, default='all', blank=True)

    # Garment layers fitted to each mannequin canvas (transparent, same size as the canvas).
    fitted_male = models.ImageField(upload_to='clothing/fitted/', max_length=500, blank=True, null=True)
    fitted_female = models.ImageField(upload_to='clothing/fitted/', max_length=500, blank=True, null=True)
    keypoints = models.JSONField(default=dict, blank=True)
    fit_score = models.FloatField(blank=True, null=True)
    is_published = models.BooleanField(default=True)
    created_by = models.ForeignKey(
        'TelegramUser', on_delete=models.SET_NULL, null=True, blank=True, related_name='studio_items'
    )

    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def fitted_for(self, gender):
        return self.fitted_female if gender == 'female' else self.fitted_male
    
    def save(self, *args, **kwargs):
        if self.image and hasattr(self.image, 'file'):
            try:
                from PIL import Image
                img = Image.open(self.image)
                img_format = img.format or 'JPEG'
                # Уменьшаем до максимум 1800px по большей стороне
                max_size = 1800
                if max(img.size) > max_size:
                    img.thumbnail((max_size, max_size), Image.LANCZOS)
                output = io.BytesIO()
                # PNG сохраняем как PNG (сохраняем прозрачность), остальное — JPEG
                if img_format == 'PNG' or img.mode in ('RGBA', 'LA', 'P'):
                    if img.mode == 'P':
                        img = img.convert('RGBA')
                    img.save(output, format='PNG', optimize=True)
                    ext = 'png'
                else:
                    if img.mode != 'RGB':
                        img = img.convert('RGB')
                    img.save(output, format='JPEG', quality=85, optimize=True)
                    ext = 'jpg'
                output.seek(0)
                original_name = os.path.splitext(os.path.basename(self.image.name))[0]
                self.image.save(f"{original_name}.{ext}", ContentFile(output.read()), save=False)
            except Exception:
                pass  # если что-то пошло не так — сохраняем как есть
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.name} ({self.get_gender_display()})"


class Hashtag(models.Model):
    """Хештеги"""
    tag = models.CharField(max_length=100, unique=True)
    usage_count = models.IntegerField(default=0)
    
    def __str__(self):
        return f"#{self.tag}"


def outfit_image_path(instance, filename):
    """Media is served publicly, so outfit images get unguessable names: private and followers-only
    outfits must not be reachable by counting ids."""
    import secrets

    ext = os.path.splitext(filename)[1].lower() or '.jpg'
    return f'outfits/outfit_{secrets.token_urlsafe(16)}{ext}'


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
    final_image = models.ImageField(upload_to=outfit_image_path, blank=True, null=True)

    VISIBILITY_CHOICES = [
        ('all', 'Все'),
        ('followers', 'Подписчики'),
        ('private', 'Только я'),
    ]
    visibility = models.CharField(max_length=10, choices=VISIBILITY_CHOICES, default='all')
    remix_of = models.ForeignKey(
        'self', on_delete=models.SET_NULL, null=True, blank=True, related_name='remixes'
    )
    is_hidden = models.BooleanField(default=False)

    created_at = models.DateTimeField(auto_now_add=True)
    likes_count = models.IntegerField(default=0)
    comments_count = models.IntegerField(default=0)
    
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
    # Mobile layers use normalized canvas coordinates; Mini App layers use screen pixels.
    normalized = models.BooleanField(default=False)
    flipped = models.BooleanField(default=False)
    # Drawn from the item's fitted layer (canvas-sized) instead of the free cutout.
    fitted = models.BooleanField(default=False)

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
    parent = models.ForeignKey(
        'self', on_delete=models.CASCADE, null=True, blank=True, related_name='replies'
    )
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
        ('reply', 'Ответ'),
        ('follow', 'Подписка'),
        ('remix', 'Ремикс'),
        ('studio', 'Студия'),
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