from django.urls import path
from .views import (
    AuthorizeView,
    SearchUsersView,
    FollowToggleView,
    ConnectionsView,
    DialogListView,
    StartDialogView,
    CreateDialogView,
    DialogMessagesView,
    MessageDetailView,
    DialogActionView,  # ✅ NEW
)

urlpatterns = [
    path('authorize/', AuthorizeView.as_view(), name='api-authorize'),
    path('search/', SearchUsersView.as_view(), name='api-search'),
    path('follow/', FollowToggleView.as_view(), name='api-follow'),
    
    # Connections
    path('connections/<str:connection_type>/', ConnectionsView.as_view(), name='api-connections'),

    # Messenger
    path('dialogs/', DialogListView.as_view(), name='api-dialogs'),
    path('dialogs/start/', StartDialogView.as_view(), name='api-dialogs-start'),  # Deprecated
    path('dialogs/create/', CreateDialogView.as_view(), name='api-dialogs-create'),
    path('dialogs/<int:dialog_id>/messages/', DialogMessagesView.as_view(), name='api-dialog-messages'),
    path('dialogs/<int:dialog_id>/action/', DialogActionView.as_view(), name='api-dialog-action'),  # ✅ NEW
    
    # Edit/Delete message
    path('dialogs/<int:dialog_id>/messages/<int:message_id>/', MessageDetailView.as_view(), name='api-message-detail'),
]