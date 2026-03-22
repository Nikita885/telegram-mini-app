from django.contrib import admin
from django.urls import path, include
from api.views import search_view, home_view, messages_view, profile_view, create_view

urlpatterns = [
    path('admin/', admin.site.urls),
    path('home/', home_view, name='home'),
    path('search/', search_view, name='search'),
    path('messages/', messages_view, name='messages'),
    path('profile/', profile_view, name='profile'),
    path('create/', create_view, name='create'),  
]
