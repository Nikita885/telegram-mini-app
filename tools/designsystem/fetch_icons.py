#!/usr/bin/env python3
"""Вендоринг иконок Material Symbols (Apache 2.0) для дизайн-системы.

Стиль: Outlined, вес 300 — тонкая линия в тон антикве заголовков.
Скрипт скачивает для каждой иконки из ICONS:
* Android VectorDrawable → res/drawable/ds_ic_<имя>.xml (тонируется ?attr/colorControlNormal);
* SVG-путь → docs/design/mockups/assets/icons.js для HTML-макетов.

Запуск: python3 tools/designsystem/fetch_icons.py
"""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "android/core/designsystem/src/main/res/drawable"
ICONS_JS = ROOT / "docs/design/mockups/assets/icons.js"
LICENSE_DIR = ROOT / "android/core/designsystem/licenses"
BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols"
WEIGHT = 300

# имя в дизайн-системе → (имя Material Symbols, залитая версия)
ICONS: dict[str, tuple[str, bool]] = {
    # навигация
    "home": ("home", False),
    "home_filled": ("home", True),
    "search": ("search", False),
    "add": ("add", False),
    "person": ("person", False),
    "person_filled": ("person", True),
    "notifications": ("notifications", False),
    "notifications_filled": ("notifications", True),
    "notifications_off": ("notifications_off", False),
    "chat": ("chat", False),
    "chat_filled": ("chat", True),
    "arrow_back": ("arrow_back", False),
    "close": ("close", False),
    "check": ("check", False),
    "chevron_right": ("chevron_right", False),
    "expand_more": ("expand_more", False),
    "more_horiz": ("more_horiz", False),
    "more_vert": ("more_vert", False),
    # социальное
    "favorite": ("favorite", False),
    "favorite_filled": ("favorite", True),
    "comment": ("chat_bubble", False),
    "bookmark": ("bookmark", False),
    "bookmark_filled": ("bookmark", True),
    "collections": ("collections_bookmark", False),
    "share": ("ios_share", False),
    "send": ("send", False),
    "remix": ("cycle", False),
    "person_add": ("person_add", False),
    "following": ("how_to_reg", False),
    "tag": ("tag", False),
    "flag": ("flag", False),
    "block": ("block", False),
    "done_all": ("done_all", False),
    "attach": ("attach_file", False),
    "link": ("link", False),
    "verified": ("verified", False),
    # конструктор
    "undo": ("undo", False),
    "redo": ("redo", False),
    "layers": ("layers", False),
    "delete": ("delete", False),
    "edit": ("edit", False),
    "draft": ("draft", False),
    "hanger": ("checkroom", False),
    "apparel": ("apparel", False),
    "dress": ("styler", False),
    "mannequin": ("accessibility_new", False),
    "drag_handle": ("drag_indicator", False),
    "open_with": ("open_with", False),
    "swap": ("swap_horiz", False),
    "magnet": ("join", False),
    # студия
    "camera": ("photo_camera", False),
    "flash_on": ("flash_on", False),
    "flash_off": ("flash_off", False),
    "gallery": ("photo_library", False),
    "light": ("wb_sunny", False),
    "frame": ("crop_free", False),
    "background": ("texture", False),
    "sparkle": ("auto_awesome", False),
    "upload": ("cloud_upload", False),
    "queue": ("hourglass_empty", False),
    "keypoint": ("adjust", False),
    "straighten": ("straighten", False),
    # состояния
    "cloud_off": ("cloud_off", False),
    "wifi_off": ("wifi_off", False),
    "refresh": ("refresh", False),
    "lock": ("lock", False),
    "error": ("error", False),
    "warning": ("warning", False),
    "info": ("info", False),
    "check_circle": ("check_circle", False),
    "schedule": ("schedule", False),
    "history": ("history", False),
    "north_west": ("north_west", False),
    # фильтры и вид
    "tune": ("tune", False),
    "grid": ("grid_view", False),
    "list": ("view_agenda", False),
    "sort": ("sort", False),
    # настройки
    "settings": ("settings", False),
    "logout": ("logout", False),
    "dark_mode": ("dark_mode", False),
    "light_mode": ("light_mode", False),
    "palette": ("palette", False),
    "language": ("language", False),
    "privacy": ("shield", False),
    "help": ("help", False),
    "mail": ("mail", False),
    "visibility": ("visibility", False),
    "visibility_off": ("visibility_off", False),
    "copy": ("content_copy", False),
    "image": ("image", False),
    "touch": ("touch_app", False),
    "swipe": ("swipe", False),
    "accessibility": ("accessibility", False),
}


def fetch(url: str) -> str | None:
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:  # noqa: S310 — фиксированный https-хост
            return resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise


def file_stem(symbol: str, filled: bool) -> str:
    return f"{symbol}_wght{WEIGHT}{'fill1' if filled else ''}_24px"


def android_xml(name: str, symbol: str, source: str) -> str:
    body = source.strip()
    body = re.sub(r"^<\?xml[^>]*\?>\s*", "", body)
    header = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Material Symbols Outlined «{symbol}», wght {WEIGHT} (Apache 2.0). "
        "Вендорено tools/designsystem/fetch_icons.py. -->\n"
    )
    return header + body + "\n"


def svg_path(source: str) -> dict:
    view_box = re.search(r'viewBox="([^"]+)"', source)
    if view_box:
        box = view_box.group(1)
    else:  # старые файлы в 24-единичной сетке без viewBox
        width = re.search(r'width="([0-9.]+)"', source)
        height = re.search(r'height="([0-9.]+)"', source)
        if not width or not height:
            raise ValueError("Не удалось определить размер SVG")
        box = f"0 0 {width.group(1)} {height.group(1)}"
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', source)
    if not paths:
        raise ValueError("Не удалось разобрать SVG")
    return {"viewBox": box, "d": " ".join(paths)}


def main() -> int:
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    ICONS_JS.parent.mkdir(parents=True, exist_ok=True)
    web: dict[str, dict] = {}
    for name, (symbol, filled) in ICONS.items():
        stem = file_stem(symbol, filled)
        xml = fetch(f"{BASE}/android/{symbol}/materialsymbolsoutlined/{stem}.xml")
        svg = fetch(f"{BASE}/web/{symbol}/materialsymbolsoutlined/{stem}.svg")
        if xml is None or svg is None:
            print(f"✗ нет иконки {symbol} ({stem})", file=sys.stderr)
            return 1
        (DRAWABLE / f"ds_ic_{name}.xml").write_text(android_xml(name, symbol, xml), encoding="utf-8")
        web[name] = svg_path(svg)
        print(f"✓ ds_ic_{name} ← {symbol}{' (fill)' if filled else ''}")
    ICONS_JS.write_text(
        "/* Material Symbols Outlined wght 300 (Apache 2.0). Сгенерировано tools/designsystem/fetch_icons.py */\n"
        "window.DS_ICONS = " + json.dumps(web, indent=0) + ";\n",
        encoding="utf-8",
    )
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)
    license_text = fetch("https://raw.githubusercontent.com/google/material-design-icons/master/LICENSE")
    if license_text:
        (LICENSE_DIR / "Apache-2.0-MaterialSymbols.txt").write_text(license_text, encoding="utf-8")
    print(f"Иконок: {len(ICONS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
