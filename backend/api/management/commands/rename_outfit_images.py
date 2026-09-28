"""Give outfit images created before random file names an unguessable name (run once after upgrading).

Media is served publicly; old names like outfits/outfit_42.jpg could be enumerated, exposing private
and followers-only outfits. Links to the image inside shared-outfit messages are updated too.
"""

import re

from django.core.files.base import ContentFile
from django.core.management.base import BaseCommand

from api.models import Message, OutfitPost

RANDOM_NAME = re.compile(r"^outfits/outfit_[\w-]{22}\.\w+$")


class Command(BaseCommand):
    help = "Rename outfit images to unguessable names"

    def handle(self, *args, **options):
        renamed = 0
        for post in OutfitPost.objects.exclude(final_image="").exclude(final_image__isnull=True).iterator():
            old = post.final_image.name
            if RANDOM_NAME.match(old):
                continue
            storage = post.final_image.storage
            if not storage.exists(old):
                continue
            with storage.open(old, "rb") as f:
                data = f.read()
            post.final_image.save(old.rsplit("/", 1)[-1], ContentFile(data), save=False)
            new = post.final_image.name
            OutfitPost.objects.filter(pk=post.pk).update(final_image=new)
            for message in Message.objects.filter(text__contains=old).only("pk", "text"):
                Message.objects.filter(pk=message.pk).update(text=message.text.replace(old, new))
            storage.delete(old)
            renamed += 1
        self.stdout.write(self.style.SUCCESS(f"Renamed {renamed} outfit images"))
