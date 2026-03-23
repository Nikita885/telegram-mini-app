from django.urls import path
from .views import TelegramUserView

urlpatterns = [
    path('telegram-user/', TelegramUserView.as_view(), name='telegram-user'),
    path('telegram-user/<int:telegram_id>/', TelegramUserView.as_view(), name='telegram-user-detail'),
]