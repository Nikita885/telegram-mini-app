from django.urls import path
from .views import AuthorizeView, SearchUsersView, FollowToggleView

urlpatterns = [
    path('authorize/', AuthorizeView.as_view(), name='api-authorize'),
    path('search/', SearchUsersView.as_view(), name='api-search'),
    path('follow/', FollowToggleView.as_view(), name='api-follow'),
]