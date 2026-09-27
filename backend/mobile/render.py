"""Server-side outfit render: mannequin canvas + layers → 1080×1440 image.

Layer geometry (shared with the Android constructor, see OutfitGeometry.java):
  * canvas is W×H (settings.MANNEQUIN_CANVAS_SIZE); x, y are the layer centre in 0..1 of W, H;
  * a fitted layer's base box is the whole canvas (W×H) — at x=y=0.5, scale=1 it sits on the body;
  * a free layer's base box is FREE_BASE_WIDTH·W wide, height from the image aspect ratio;
  * the box is mirrored horizontally if `flipped`, scaled by `scale`, rotated `rotation` degrees
    clockwise around its centre; layers are drawn by ascending `z`.
"""

import io

from django.conf import settings
from PIL import Image

from studio.pipeline.mannequin import CANVAS_TONE

FREE_BASE_WIDTH = 0.4
OUTPUT_WIDTH = 1080


def _open(field) -> Image.Image:
    with field.open("rb") as f:
        image = Image.open(f)
        image.load()
    return image.convert("RGBA")


def render_outfit(mannequin, layers) -> bytes:
    """`layers` — iterable of (clothing_item, layer_dict) sorted by z. Returns JPEG bytes."""
    width, height = settings.MANNEQUIN_CANVAS_SIZE
    k = OUTPUT_WIDTH / width
    out_w, out_h = OUTPUT_WIDTH, int(round(height * k))
    out = Image.new("RGBA", (out_w, out_h), CANVAS_TONE + (255,))

    if mannequin is not None and mannequin.canvas:
        body = _open(mannequin.canvas).resize((out_w, out_h), Image.LANCZOS)
        out.alpha_composite(body)

    gender = mannequin.gender if mannequin is not None else "male"
    for clothing, layer in layers:
        fitted_field = clothing.fitted_for(gender)
        use_fitted = bool(layer.get("fitted")) and bool(fitted_field)
        source = _open(fitted_field) if use_fitted else _open(clothing.image)
        if use_fitted:
            base_w, base_h = width, height
        else:
            base_w = FREE_BASE_WIDTH * width
            base_h = base_w * source.height / max(source.width, 1)
        scale = float(layer.get("scale", 1.0))
        w = max(1, int(round(base_w * scale * k)))
        h = max(1, int(round(base_h * scale * k)))
        img = source.resize((w, h), Image.LANCZOS)
        if layer.get("flipped"):
            img = img.transpose(Image.FLIP_LEFT_RIGHT)
        rotation = float(layer.get("rotation", 0.0))
        if rotation:
            img = img.rotate(-rotation, resample=Image.BICUBIC, expand=True)
        cx = float(layer.get("x", 0.5)) * width * k
        cy = float(layer.get("y", 0.5)) * height * k
        _paste_clipped(out, img, cx, cy)

    buf = io.BytesIO()
    out.convert("RGB").save(buf, format="JPEG", quality=90, optimize=True, progressive=True)
    return buf.getvalue()


def _paste_clipped(out: Image.Image, img: Image.Image, cx: float, cy: float):
    """Composite `img` centred at (cx, cy), clipping whatever falls outside the output."""
    left = int(round(cx - img.width / 2))
    top = int(round(cy - img.height / 2))
    crop_left, crop_top = max(0, -left), max(0, -top)
    if crop_left >= img.width or crop_top >= img.height:
        return
    part = img.crop((crop_left, crop_top, img.width, img.height))
    dest = (max(0, left), max(0, top))
    if dest[0] >= out.width or dest[1] >= out.height:
        return
    part = part.crop((0, 0, min(part.width, out.width - dest[0]), min(part.height, out.height - dest[1])))
    out.alpha_composite(part, dest=dest)
