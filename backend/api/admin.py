from django.contrib import admin
from .models import CustomUser, TelegramUser, Follow, Post, Dialog, Message

admin.site.register(CustomUser)
admin.site.register(Follow)
admin.site.register(Post)
admin.site.register(Dialog)
admin.site.register(Message)

from .models import (
    ClothingCategory, ClothingItem, OutfitPost, PostClothingItem, Hashtag,
    PostLike, PostComment, CommentLike, Notification, Mannequin,
)


@admin.register(Mannequin)
class MannequinAdmin(admin.ModelAdmin):
    list_display = ('get_gender_display', 'image', 'canvas')
    fields = ('gender', 'image', 'canvas', 'anchors')


@admin.register(ClothingItem)
class ClothingItemAdmin(admin.ModelAdmin):
    list_display = ('name', 'category', 'gender', 'style', 'color', 'fit_score', 'is_published')
    list_filter = ('category', 'gender', 'style', 'is_published')
    search_fields = ('name', 'tags', 'color')
    fieldsets = (
        (None, {
            'fields': ('category', 'name', 'image', 'gender', 'style', 'color', 'tags'),
        }),
        ('Описание и покупка', {
            'fields': ('item_description', 'buy_link', 'brand', 'price', 'season', 'is_published'),
        }),
        ('Посадка на манекен', {
            'fields': ('fitted_male', 'fitted_female', 'fit_score', 'keypoints'),
        }),
    )


@admin.register(ClothingCategory)
class ClothingCategoryAdmin(admin.ModelAdmin):
    list_display = ('get_name_display', 'zone', 'default_layer', 'order')
    fields = ('name', 'zone', 'default_layer', 'icon_class', 'icon_svg', 'order')
    ordering = ('order',)
admin.site.register(OutfitPost)
admin.site.register(PostClothingItem)
admin.site.register(Hashtag)
admin.site.register(PostLike)
admin.site.register(PostComment)
admin.site.register(CommentLike)
admin.site.register(Notification)

@admin.register(TelegramUser)
class TelegramUserAdmin(admin.ModelAdmin):
    list_display = ('telegram_id', 'username', 'first_name', 'role', 'is_banned')
    list_filter = ('role', 'is_banned')
    list_editable = ('role', 'is_banned')
    search_fields = ('username', 'first_name', 'last_name', 'telegram_id')
