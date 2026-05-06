from django.contrib import admin
from .models import CustomUser, TelegramUser, Follow, Post

admin.site.register(TelegramUser)
admin.site.register(CustomUser)
admin.site.register(Follow)
admin.site.register(Post)
