#!/usr/bin/env python3
"""Упаковка скриншотов после docs/design/mockups/render.mjs.

PNG из docs/design/screenshots/.raw → WebP (доски) и GIF (кадры анимаций) в
docs/design/screenshots/{screens,components,foundations,motion}. WebP в 2x весит в 6–7 раз
меньше PNG при неотличимом качестве; GitHub показывает WebP и GIF прямо в Markdown.

Зависимости: pip install pillow
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "docs/design/screenshots"
RAW = SHOTS / ".raw"
WEBP_QUALITY = 86
GIF_FPS = 30
GIF_HOLD_MS = 900  # последний кадр держится, чтобы движение читалось


def pack_boards() -> int:
    count = 0
    for folder in ("screens", "components", "foundations", "motion"):
        out_dir = SHOTS / folder
        if out_dir.exists():
            shutil.rmtree(out_dir)
        out_dir.mkdir(parents=True)
        for png in sorted((RAW / folder).glob("*.png")):
            image = Image.open(png).convert("RGB")
            image.save(out_dir / f"{png.stem.lstrip('_')}.webp", "WEBP", quality=WEBP_QUALITY, method=6)
            count += 1
    return count


def pack_gifs() -> int:
    frames_root = RAW / "frames"
    count = 0
    for scene in sorted(p for p in frames_root.iterdir() if p.is_dir()):
        frames = [Image.open(f).convert("RGB") for f in sorted(scene.glob("*.png"))]
        if not frames:
            continue
        palette_source = frames[len(frames) // 2].quantize(colors=255, method=Image.Quantize.MEDIANCUT)
        paletted = [f.quantize(palette=palette_source, dither=Image.Dither.NONE) for f in frames]
        durations = [round(1000 / GIF_FPS)] * len(paletted)
        durations[-1] = GIF_HOLD_MS
        paletted[0].save(
            SHOTS / "motion" / f"{scene.name}.gif",
            save_all=True,
            append_images=paletted[1:],
            duration=durations,
            loop=0,
            optimize=True,
            disposal=1,
        )
        count += 1
    return count


def main() -> int:
    if not RAW.exists():
        print("Нет docs/design/screenshots/.raw — сначала node docs/design/mockups/render.mjs", file=sys.stderr)
        return 1
    boards = pack_boards()
    gifs = pack_gifs()
    size = sum(p.stat().st_size for p in SHOTS.rglob("*") if p.is_file() and ".raw" not in p.parts)
    print(f"Досок: {boards}, GIF: {gifs}, итого {size / 1024 / 1024:.1f} МБ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
