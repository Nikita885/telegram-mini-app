from django.contrib import admin
from .models import CustomUser, TelegramUser, Follow, Post, Dialog, Message

admin.site.register(TelegramUser)
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
    list_display = ('get_gender_display', 'image')
    fields = ('gender', 'image')


@admin.register(ClothingItem)
class ClothingItemAdmin(admin.ModelAdmin):
    list_display = ('name', 'category', 'gender', 'style', 'color', 'buy_link')
    list_filter = ('category', 'gender', 'style')
    search_fields = ('name', 'tags', 'color')
    fieldsets = (
        (None, {
            'fields': ('category', 'name', 'image', 'gender', 'style', 'color', 'tags'),
        }),
        ('Описание и покупка', {
            'fields': ('item_description', 'buy_link'),
        }),
    )


@admin.register(ClothingCategory)
class ClothingCategoryAdmin(admin.ModelAdmin):
    list_display = ('get_name_display', 'icon_class', 'icon_svg', 'order')
    fields = ('name', 'icon_class', 'icon_svg', 'order')
    ordering = ('order',)
admin.site.register(OutfitPost)
admin.site.register(PostClothingItem)
admin.site.register(Hashtag)
admin.site.register(PostLike)
admin.site.register(PostComment)
admin.site.register(CommentLike)
admin.site.register(Notification)