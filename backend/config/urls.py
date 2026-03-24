from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from api.views import search_view, home_view, messages_view, profile_view, create_view, authorize_view

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/', include('api.urls')),
    path('authorize/', authorize_view, name='authorize'),
    path('home/<int:telegram_id>/', home_view, name='home'),
    path('search/<int:telegram_id>/', search_view, name='search'),
    path('messages/<int:telegram_id>/', messages_view, name='messages'),
    path('profile/<int:telegram_id>/', profile_view, name='profile'),
    path('create/<int:telegram_id>/', create_view, name='create'),
]