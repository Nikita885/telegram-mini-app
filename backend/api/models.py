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