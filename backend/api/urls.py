from django.urls import path
from .views import (
    AuthorizeView,
    SearchUsersView,
    FollowToggleView,
    DialogListView,
    StartDialogView,
    DialogMessagesView,
)

urlpatterns = [
    path('authorize/', AuthorizeView.as_view(), name='api-authorize'),
    path('search/', SearchUsersView.as_view(), name='api-search'),
    path('follow/', FollowToggleView.as_view(), name='api-follow'),

    # Messenger
    path('dialogs/', DialogListView.as_view(), name='api-dialogs'),
    path('dialogs/start/', StartDialogView.as_view(), name='api-dialogs-start'),
    path('dialogs/<int:dialog_id>/messages/', DialogMessagesView.as_view(), name='api-dialog-messages'),
]