"""Pin and «delete dialog» become per participant (a delete no longer wipes the other side)."""

from django.db import migrations, models


def copy_pinned(apps, schema_editor):
    Dialog = apps.get_model("api", "Dialog")
    Dialog.objects.filter(pinned=True).update(user1_pinned=True, user2_pinned=True)


def restore_pinned(apps, schema_editor):
    Dialog = apps.get_model("api", "Dialog")
    Dialog.objects.filter(models.Q(user1_pinned=True) | models.Q(user2_pinned=True)).update(pinned=True)


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0010_backfill_mobile_fields"),
    ]

    operations = [
        migrations.AddField(
            model_name="dialog",
            name="user1_pinned",
            field=models.BooleanField(default=False),
        ),
        migrations.AddField(
            model_name="dialog",
            name="user2_pinned",
            field=models.BooleanField(default=False),
        ),
        migrations.AddField(
            model_name="dialog",
            name="user1_cleared_at",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name="dialog",
            name="user2_cleared_at",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.RunPython(copy_pinned, restore_pinned),
        migrations.RemoveField(
            model_name="dialog",
            name="pinned",
        ),
        migrations.AlterModelOptions(
            name="dialog",
            options={"ordering": ["-updated_at"]},
        ),
    ]
