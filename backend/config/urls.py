from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from django.shortcuts import redirect
from api.views import (
    authorize_view, home_view, search_view, messages_view, chat_view,
    profile_view, user_profile_view, create_view, avatar_view,
    connections_view, UpdateAvatarView,
)

urlpatterns = [
    path('', lambda request: redirect('authorize')),
    path('admin/', admin.site.urls),
    path('api/', include('api.urls')),

    path('authorize/', authorize_view, name='authorize'),
    path('home/', home_view, name='home'),
    path('search/', search_view, name='search'),
    path('messages/', messages_view, name='messages'),
    path('chat/<int:dialog_id>/', chat_view, name='chat'),
    path('profile/', profile_view, name='profile'),
    path('user/<int:telegram_id>/', user_profile_view, name='user-profile'),
    path('create/', create_view, name='create'),
    path('avatar/', avatar_view, name='avatar'),
    path('connections/<str:connection_type>/', connections_view, name='connections'),
    path('update-avatar/', UpdateAvatarView.as_view(), name='update-avatar'),
] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)