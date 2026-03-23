from rest_framework import serializers

from api.models import TelegramUser

class TelegramUserSerializer(serializers.ModelSerializer):
    class Meta:
        model = TelegramUser
        fields = ['id', 'telegram_id', 'username', 'first_name', 'last_name']
        read_only_fields = ['id']