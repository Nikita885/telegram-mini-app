import os
from django.core.management.base import BaseCommand
from django.core.files import File
from api.models import Mannequin


class Command(BaseCommand):
    help = 'Initialize mannequin images from static files'

    def add_arguments(self, parser):
        parser.add_argument(
            '--path',
            type=str,
            default=None,
            help='Path to directory with mannequin PNG files (default: auto-detect)',
        )

    def handle(self, *args, **kwargs):
        images_path = kwargs.get('path')

        if not images_path:
            # Production: /frontend/static/images/ (Railway Dockerfile)
            candidates = [
                '/frontend/static/images',
                os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..', 'frontend', 'static', 'images'),
            ]
            for path in candidates:
                if os.path.exists(path):
                    images_path = os.path.normpath(path)
                    break

        if not images_path or not os.path.exists(images_path):
            self.stdout.write(self.style.ERROR(
                f'Images directory not found. Use --path /your/path/to/images'
            ))
            return

        self.stdout.write(f'Loading from: {images_path}')

        mannequins = [
            {'gender': 'male',   'filename': 'mannequin_male.png'},
            {'gender': 'female', 'filename': 'mannequin_female.png'},
        ]

        for data in mannequins:
            filepath = os.path.join(images_path, data['filename'])

            if not os.path.exists(filepath):
                self.stdout.write(self.style.WARNING(f'File not found: {filepath}'))
                continue

            mannequin, created = Mannequin.objects.get_or_create(gender=data['gender'])

            with open(filepath, 'rb') as f:
                mannequin.image.save(data['filename'], File(f), save=True)

            action = 'Created' if created else 'Updated'
            self.stdout.write(self.style.SUCCESS(f'{action}: {mannequin.get_gender_display()}'))

        self.stdout.write(self.style.SUCCESS('\nAll mannequins initialized!'))
