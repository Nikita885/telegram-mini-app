from django.urls import path

from . import views

urlpatterns = [
    path("config/", views.app_config),
    # Auth
    path("auth/telegram/start/", views.TelegramStartView.as_view()),
    path("auth/telegram/poll/", views.TelegramPollView.as_view()),
    path("auth/telegram/webapp/", views.WebAppLoginView.as_view()),
    path("auth/dev/", views.DevLoginView.as_view()),
    path("auth/refresh/", views.RefreshView.as_view()),
    path("auth/logout/", views.logout),
    # Me & users
    path("me/", views.me_view),
    path("me/avatar/", views.AvatarView.as_view()),
    path("users/search/", views.search_users),
    path("users/<int:user_id>/", views.user_detail),
    path("users/<int:user_id>/outfits/", views.user_outfits),
    path("users/<int:user_id>/follow/", views.follow),
    path("users/<int:user_id>/<str:kind>/", views.user_connections),
    # Feed & outfits
    path("feed/", views.feed),
    path("outfits/", views.OutfitCreateView.as_view()),
    path("outfits/search/", views.search_outfits),
    path("outfits/<int:outfit_id>/", views.outfit_detail),
    path("outfits/<int:outfit_id>/similar/", views.outfit_similar),
    path("outfits/<int:outfit_id>/like/", views.outfit_like),
    path("outfits/<int:outfit_id>/save/", views.outfit_save),
    path("outfits/<int:outfit_id>/comments/", views.outfit_comments),
    path("comments/<int:comment_id>/", views.comment_detail),
    path("comments/<int:comment_id>/like/", views.comment_like),
    path("hashtags/trending/", views.trending_hashtags),
    # Catalog
    path("catalog/mannequins/", views.mannequins),
    path("catalog/categories/", views.categories),
    path("catalog/items/", views.catalog_items),
    path("catalog/items/<int:item_id>/", views.catalog_item),
    path("catalog/items/<int:item_id>/similar/", views.catalog_item_similar),
    # Collections
    path("collections/", views.collections),
    path("collections/<int:collection_id>/", views.collection_detail),
    # Messaging
    path("dialogs/", views.dialogs),
    path("dialogs/<int:dialog_id>/", views.dialog_detail),
    path("dialogs/<int:dialog_id>/pin/", views.dialog_pin),
    path("dialogs/<int:dialog_id>/read/", views.dialog_read),
    path("dialogs/<int:dialog_id>/messages/", views.DialogMessagesView.as_view()),
    path("messages/<int:message_id>/", views.message_detail),
    # Notifications
    path("notifications/", views.notifications),
    path("notifications/read/", views.notifications_read),
    path("counters/", views.counters),
    # Reports
    path("reports/", views.ReportView.as_view()),
    # Studio
    path("studio/jobs/", views.StudioJobsView.as_view()),
    path("studio/jobs/<int:job_id>/", views.studio_job),
    path("studio/jobs/<int:job_id>/retry/", views.studio_job_retry),
    path("studio/jobs/<int:job_id>/refit/", views.studio_job_refit),
    path("studio/jobs/<int:job_id>/publish/", views.studio_job_publish),
]
