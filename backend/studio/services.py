"""Studio orchestration: runs the garment pipeline for a GarmentJob and publishes the result."""

import io
import logging
import time

from django.conf import settings
from django.core.files.base import ContentFile
from django.db import transaction
from PIL import Image

from api.models import ClothingItem, Mannequin

from .models import GarmentJob
from .pipeline import garment as G
from .pipeline.mannequin import build_canvas, compute_anchors

log = logging.getLogger(__name__)

GENDERS = ("male", "female")


# ── Mannequins ───────────────────────────────────────────────────────────────


def _png_bytes(image: Image.Image) -> bytes:
    buf = io.BytesIO()
    image.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def _webp_bytes(image: Image.Image, quality: int = 90) -> bytes:
    buf = io.BytesIO()
    image.save(buf, format="WEBP", quality=quality, method=5)
    return buf.getvalue()


def prepare_mannequin(mannequin: Mannequin, force: bool = False) -> Mannequin:
    """Build the normalized canvas and anchors from the uploaded silhouette."""
    if mannequin.canvas and mannequin.anchors and not force:
        return mannequin
    with mannequin.image.open("rb") as f:
        source = Image.open(f)
        source.load()
    canvas = build_canvas(source, settings.MANNEQUIN_CANVAS_SIZE)
    anchors, _ = compute_anchors(canvas)
    mannequin.canvas.save(f"mannequin_{mannequin.gender}.png", ContentFile(_png_bytes(canvas)), save=False)
    mannequin.anchors = anchors
    mannequin.save(update_fields=["canvas", "anchors"])
    _fit_context_cache.pop(mannequin.gender, None)
    return mannequin


_fit_context_cache = {}


def fit_context(gender: str):
    """(canvas image, anchors, body profile) for a mannequin, cached per process."""
    mannequin = Mannequin.objects.get(gender=gender)
    prepare_mannequin(mannequin)
    key = (mannequin.canvas.name, tuple(sorted((k, tuple(v)) for k, v in mannequin.anchors.items())))
    cached = _fit_context_cache.get(gender)
    if cached and cached[0] == key:
        return cached[1]
    with mannequin.canvas.open("rb") as f:
        canvas = Image.open(f).convert("RGBA")
        canvas.load()
    _, profile = compute_anchors(canvas)
    context = (canvas, mannequin.anchors, profile)
    _fit_context_cache[gender] = (key, context)
    return context


def genders_for(item_gender: str):
    return GENDERS if item_gender == "unisex" else (item_gender,)


# ── Job pipeline ─────────────────────────────────────────────────────────────


def _notify(job: GarmentJob):
    """Push job progress to the admin's WebSocket (best effort)."""
    try:
        from mobile.realtime import send_to_user

        send_to_user(job.created_by_id, "studio.job", {"id": job.id, "status": job.status, "stage": job.stage,
                                                       "progress": job.progress})
    except Exception:  # noqa: BLE001 - realtime is optional
        log.debug("studio notify failed", exc_info=True)


def _set_stage(job: GarmentJob, stage: str):
    job.stage = stage
    job.progress = int(100 * GarmentJob.STAGES.index(stage) / len(GarmentJob.STAGES))
    job.save(update_fields=["stage", "progress", "updated_at"])
    _notify(job)


def _open(field) -> Image.Image:
    with field.open("rb") as f:
        image = Image.open(f)
        image.load()
    return image


def _fit_all(job: GarmentJob, cutout: Image.Image, keypoints: dict):
    """Fit the cutout onto every mannequin the item is meant for; stores layers and previews."""
    zone = job.category.zone
    scores, details = [], {}
    # New files get new names (the storage adds a suffix), so clients never see a cached old fit.
    old_files = [getattr(job, f).name for f in ("fitted_male", "fitted_female", "preview_male", "preview_female")
                 if getattr(job, f)]
    for gender in genders_for(job.gender):
        canvas, anchors, profile = fit_context(gender)
        result = G.fit_garment(cutout, keypoints, zone, anchors, profile, canvas.size)
        if result is None:
            continue
        getattr(job, f"fitted_{gender}").save(
            f"job{job.id}_{gender}.webp", ContentFile(_webp_bytes(result.layer, 92)), save=False
        )
        getattr(job, f"preview_{gender}").save(
            f"job{job.id}_{gender}.jpg", ContentFile(_jpeg_bytes(G.preview(canvas, result.layer))), save=False
        )
        scores.append(result.score)
        details[gender] = {k: v for k, v in result.details.items() if k != "control_points"}
    job.fit_score = min(scores) if scores else None
    job.attributes = {**job.attributes, "fit": details}
    current = {getattr(job, f).name for f in ("fitted_male", "fitted_female", "preview_male", "preview_female")}
    for name in old_files:
        if name not in current:
            job.fitted_male.storage.delete(name)


def _jpeg_bytes(image: Image.Image) -> bytes:
    buf = io.BytesIO()
    image.convert("RGB").save(buf, format="JPEG", quality=88, optimize=True)
    return buf.getvalue()


def process_job(job_id: int):
    job = GarmentJob.objects.select_related("category").get(pk=job_id)
    job.status = GarmentJob.STATUS_PROCESSING
    job.error = ""
    job.save(update_fields=["status", "error", "updated_at"])
    timings = {}
    zone = job.category.zone
    try:
        t = time.monotonic()
        _set_stage(job, "quality")
        source = G.load_source(_open(job.source))
        quality = G.assess_quality(source)
        timings["quality"] = round(time.monotonic() - t, 2)

        t = time.monotonic()
        _set_stage(job, "background")
        cutout = G.isolate_garment(source, zone, settings.STUDIO_BG_MODEL)
        timings["background"] = round(time.monotonic() - t, 2)

        t = time.monotonic()
        _set_stage(job, "normalize")
        cutout = G.clean_alpha(cutout)
        warnings = quality["warnings"] + G.framing_warnings(cutout)
        cutout = G.crop_to_content(cutout)
        if zone == "feet":
            cutout = G.pair_single_shoe(cutout)
        job.cutout.save(f"job{job.id}.png", ContentFile(_png_bytes(cutout)), save=False)
        timings["normalize"] = round(time.monotonic() - t, 2)

        t = time.monotonic()
        _set_stage(job, "attributes")
        colors = G.dominant_colors(cutout)
        job.attributes = {"quality": {**quality, "warnings": warnings}, "colors": colors}
        timings["attributes"] = round(time.monotonic() - t, 2)

        t = time.monotonic()
        _set_stage(job, "keypoints")
        job.keypoints = G.detect_keypoints(cutout, zone)
        timings["keypoints"] = round(time.monotonic() - t, 2)

        t = time.monotonic()
        _set_stage(job, "fit")
        _fit_all(job, cutout, job.keypoints)
        timings["fit"] = round(time.monotonic() - t, 2)

        job.stage = "preview"
        job.progress = 100
        job.status = GarmentJob.STATUS_REVIEW
        job.timings = timings
        job.save()
    except G.PipelineError as exc:
        _fail(job, exc.message, exc.code)
    except Exception:  # noqa: BLE001 - any crash must surface in the Studio queue
        log.exception("garment job %s crashed", job.id)
        _fail(job, "Не удалось обработать фото — попробуйте ещё раз", "crash")
    _notify(job)
    return job


def _fail(job: GarmentJob, message: str, code: str):
    job.status = GarmentJob.STATUS_FAILED
    job.error = message
    job.attributes = {**job.attributes, "error_code": code}
    job.save()


def refit_job(job: GarmentJob, keypoints: dict) -> GarmentJob:
    """Re-run only the fitting step with keypoints adjusted by the admin."""
    if not job.cutout:
        raise G.PipelineError("no_cutout", "Фото ещё не обработано")
    cutout = _open(job.cutout).convert("RGBA")
    merged = {**job.keypoints}
    for name, value in keypoints.items():
        if name in merged and isinstance(value, (list, tuple)) and len(value) == 2:
            merged[name] = [float(value[0]), float(value[1])]
    job.keypoints = merged
    _fit_all(job, cutout, merged)
    job.status = GarmentJob.STATUS_REVIEW if job.status != GarmentJob.STATUS_PUBLISHED else job.status
    job.save()
    return job


@transaction.atomic
def publish_job(job: GarmentJob, data: dict) -> ClothingItem:
    """Create (or update) the catalog item from a reviewed job."""
    item = job.item or ClothingItem(created_by=job.created_by)
    item.category = data.get("category") or job.category
    item.gender = data.get("gender") or job.gender
    item.name = data["name"]
    item.style = data.get("style", "") or ""
    item.color = data.get("color") or (job.attributes.get("colors") or [{}])[0].get("name", "")
    item.tags = data.get("tags", "") or ""
    item.item_description = data.get("description", "") or ""
    item.buy_link = data.get("buy_link") or None
    item.brand = data.get("brand", "") or ""
    item.price = data.get("price")
    item.season = data.get("season") or "all"
    item.keypoints = job.keypoints
    item.fit_score = job.fit_score
    item.is_published = True

    with job.cutout.open("rb") as f:
        item.image.save(f"item_job{job.id}.png", ContentFile(f.read()), save=False)
    for gender in GENDERS:
        source = getattr(job, f"fitted_{gender}")
        target = getattr(item, f"fitted_{gender}")
        if source:
            with source.open("rb") as f:
                target.save(f"item_job{job.id}_{gender}.webp", ContentFile(f.read()), save=False)
        elif target:
            target.delete(save=False)
    item.save()

    job.item = item
    job.status = GarmentJob.STATUS_PUBLISHED
    job.save(update_fields=["item", "status", "updated_at"])
    return item


def fit_catalog_item(item: ClothingItem, model: str = None) -> dict:
    """Generate fitted layers for an existing catalog item (e.g. items added via the Mini App)."""
    zone = item.category.zone
    source = G.load_source(_open(item.image))
    cutout = G.isolate_garment(source, zone, model or settings.STUDIO_BG_MODEL)
    cutout = G.crop_to_content(G.clean_alpha(cutout))
    if zone == "feet":
        cutout = G.pair_single_shoe(cutout)
    keypoints = G.detect_keypoints(cutout, zone)
    scores = {}
    for gender in genders_for(item.gender):
        canvas, anchors, profile = fit_context(gender)
        result = G.fit_garment(cutout, keypoints, zone, anchors, profile, canvas.size)
        if result is None:
            continue
        getattr(item, f"fitted_{gender}").save(
            f"item{item.id}_{gender}.webp", ContentFile(_webp_bytes(result.layer, 92)), save=False
        )
        scores[gender] = result.score
    item.keypoints = keypoints
    item.fit_score = min(scores.values()) if scores else None
    # Skip ClothingItem.save() image recompression: only update the new fields.
    ClothingItem.objects.filter(pk=item.pk).update(
        fitted_male=item.fitted_male.name or None,
        fitted_female=item.fitted_female.name or None,
        keypoints=keypoints,
        fit_score=item.fit_score,
    )
    return scores
