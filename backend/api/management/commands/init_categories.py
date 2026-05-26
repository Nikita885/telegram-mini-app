from django.core.management.base import BaseCommand
from api.models import ClothingCategory


class Command(BaseCommand):
    help = 'Initialize clothing categories with icons'

    def handle(self, *args, **kwargs):
        categories = [
            {'name': 'top', 'icon_class': 'ri-t-shirt-line', 'order': 1},
            {'name': 'shirt', 'icon_class': 'ri-shirt-line', 'order': 2},
            {'name': 'pants', 'icon_class': 'ri-pantone-line', 'order': 3},
            {'name': 'dress', 'icon_class': 'ri-user-heart-line', 'order': 4},
            {'name': 'skirt', 'icon_class': 'ri-user-smile-line', 'order': 5},
            {'name': 'shoes', 'icon_class': 'ri-footprint-line', 'order': 6},
            {'name': 'accessories', 'icon_class': 'ri-honour-line', 'order': 7},
            {'name': 'hat', 'icon_class': 'ri-hat-line', 'order': 8},
        ]

        for cat_data in categories:
            cat, created = ClothingCategory.objects.get_or_create(
                name=cat_data['name'],
                defaults={
                    'icon_class': cat_data['icon_class'],
                    'order': cat_data['order']
                }
            )
            if created:
                self.stdout.write(self.style.SUCCESS(f'Created category: {cat.get_name_display()}'))
            else:
                self.stdout.write(f'Category already exists: {cat.get_name_display()}')

        self.stdout.write(self.style.SUCCESS('\nAll categories initialized!'))