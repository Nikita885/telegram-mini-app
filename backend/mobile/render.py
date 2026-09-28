"""Server-side outfit render: mannequin canvas + layers → 1080×1440 image.

Layer geometry (shared with the Android constructor, see OutfitGeometry.java):
  * canvas is W×H (settings.MANNEQUIN_CANVAS_SIZE); x, y are the layer centre in 0..1 of W, H;
  * a fitted layer's base box is the whole canvas (W×H) — at x=y=0.5, scale=1 it sits on the body;
  * a free layer's base box is FREE_BASE_WIDTH·W wide, height from the image aspect ratio;
  * the box is mirrored horizontally if `flipped`, scaled by `scale`, rotated `rotation` degrees
    clockwise around its centre; layers are drawn by ascending `z`.

Each layer is drawn with one affine transform straight into the part of the output it covers, so
memory and time are bounded by the output size whatever the scale — a huge `scale` can no longer make
the server allocate a gigapixel intermediate image.
"""

import io
import math

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
        draw_layer(
            out,
            source,
            box_w=base_w * scale * k,
            box_h=base_h * scale * k,
            cx=float(layer.get("x", 0.5)) * width * k,
            cy=float(layer.get("y", 0.5)) * height * k,
            rotation=float(layer.get("rotation", 0.0)),
            flipped=bool(layer.get("flipped")),
        )

    buf = io.BytesIO()
    out.convert("RGB").save(buf, format="JPEG", quality=90, optimize=True, progressive=True)
    return buf.getvalue()


def draw_layer(out, source, *, box_w, box_h, cx, cy, rotation=0.0, flipped=False):
    """Composite `source`, stretched to a box_w×box_h box centred at (cx, cy), onto `out` (output px).

    The box is mirrored first (if `flipped`), then rotated `rotation` degrees clockwise.
    """
    if box_w < 1 or box_h < 1:
        return
    # Shrinking a large image by an affine transform alone aliases; pre-scale it to the box size first
    # (never enlarging, so this intermediate is at most as big as the source).
    if source.width > box_w * 1.5 or source.height > box_h * 1.5:
        source = source.resize(
            (max(1, min(source.width, round(box_w))), max(1, min(source.height, round(box_h)))), Image.LANCZOS
        )

    theta = math.radians(rotation)
    cos, sin = math.cos(theta), math.sin(theta)
    corners = []
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        x, y = sx * box_w / 2, sy * box_h / 2
        corners.append((cx + x * cos - y * sin, cy + x * sin + y * cos))
    left = max(0, math.floor(min(p[0] for p in corners)))
    top = max(0, math.floor(min(p[1] for p in corners)))
    right = min(out.width, math.ceil(max(p[0] for p in corners)))
    bottom = min(out.height, math.ceil(max(p[1] for p in corners)))
    if right <= left or bottom <= top:
        return

    # Inverse mapping: output pixel (X, Y) → source point (u, v), both in continuous pixel coordinates.
    #   q = (X - cx, Y - cy) rotated back by -theta, un-mirrored, scaled from box units to source pixels.
    su = source.width / box_w * (-1 if flipped else 1)
    sv = source.height / box_h
    a, b = su * cos, su * sin
    d, e = -sv * sin, sv * cos
    c = source.width / 2 - (a * cx + b * cy)
    f = source.height / 2 - (d * cx + e * cy)
    # The transform works on the covered region only: shift the origin to its top-left corner.
    c += a * left + b * top
    f += d * left + e * top
    part = source.transform(
        (right - left, bottom - top), Image.AFFINE, (a, b, c, d, e, f), resample=Image.BICUBIC
    )
    out.alpha_composite(part, dest=(left, top))
