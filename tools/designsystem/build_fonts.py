#!/usr/bin/env python3
"""Сборка шрифтов дизайн-системы из вариативных исходников Google Fonts.

Что делает:
1. Скачивает вариативные TTF (Playfair Display, Inter) из github.com/google/fonts
   и сверяет SHA-256 с зафиксированными ниже значениями.
2. Инстанцирует статические начертания (fontTools.varLib.instancer): Android
   выбирает вес надёжно только по отдельному файлу на начертание (minSdk 26).
3. Сабсетит до латиницы, кириллицы и типографских знаков — минус ~80 % веса.
4. Playfair Display распространяется с Reserved Font Name «Playfair Display».
   По OFL 1.1 изменённая версия (а инстанс и сабсет — это изменение) не может
   носить зарезервированное имя, поэтому производная называется
   «OutfitShare Display». Копирайт и лицензия в таблице name сохраняются.
5. Пишет TTF в res/font модуля :core:designsystem и WOFF2 для HTML-макетов.

Зависимости: pip install fonttools brotli
Запуск:      python3 tools/designsystem/build_fonts.py [--cache DIR]
"""

from __future__ import annotations

import argparse
import hashlib
import io
import sys
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[2]
ANDROID_FONT_DIR = ROOT / "android/core/designsystem/src/main/res/font"
WEB_FONT_DIR = ROOT / "docs/design/mockups/assets/fonts"
LICENSE_DIR = ROOT / "android/core/designsystem/licenses"

UPSTREAM = "https://raw.githubusercontent.com/google/fonts/main/ofl"

SOURCES = {
    "playfair": {
        "url": f"{UPSTREAM}/playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf",
        "sha256": "c40f2293766a503bc70cce9e512ef844a4ccb7cbcde792fe2ea31d191917d8d6",
    },
    "playfair_italic": {
        "url": f"{UPSTREAM}/playfairdisplay/PlayfairDisplay-Italic%5Bwght%5D.ttf",
        "sha256": "a5e26dc5e2e77fb2803a0bf02fd4f81ee136ec8dea863ccdb0c59a263b21378b",
    },
    "playfair_ofl": {
        "url": f"{UPSTREAM}/playfairdisplay/OFL.txt",
        "sha256": "566be814f8e96e93dfa16101331557eb6b5467e9e03f627c0910fe93ca12300e",
    },
    "inter": {
        "url": f"{UPSTREAM}/inter/Inter%5Bopsz,wght%5D.ttf",
        "sha256": "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031",
    },
    "inter_ofl": {
        "url": f"{UPSTREAM}/inter/OFL.txt",
        "sha256": "5b9321a4298cfeb6b34354164a1c3afc3db114569984c502b9b35d988fd58c57",
    },
}

# (имя ресурса, источник, координаты осей, имя семейства, имя начертания, rename)
INSTANCES = [
    ("ds_display_regular", "playfair", {"wght": 400}, "OutfitShare Display", "Regular", True),
    ("ds_display_medium", "playfair", {"wght": 500}, "OutfitShare Display", "Medium", True),
    ("ds_display_italic", "playfair_italic", {"wght": 400}, "OutfitShare Display", "Italic", True),
    ("ds_text_regular", "inter", {"wght": 400, "opsz": 14}, "Inter", "Regular", False),
    ("ds_text_medium", "inter", {"wght": 500, "opsz": 14}, "Inter", "Medium", False),
    ("ds_text_semibold", "inter", {"wght": 600, "opsz": 14}, "Inter", "SemiBold", False),
]

WEIGHT_CLASS = {"Regular": 400, "Italic": 400, "Medium": 500, "SemiBold": 600}

UNICODES = (
    list(range(0x0020, 0x007F))  # Basic Latin
    + list(range(0x00A0, 0x0100))  # Latin-1 Supplement: « » © ° × é …
    + list(range(0x0100, 0x0180))  # Latin Extended-A: имена пользователей
    + list(range(0x0400, 0x0500))  # Cyrillic
    + list(range(0x2010, 0x2028))  # тире, кавычки „“ ‘’, многоточие, буллет
    + list(range(0x2030, 0x203B))  # ‰ ′ ″ ‹ ›
    + [0x20BD, 0x20AC, 0x2116, 0x2122, 0x2190, 0x2192, 0x2212, 0x00D7, 0x2715]  # ₽ € № ™ ← → − × ✕
)

LAYOUT_FEATURES = ["*"]  # kern, liga, tnum, case, locl (болгарская/сербская кириллица) и др.

MODIFICATION_NOTE = (
    "Modified Version of {upstream} under SIL OFL 1.1: static instance ({axes}), "
    "subset to Latin + Cyrillic for the Outfit Share app."
)


def fetch(key: str, cache: Path) -> bytes:
    spec = SOURCES[key]
    cached = cache / f"{key}.bin"
    if cached.exists():
        data = cached.read_bytes()
    else:
        print(f"↓ {spec['url']}")
        with urllib.request.urlopen(spec["url"], timeout=60) as resp:  # noqa: S310 — фиксированный https-URL
            data = resp.read()
        cache.mkdir(parents=True, exist_ok=True)
        cached.write_bytes(data)
    digest = hashlib.sha256(data).hexdigest()
    if digest != spec["sha256"]:
        raise SystemExit(f"SHA-256 не совпал для {key}: {digest}. Обновите хеш осознанно, проверив лицензию.")
    return data


def rename(font: TTFont, family: str, style: str, upstream: str, axes: dict) -> None:
    name = font["name"]
    full = f"{family} {style}" if style != "Regular" else f"{family} Regular"
    ps = f"{family.replace(' ', '')}-{style}"
    typographic = style in ("Medium", "SemiBold")
    legacy_family = f"{family} {style}" if typographic else family
    legacy_style = "Regular" if typographic else style
    # Убираем все старые имена, где могло остаться зарезервированное имя.
    for record_id in (1, 2, 3, 4, 6, 16, 17, 21, 22, 25):
        name.removeNames(nameID=record_id)
    name.setName(legacy_family, 1, 3, 1, 0x409)
    name.setName(legacy_style, 2, 3, 1, 0x409)
    name.setName(f"{ps};OutfitShare", 3, 3, 1, 0x409)
    name.setName(full, 4, 3, 1, 0x409)
    name.setName(ps, 6, 3, 1, 0x409)
    if typographic:
        name.setName(family, 16, 3, 1, 0x409)
        name.setName(style, 17, 3, 1, 0x409)
    axes_text = ", ".join(f"{k}={v}" for k, v in axes.items())
    name.setName(MODIFICATION_NOTE.format(upstream=upstream, axes=axes_text), 10, 3, 1, 0x409)
    font["OS/2"].usWeightClass = WEIGHT_CLASS[style]
    if "STAT" in font:
        del font["STAT"]


def build_instance(source: bytes, axes: dict, family: str, style: str, do_rename: bool) -> TTFont:
    font = TTFont(io.BytesIO(source))
    upstream = font["name"].getDebugName(1)
    static = instancer.instantiateVariableFont(font, axes, updateFontNames=False)
    if do_rename:
        rename(static, family, style, upstream, axes)
    else:
        # Inter без RFN: имя сохраняем, но фиксируем начертание и пометку об изменении.
        axes_text = ", ".join(f"{k}={v}" for k, v in axes.items())
        static["name"].setName(MODIFICATION_NOTE.format(upstream=upstream, axes=axes_text), 10, 3, 1, 0x409)
        static["name"].setName(style if style in ("Regular", "Italic") else "Regular", 2, 3, 1, 0x409)
        if style in ("Medium", "SemiBold"):
            static["name"].setName(f"{family} {style}", 1, 3, 1, 0x409)
            static["name"].setName(family, 16, 3, 1, 0x409)
            static["name"].setName(style, 17, 3, 1, 0x409)
        static["name"].setName(f"{family} {style}", 4, 3, 1, 0x409)
        static["name"].setName(f"{family.replace(' ', '')}-{style}", 6, 3, 1, 0x409)
        static["OS/2"].usWeightClass = WEIGHT_CLASS[style]
        if "STAT" in static:
            del static["STAT"]

    options = subset.Options()
    options.layout_features = LAYOUT_FEATURES
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.glyph_names = False
    options.hinting = False  # TrueType-хинтинг не нужен на Android с высоким DPI
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=UNICODES)
    subsetter.subset(static)
    return static


def check_coverage(font: TTFont, label: str) -> None:
    cmap = font.getBestCmap()
    alphabet = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя«»—–…№"
    missing = [c for c in alphabet if ord(c) not in cmap]
    if missing:
        raise SystemExit(f"{label}: нет глифов {''.join(missing)}")


FAMILY_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- СГЕНЕРИРОВАНО tools/designsystem/build_fonts.py. {comment} -->
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
{fonts}
</font-family>
"""

FONT_ENTRY = """    <font
        android:font="@font/{res}"
        android:fontStyle="{style}"
        android:fontWeight="{weight}"
        app:font="@font/{res}"
        app:fontStyle="{style}"
        app:fontWeight="{weight}" />"""


def write_family_xml() -> None:
    groups = {
        "ds_display": ("Семейство OutfitShare Display (производная Playfair Display, OFL).", "ds_display_"),
        "ds_text": ("Семейство Inter (OFL).", "ds_text_"),
    }
    for family_res, (comment, prefix) in groups.items():
        entries = []
        for res, _src, _axes, _fam, style, _rn in INSTANCES:
            if not res.startswith(prefix):
                continue
            entries.append(
                FONT_ENTRY.format(
                    res=res,
                    style="italic" if style == "Italic" else "normal",
                    weight=WEIGHT_CLASS[style],
                )
            )
        (ANDROID_FONT_DIR / f"{family_res}.xml").write_text(
            FAMILY_XML.format(comment=comment, fonts="\n".join(entries)), encoding="utf-8"
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cache", type=Path, default=ROOT / ".cache/fonts", help="кэш скачанных исходников")
    args = parser.parse_args(argv)

    ANDROID_FONT_DIR.mkdir(parents=True, exist_ok=True)
    WEB_FONT_DIR.mkdir(parents=True, exist_ok=True)
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)

    sources = {key: fetch(key, args.cache) for key in SOURCES}
    (LICENSE_DIR / "OFL-PlayfairDisplay.txt").write_bytes(sources["playfair_ofl"])
    (LICENSE_DIR / "OFL-Inter.txt").write_bytes(sources["inter_ofl"])

    total = 0
    for res, src, axes, family, style, do_rename in INSTANCES:
        font = build_instance(sources[src], axes, family, style, do_rename)
        check_coverage(font, res)
        ttf = ANDROID_FONT_DIR / f"{res}.ttf"
        font.flavor = None
        font.save(ttf)
        font.flavor = "woff2"
        font.save(WEB_FONT_DIR / f"{res}.woff2")
        size = ttf.stat().st_size
        total += size
        print(f"✓ {res}.ttf  {size / 1024:.0f} KB  ({family} {style})")
    write_family_xml()
    print(f"Итого в APK: {total / 1024:.0f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
