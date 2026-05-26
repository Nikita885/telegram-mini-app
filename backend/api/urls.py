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
    DialogActionView,
)
from .views_constructor import (
    ClothingCategoriesView, ClothingItemsView, CreateOutfitView,
    UserOutfitsView, PostLikeView, PostCommentsView, CommentLikeView,
    SearchPostsView, DeleteOutfitView, HomeFeedView, NotificationListView,
    PostDetailView,
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
    path('dialogs/<int:dialog_id>/action/', DialogActionView.as_view(), name='api-dialog-action'),

    # Edit/Delete message
    path('dialogs/<int:dialog_id>/messages/<int:message_id>/', MessageDetailView.as_view(), name='api-message-detail'),

    # Clothing Constructor
    path('clothing/categories/', ClothingCategoriesView.as_view(), name='api-clothing-categories'),
    path('clothing/items/', ClothingItemsView.as_view(), name='api-clothing-items'),
    path('outfit/create/', CreateOutfitView.as_view(), name='api-outfit-create'),
    path('outfit/user/<int:telegram_id>/', UserOutfitsView.as_view(), name='api-user-outfits'),
    path('outfit/<int:post_id>/like/', PostLikeView.as_view(), name='api-post-like'),
    path('outfit/<int:post_id>/comments/', PostCommentsView.as_view(), name='api-post-comments'),
    path('outfit/<int:post_id>/comments/<int:comment_id>/like/', CommentLikeView.as_view(), name='api-comment-like'),
    path('outfit/<int:post_id>/delete/', DeleteOutfitView.as_view(), name='api-outfit-delete'),
    path('outfit/<int:post_id>/', PostDetailView.as_view(), name='api-post-detail'),
    path('search/posts/', SearchPostsView.as_view(), name='api-search-posts'),
    path('home/feed/', HomeFeedView.as_view(), name='api-home-feed'),
    path('notifications/', NotificationListView.as_view(), name='api-notifications'),
]
