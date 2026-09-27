from django.db import models

from api.models import ClothingCategory, ClothingItem, TelegramUser


class GarmentJob(models.Model):
    """A photo taken in the Studio travelling through the garment → mannequin pipeline."""

    STATUS_QUEUED = "queued"
    STATUS_PROCESSING = "processing"
    STATUS_REVIEW = "review"
    STATUS_FAILED = "failed"
    STATUS_PUBLISHED = "published"
    STATUS_CHOICES = [
        (STATUS_QUEUED, "В очереди"),
        (STATUS_PROCESSING, "Обработка"),
        (STATUS_REVIEW, "Ждёт проверки"),
        (STATUS_FAILED, "Ошибка"),
        (STATUS_PUBLISHED, "Опубликовано"),
    ]

    # Pipeline stages in order; `stage` holds the one currently running (or last finished).
    STAGES = ["quality", "background", "normalize", "attributes", "keypoints", "fit", "preview"]

    created_by = models.ForeignKey(TelegramUser, on_delete=models.CASCADE, related_name="garment_jobs")
    category = models.ForeignKey(ClothingCategory, on_delete=models.PROTECT, related_name="garment_jobs")
    gender = models.CharField(max_length=10, choices=ClothingItem.GENDER_CHOICES, default="unisex")

    source = models.ImageField(upload_to="studio/source/", max_length=500)
    cutout = models.ImageField(upload_to="studio/cutout/", max_length=500, blank=True, null=True)
    fitted_male = models.ImageField(upload_to="studio/fitted/", max_length=500, blank=True, null=True)
    fitted_female = models.ImageField(upload_to="studio/fitted/", max_length=500, blank=True, null=True)
    preview_male = models.ImageField(upload_to="studio/preview/", max_length=500, blank=True, null=True)
    preview_female = models.ImageField(upload_to="studio/preview/", max_length=500, blank=True, null=True)

    status = models.CharField(max_length=12, choices=STATUS_CHOICES, default=STATUS_QUEUED)
    stage = models.CharField(max_length=16, blank=True, default="")
    progress = models.PositiveSmallIntegerField(default=0)
    error = models.CharField(max_length=300, blank=True, default="")

    # Pipeline output: {"color": {...}, "colors": [...], "quality": {...}} etc.
    attributes = models.JSONField(default=dict, blank=True)
    # Garment keypoints in cutout pixel coordinates {name: [x, y]}.
    keypoints = models.JSONField(default=dict, blank=True)
    fit_score = models.FloatField(null=True, blank=True)
    timings = models.JSONField(default=dict, blank=True)

    item = models.OneToOneField(
        ClothingItem, on_delete=models.SET_NULL, null=True, blank=True, related_name="garment_job"
    )

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"GarmentJob#{self.pk} {self.category.name} {self.status}"
