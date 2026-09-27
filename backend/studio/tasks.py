from celery import shared_task

from . import services


@shared_task(name="studio.tasks.process_garment_job")
def process_garment_job(job_id: int):
    services.process_job(job_id)
