from django.db import migrations
from django.db.models import Count

ZONES = {
    "hat": ("head", 60),
    "top": ("upper", 40),
    "shirt": ("upper", 30),
    "dress": ("full", 28),
    "skirt": ("lower", 22),
    "pants": ("lower", 20),
    "shoes": ("feet", 15),
    "accessories": ("accessory", 50),
}


def forwards(apps, schema_editor):
    OutfitPost = apps.get_model("api", "OutfitPost")
    ClothingCategory = apps.get_model("api", "ClothingCategory")
    for post in OutfitPost.objects.annotate(n=Count("comments")).filter(n__gt=0):
        OutfitPost.objects.filter(pk=post.pk).update(comments_count=post.n)
    for category in ClothingCategory.objects.all():
        zone, layer = ZONES.get(category.name, ("upper", 30))
        ClothingCategory.objects.filter(pk=category.pk).update(zone=zone, default_layer=layer)


class Migration(migrations.Migration):
    dependencies = [("api", "0009_clothingcategory_default_layer_clothingcategory_zone_and_more")]

    operations = [migrations.RunPython(forwards, migrations.RunPython.noop)]
