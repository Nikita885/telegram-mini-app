from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0007_mannequin'),
    ]

    operations = [
        migrations.AlterField(
            model_name='clothingitem',
            name='image',
            field=models.ImageField(max_length=500, upload_to='clothing/'),
        ),
    ]
