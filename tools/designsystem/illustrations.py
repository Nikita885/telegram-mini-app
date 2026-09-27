#!/usr/bin/env python3
"""Иллюстрации пустых состояний и ошибок — единый источник для Android и макетов.

Стиль: тонкая журнальная линия (2 px на холсте 160×160), «бумажная» заливка
surfaceSunken и одна деталь фирменного акцента. Цвета заданы ролями, которые
раскладываются в атрибуты темы Android и CSS-переменные, поэтому иллюстрации
сами перекрашиваются в тёмной теме.

Выход:
* android/core/designsystem/src/main/res/drawable/ds_illustration_<имя>.xml
* docs/design/illustrations/<имя>.svg (превью в светлой теме)
* docs/design/mockups/assets/illustrations.js

Запуск: python3 tools/designsystem/illustrations.py [--check]
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "android/core/designsystem/src/main/res/drawable"
SVG_DIR = ROOT / "docs/design/illustrations"
JS_OUT = ROOT / "docs/design/mockups/assets/illustrations.js"

SIZE = 160
STROKE = 2.0

# роль → (атрибут темы Android, CSS-переменная, HEX светлой темы для превью SVG)
ROLES = {
    "ink": ("?attr/dsColorOnSurface", "var(--ds-color-on-surface)", "#1C1B19"),
    "muted": ("?attr/dsColorOnSurfaceVariant", "var(--ds-color-on-surface-variant)", "#6B6358"),
    "paper": ("?attr/dsColorSurfaceSunken", "var(--ds-color-surface-sunken)", "#EFE9DF"),
    "surface": ("?attr/dsColorSurface", "var(--ds-color-surface)", "#FCFAF6"),
    "background": ("?attr/dsColorBackground", "var(--ds-color-background)", "#F6F2EB"),
    "accent": ("?attr/dsColorAccent", "var(--ds-color-accent)", "#2F3BD6"),
}


@dataclass(frozen=True)
class Shape:
    d: str
    fill: str | None = None
    stroke: str | None = "ink"
    width: float = STROKE


def n(v: float) -> str:
    return f"{v:.2f}".rstrip("0").rstrip(".")


def circle(cx: float, cy: float, r: float, **kw) -> Shape:
    d = f"M{n(cx - r)},{n(cy)} A{n(r)},{n(r)} 0 1,0 {n(cx + r)},{n(cy)} A{n(r)},{n(r)} 0 1,0 {n(cx - r)},{n(cy)} Z"
    return Shape(d, **kw)


def rrect(x: float, y: float, w: float, h: float, r: float, **kw) -> Shape:
    d = (
        f"M{n(x + r)},{n(y)} H{n(x + w - r)} A{n(r)},{n(r)} 0 0,1 {n(x + w)},{n(y + r)} "
        f"V{n(y + h - r)} A{n(r)},{n(r)} 0 0,1 {n(x + w - r)},{n(y + h)} "
        f"H{n(x + r)} A{n(r)},{n(r)} 0 0,1 {n(x)},{n(y + h - r)} "
        f"V{n(y + r)} A{n(r)},{n(r)} 0 0,1 {n(x + r)},{n(y)} Z"
    )
    return Shape(d, **kw)


def polygon(points: list[tuple[float, float]], closed: bool = True, **kw) -> Shape:
    head, *rest = points
    d = f"M{n(head[0])},{n(head[1])} " + " ".join(f"L{n(x)},{n(y)}" for x, y in rest)
    return Shape(d + (" Z" if closed else ""), **kw)


def rotate(points: list[tuple[float, float]], deg: float, cx: float, cy: float) -> list[tuple[float, float]]:
    a = math.radians(deg)
    return [
        (cx + (x - cx) * math.cos(a) - (y - cy) * math.sin(a), cy + (x - cx) * math.sin(a) + (y - cy) * math.cos(a))
        for x, y in points
    ]


def sparkle(cx: float, cy: float, r: float, role: str = "accent") -> Shape:
    k = r * 0.28
    pts = [
        (cx, cy - r),
        (cx + k, cy - k),
        (cx + r, cy),
        (cx + k, cy + k),
        (cx, cy + r),
        (cx - k, cy + k),
        (cx - r, cy),
        (cx - k, cy - k),
    ]
    return polygon(pts, fill=role, stroke=None)


def hanger(
    cx: float, top: float, half: float, fill: str | None, stroke: str = "ink", width: float = STROKE, hook: float = 4
) -> list[Shape]:
    """Вешалка: крючок сидит на перекладине y=top, плечики — треугольник."""
    hy = top + hook
    ex = cx + hook * math.cos(math.radians(45))
    ey = hy + hook * math.sin(math.radians(45))
    neck = hy + hook * 2
    shoulders = neck + half * 0.85
    return [
        Shape(
            f"M{n(cx - hook)},{n(hy)} A{n(hook)},{n(hook)} 0 1,1 {n(ex)},{n(ey)} "
            f"Q{n(cx)},{n(ey + 2)} {n(cx)},{n(neck)}",
            stroke=stroke,
            width=width,
        ),
        polygon([(cx, neck), (cx - half, shoulders), (cx + half, shoulders)], fill=fill, stroke=stroke, width=width),
    ]


def plus_badge(cx: float, cy: float, r: float = 9) -> list[Shape]:
    return [
        circle(cx, cy, r, fill="accent", stroke=None),
        Shape(
            f"M{n(cx)},{n(cy - r / 2)} V{n(cy + r / 2)} M{n(cx - r / 2)},{n(cy)} H{n(cx + r / 2)}",
            stroke="surface",
            width=2,
        ),
    ]


def bubble(x: float, y: float, w: float, h: float, r: float, tail: str) -> str:
    """Облачко реплики с хвостиком снизу слева ('left') или справа ('right')."""
    b = y + h
    if tail == "left":
        return (
            f"M{n(x + r)},{n(y)} H{n(x + w - r)} A{n(r)},{n(r)} 0 0,1 {n(x + w)},{n(y + r)} V{n(b - r)} "
            f"A{n(r)},{n(r)} 0 0,1 {n(x + w - r)},{n(b)} H{n(x + r + 12)} L{n(x + 8)},{n(b + 12)} L{n(x + r)},{n(b)} "
            f"A{n(r)},{n(r)} 0 0,1 {n(x)},{n(b - r)} V{n(y + r)} A{n(r)},{n(r)} 0 0,1 {n(x + r)},{n(y)} Z"
        )
    return (
        f"M{n(x + r)},{n(y)} H{n(x + w - r)} A{n(r)},{n(r)} 0 0,1 {n(x + w)},{n(y + r)} V{n(b - r)} "
        f"A{n(r)},{n(r)} 0 0,1 {n(x + w - r)},{n(b)} L{n(x + w - 8)},{n(b + 12)} L{n(x + w - r - 12)},{n(b)} "
        f"H{n(x + r)} A{n(r)},{n(r)} 0 0,1 {n(x)},{n(b - r)} V{n(y + r)} A{n(r)},{n(r)} 0 0,1 {n(x + r)},{n(y)} Z"
    )


# ---------------------------------------------------------------------------
# Композиции


def empty_feed() -> list[Shape]:
    shapes = [
        Shape("M24,40 H136 M32,40 V128 M128,40 V128 M24,128 H40 M120,128 H136"),
        Shape("M16,136 H144", stroke="muted"),
    ]
    shapes += hanger(54, 40, 13, fill="paper")
    shapes += hanger(80, 40, 13, fill="paper", stroke="accent")
    shapes += hanger(106, 40, 13, fill="paper")
    shapes.append(sparkle(108, 96, 7))
    shapes.append(sparkle(52, 104, 4, role="muted"))
    return shapes


def empty_search() -> list[Shape]:
    shapes = [
        Shape("M96,94 L124,122", stroke="ink", width=7),
        circle(70, 68, 34, fill="paper"),
        Shape("M50,58 A22,22 0 0,1 62,46", stroke="muted"),
    ]
    shapes += hanger(72, 56, 13, fill="surface", stroke="accent", hook=3.5)
    shapes.append(sparkle(126, 40, 6))
    return shapes


def empty_collections() -> list[Shape]:
    back = rotate([(44, 36), (104, 36), (104, 116), (44, 116)], -8, 74, 76)
    return [
        polygon(back, fill="paper"),
        rrect(58, 44, 60, 80, 2, fill="surface"),
        *hanger(88, 66, 14, fill="paper", stroke="muted"),
        polygon([(100, 44), (110, 44), (110, 66), (105, 61), (100, 66)], fill="accent", stroke=None),
        Shape("M70,110 H100", stroke="muted"),
    ]


def empty_messages() -> list[Shape]:
    return [
        Shape(bubble(26, 36, 76, 50, 14, "left"), fill="paper"),
        Shape(bubble(58, 64, 76, 46, 14, "right"), fill="surface"),
        circle(82, 87, 3, fill="accent", stroke=None),
        circle(96, 87, 3, fill="accent", stroke=None),
        circle(110, 87, 3, fill="accent", stroke=None),
    ]


def empty_notifications() -> list[Shape]:
    return [
        Shape("M80,22 V30"),
        Shape("M80,30 C60,30 50,46 50,64 V86 L40,100 H120 L110,86 V64 C110,46 100,30 80,30 Z", fill="paper"),
        circle(80, 110, 7, fill="accent", stroke=None),
        Shape("M28,128 H132", stroke="muted"),
        sparkle(128, 40, 5),
    ]


def empty_comments() -> list[Shape]:
    shapes = [Shape(bubble(30, 34, 100, 72, 18, "left"), fill="paper")]
    for cx in (68, 90):
        shapes.append(circle(cx, 64, 5, fill="accent", stroke=None))
        shapes.append(Shape(f"M{cx + 5},64 C{cx + 5},72 {cx + 1},77 {cx - 5},79", stroke="accent", width=2.5))
    return shapes


def empty_outfits() -> list[Shape]:
    return [
        Shape("M80,120 V138 M64,140 H96"),
        rrect(74, 24, 12, 10, 2, fill="surface"),
        Shape(
            "M68,34 H92 C92,40 96,44 104,46 C114,50 116,62 112,74 C108,86 106,94 108,104 "
            "C110,112 106,118 98,120 H62 C54,118 50,112 52,104 C54,94 52,86 48,74 "
            "C44,62 46,50 56,46 C64,44 68,40 68,34 Z",
            fill="paper",
        ),
        Shape("M62,58 C70,64 90,64 98,58", stroke="muted"),
        *plus_badge(118, 40),
    ]


def error() -> list[Shape]:
    shapes = [
        rrect(40, 40, 40, 8, 2, fill="surface"),
        Shape("M46,48 H74 V88 H46 Z", fill="paper"),
    ]
    for y in (55, 62, 69, 76, 83):
        shapes.append(Shape(f"M48,{y} H72", stroke="muted", width=1.5))
    shapes += [
        rrect(40, 88, 40, 8, 2, fill="surface"),
        Shape(
            "M74,60 C96,56 104,72 96,82 C88,92 104,100 114,92 C124,84 116,72 108,80 "
            "C100,88 112,108 124,112 C132,114 136,120 132,128",
            stroke="accent",
        ),
    ]
    return shapes


def offline() -> list[Shape]:
    return [
        Shape(
            "M50,104 H112 C124,104 132,95 132,84 C132,72 122,64 110,66 C106,52 94,44 80,44 "
            "C64,44 52,56 50,70 C38,72 30,80 30,90 C30,98 38,104 50,104 Z",
            fill="paper",
        ),
        Shape("M40,40 L124,124", stroke="background", width=8),
        Shape("M40,40 L124,124", stroke="accent", width=2.5),
    ]


def locked() -> list[Shape]:
    return [
        Shape("M62,70 V56 C62,44 70,36 80,36 C90,36 98,44 98,56 V70", width=2.5),
        rrect(50, 70, 60, 50, 8, fill="paper"),
        circle(80, 90, 5, fill="accent", stroke=None),
        Shape("M80,94 V104", stroke="accent", width=3),
    ]


def camera() -> list[Shape]:
    return [
        polygon([(58, 54), (64, 42), (96, 42), (102, 54)], fill="paper"),
        rrect(32, 52, 96, 64, 10, fill="paper"),
        circle(80, 84, 20, fill="surface"),
        circle(80, 84, 10, fill=None, stroke="accent"),
        circle(114, 64, 3, fill="ink", stroke=None),
        sparkle(132, 34, 7),
    ]


def people() -> list[Shape]:
    return [
        circle(64, 56, 14, fill="paper"),
        Shape("M36,112 C36,90 48,80 64,80 C80,80 92,90 92,112 Z", fill="paper"),
        circle(98, 66, 14, fill="surface"),
        Shape("M70,122 C70,100 82,90 98,90 C114,90 126,100 126,122 Z", fill="surface"),
        *plus_badge(124, 44),
    ]


ILLUSTRATIONS = {
    "empty_feed": (empty_feed, "Пустая лента: вешалки на рейле"),
    "empty_search": (empty_search, "Ничего не найдено"),
    "empty_collections": (empty_collections, "Нет коллекций / сохранённого"),
    "empty_messages": (empty_messages, "Нет диалогов"),
    "empty_notifications": (empty_notifications, "Нет уведомлений"),
    "empty_comments": (empty_comments, "Нет комментариев"),
    "empty_outfits": (empty_outfits, "Нет образов / черновиков: манекен"),
    "error": (error, "Ошибка загрузки: распущенная нить"),
    "offline": (offline, "Нет сети"),
    "locked": (locked, "Нет прав / приватный профиль"),
    "camera": (camera, "Студия: нет доступа к камере, пустая очередь"),
    "people": (people, "Нет подписчиков / подписок"),
}


def to_vector(name: str, desc: str, shapes: list[Shape]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {desc}. СГЕНЕРИРОВАНО tools/designsystem/illustrations.py -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="@dimen/ds_size_illustration"',
        '    android:height="@dimen/ds_size_illustration"',
        f'    android:viewportWidth="{SIZE}"',
        f'    android:viewportHeight="{SIZE}">',
    ]
    for s in shapes:
        lines.append("    <path")
        if s.fill:
            lines.append(f'        android:fillColor="{ROLES[s.fill][0]}"')
        lines.append(f'        android:pathData="{s.d}"')
        if s.stroke:
            lines.append(f'        android:strokeColor="{ROLES[s.stroke][0]}"')
            lines.append('        android:strokeLineCap="round"')
            lines.append('        android:strokeLineJoin="round"')
            lines.append(f'        android:strokeWidth="{n(s.width)}"')
        lines[-1] += " />"
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def to_svg(shapes: list[Shape], mode: str) -> str:
    """mode='hex' — самостоятельный файл, 'css' — для вставки в макеты."""
    idx = 2 if mode == "hex" else 1
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE} {SIZE}" width="{SIZE}" height="{SIZE}" '
        'fill="none" stroke-linecap="round" stroke-linejoin="round">'
    ]
    for s in shapes:
        fill = ROLES[s.fill][idx] if s.fill else "none"
        attrs = f'd="{s.d}" fill="{fill}"'
        if s.stroke:
            attrs += f' stroke="{ROLES[s.stroke][idx]}" stroke-width="{n(s.width)}"'
        parts.append(f"<path {attrs}/>")
    parts.append("</svg>")
    return "".join(parts)


def build() -> dict[Path, str]:
    out: dict[Path, str] = {}
    web = {}
    for name, (fn, desc) in ILLUSTRATIONS.items():
        shapes = fn()
        out[DRAWABLE / f"ds_illustration_{name}.xml"] = to_vector(name, desc, shapes)
        out[SVG_DIR / f"{name}.svg"] = to_svg(shapes, "hex") + "\n"
        web[name] = to_svg(shapes, "css")
    out[JS_OUT] = (
        "/* СГЕНЕРИРОВАНО tools/designsystem/illustrations.py */\n"
        "window.DS_ILLUSTRATIONS = " + json.dumps(web, ensure_ascii=False, indent=0) + ";\n"
    )
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    stale = []
    for path, content in build().items():
        if not path.exists() or path.read_text(encoding="utf-8") != content:
            stale.append(path)
            if not args.check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
    if args.check and stale:
        for p in stale:
            print(f"устарел: {p.relative_to(ROOT)}", file=sys.stderr)
        return 1
    print(f"Иллюстраций: {len(ILLUSTRATIONS)}; обновлено файлов: {len(stale)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
