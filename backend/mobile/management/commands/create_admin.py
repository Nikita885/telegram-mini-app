from django.core.management.base import BaseCommand, CommandError

from api.models import TelegramUser


class Command(BaseCommand):
    help = "Grant the admin role (Studio access) to a Telegram user. The user must have signed in once."

    def add_arguments(self, parser):
        who = parser.add_mutually_exclusive_group(required=True)
        who.add_argument("--telegram-id", type=int)
        who.add_argument("--username")
        parser.add_argument("--role", default="admin", choices=["admin", "moderator", "user"])

    def handle(self, *args, **options):
        if options["telegram_id"]:
            user = TelegramUser.objects.filter(telegram_id=options["telegram_id"]).first()
        else:
            user = TelegramUser.objects.filter(username__iexact=options["username"].lstrip("@")).first()
        if user is None:
            raise CommandError(
                "Пользователь не найден. Сначала войдите в приложение или Mini App через Telegram, затем повторите."
            )
        user.role = options["role"]
        user.save(update_fields=["role"])
        self.stdout.write(self.style.SUCCESS(f"{user} ({user.telegram_id}) → {user.role}"))
