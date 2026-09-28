"""Demo content: catalog items from a folder of product photos (through the real Studio pipeline),
a few users and outfits built from them. For local testing and first-run demos.

    python manage.py seed_demo --images /path/to/photos --map photos.csv

The CSV maps file names to categories: `file,category,gender,name` (category = ClothingCategory.name).
Without --map, files named like `shirt__Синяя рубашка.jpg` are understood as category__name.
"""

import csv
import os
import random

from django.core.files import File
from django.core.management.base import BaseCommand, CommandError

from api.models import ClothingCategory, Follow, TelegramUser
from mobile.services import add_comment, create_outfit, set_like
from studio.models import GarmentJob
from studio.services import process_job, publish_job

DEMO_USERS = [
    (900001, "anna.kov", "Анна", "Ковалёва", "Стилист. Капсулы на каждый сезон"),
    (900002, "dan.sh", "Даниил", "Шестаков", "Собираю ремиксы"),
    (900003, "mila_o", "Мила", "Орлова", "Минимализм, лён и хорошие ботинки"),
]


class Command(BaseCommand):
    help = "Seed demo catalog items, users and outfits"

    def add_arguments(self, parser):
        parser.add_argument("--images", required=True)
        parser.add_argument("--map", default=None)
        parser.add_argument("--admin-telegram-id", type=int, default=None)

    def handle(self, *args, **opts):
        folder = opts["images"]
        if not os.path.isdir(folder):
            raise CommandError(f"no such folder: {folder}")
        rows = self._rows(folder, opts["map"])
        admin = self._admin(opts["admin_telegram_id"])
        users = [self._user(*u) for u in DEMO_USERS]

        items = []
        for row in rows:
            category = ClothingCategory.objects.filter(name=row["category"]).first()
            if category is None:
                self.stderr.write(f"skip {row['file']}: unknown category {row['category']}")
                continue
            job = GarmentJob(created_by=admin, category=category, gender=row.get("gender") or "unisex")
            with open(os.path.join(folder, row["file"]), "rb") as f:
                job.source.save(row["file"], File(f), save=True)
            job = process_job(job.pk)
            if job.status != GarmentJob.STATUS_REVIEW:
                self.stderr.write(f"{row['file']}: {job.status} {job.error}")
                continue
            item = publish_job(job, {"name": row["name"], "style": row.get("style", "casual")})
            items.append(item)
            self.stdout.write(self.style.SUCCESS(f"item {item.name} fit={job.fit_score}"))

        by_zone = {}
        for item in items:
            by_zone.setdefault(item.category.zone, []).append(item)
        rng = random.Random(7)
        captions = [
            "База на каждый день #капсула #осень",
            "Офис без скуки #офис #минимализм",
            "Выходные в городе #casual",
            "Слои и фактуры #осень #тренч",
        ]
        outfits = []
        for i in range(6):
            author = users[i % len(users)]
            gender = "female" if i % 2 == 0 else "male"
            layers = []
            for zone in ("lower", "upper", "feet"):
                candidates = [it for it in by_zone.get(zone, []) if it.gender in (gender, "unisex")]
                if candidates:
                    it = rng.choice(candidates)
                    fitted = bool(it.fitted_for(gender))
                    layers.append({"item_id": it.pk, "x": 0.5, "y": 0.5, "scale": 1.0, "rotation": 0.0,
                                   "z": it.category.default_layer, "flipped": False, "fitted": fitted})
            if not layers:
                continue
            post = create_outfit(author, {"mannequin": gender, "description": captions[i % len(captions)],
                                          "hashtags": [], "visibility": "all", "layers": layers})
            outfits.append(post)
        for post in outfits:
            for u in users:
                if u != post.user and rng.random() < 0.7:
                    set_like(u, post, True)
            add_comment(users[(post.pk + 1) % len(users)], post, "Классное сочетание!")
        for a in users:
            for b in users:
                if a != b:
                    Follow.objects.get_or_create(follower=a, following=b)
        self.stdout.write(self.style.SUCCESS(f"{len(items)} items, {len(outfits)} outfits"))

    def _rows(self, folder, map_path):
        if map_path:
            with open(map_path, encoding="utf-8") as f:
                return list(csv.DictReader(f))
        rows = []
        for name in sorted(os.listdir(folder)):
            base, ext = os.path.splitext(name)
            if ext.lower() not in (".jpg", ".jpeg", ".png", ".webp") or "__" not in base:
                continue
            category, title = base.split("__", 1)
            rows.append({"file": name, "category": category, "name": title.replace("_", " ")})
        return rows

    def _admin(self, telegram_id):
        if telegram_id:
            user = TelegramUser.objects.filter(telegram_id=telegram_id).first()
            if user:
                return user
        user, _ = TelegramUser.objects.get_or_create(
            telegram_id=900000, defaults={"username": "studio", "first_name": "Студия", "role": "admin"}
        )
        return user

    def _user(self, telegram_id, username, first, last, bio):
        user, _ = TelegramUser.objects.get_or_create(
            telegram_id=telegram_id,
            defaults={"username": username, "first_name": first, "last_name": last, "bio": bio},
        )
        return user
