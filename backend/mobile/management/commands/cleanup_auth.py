"""Delete stale login requests and expired refresh tokens (the bot also does this hourly)."""

from django.core.management.base import BaseCommand

from mobile.auth import cleanup_expired


class Command(BaseCommand):
    help = "Delete login requests older than a day and expired refresh tokens"

    def handle(self, *args, **options):
        nonces, tokens = cleanup_expired()
        self.stdout.write(f"Deleted {nonces} login requests, {tokens} refresh tokens")
