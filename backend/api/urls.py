from django.urls import path
from .views import (
    AuthorizeView,
    SearchUsersView,
    FollowToggleView,
    ConnectionsView,
    DialogListView,
    StartDialogView,
    DialogMessagesView,
    MessageDetailView,
)

urlpatterns = [
    path('authorize/', AuthorizeView.as_view(), name='api-authorize'),
    path('search/', SearchUsersView.as_view(), name='api-search'),
    path('follow/', FollowToggleView.as_view(), name='api-follow'),
    
    # Connections
    path('connections/<str:connection_type>/', ConnectionsView.as_view(), name='api-connections'),

    # Messenger
    path('dialogs/', DialogListView.as_view(), name='api-dialogs'),
    path('dialogs/start/', StartDialogView.as_view(), name='api-dialogs-start'),
    path('dialogs/<int:dialog_id>/messages/', DialogMessagesView.as_view(), name='api-dialog-messages'),
    
    # Edit/Delete message
    path('dialogs/<int:dialog_id>/messages/<int:message_id>/', MessageDetailView.as_view(), name='api-message-detail'),
]