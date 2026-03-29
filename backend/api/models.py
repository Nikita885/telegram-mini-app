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
    username = models.CharField(max_length=255, blank=True, null=True, verbose_name="Username")
    first_name = models.CharField(max_length=255, blank=True, null=True, verbose_name="First name")
    last_name = models.CharField(max_length=255, blank=True, null=True, verbose_name="Last name")
    language_code = models.CharField(max_length=10, blank=True, null=True, verbose_name="Language code")
    avatar = models.ImageField(upload_to='avatars/', blank=True, null=True)
    avatar_random_color = models.CharField(max_length=7, blank=True, null=True, verbose_name="Avatar color in HEX")

    def __str__(self):
        return f"{self.username or self.first_name or str(self.telegram_id)}"
    
class Follow(models.Model):
    follower = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='following')   # кто подписался
    following = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='followers')  # на кого подписались
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('follower', 'following')  # один пользователь может подписаться на другого только раз

class Post(models.Model):
    user = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name='posts')
    text = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)