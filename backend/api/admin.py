from django.contrib import admin
from .models import CustomUser, TelegramUser, Follow, Post, Dialog, Message

admin.site.register(TelegramUser)
admin.site.register(CustomUser)
admin.site.register(Follow)
admin.site.register(Post)
admin.site.register(Dialog)
admin.site.register(Message)
# backend/api/admin.py
from .models import (
    ClothingCategory, ClothingTag, ClothingItem,
    Outfit, OutfitItem, OutfitLike, OutfitComment
)

admin.site.register(ClothingCategory)
admin.site.register(ClothingTag)
admin.site.register(ClothingItem)
admin.site.register(Outfit)
admin.site.register(OutfitItem)
admin.site.register(OutfitLike)
admin.site.register(OutfitComment)