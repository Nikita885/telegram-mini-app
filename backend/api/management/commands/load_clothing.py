# backend/api/management/commands/load_clothing.py
from django.core.management.base import BaseCommand
from api.models import ClothingCategory, ClothingTag, ClothingItem
import json

class Command(BaseCommand):
    help = 'Load clothing database'
    
    def handle(self, *args, **options):
        # Создать категории
        categories = {
            'tops': ('Верх', 1, 'ri-t-shirt-line'),
            'bottoms': ('Низ', 2, 'ri-folder-line'),
            'shoes': ('Обувь', 3, 'ri-footprint-line'),
            'accessories': ('Аксессуары', 4, 'ri-bear-smile-line'),
        }
        
        for slug, (name, order, icon) in categories.items():
            ClothingCategory.objects.get_or_create(
                slug=slug,
                defaults={'name': name, 'order': order, 'icon': icon}
            )
        
        # Создать теги цветов
        colors = [
            ('black', 'Черный', '#000000'),
            ('white', 'Белый', '#FFFFFF'),
            ('red', 'Красный', '#FF0000'),
            ('blue', 'Синий', '#0000FF'),
            ('green', 'Зеленый', '#00FF00'),
            ('yellow', 'Желтый', '#FFFF00'),
            ('pink', 'Розовый', '#FFC0CB'),
            ('gray', 'Серый', '#808080'),
        ]
        
        for slug, name, hex_color in colors:
            ClothingTag.objects.get_or_create(
                slug=slug,
                tag_type='color',
                defaults={'name': name, 'color_hex': hex_color}
            )
        
        # Создать теги стилей
        styles = [
            ('casual', 'Casual'),
            ('formal', 'Formal'),
            ('sport', 'Sport'),
            ('streetwear', 'Streetwear'),
            ('vintage', 'Vintage'),
        ]
        
        for slug, name in styles:
            ClothingTag.objects.get_or_create(
                slug=slug,
                tag_type='style',
                defaults={'name': name}
            )
        
        self.stdout.write(self.style.SUCCESS('Database loaded successfully!'))