from django.contrib import admin
from django.urls import path, include, re_path
from django.http import JsonResponse
from django.shortcuts import redirect
from mobile.media import serve_media
from api.views import (
    authorize_view, home_view, search_view, messages_view,
    chat_view, chat_with_user_view,  # ✅ NEW
    profile_view, user_profile_view, create_view, avatar_view,
    connections_view, UpdateAvatarView,
)

urlpatterns = [
    path('', lambda request: redirect('authorize')),
    path('admin/', admin.site.urls),
    path('health/', lambda request: JsonResponse({'status': 'ok'})),
    path('api/v1/', include('mobile.urls')),
    path('api/', include('api.urls')),

    path('authorize/', authorize_view, name='authorize'),
    path('home/', home_view, name='home'),
    path('search/', search_view, name='search'),
    path('messages/', messages_view, name='messages'),

    # ✅ NEW: Chat with user (no dialog yet)
    path('chat/with/<int:telegram_id>/', chat_with_user_view, name='chat-with-user'),

    # Existing chat with dialog
    path('chat/<int:dialog_id>/', chat_view, name='chat'),

    path('profile/', profile_view, name='profile'),
    path('user/<int:telegram_id>/', user_profile_view, name='user-profile'),
    path('create/', create_view, name='create'),
    path('avatar/', avatar_view, name='avatar'),
    path('connections/<str:connection_type>/', connections_view, name='connections'),
    path('update-avatar/', UpdateAvatarView.as_view(), name='update-avatar'),
    # Media is served by Django itself so the deployment needs no extra proxy rules;
    # a front proxy may still serve /media/ directly from the shared volume.
    re_path(r'^media/(?P<path>.*)$', serve_media),
]
