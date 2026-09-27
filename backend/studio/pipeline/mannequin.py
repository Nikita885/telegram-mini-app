"""Mannequin canvas: a fixed-size transparent image with the mannequin centred on it.

Every garment layer is rendered in these coordinates, so a fitted layer drawn at (0, 0) with
scale 1 sits exactly on the body — on the phone, on a tablet and in the server-side render.
"""

from dataclasses import dataclass

import numpy as np
from PIL import Image, ImageFilter

MANNEQUIN_TONE = (0xD8, 0xD2, 0xC9)  # color.semantic.mannequin.light (stone.300)
CANVAS_TONE = (0xEF, 0xE9, 0xDF)  # color.semantic.canvas.light (milk.200)

TOP_MARGIN = 0.035
BODY_HEIGHT = 0.93


def silhouette_mask(image: Image.Image) -> np.ndarray:
    """Float mask 0..1 of the mannequin silhouette from an RGBA/LA or opaque dark-on-light image."""
    if image.mode in ("LA", "RGBA", "PA", "P"):
        alpha = np.asarray(image.convert("RGBA").split()[-1], dtype=np.float32) / 255.0
        if alpha.min() < 0.5:
            return alpha
    gray = np.asarray(image.convert("L"), dtype=np.float32) / 255.0
    return 1.0 - gray


def build_canvas(source: Image.Image, size=(750, 1000)) -> Image.Image:
    """Crop the silhouette, scale it to BODY_HEIGHT of the canvas and tint it with the mannequin tone."""
    width, height = size
    mask = silhouette_mask(source)
    ys, xs = np.nonzero(mask > 0.5)
    crop = mask[ys.min() : ys.max() + 1, xs.min() : xs.max() + 1]

    target_h = int(round(height * BODY_HEIGHT))
    target_w = max(1, int(round(crop.shape[1] * target_h / crop.shape[0])))
    alpha = Image.fromarray((crop * 255).astype(np.uint8), "L").resize((target_w, target_h), Image.BICUBIC)
    # Upscaled low-res silhouettes get soft, blobby edges: re-sharpen to a crisp anti-aliased contour.
    alpha = alpha.filter(ImageFilter.GaussianBlur(radius=max(1.0, target_h / 900)))
    a = np.asarray(alpha, dtype=np.float32) / 255.0
    a = np.clip((a - 0.5) * 6 + 0.5, 0, 1)

    canvas_alpha = np.zeros((height, width), dtype=np.float32)
    top = int(round(height * TOP_MARGIN))
    left = (width - target_w) // 2
    canvas_alpha[top : top + target_h, left : left + target_w] = a[: height - top, : width - left]

    rgba = np.zeros((height, width, 4), dtype=np.uint8)
    rgba[..., :3] = MANNEQUIN_TONE
    rgba[..., 3] = (canvas_alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


@dataclass
class BodyProfile:
    """Row-by-row outline of the body (arms excluded) on the canvas, in pixels."""

    width: int
    height: int
    left: np.ndarray  # per row, NaN where there is no body
    right: np.ndarray
    center_x: float
    top: int
    bottom: int

    def half_width(self, y: float) -> float:
        row = int(np.clip(round(y), self.top, self.bottom))
        if np.isnan(self.left[row]):
            return 0.0
        return float(self.right[row] - self.left[row]) / 2.0


def _runs(row: np.ndarray):
    """Start/end (inclusive) indices of consecutive True runs in a boolean row."""
    padded = np.concatenate([[False], row, [False]])
    diff = np.diff(padded.astype(np.int8))
    starts = np.nonzero(diff == 1)[0]
    ends = np.nonzero(diff == -1)[0] - 1
    return list(zip(starts, ends))


def compute_anchors(canvas: Image.Image):
    """Anchor points (normalized 0..1) and the arm-free body profile of a mannequin canvas."""
    alpha = np.asarray(canvas.split()[-1], dtype=np.float32) / 255.0
    mask = alpha > 0.5
    height, width = mask.shape
    rows = np.nonzero(mask.any(axis=1))[0]
    top, bottom = int(rows.min()), int(rows.max())
    body_h = bottom - top
    cols = np.nonzero(mask.any(axis=0))[0]
    cx = float(cols.min() + cols.max()) / 2.0

    row_runs = [_runs(mask[y]) for y in range(height)]

    def span(y):
        runs = row_runs[y]
        if not runs:
            return None
        return float(runs[0][0]), float(runs[-1][1])

    def center_run(y):
        for start, end in row_runs[y]:
            if start <= cx <= end:
                return float(start), float(end)
        return None

    # Neck: the narrowest row between the head and the shoulders.
    neck_search = range(top + int(0.08 * body_h), top + int(0.2 * body_h))
    neck_y = min(neck_search, key=lambda y: (span(y)[1] - span(y)[0]) if span(y) else 1e9)
    head_l, head_r = span(top + int(0.06 * body_h))

    # Shoulders: first row below the neck reaching 92% of the widest upper-body row.
    upper = range(neck_y, top + int(0.3 * body_h))
    widest = max((span(y)[1] - span(y)[0]) for y in upper if span(y))
    shoulder_y = next(y for y in upper if span(y) and (span(y)[1] - span(y)[0]) >= 0.92 * widest)
    shoulder_l, shoulder_r = span(shoulder_y)

    # Armpit: first row where arms separate from the torso (3+ runs).
    armpit_y = next(
        (y for y in range(shoulder_y, top + int(0.45 * body_h)) if len(row_runs[y]) >= 3),
        shoulder_y + int(0.06 * body_h),
    )

    # Crotch: first row below the armpits where the centre line leaves the body.
    crotch_y = next(
        (y for y in range(armpit_y + int(0.1 * body_h), bottom) if center_run(y) is None),
        top + int(0.5 * body_h),
    )
    waist_range = range(armpit_y + int(0.04 * body_h), crotch_y - int(0.08 * body_h))
    waist_y = min(
        waist_range,
        key=lambda y: (center_run(y)[1] - center_run(y)[0]) if center_run(y) else 1e9,
    )
    hips_y = crotch_y - int(0.03 * body_h)

    # Arm-free profile: torso (centre run) above the crotch, outer edges of both legs below it.
    left = np.full(height, np.nan)
    right = np.full(height, np.nan)
    for y in range(top, bottom + 1):
        runs = row_runs[y]
        if not runs:
            continue
        if y < armpit_y:
            left[y], right[y] = runs[0][0], runs[-1][1]
        elif y < crotch_y:
            run = center_run(y)
            if run:
                left[y], right[y] = run
        else:
            # Legs: the two runs closest to the centre line on each side.
            left_runs = [r for r in runs if r[1] < cx]
            right_runs = [r for r in runs if r[0] > cx]
            if left_runs and right_runs:
                left[y] = max(left_runs, key=lambda r: r[1])[0]
                right[y] = min(right_runs, key=lambda r: r[0])[1]
    # Hands hang at crotch level: they are separate runs, so the leg pick above ignores them.
    profile = BodyProfile(width, height, left, right, cx, top, bottom)

    knee_y = crotch_y + int(0.48 * (bottom - crotch_y))
    ankle_y = bottom - int(0.045 * body_h)

    def point(x, y):
        return [round(float(x) / width, 4), round(float(y) / height, 4)]

    torso_l, torso_r = center_run(armpit_y + 2) or (shoulder_l, shoulder_r)
    waist_l, waist_r = center_run(waist_y)
    hips_l, hips_r = center_run(hips_y) or (waist_l, waist_r)
    feet_l, feet_r = span(bottom - 2)

    anchors = {
        "head_top": point(cx, top),
        "head_left": point(head_l, top + 0.06 * body_h),
        "head_right": point(head_r, top + 0.06 * body_h),
        "neck": point(cx, neck_y),
        "shoulder_left": point(shoulder_l, shoulder_y),
        "shoulder_right": point(shoulder_r, shoulder_y),
        "armpit_left": point(torso_l, armpit_y),
        "armpit_right": point(torso_r, armpit_y),
        "waist_left": point(waist_l, waist_y),
        "waist_right": point(waist_r, waist_y),
        "hips_left": point(hips_l, hips_y),
        "hips_right": point(hips_r, hips_y),
        "crotch": point(cx, crotch_y),
        "knee_left": point(left[knee_y] if not np.isnan(left[knee_y]) else cx, knee_y),
        "knee_right": point(right[knee_y] if not np.isnan(right[knee_y]) else cx, knee_y),
        "ankle_left": point(left[ankle_y] if not np.isnan(left[ankle_y]) else cx, ankle_y),
        "ankle_right": point(right[ankle_y] if not np.isnan(right[ankle_y]) else cx, ankle_y),
        "feet_left": point(feet_l, bottom),
        "feet_right": point(feet_r, bottom),
    }
    return anchors, profile


def body_profile(canvas: Image.Image) -> BodyProfile:
    return compute_anchors(canvas)[1]
