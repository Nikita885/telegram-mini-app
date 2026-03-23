from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from api.views import search_view, home_view, messages_view, profile_view, create_view, authorize_view

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/', include('api.urls')),
    path('home/', home_view, name='home'),
    path('search/', search_view, name='search'),
    path('messages/', messages_view, name='messages'),
    path('profile/', profile_view, name='profile'),
    path('create/', create_view, name='create'),
    path('authorize/', authorize_view, name='authorize'),
]

if settings.DEBUG:
    urlpatterns += static(settings.STATIC_URL, document_root=settings.STATICFILES_DIRS[0])