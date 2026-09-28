"""Garment processing: quality checks, cutout, colour attributes, keypoints and fitting."""

from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ImageOps
from scipy import ndimage

from .geometry import local_distortion, warp_rgba
from .mannequin import BodyProfile

MAX_SOURCE_SIDE = 1600


class PipelineError(Exception):
    """A problem the admin can fix by reshooting; `code` is shown in the Studio queue."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


# ── Quality ──────────────────────────────────────────────────────────────────


def load_source(image: Image.Image) -> Image.Image:
    image = ImageOps.exif_transpose(image)
    if max(image.size) > MAX_SOURCE_SIDE:
        image = image.copy()
        image.thumbnail((MAX_SOURCE_SIDE, MAX_SOURCE_SIDE), Image.LANCZOS)
    return image


def assess_quality(image: Image.Image) -> dict:
    """Sharpness (variance of the Laplacian) and exposure on a 512 px grayscale copy."""
    gray = image.convert("L")
    gray.thumbnail((512, 512))
    g = np.asarray(gray, dtype=np.float32)
    laplacian = ndimage.laplace(g)
    sharpness = float(laplacian.var())
    brightness = float(g.mean() / 255.0)
    warnings = []
    if sharpness < 25:
        warnings.append("blurry")
    if brightness < 0.18:
        warnings.append("dark")
    if brightness > 0.95:
        warnings.append("overexposed")
    return {"sharpness": round(sharpness, 1), "brightness": round(brightness, 3), "warnings": warnings}


def has_transparency(image: Image.Image) -> bool:
    if image.mode not in ("RGBA", "LA", "PA") and not (image.mode == "P" and "transparency" in image.info):
        return False
    alpha = np.asarray(image.convert("RGBA").split()[-1])
    return bool((alpha < 250).mean() > 0.02)


# ── Background removal ───────────────────────────────────────────────────────

_sessions = {}


def _session(model: str):
    from rembg import new_session  # heavy import, only in the worker

    if model not in _sessions:
        _sessions[model] = new_session(model)
    return _sessions[model]


def remove_background(image: Image.Image, model: str) -> Image.Image:
    """Cut the foreground out with rembg; images that already carry transparency are kept as is."""
    if has_transparency(image):
        return image.convert("RGBA")
    from rembg import remove

    return remove(image.convert("RGB"), session=_session(model)).convert("RGBA")


# u2net_cloth_seg classes: 1 upper body, 2 lower body, 3 full body.
_CLOTH_CLASSES = {"upper": (1, 3), "lower": (2,), "full": (1, 2, 3)}
CLOTH_SEG_MODEL = "u2net_cloth_seg"


def cloth_mask(image: Image.Image, zone: str):
    """Mask of the garment worn in the zone (drops skin, face and other clothes), or None."""
    classes = _CLOTH_CLASSES.get(zone)
    if not classes:
        return None
    masks = _session(CLOTH_SEG_MODEL).predict(image.convert("RGB"))
    combined = np.zeros((image.height, image.width), dtype=bool)
    for cls in classes:
        combined |= np.asarray(masks[cls - 1]) > 127
    return combined


def isolate_garment(image: Image.Image, zone: str, model: str, segment=cloth_mask) -> Image.Image:
    """Background removal plus, for photos of a worn garment, dropping the person around it.

    Flat-lay or hanger shots contain no person; the cloth segmenter then either covers the whole
    cutout or fails on it — in both cases the plain cutout is kept.
    """
    cutout = remove_background(image, model)
    if has_transparency(image):
        return cutout
    garment = segment(image, zone)
    if garment is None:
        return cutout
    alpha = np.asarray(cutout.split()[-1], dtype=np.float32) / 255.0
    foreground = alpha > 0.5
    fg_area = max(int(foreground.sum()), 1)
    share = float((garment & foreground).sum()) / fg_area
    if share < 0.12 or share > 0.9:
        return cutout
    # Grow the coarse class mask a little and keep the fine matting edge from the cutout.
    grown = ndimage.binary_dilation(garment, iterations=3)
    soft = ndimage.gaussian_filter(grown.astype(np.float32), sigma=1.5)
    rgba = np.asarray(cutout).copy()
    rgba[..., 3] = (alpha * np.clip(soft * 1.4, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def clean_alpha(cutout: Image.Image) -> Image.Image:
    """Drop specks and holes left by matting; keep every blob ≥ 3% of the largest (pairs of shoes)."""
    rgba = np.asarray(cutout.convert("RGBA")).copy()
    alpha = rgba[..., 3]
    mask = alpha > 40
    labels, count = ndimage.label(mask)
    if count == 0:
        raise PipelineError("not_found", "Вещь не найдена на фото — снимите её целиком на ровном фоне")
    sizes = ndimage.sum(mask, labels, range(1, count + 1))
    keep = np.zeros(count + 1, dtype=bool)
    keep[1:] = sizes >= 0.03 * sizes.max()
    rgba[..., 3] = np.where(keep[labels], alpha, 0).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def crop_to_content(cutout: Image.Image, padding: int = 4) -> Image.Image:
    alpha = np.asarray(cutout.split()[-1])
    ys, xs = np.nonzero(alpha > 40)
    if len(xs) == 0:
        raise PipelineError("not_found", "Вещь не найдена на фото — снимите её целиком на ровном фоне")
    box = (
        max(0, xs.min() - padding),
        max(0, ys.min() - padding),
        min(cutout.width, xs.max() + 1 + padding),
        min(cutout.height, ys.max() + 1 + padding),
    )
    return cutout.crop(box)


def framing_warnings(cutout: Image.Image) -> list:
    alpha = np.asarray(cutout.split()[-1]) > 40
    coverage = float(alpha.mean())
    warnings = []
    if coverage < 0.03:
        raise PipelineError("too_small", "Вещь слишком маленькая в кадре — подойдите ближе")
    edges = [alpha[0].mean(), alpha[-1].mean(), alpha[:, 0].mean(), alpha[:, -1].mean()]
    if sum(e > 0.05 for e in edges) >= 2:
        warnings.append("cropped")
    return warnings


# ── Colour ───────────────────────────────────────────────────────────────────

NAMED_COLORS = [
    ("белый", "#F7F7F5"), ("молочный", "#EFE8DA"), ("бежевый", "#D9C4A5"), ("песочный", "#C8A97E"),
    ("коричневый", "#7A5134"), ("шоколадный", "#4A2F22"), ("чёрный", "#1C1C1C"), ("графитовый", "#3F4145"),
    ("серый", "#8E8E8E"), ("светло-серый", "#C9C9C9"), ("красный", "#C62828"), ("бордовый", "#6D1A2A"),
    ("розовый", "#E89AB0"), ("пудровый", "#E3C3BE"), ("оранжевый", "#E8772E"), ("терракотовый", "#B4553A"),
    ("жёлтый", "#F2CF3C"), ("горчичный", "#C49A2C"), ("оливковый", "#6B6B34"), ("зелёный", "#2E7D4F"),
    ("хаки", "#8A8458"), ("мятный", "#A8DCC8"), ("бирюзовый", "#2A9D9A"), ("голубой", "#8EC1E6"),
    ("синий", "#2F55B5"), ("тёмно-синий", "#1F2A48"), ("фиолетовый", "#6B3FA0"), ("лавандовый", "#B9A7DB"),
    ("джинсовый", "#4A6A8F"),
]


def _srgb_to_lab(rgb: np.ndarray) -> np.ndarray:
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ m.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[:, 1] - 16, 500 * (f[:, 0] - f[:, 1]), 200 * (f[:, 1] - f[:, 2])], axis=1)


_NAMED_LAB = _srgb_to_lab(
    np.array([[int(h[i : i + 2], 16) for i in (1, 3, 5)] for _, h in NAMED_COLORS], dtype=np.float64)
)


def dominant_colors(cutout: Image.Image, k: int = 3, seed: int = 7) -> list:
    """k-means in Lab over opaque pixels → [{name, hex, share}] sorted by share."""
    rgba = np.asarray(cutout.convert("RGBA")).reshape(-1, 4)
    pixels = rgba[rgba[:, 3] > 200][:, :3].astype(np.float64)
    if len(pixels) == 0:
        return []
    rng = np.random.default_rng(seed)
    if len(pixels) > 6000:
        pixels = pixels[rng.choice(len(pixels), 6000, replace=False)]
    lab = _srgb_to_lab(pixels)
    k = min(k, len(lab))
    centers = lab[rng.choice(len(lab), k, replace=False)]
    for _ in range(15):
        labels = np.argmin(((lab[:, None] - centers[None]) ** 2).sum(-1), axis=1)
        new = np.array([lab[labels == i].mean(0) if (labels == i).any() else centers[i] for i in range(k)])
        if np.allclose(new, centers, atol=0.1):
            break
        centers = new
    result = []
    for i in range(k):
        share = float((labels == i).mean())
        if share < 0.08:
            continue
        rgb_mean = pixels[labels == i].mean(0)
        name_idx = int(np.argmin(((_NAMED_LAB - centers[i]) ** 2).sum(-1)))
        result.append(
            {
                "name": NAMED_COLORS[name_idx][0],
                "hex": "#{:02X}{:02X}{:02X}".format(*[int(round(v)) for v in rgb_mean]),
                "share": round(share, 3),
            }
        )
    result.sort(key=lambda c: -c["share"])
    # Merge clusters that map to the same name.
    merged = {}
    for color in result:
        if color["name"] in merged:
            merged[color["name"]]["share"] = round(merged[color["name"]]["share"] + color["share"], 3)
        else:
            merged[color["name"]] = dict(color)
    return sorted(merged.values(), key=lambda c: -c["share"])


def pair_single_shoe(cutout: Image.Image) -> Image.Image:
    """A single shoe shot from the side becomes a mirrored pair, as worn on the mannequin."""
    alpha = np.asarray(cutout.split()[-1]) > 60
    labels, count = ndimage.label(alpha)
    if count == 0:
        return cutout
    sizes = ndimage.sum(alpha, labels, range(1, count + 1))
    if (sizes >= 0.2 * sizes.max()).sum() > 1 or cutout.width < cutout.height * 1.2:
        return cutout
    gap = int(cutout.width * 0.08)
    pair = Image.new("RGBA", (cutout.width * 2 + gap, cutout.height), (0, 0, 0, 0))
    pair.paste(ImageOps.mirror(cutout), (0, 0))
    pair.paste(cutout, (cutout.width + gap, 0))
    return pair


# ── Keypoints ────────────────────────────────────────────────────────────────


def _row_runs(mask_row):
    padded = np.concatenate([[False], mask_row, [False]])
    diff = np.diff(padded.astype(np.int8))
    return list(zip(np.nonzero(diff == 1)[0], np.nonzero(diff == -1)[0] - 1, strict=True))


def _center_run(mask, y, cx):
    runs = _row_runs(mask[y])
    if not runs:
        return None
    for start, end in runs:
        if start <= cx <= end:
            return float(start), float(end)
    # Centre falls in a gap (e.g. between trouser legs): take the whole span.
    return float(runs[0][0]), float(runs[-1][1])


def _span(mask, y):
    runs = _row_runs(mask[y])
    if not runs:
        return None
    return float(runs[0][0]), float(runs[-1][1])


def detect_keypoints(cutout: Image.Image, zone: str) -> dict:
    """Garment keypoints in cutout pixels, per body zone. Heuristic, editable in the Studio."""
    mask = np.asarray(cutout.split()[-1]) > 60
    ys, xs = np.nonzero(mask)
    top, bottom = int(ys.min()), int(ys.max())
    left, right = int(xs.min()), int(xs.max())
    gh = bottom - top + 1
    cx = (left + right) / 2.0

    def row(frac):
        return int(np.clip(top + frac * gh, top, bottom))

    if zone in ("upper", "full"):
        top_runs = _row_runs(mask[top + 1])
        neck_x = (top_runs[0][0] + top_runs[-1][1]) / 2.0 if top_runs else cx
        # Torso width: the narrowest body band (waist for tops, bust for dresses). Sleeves that hang
        # along the body only widen rows, so the minimum is the torso itself.
        band = (0.35, 0.65) if zone == "upper" else (0.2, 0.45)
        widths = []
        for frac in np.linspace(band[0], band[1], 16):
            run = _center_run(mask, row(frac), neck_x)
            if run:
                widths.append((run[1] - run[0], frac, run))
        if widths:
            torso, waist_frac, waist_run = min(widths)
        else:
            torso, waist_frac, waist_run = float(right - left), 0.5, (float(left), float(right))
        torso = float(torso)
        half = torso * 0.54
        # Shoulder line: first row under the collar as wide as the torso.
        shoulder_y = row(0.12)
        for frac in np.linspace(0.04, 0.3, 27):
            run = _center_run(mask, row(frac), neck_x)
            if run and run[1] - run[0] >= 0.9 * torso:
                shoulder_y = row(frac)
                break
        sl, sr = neck_x - half, neck_x + half
        # Hem: median span over the lowest rows, clamped so stray flaps don't drag the warp.
        hem_y = row(0.985)
        spans = [_span(mask, row(f)) for f in np.linspace(0.9, 0.98, 9)]
        spans = [sp for sp in spans if sp]
        hem_half = float(np.median([(b - a) / 2 for a, b in spans])) if spans else half
        hem_half = float(np.clip(hem_half, 0.6 * half, 1.6 * half))
        waist_y = row(waist_frac)
        # Symmetric waist around the collar centre: an arm pressed to one side must not skew it.
        waist_half = min(neck_x - waist_run[0], waist_run[1] - neck_x)
        waist_half = float(np.clip(waist_half, 0.75 * half, 1.15 * half))
        waist_run = (neck_x - waist_half, neck_x + waist_half)
        return {
            "neck": [neck_x, float(top)],
            "shoulder_left": [sl, float(shoulder_y)],
            "shoulder_right": [sr, float(shoulder_y)],
            "waist_left": [float(waist_run[0]), float(waist_y)],
            "waist_right": [float(waist_run[1]), float(waist_y)],
            "hem_left": [neck_x - hem_half, float(hem_y)],
            "hem_right": [neck_x + hem_half, float(hem_y)],
        }
    if zone == "lower":
        waist_y = row(0.02)
        wl, wr = _span(mask, waist_y)
        hem_y = row(0.985)
        hl, hr = _span(mask, hem_y)
        return {
            "waist_left": [wl, float(waist_y)],
            "waist_right": [wr, float(waist_y)],
            "hem_left": [hl, float(hem_y)],
            "hem_right": [hr, float(hem_y)],
        }
    # head, feet, accessory: the bounding box is what matters.
    return {
        "box_left_top": [float(left), float(top)],
        "box_right_bottom": [float(right), float(bottom)],
    }


# ── Fitting ──────────────────────────────────────────────────────────────────

SHOULDER_EASE = 0.035  # garment shoulder seam sits slightly outside the body edge
BODY_EASE = 1.08


@dataclass
class FitResult:
    layer: Image.Image
    score: float
    details: dict = field(default_factory=dict)


def _px(anchors, name, size):
    x, y = anchors[name]
    return np.array([x * size[0], y * size[1]])


def fit_targets(keypoints: dict, zone: str, anchors: dict, profile: BodyProfile, size):
    """Control point pairs (garment px → canvas px) for the zone."""
    width, height = size
    cx = profile.center_x
    kp = {k: np.array(v, dtype=np.float64) for k, v in keypoints.items()}

    if zone in ("upper", "full"):
        sl, sr = _px(anchors, "shoulder_left", size), _px(anchors, "shoulder_right", size)
        ease = SHOULDER_EASE * width
        t_sl = np.array([sl[0] - ease, sl[1]])
        t_sr = np.array([sr[0] + ease, sr[1]])
        g_width = np.linalg.norm(kp["shoulder_right"] - kp["shoulder_left"])
        s = np.linalg.norm(t_sr - t_sl) / max(g_width, 1.0)
        g_shoulder_y = (kp["shoulder_left"][1] + kp["shoulder_right"][1]) / 2
        t_shoulder_y = (t_sl[1] + t_sr[1]) / 2

        neck_t = np.array([cx, t_shoulder_y - (g_shoulder_y - kp["neck"][1]) * s])
        hem_g_y = (kp["hem_left"][1] + kp["hem_right"][1]) / 2
        hem_t_y = t_shoulder_y + (hem_g_y - g_shoulder_y) * s
        ankle_y = _px(anchors, "ankle_left", size)[1]
        if hem_t_y > ankle_y:
            hem_t_y = ankle_y
        g_hem_half = (kp["hem_right"][0] - kp["hem_left"][0]) / 2 * s
        body_half = profile.half_width(hem_t_y) * BODY_EASE
        # Pull the hem halfway towards the body so the garment drapes instead of floating.
        blend = 0.5 if zone == "upper" else 0.25
        hem_half = max((1 - blend) * g_hem_half + blend * body_half, body_half * 0.9)
        src = [kp["neck"], kp["shoulder_left"], kp["shoulder_right"], kp["hem_left"], kp["hem_right"]]
        dst = [neck_t, t_sl, t_sr, [cx - hem_half, hem_t_y], [cx + hem_half, hem_t_y]]
        if "waist_left" in kp and "waist_right" in kp:
            g_waist_y = (kp["waist_left"][1] + kp["waist_right"][1]) / 2
            w_t_y = t_shoulder_y + (g_waist_y - g_shoulder_y) * s
            g_waist_half = (kp["waist_right"][0] - kp["waist_left"][0]) / 2 * s
            w_body = profile.half_width(w_t_y) * BODY_EASE
            w_half = max(0.5 * g_waist_half + 0.5 * w_body, w_body)
            src += [kp["waist_left"], kp["waist_right"]]
            dst += [[cx - w_half, w_t_y], [cx + w_half, w_t_y]]
        return np.array(src), np.array(dst)

    if zone == "lower":
        waist = _px(anchors, "waist_left", size)[1]
        hips = _px(anchors, "hips_left", size)[1]
        t_y = waist + 0.35 * (hips - waist)
        half = profile.half_width(t_y) * 1.04
        t_wl, t_wr = np.array([cx - half, t_y]), np.array([cx + half, t_y])
        g_width = np.linalg.norm(kp["waist_right"] - kp["waist_left"])
        s = 2 * half / max(g_width, 1.0)
        g_waist_y = (kp["waist_left"][1] + kp["waist_right"][1]) / 2
        hem_g_y = (kp["hem_left"][1] + kp["hem_right"][1]) / 2
        hem_t_y = t_y + (hem_g_y - g_waist_y) * s
        ankle_y = _px(anchors, "ankle_left", size)[1]
        hem_t_y = min(hem_t_y, ankle_y + 0.01 * height)
        g_hem_half = (kp["hem_right"][0] - kp["hem_left"][0]) / 2 * s
        legs_half = profile.half_width(hem_t_y) * 1.06
        hem_half = 0.6 * g_hem_half + 0.4 * max(legs_half, g_hem_half * 0.7)
        src = [kp["waist_left"], kp["waist_right"], kp["hem_left"], kp["hem_right"]]
        dst = [t_wl, t_wr, [cx - hem_half, hem_t_y], [cx + hem_half, hem_t_y]]
        return np.array(src), np.array(dst)

    lt, rb = kp["box_left_top"], kp["box_right_bottom"]
    g_w, g_h = rb[0] - lt[0], rb[1] - lt[1]
    if zone == "feet":
        fl = _px(anchors, "feet_left", size)
        al, ar = _px(anchors, "ankle_left", size), _px(anchors, "ankle_right", size)
        t_w = (ar[0] - al[0]) * 1.3
        s = t_w / max(g_w, 1.0)
        t_bottom = fl[1] + 0.006 * height
        t_left = cx - t_w / 2
        dst_lt = [t_left, t_bottom - g_h * s]
        dst_rb = [t_left + t_w, t_bottom]
    elif zone == "head":
        hl, hr = _px(anchors, "head_left", size), _px(anchors, "head_right", size)
        head_top = _px(anchors, "head_top", size)[1]
        t_w = (hr[0] - hl[0]) * 1.35
        s = t_w / max(g_w, 1.0)
        t_bottom = head_top + (hl[1] - head_top) * 1.1
        dst_lt = [cx - t_w / 2, t_bottom - g_h * s]
        dst_rb = [cx + t_w / 2, t_bottom]
    else:
        return None
    src = [lt, [rb[0], lt[1]], [lt[0], rb[1]], rb]
    dst = [dst_lt, [dst_rb[0], dst_lt[1]], [dst_lt[0], dst_rb[1]], dst_rb]
    return np.array(src), np.array(dst)


def _coverage_score(layer_alpha: np.ndarray, zone: str, anchors: dict, profile: BodyProfile, size) -> float:
    """Share of the body band that the garment should cover and actually does."""
    if zone in ("head", "feet"):
        return 1.0 if layer_alpha.any() else 0.0
    height = size[1]
    if zone == "upper":
        y0, y1 = anchors["shoulder_left"][1] * height, anchors["waist_left"][1] * height
    elif zone == "lower":
        y0, y1 = anchors["hips_left"][1] * height, anchors["crotch"][1] * height + 0.03 * height
    else:
        y0, y1 = anchors["shoulder_left"][1] * height, anchors["hips_left"][1] * height
    covered = total = 0
    for y in range(int(y0) + 2, int(y1)):
        if np.isnan(profile.left[y]):
            continue
        left, right = int(profile.left[y]), int(profile.right[y])
        # Inner 80% of the body width: edges are allowed to show.
        inset = int((right - left) * 0.1)
        segment = layer_alpha[y, left + inset : right - inset]
        covered += int((segment > 0.5).sum())
        total += segment.size
    return covered / total if total else 0.0


def _symmetry_score(keypoints: dict) -> float:
    pairs = [("shoulder_left", "shoulder_right"), ("hem_left", "hem_right"), ("waist_left", "waist_right")]
    scores = []
    for a, b in pairs:
        if a in keypoints and b in keypoints:
            dy = abs(keypoints[a][1] - keypoints[b][1])
            dx = max(abs(keypoints[b][0] - keypoints[a][0]), 1.0)
            scores.append(max(0.0, 1.0 - dy / dx * 4))
    return float(np.mean(scores)) if scores else 1.0


def fit_garment(cutout: Image.Image, keypoints: dict, zone: str, anchors: dict, profile: BodyProfile, size):
    """Warp the cutout onto the canvas. Returns None for zones that are placed freely (accessories)."""
    pairs = fit_targets(keypoints, zone, anchors, profile, size)
    if pairs is None:
        return None
    src, dst = pairs
    layer = warp_rgba(cutout, src, dst, size)
    alpha = np.asarray(layer.split()[-1], dtype=np.float32) / 255.0
    if alpha.max() < 0.5:
        raise PipelineError("fit_failed", "Не удалось посадить вещь — поправьте опорные точки")

    coverage = _coverage_score(alpha, zone, anchors, profile, size)
    distortion = local_distortion(src, dst, (0, 0, cutout.width, cutout.height))
    symmetry = _symmetry_score(keypoints)
    score = 0.5 * min(1.0, coverage / 0.85) + 0.3 * float(np.exp(-2.0 * distortion)) + 0.2 * symmetry
    return FitResult(
        layer=layer,
        score=round(float(np.clip(score, 0, 1)), 3),
        details={
            "coverage": round(coverage, 3),
            "distortion": round(distortion, 3),
            "symmetry": round(symmetry, 3),
            "control_points": {"src": src.round(1).tolist(), "dst": dst.round(1).tolist()},
        },
    )


def preview(canvas: Image.Image, layer: Image.Image, background=(0xEF, 0xE9, 0xDF)) -> Image.Image:
    out = Image.new("RGBA", canvas.size, background + (255,))
    out.alpha_composite(canvas)
    out.alpha_composite(layer)
    return out.convert("RGB")
