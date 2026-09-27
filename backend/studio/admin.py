from django.contrib import admin
from django.utils.html import format_html

from .models import GarmentJob


@admin.register(GarmentJob)
class GarmentJobAdmin(admin.ModelAdmin):
    list_display = ("id", "thumb", "category", "gender", "status", "stage", "fit_score", "created_by", "created_at")
    list_filter = ("status", "category", "gender")
    readonly_fields = ("thumb", "preview", "attributes", "keypoints", "timings", "fit_score")

    @admin.display(description="Фото")
    def thumb(self, obj):
        return format_html('<img src="{}" style="height:64px">', obj.source.url) if obj.source else "—"

    @admin.display(description="На манекене")
    def preview(self, obj):
        parts = [
            format_html('<img src="{}" style="height:320px;margin-right:8px">', f.url)
            for f in (obj.preview_male, obj.preview_female)
            if f
        ]
        return format_html("".join(str(p) for p in parts)) if parts else "—"
