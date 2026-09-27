from django.contrib import admin

from .models import Collection, CollectionItem, LoginNonce, RefreshToken, Report


@admin.register(Report)
class ReportAdmin(admin.ModelAdmin):
    list_display = ("target_type", "target_id", "reporter", "reason", "status", "created_at")
    list_filter = ("status", "target_type")
    list_editable = ("status",)


@admin.register(Collection)
class CollectionAdmin(admin.ModelAdmin):
    list_display = ("title", "owner", "is_default", "is_private", "updated_at")


admin.site.register(CollectionItem)
admin.site.register(LoginNonce)
admin.site.register(RefreshToken)
