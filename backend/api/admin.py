from django.contrib import admin
from .models import CustomUser, TelegramUser

admin.site.register(TelegramUser)
admin.site.register(CustomUser)
