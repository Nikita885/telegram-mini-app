"""Thin-plate spline warping of an RGBA garment onto the mannequin canvas."""

import numpy as np
from PIL import Image
from scipy import ndimage


def _kernel(r2: np.ndarray) -> np.ndarray:
    with np.errstate(divide="ignore", invalid="ignore"):
        out = r2 * np.log(r2)
    out[~np.isfinite(out)] = 0.0
    return out


class ThinPlateSpline:
    """2D → 2D TPS fitted on control points; `regularization` trades exactness for smoothness."""

    def __init__(self, src: np.ndarray, dst: np.ndarray, regularization: float = 0.0):
        src = np.asarray(src, dtype=np.float64)
        dst = np.asarray(dst, dtype=np.float64)
        n = len(src)
        # Normalize for numerical stability.
        self._mean = src.mean(axis=0)
        self._scale = max(float(np.abs(src - self._mean).max()), 1.0)
        s = (src - self._mean) / self._scale
        d2 = ((s[:, None, :] - s[None, :, :]) ** 2).sum(-1)
        k = _kernel(d2) + regularization * np.eye(n)
        p = np.hstack([np.ones((n, 1)), s])
        a = np.zeros((n + 3, n + 3))
        a[:n, :n] = k
        a[:n, n:] = p
        a[n:, :n] = p.T
        b = np.zeros((n + 3, 2))
        b[:n] = dst
        self._coef = np.linalg.lstsq(a, b, rcond=None)[0]
        self._ctrl = s

    def __call__(self, points: np.ndarray) -> np.ndarray:
        pts = (np.asarray(points, dtype=np.float64) - self._mean) / self._scale
        d2 = ((pts[:, None, :] - self._ctrl[None, :, :]) ** 2).sum(-1)
        n = len(self._ctrl)
        return _kernel(d2) @ self._coef[:n] + self._coef[n] + pts @ self._coef[n + 1 :]


def warp_rgba(
    garment: Image.Image,
    src_points,
    dst_points,
    canvas_size,
    regularization: float = 0.02,
):
    """Warp `garment` so that `src_points` land on `dst_points` of a transparent canvas.

    Uses the inverse mapping (canvas → garment) so every canvas pixel is sampled exactly once,
    with premultiplied alpha to avoid dark fringes along the cut edge.
    """
    width, height = canvas_size
    inverse = ThinPlateSpline(dst_points, src_points, regularization)
    forward = ThinPlateSpline(src_points, dst_points, regularization)

    gw, gh = garment.size
    corners = np.array([[0, 0], [gw, 0], [0, gh], [gw, gh], [gw / 2, 0], [gw / 2, gh], [0, gh / 2], [gw, gh / 2]])
    mapped = forward(corners)
    pad = 24
    x0 = int(max(0, np.floor(mapped[:, 0].min()) - pad))
    x1 = int(min(width, np.ceil(mapped[:, 0].max()) + pad))
    y0 = int(max(0, np.floor(mapped[:, 1].min()) - pad))
    y1 = int(min(height, np.ceil(mapped[:, 1].max()) + pad))

    out = np.zeros((height, width, 4), dtype=np.float32)
    if x1 <= x0 or y1 <= y0:
        return Image.fromarray(out.astype(np.uint8), "RGBA")

    yy, xx = np.mgrid[y0:y1, x0:x1]
    grid = np.stack([xx.ravel(), yy.ravel()], axis=1).astype(np.float64)
    src = inverse(grid)
    coords = [src[:, 1].reshape(yy.shape), src[:, 0].reshape(yy.shape)]

    rgba = np.asarray(garment.convert("RGBA"), dtype=np.float32) / 255.0
    alpha = rgba[..., 3]
    premultiplied = rgba[..., :3] * alpha[..., None]

    sampled_alpha = ndimage.map_coordinates(alpha, coords, order=1, mode="constant", cval=0.0)
    region = np.zeros((y1 - y0, x1 - x0, 4), dtype=np.float32)
    for channel in range(3):
        region[..., channel] = ndimage.map_coordinates(
            premultiplied[..., channel], coords, order=1, mode="constant", cval=0.0
        )
    safe = np.where(sampled_alpha > 1e-4, sampled_alpha, 1.0)
    region[..., :3] = region[..., :3] / safe[..., None]
    region[..., 3] = sampled_alpha
    out[y0:y1, x0:x1] = region
    return Image.fromarray(np.clip(out * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def local_distortion(src_points, dst_points, sample_box, samples: int = 12) -> float:
    """0 = pure similarity transform, grows as the warp stretches unevenly (anisotropy + shear)."""
    tps = ThinPlateSpline(src_points, dst_points, 0.02)
    x0, y0, x1, y1 = sample_box
    xs = np.linspace(x0, x1, samples)
    ys = np.linspace(y0, y1, samples)
    gx, gy = np.meshgrid(xs, ys)
    pts = np.stack([gx.ravel(), gy.ravel()], axis=1)
    eps = max(x1 - x0, y1 - y0) / 200.0
    fx = (tps(pts + [eps, 0]) - tps(pts - [eps, 0])) / (2 * eps)
    fy = (tps(pts + [0, eps]) - tps(pts - [0, eps])) / (2 * eps)
    sx = np.linalg.norm(fx, axis=1)
    sy = np.linalg.norm(fy, axis=1)
    cos = np.abs((fx * fy).sum(axis=1)) / np.maximum(sx * sy, 1e-9)
    anisotropy = np.abs(np.log(np.maximum(sx, 1e-6) / np.maximum(sy, 1e-6)))
    scale = np.sqrt(sx * sy)
    scale_var = np.std(np.log(np.maximum(scale, 1e-6)))
    return float(np.mean(anisotropy) + np.mean(cos) + scale_var)
