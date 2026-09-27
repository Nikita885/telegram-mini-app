"""Generate fitted layers for catalog items that don't have them yet (e.g. added via the Mini App)."""

from django.core.management.base import BaseCommand

from api.models import ClothingItem
from studio.services import fit_catalog_item


class Command(BaseCommand):
    help = "Fit existing catalog items onto the mannequins"

    def add_arguments(self, parser):
        parser.add_argument("--all", action="store_true", help="Refit items that already have fitted layers")
        parser.add_argument("--id", type=int, action="append", help="Only these item ids")

    def handle(self, *args, **options):
        qs = ClothingItem.objects.select_related("category")
        if options["id"]:
            qs = qs.filter(pk__in=options["id"])
        elif not options["all"]:
            qs = qs.filter(fitted_male__in=["", None], fitted_female__in=["", None])
        for item in qs:
            try:
                scores = fit_catalog_item(item)
                self.stdout.write(self.style.SUCCESS(f"#{item.pk} {item.name}: {scores or 'free layer'}"))
            except Exception as exc:  # noqa: BLE001 - keep going through the catalog
                self.stderr.write(f"#{item.pk} {item.name}: {exc}")
