"""Input validation for the mobile API (output shapes live in presenters.py)."""

import re

from rest_framework import serializers

from api.models import ClothingCategory, ClothingItem, OutfitPost

USERNAME_RE = re.compile(r"^[A-Za-z0-9_.]{3,32}$")


class TelegramPollSerializer(serializers.Serializer):
    nonce = serializers.CharField(max_length=64)


class RefreshSerializer(serializers.Serializer):
    refresh = serializers.CharField()


class WebAppLoginSerializer(serializers.Serializer):
    init_data = serializers.CharField()


class DevLoginSerializer(serializers.Serializer):
    telegram_id = serializers.IntegerField()
    first_name = serializers.CharField(required=False, allow_blank=True, max_length=64)


class ProfileUpdateSerializer(serializers.Serializer):
    first_name = serializers.CharField(required=False, allow_blank=True, max_length=64)
    last_name = serializers.CharField(required=False, allow_blank=True, max_length=64)
    username = serializers.CharField(required=False, allow_blank=True, max_length=32)
    bio = serializers.CharField(required=False, allow_blank=True, max_length=150)
    avatar_color = serializers.RegexField(r"^#[0-9A-Fa-f]{6}$", required=False)

    def validate_username(self, value):
        value = value.strip().lstrip("@")
        if value and not USERNAME_RE.match(value):
            raise serializers.ValidationError("Латиница, цифры, точка и _ — от 3 до 32 символов")
        return value


class LayerSerializer(serializers.Serializer):
    item_id = serializers.IntegerField()
    x = serializers.FloatField(min_value=-1.0, max_value=2.0)
    y = serializers.FloatField(min_value=-1.0, max_value=2.0)
    scale = serializers.FloatField(min_value=0.05, max_value=8.0)
    rotation = serializers.FloatField(min_value=-360.0, max_value=360.0, default=0.0)
    z = serializers.IntegerField(default=0)
    flipped = serializers.BooleanField(default=False)
    fitted = serializers.BooleanField(default=False)


class OutfitCreateSerializer(serializers.Serializer):
    mannequin = serializers.ChoiceField(choices=["male", "female"])
    description = serializers.CharField(required=False, allow_blank=True, max_length=2200, default="")
    hashtags = serializers.ListField(child=serializers.CharField(max_length=41), required=False, default=list)
    visibility = serializers.ChoiceField(choices=[c[0] for c in OutfitPost.VISIBILITY_CHOICES], default="all")
    remix_of = serializers.IntegerField(required=False, allow_null=True)
    layers = LayerSerializer(many=True)

    def validate_layers(self, value):
        if len(value) > 30:
            raise serializers.ValidationError("Не больше 30 вещей в образе")
        return value


class CommentCreateSerializer(serializers.Serializer):
    text = serializers.CharField(max_length=1000)
    parent_id = serializers.IntegerField(required=False, allow_null=True)


class CollectionSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=60)
    is_private = serializers.BooleanField(default=False)


class MessageCreateSerializer(serializers.Serializer):
    text = serializers.CharField(required=False, allow_blank=True, max_length=4000, default="")
    outfit_id = serializers.IntegerField(required=False, allow_null=True)


class MessageEditSerializer(serializers.Serializer):
    text = serializers.CharField(max_length=4000)


class DialogCreateSerializer(serializers.Serializer):
    user_id = serializers.IntegerField()


class ReportSerializer(serializers.Serializer):
    target_type = serializers.ChoiceField(choices=["post", "comment", "user"])
    target_id = serializers.IntegerField()
    reason = serializers.CharField(max_length=500)


class GarmentJobCreateSerializer(serializers.Serializer):
    photo = serializers.ImageField()
    category = serializers.SlugRelatedField(slug_field="name", queryset=ClothingCategory.objects.all())
    gender = serializers.ChoiceField(choices=[c[0] for c in ClothingItem.GENDER_CHOICES], default="unisex")


class RefitSerializer(serializers.Serializer):
    keypoints = serializers.DictField(child=serializers.ListField(child=serializers.FloatField(), min_length=2, max_length=2))


class PublishItemSerializer(serializers.Serializer):
    name = serializers.CharField(max_length=255)
    category = serializers.SlugRelatedField(
        slug_field="name", queryset=ClothingCategory.objects.all(), required=False
    )
    gender = serializers.ChoiceField(choices=[c[0] for c in ClothingItem.GENDER_CHOICES], required=False)
    style = serializers.ChoiceField(
        choices=[""] + [c[0] for c in ClothingItem.STYLE_CHOICES], required=False, allow_blank=True
    )
    color = serializers.CharField(max_length=50, required=False, allow_blank=True)
    season = serializers.ChoiceField(choices=[c[0] for c in ClothingItem.SEASON_CHOICES], required=False)
    tags = serializers.CharField(max_length=500, required=False, allow_blank=True)
    description = serializers.CharField(required=False, allow_blank=True, max_length=2000)
    buy_link = serializers.URLField(required=False, allow_blank=True, max_length=500)
    brand = serializers.CharField(required=False, allow_blank=True, max_length=120)
    price = serializers.DecimalField(max_digits=10, decimal_places=2, required=False, allow_null=True)
