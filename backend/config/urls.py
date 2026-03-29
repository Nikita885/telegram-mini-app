from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from api.views import search_view, home_view, messages_view, profile_view, create_view, authorize_view, avatar_view
from api.views import UploadAvatarView, UpdateAvatarColorView, UpdateAvatarView

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/', include('api.urls')),

    path('authorize/', authorize_view, name='authorize'),

    path('home/', home_view, name='home'),
    path('search/', search_view, name='search'),
    path('messages/', messages_view, name='messages'),
    path('profile/', profile_view, name='profile'),
    path('create/', create_view, name='create'),
    path('avatar/', avatar_view, name='avatar'),
    path('upload-avatar/', UploadAvatarView.as_view()),
    path('update-avatar-color/', UpdateAvatarColorView.as_view()),
    path('update-avatar/', UpdateAvatarView.as_view()),
] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)