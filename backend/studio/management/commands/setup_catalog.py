"""Idempotent bootstrap: categories with body zones/layers and mannequin canvases."""

import os

from django.conf import settings
from django.core.files import File
from django.core.management.base import BaseCommand

from api.models import ClothingCategory, Mannequin
from studio.services import prepare_mannequin

CATEGORIES = [
    # name, zone, default layer, order, icon
    ("hat", "head", 60, 1, "ri-hat-line"),
    ("top", "upper", 40, 2, "ri-t-shirt-line"),
    ("shirt", "upper", 30, 3, "ri-shirt-line"),
    ("dress", "full", 28, 4, "ri-user-heart-line"),
    ("skirt", "lower", 22, 5, "ri-user-smile-line"),
    ("pants", "lower", 20, 6, "ri-pantone-line"),
    ("shoes", "feet", 15, 7, "ri-footprint-line"),
    ("accessories", "accessory", 50, 8, "ri-honour-line"),
]


class Command(BaseCommand):
    help = "Create/update clothing categories and build mannequin canvases with anchors"

    def add_arguments(self, parser):
        parser.add_argument("--images", default=None, help="Directory with mannequin_male.png / mannequin_female.png")
        parser.add_argument("--rebuild", action="store_true", help="Rebuild mannequin canvases and anchors")

    def handle(self, *args, **options):
        for name, zone, layer, order, icon in CATEGORIES:
            category, created = ClothingCategory.objects.get_or_create(
                name=name, defaults={"zone": zone, "default_layer": layer, "order": order, "icon_class": icon}
            )
            if not created:
                category.zone, category.default_layer = zone, layer
                category.save(update_fields=["zone", "default_layer"])
            self.stdout.write(f"category {name}: zone={zone} layer={layer}")

        images = options["images"] or next(
            (p for p in [settings.FRONTEND_DIR / "static" / "images", "/frontend/static/images"] if os.path.exists(p)),
            None,
        )
        for gender in ("male", "female"):
            mannequin = Mannequin.objects.filter(gender=gender).first()
            if mannequin is None:
                path = os.path.join(str(images or ""), f"mannequin_{gender}.png")
                if not os.path.exists(path):
                    self.stderr.write(f"mannequin image not found: {path}")
                    continue
                mannequin = Mannequin(gender=gender)
                with open(path, "rb") as f:
                    mannequin.image.save(f"mannequin_{gender}.png", File(f), save=True)
            prepare_mannequin(mannequin, force=options["rebuild"])
            self.stdout.write(self.style.SUCCESS(f"mannequin {gender}: {len(mannequin.anchors)} anchors"))
