# backend/api/management/commands/load_test_clothing.py
from django.core.management.base import BaseCommand
from django.core.files.base import ContentFile
from api.models import ClothingCategory, ClothingTag, ClothingItem
import requests
from io import BytesIO
from PIL import Image, ImageDraw

class Command(BaseCommand):
    help = 'Load test clothing items with placeholder images'
    
    def handle(self, *args, **options):
        # Получаем категории
        tops = ClothingCategory.objects.get(slug='tops')
        bottoms = ClothingCategory.objects.get(slug='bottoms')
        shoes = ClothingCategory.objects.get(slug='shoes')
        accessories = ClothingCategory.objects.get(slug='accessories')
        
        # Получаем теги
        black = ClothingTag.objects.get(slug='black', tag_type='color')
        white = ClothingTag.objects.get(slug='white', tag_type='color')
        red = ClothingTag.objects.get(slug='red', tag_type='color')
        blue = ClothingTag.objects.get(slug='blue', tag_type='color')
        
        casual = ClothingTag.objects.get(slug='casual', tag_type='style')
        formal = ClothingTag.objects.get(slug='formal', tag_type='style')
        sport = ClothingTag.objects.get(slug='sport', tag_type='style')
        
        # Создаем тестовые предметы одежды
        test_items = [
            # Верх
            {
                'name': 'Черная футболка',
                'category': tops,
                'tags': [black, casual],
                'color': '#000000',
                'z_index': 2
            },
            {
                'name': 'Белая рубашка',
                'category': tops,
                'tags': [white, formal],
                'color': '#FFFFFF',
                'z_index': 2
            },
            {
                'name': 'Синяя толстовка',
                'category': tops,
                'tags': [blue, sport],
                'color': '#0000FF',
                'z_index': 2
            },
            {
                'name': 'Красная футболка',
                'category': tops,
                'tags': [red, casual],
                'color': '#FF0000',
                'z_index': 2
            },
            
            # Низ
            {
                'name': 'Черные джинсы',
                'category': bottoms,
                'tags': [black, casual],
                'color': '#1a1a1a',
                'z_index': 1
            },
            {
                'name': 'Синие джинсы',
                'category': bottoms,
                'tags': [blue, casual],
                'color': '#000080',
                'z_index': 1
            },
            {
                'name': 'Черные брюки',
                'category': bottoms,
                'tags': [black, formal],
                'color': '#000000',
                'z_index': 1
            },
            
            # Обувь
            {
                'name': 'Черные кроссовки',
                'category': shoes,
                'tags': [black, sport],
                'color': '#000000',
                'z_index': 0
            },
            {
                'name': 'Белые кроссовки',
                'category': shoes,
                'tags': [white, sport],
                'color': '#FFFFFF',
                'z_index': 0
            },
            {
                'name': 'Черные туфли',
                'category': shoes,
                'tags': [black, formal],
                'color': '#000000',
                'z_index': 0
            },
            
            # Аксессуары
            {
                'name': 'Черная шапка',
                'category': accessories,
                'tags': [black, casual],
                'color': '#000000',
                'z_index': 3
            },
            {
                'name': 'Красный шарф',
                'category': accessories,
                'tags': [red, casual],
                'color': '#FF0000',
                'z_index': 3
            },
        ]
        
        created_count = 0
        
        for item_data in test_items:
            # Проверяем, не существует ли уже
            if ClothingItem.objects.filter(name=item_data['name']).exists():
                self.stdout.write(f"  Пропускаем '{item_data['name']}' - уже существует")
                continue
            
            # Создаем placeholder изображение
            img = self.create_placeholder_image(
                item_data['color'],
                item_data['name'],
                item_data['category'].name
            )
            
            # Сохраняем изображение в BytesIO
            img_io = BytesIO()
            img.save(img_io, format='PNG')
            img_io.seek(0)
            
            # Создаем предмет одежды
            tags = item_data.pop('tags')
            color = item_data.pop('color')
            
            clothing_item = ClothingItem.objects.create(
                name=item_data['name'],
                category=item_data['category'],
                default_z_index=item_data['z_index']
            )
            
            # Сохраняем изображение
            filename = f"{item_data['name'].lower().replace(' ', '_')}.png"
            clothing_item.image.save(filename, ContentFile(img_io.read()), save=False)
            
            # Добавляем теги
            clothing_item.tags.set(tags)
            clothing_item.save()
            
            created_count += 1
            self.stdout.write(self.style.SUCCESS(f"  ✓ Создано: {item_data['name']}"))
        
        self.stdout.write(self.style.SUCCESS(f'\nВсего создано предметов: {created_count}'))
    
    def create_placeholder_image(self, color, name, category):
        """Создает простое изображение-заглушку"""
        # Создаем изображение 300x400 с прозрачным фоном
        img = Image.new('RGBA', (300, 400), (255, 255, 255, 0))
        draw = ImageDraw.Draw(img)
        
        # Рисуем простую форму в зависимости от категории
        if category == 'Верх':
            # Прямоугольник для футболки/рубашки
            draw.rectangle([50, 100, 250, 300], fill=color)
            # "Рукава"
            draw.rectangle([20, 120, 50, 200], fill=color)
            draw.rectangle([250, 120, 280, 200], fill=color)
        
        elif category == 'Низ':
            # Штаны
            draw.rectangle([80, 50, 150, 350], fill=color)
            draw.rectangle([150, 50, 220, 350], fill=color)
        
        elif category == 'Обувь':
            # Обувь
            draw.ellipse([50, 200, 150, 280], fill=color)
            draw.ellipse([150, 200, 250, 280], fill=color)
        
        elif category == 'Аксессуары':
            # Круг для шапки/шарфа
            draw.ellipse([100, 150, 200, 250], fill=color)
        
        else:
            # Простой квадрат по умолчанию
            draw.rectangle([75, 100, 225, 300], fill=color)
        
        return img