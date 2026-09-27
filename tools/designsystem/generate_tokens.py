#!/usr/bin/env python3
"""Генератор дизайн-токенов Outfit Share.

Единый источник — docs/design/tokens/tokens.json. Скрипт строит из него:

* Android-ресурсы модуля :core:designsystem (цвета light/dark, dimens, integers,
  float-ресурсы, интерполяторы, TextAppearance-стили, атрибуты и тема-основа);
* Java-константы движения и хаптики;
* CSS-переменные и JS-снимок токенов для HTML-макетов экранов;
* таблицы документации docs/design/tokens/README.md (с отчётом по контрасту).

Перед записью проверяется контраст всех пар из color.contrast по WCAG 2.2:
текст ≥ 4.5:1, элементы интерфейса ≥ 3:1. Нарушение — ненулевой код выхода.

Использование:
    python3 tools/designsystem/generate_tokens.py          # сгенерировать
    python3 tools/designsystem/generate_tokens.py --check  # CI: всё ли актуально
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOKENS_PATH = ROOT / "docs/design/tokens/tokens.json"
MODULE = ROOT / "android/core/designsystem/src/main"
RES = MODULE / "res"
JAVA_PACKAGE = "app.outfitshare.core.designsystem.tokens"
JAVA_DIR = MODULE / "java" / Path(*JAVA_PACKAGE.split("."))
MOCKUP_ASSETS = ROOT / "docs/design/mockups/assets"
TOKENS_DOC = ROOT / "docs/design/tokens/README.md"

HEADER_XML = (
    "<!-- СГЕНЕРИРОВАНО tools/designsystem/generate_tokens.py из docs/design/tokens/tokens.json.\n"
    "     Не редактировать вручную: правьте tokens.json и перезапустите генератор. -->"
)
HEADER_SLASH = (
    "СГЕНЕРИРОВАНО tools/designsystem/generate_tokens.py из docs/design/tokens/tokens.json.\n"
    "Не редактировать вручную: правьте tokens.json и перезапустите генератор."
)

TEXT_MIN = 4.5
UI_MIN = 3.0

# Нелинейное масштабирование шрифта Android 14+ (FontScaleConverterFactory):
# sp → dp для контрольных точек. Выше 100 sp масштабирование линейное 1:1.
FONT_SCALE_FROM_SP = [8, 10, 12, 14, 18, 20, 24, 30, 100]
FONT_SCALE_TABLES = {
    1.15: [9.2, 11.5, 13.8, 16.4, 19.8, 21.8, 25.2, 30, 100],
    1.3: [10.4, 13, 15.6, 18.8, 21.6, 23.6, 26.4, 30, 100],
    1.5: [12, 15, 18, 22, 24, 26, 28, 30, 100],
    1.8: [14.4, 18, 21.6, 24.4, 27.6, 30.8, 32.8, 34.8, 100],
    2.0: [16, 20, 24, 26, 30, 34, 36, 38, 100],
}

# Константы HapticFeedbackConstants, на которые ссылается tokens.json.
KNOWN_HAPTICS = {
    "SEGMENT_TICK",
    "SEGMENT_FREQUENT_TICK",
    "CLOCK_TICK",
    "CONFIRM",
    "REJECT",
    "VIRTUAL_KEY",
    "LONG_PRESS",
    "KEYBOARD_TAP",
    "GESTURE_START",
    "GESTURE_END",
    "DRAG_START",
    "TOGGLE_ON",
    "TOGGLE_OFF",
}


# ---------------------------------------------------------------------------
# Утилиты


def snake(name: str) -> str:
    """camelCase → snake_case: surfaceContainerHigh → surface_container_high."""
    s = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name)
    return s.lower()


def kebab(name: str) -> str:
    return snake(name).replace("_", "-")


def pascal(name: str) -> str:
    return name[0].upper() + name[1:]


def fmt_num(v: float) -> str:
    """Короткая запись числа без хвостовых нулей."""
    if float(v).is_integer():
        return str(int(v))
    return f"{v:.4f}".rstrip("0").rstrip(".")


def hex_to_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    if len(h) != 6:
        raise ValueError(f"Ожидался цвет #RRGGBB, получено #{h}")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def relative_luminance(h: str) -> float:
    def channel(c: int) -> float:
        s = c / 255
        return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4

    r, g, b = hex_to_rgb(h)
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast_ratio(fg: str, bg: str) -> float:
    la, lb = relative_luminance(fg), relative_luminance(bg)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def android_argb(h: str, alpha: float = 1.0) -> str:
    a = round(alpha * 255)
    return f"#{a:02X}{h.lstrip('#').upper()}"


def css_color(h: str, alpha: float = 1.0) -> str:
    if alpha >= 1.0:
        return h.upper()
    r, g, b = hex_to_rgb(h)
    return f"rgba({r}, {g}, {b}, {fmt_num(alpha)})"


def scale_font(sp: float, scale: float) -> float:
    """Размер в dp для sp при заданном масштабе шрифта (Android 14+)."""
    if scale == 1.0:
        return sp
    table = FONT_SCALE_TABLES[scale]
    xs = FONT_SCALE_FROM_SP
    if sp <= xs[0]:
        return sp * table[0] / xs[0]
    if sp >= xs[-1]:
        return sp
    for i in range(len(xs) - 1):
        if xs[i] <= sp <= xs[i + 1]:
            t = (sp - xs[i]) / (xs[i + 1] - xs[i])
            return table[i] + t * (table[i + 1] - table[i])
    raise AssertionError("unreachable")


def xml_escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("--", "—")


# ---------------------------------------------------------------------------
# Модель токенов


@dataclass(frozen=True)
class SemanticColor:
    name: str
    light: str
    dark: str
    alpha_light: float
    alpha_dark: float
    description: str

    def value(self, theme: str) -> tuple[str, float]:
        if theme == "light":
            return self.light, self.alpha_light
        return self.dark, self.alpha_dark


class Tokens:
    def __init__(self, data: dict):
        self.data = data
        self.meta = data["meta"]
        self.primitives = self._flatten_primitives(data["color"]["primitive"])
        self.colors = self._semantic_colors()
        self.avatar_palette = [
            (p["name"], self.resolve(p["light"]), self.resolve(p["dark"])) for p in data["color"]["avatar"]["palette"]
        ]
        on_avatar = data["color"]["avatar"]["onAvatar"]
        self.on_avatar = (self.resolve(on_avatar["light"]), self.resolve(on_avatar["dark"]))

    @staticmethod
    def _flatten_primitives(prim: dict) -> dict[str, str]:
        out: dict[str, str] = {}
        for family, shades in prim.items():
            if isinstance(shades, str):
                out[family] = shades.upper()
            else:
                for shade, value in shades.items():
                    out[f"{family}.{shade}"] = value.upper()
        return out

    def resolve(self, ref: str) -> str:
        m = re.fullmatch(r"\{([a-z]+(?:\.[0-9]+)?)\}", ref)
        if m:
            key = m.group(1)
            if key not in self.primitives:
                raise KeyError(f"Неизвестный примитив цвета: {ref}")
            return self.primitives[key]
        if re.fullmatch(r"#[0-9A-Fa-f]{6}", ref):
            return ref.upper()
        raise ValueError(f"Неверное значение цвета: {ref}")

    def _semantic_colors(self) -> OrderedDict[str, SemanticColor]:
        out: OrderedDict[str, SemanticColor] = OrderedDict()
        for name, spec in self.data["color"]["semantic"].items():
            alpha = spec.get("alpha", {})
            out[name] = SemanticColor(
                name=name,
                light=self.resolve(spec["light"]),
                dark=self.resolve(spec["dark"]),
                alpha_light=float(alpha.get("light", 1.0)),
                alpha_dark=float(alpha.get("dark", 1.0)),
                description=spec.get("$description", ""),
            )
        return out

    # --- контраст ---

    def contrast_report(self) -> list[dict]:
        rows = []
        for kind, minimum in (("text", TEXT_MIN), ("ui", UI_MIN)):
            for fg, bg in self.data["color"]["contrast"][kind]:
                for theme in ("light", "dark"):
                    fg_hex, fg_a = self.colors[fg].value(theme)
                    bg_hex, bg_a = self.colors[bg].value(theme)
                    if fg_a < 1 or bg_a < 1:
                        raise ValueError(f"Контраст не считается для полупрозрачных цветов: {fg}/{bg}")
                    ratio = contrast_ratio(fg_hex, bg_hex)
                    rows.append(
                        dict(kind=kind, fg=fg, bg=bg, theme=theme, ratio=ratio, min=minimum, ok=ratio >= minimum)
                    )
        for theme_index, theme in enumerate(("light", "dark")):
            on = self.on_avatar[theme_index]
            for name, light, dark in self.avatar_palette:
                bg = light if theme == "light" else dark
                ratio = contrast_ratio(on, bg)
                rows.append(
                    dict(
                        kind="text",
                        fg="onAvatar",
                        bg=f"avatar/{name}",
                        theme=theme,
                        ratio=ratio,
                        min=TEXT_MIN,
                        ok=ratio >= TEXT_MIN,
                    )
                )
        return rows

    # --- типографика ---

    def type_scale(self) -> OrderedDict[str, dict]:
        return self.data["typography"]["scale"]

    def font_families(self) -> dict:
        return self.data["typography"]["fontFamily"]


# ---------------------------------------------------------------------------
# Android


def android_colors(t: Tokens) -> dict[Path, str]:
    base = [HEADER_XML, "<resources>"]
    base.append("    <!-- Семантические цвета, светлая тема -->")
    for c in t.colors.values():
        base.append(f'    <color name="ds_light_{snake(c.name)}">{android_argb(c.light, c.alpha_light)}</color>')
    base.append("")
    base.append("    <!-- Семантические цвета, тёмная тема -->")
    for c in t.colors.values():
        base.append(f'    <color name="ds_dark_{snake(c.name)}">{android_argb(c.dark, c.alpha_dark)}</color>')
    base.append("")
    base.append("    <!-- Палитра аватаров-инициалов -->")
    for name, light, dark in t.avatar_palette:
        base.append(f'    <color name="ds_light_avatar_{name}">{android_argb(light)}</color>')
        base.append(f'    <color name="ds_dark_avatar_{name}">{android_argb(dark)}</color>')
    base.append(f'    <color name="ds_light_on_avatar">{android_argb(t.on_avatar[0])}</color>')
    base.append(f'    <color name="ds_dark_on_avatar">{android_argb(t.on_avatar[1])}</color>')
    base.append("")
    base.append("    <!-- Семантические алиасы: переключаются квалификатором -night -->")
    for c in t.colors.values():
        desc = xml_escape(c.description)
        base.append(f"    <!-- {desc} -->")
        base.append(f'    <color name="ds_color_{snake(c.name)}">@color/ds_light_{snake(c.name)}</color>')
    for name, _, _ in t.avatar_palette:
        base.append(f'    <color name="ds_color_avatar_{name}">@color/ds_light_avatar_{name}</color>')
    base.append('    <color name="ds_color_on_avatar">@color/ds_light_on_avatar</color>')
    base.append("</resources>")

    night = [HEADER_XML, "<resources>"]
    for c in t.colors.values():
        night.append(f'    <color name="ds_color_{snake(c.name)}">@color/ds_dark_{snake(c.name)}</color>')
    for name, _, _ in t.avatar_palette:
        night.append(f'    <color name="ds_color_avatar_{name}">@color/ds_dark_avatar_{name}</color>')
    night.append('    <color name="ds_color_on_avatar">@color/ds_dark_on_avatar</color>')
    night.append("</resources>")

    arrays = [HEADER_XML, "<resources>", '    <array name="ds_avatar_palette">']
    for name, _, _ in t.avatar_palette:
        arrays.append(f"        <item>@color/ds_color_avatar_{name}</item>")
    arrays += ["    </array>", "</resources>"]

    return {
        RES / "values/ds_tokens_colors.xml": "\n".join(base) + "\n",
        RES / "values-night/ds_tokens_colors.xml": "\n".join(night) + "\n",
        RES / "values/ds_tokens_arrays.xml": "\n".join(arrays) + "\n",
    }


def _dimen(name: str, value: float, unit: str = "dp", comment: str = "") -> str:
    line = f'    <dimen name="{name}">{fmt_num(value)}{unit}</dimen>'
    if comment:
        line = f"    <!-- {xml_escape(comment)} -->\n" + line
    return line


def _float(name: str, value: float) -> str:
    return f'    <item name="{name}" format="float" type="dimen">{fmt_num(value)}</item>'


def android_dimens(t: Tokens) -> dict[Path, str]:
    d = t.data
    lines = [HEADER_XML, "<resources>", "    <!-- Отступы: сетка 4/8 dp -->"]
    for key, value in d["spacing"].items():
        if key.startswith("$"):
            continue
        lines.append(_dimen(f"ds_space_{key}", value))
    lines.append("")
    lines.append("    <!-- Раскладка -->")
    for key, spec in d["layout"].items():
        lines.append(_dimen(f"ds_layout_{snake(key)}", spec["value"], comment=spec.get("$description", "")))
    lines.append("")
    lines.append("    <!-- Размеры -->")
    for key, spec in d["size"].items():
        lines.append(_dimen(f"ds_size_{snake(key)}", spec["value"], comment=spec.get("$description", "")))
    lines.append("")
    lines.append("    <!-- Радиусы -->")
    for key, value in d["radius"].items():
        lines.append(_dimen(f"ds_radius_{key}", value))
    lines.append("")
    lines.append("    <!-- Семантические формы -->")
    for key, value in d["shape"].items():
        if key.startswith("$"):
            continue
        lines.append(f'    <dimen name="ds_shape_{snake(key)}">@dimen/ds_radius_{value}</dimen>')
    lines.append("")
    lines.append("    <!-- Elevation -->")
    for key, spec in d["elevation"].items():
        if key.startswith("level"):
            lines.append(_dimen(f"ds_elevation_{key}", spec["dp"]))
    for key, level in d["elevation"]["semantic"].items():
        lines.append(f'    <dimen name="ds_elevation_{snake(key)}">@dimen/ds_elevation_{level}</dimen>')
    lines.append("")
    lines.append("    <!-- Движение: расстояния -->")
    for key, value in d["motion"]["distance"].items():
        if key.endswith("Scale"):
            continue
        lines.append(_dimen(f"ds_motion_{snake(key)}", value))
    lines.append("")
    lines.append("    <!-- Типографика: размеры и интерлиньяж в sp -->")
    for key, spec in t.type_scale().items():
        lines.append(_dimen(f"ds_type_{snake(key)}_size", spec["size"], "sp"))
        lines.append(_dimen(f"ds_type_{snake(key)}_line_height", spec["lineHeight"], "sp"))
    lines.append("")
    lines.append("    <!-- Безразмерные значения: читать через ResourcesCompat.getFloat() -->")
    for key, value in d["opacity"].items():
        lines.append(_float(f"ds_opacity_{snake(key)}", value))
    for key, value in d["motion"]["distance"].items():
        if key.endswith("Scale"):
            lines.append(_float(f"ds_motion_{snake(key)}", value))
    for key, spec in d["motion"]["spring"].items():
        if key.startswith("$"):
            continue
        lines.append(_float(f"ds_spring_{snake(key)}_stiffness", spec["stiffness"]))
        lines.append(_float(f"ds_spring_{snake(key)}_damping_ratio", spec["dampingRatio"]))
    scrim = t.colors["scrim"]
    lines.append("    <!-- Затемнение окна под диалогами и bottom sheet (= alpha токена scrim) -->")
    lines.append(_float("ds_scrim_alpha", scrim.alpha_light))
    lines.append("</resources>")

    night = [
        HEADER_XML,
        "<resources>",
        _float("ds_scrim_alpha", scrim.alpha_dark),
        "</resources>",
    ]

    medium = [
        HEADER_XML,
        "<resources>",
        "    <!-- Планшет / раскрытый складной: шире поля -->",
        f'    <dimen name="ds_layout_gutter">{fmt_num(d["layout"]["gutterMedium"]["value"])}dp</dimen>',
        "</resources>",
    ]
    return {
        RES / "values/ds_tokens_dimens.xml": "\n".join(lines) + "\n",
        RES / "values-night/ds_tokens_dimens.xml": "\n".join(night) + "\n",
        RES / "values-w600dp/ds_tokens_dimens.xml": "\n".join(medium) + "\n",
    }


def android_integers(t: Tokens) -> dict[Path, str]:
    d = t.data
    lines = [HEADER_XML, "<resources>", "    <!-- Длительности анимаций, мс -->"]
    for key, value in d["motion"]["duration"].items():
        if key.startswith("$"):
            continue
        lines.append(f'    <integer name="ds_motion_duration_{snake(key)}">{value}</integer>')
    lines.append("")
    lines.append("    <!-- Колонки сетки (compact) -->")
    compact = d["breakpoint"]["compact"]
    lines.append(f'    <integer name="ds_feed_columns">{compact["feedColumns"]}</integer>')
    lines.append(f'    <integer name="ds_grid_columns">{compact["gridColumns"]}</integer>')
    lines.append("</resources>")
    out = {RES / "values/ds_tokens_integers.xml": "\n".join(lines) + "\n"}
    for key in ("medium", "expanded"):
        bp = d["breakpoint"][key]
        out[RES / f"values-w{bp['min']}dp/ds_tokens_integers.xml"] = (
            "\n".join(
                [
                    HEADER_XML,
                    "<resources>",
                    f'    <integer name="ds_feed_columns">{bp["feedColumns"]}</integer>',
                    f'    <integer name="ds_grid_columns">{bp["gridColumns"]}</integer>',
                    "</resources>",
                ]
            )
            + "\n"
        )
    return out


def android_strings(t: Tokens) -> dict[Path, str]:
    lines = [HEADER_XML, "<resources>", "    <!-- Пропорции медиа для AspectRatioImageView (app:dsAspectRatio) -->"]
    for key, value in t.data["aspectRatio"].items():
        lines.append(f'    <string name="ds_aspect_{snake(key)}" translatable="false">{value}</string>')
    lines.append("</resources>")
    return {RES / "values/ds_tokens_strings.xml": "\n".join(lines) + "\n"}


def android_interpolators(t: Tokens) -> dict[Path, str]:
    out = {}
    for key, (x1, y1, x2, y2) in t.data["motion"]["easing"].items():
        if key == "linear":
            body = '<linearInterpolator xmlns:android="http://schemas.android.com/apk/res/android" />'
        else:
            body = (
                '<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"\n'
                f'    android:controlX1="{fmt_num(x1)}"\n'
                f'    android:controlY1="{fmt_num(y1)}"\n'
                f'    android:controlX2="{fmt_num(x2)}"\n'
                f'    android:controlY2="{fmt_num(y2)}" />'
            )
        out[RES / f"interpolator/ds_easing_{snake(key)}.xml"] = (
            '<?xml version="1.0" encoding="utf-8"?>\n' + HEADER_XML + "\n" + body + "\n"
        )
    return out


WEIGHT_NAMES = {400: "regular", 500: "medium", 600: "semibold"}


def font_resource(family: dict, spec: dict) -> str:
    """Отдельный файл шрифта на каждое начертание: выбор веса работает на всех API ≥ 26."""
    if spec.get("italic"):
        if spec["weight"] not in family["italic"]:
            raise ValueError(f"Нет курсива {spec['weight']} у {family['name']}")
        return f"{family['android']}_italic"
    if spec["weight"] not in family["weights"]:
        raise ValueError(f"Нет начертания {spec['weight']} у {family['name']}")
    return f"{family['android']}_{WEIGHT_NAMES[spec['weight']]}"


def android_type_styles(t: Tokens) -> dict[Path, str]:
    families = t.font_families()
    lines = [HEADER_XML, "<resources>"]
    for key, spec in t.type_scale().items():
        fam = families[spec["family"]]
        desc = spec.get("$description", "")
        lines.append(f"    <!-- {xml_escape(desc)} -->")
        font = font_resource(fam, spec)
        lines.append(f'    <style name="TextAppearance.Ds.{pascal(key)}" parent="TextAppearance.Material3.BodyMedium">')
        lines.append(f'        <item name="fontFamily">{font}</item>')
        lines.append(f'        <item name="android:fontFamily">{font}</item>')
        lines.append('        <item name="android:textStyle">normal</item>')
        lines.append(f'        <item name="android:textSize">@dimen/ds_type_{snake(key)}_size</item>')
        lines.append(f'        <item name="lineHeight">@dimen/ds_type_{snake(key)}_line_height</item>')
        lines.append(f'        <item name="android:letterSpacing">{fmt_num(spec["letterSpacing"])}</item>')
        caps = "true" if spec.get("textTransform") == "uppercase" else "false"
        lines.append(f'        <item name="android:textAllCaps">{caps}</item>')
        lines.append(f'        <item name="textAllCaps">{caps}</item>')
        lines.append("    </style>")
    lines.append("</resources>")
    return {RES / "values/ds_tokens_type.xml": "\n".join(lines) + "\n"}


def android_attrs_and_themes(t: Tokens) -> dict[Path, str]:
    attrs = [HEADER_XML, "<resources>"]
    for c in t.colors.values():
        attrs.append(f"    <!-- {xml_escape(c.description)} -->")
        attrs.append(f'    <attr name="dsColor{pascal(c.name)}" format="color" />')
    attrs.append('    <attr name="dsColorOnAvatar" format="color" />')
    attrs.append("</resources>")

    mapping = t.data["platform"]["android"]["materialColorAttrs"]

    def color_items(prefix: str) -> list[str]:
        items = []
        for c in t.colors.values():
            items.append(f'        <item name="dsColor{pascal(c.name)}">@color/{prefix}{snake(c.name)}</item>')
        items.append(f'        <item name="dsColorOnAvatar">@color/{prefix}on_avatar</item>')
        for attr, token in mapping.items():
            items.append(f'        <item name="{attr}">@color/{prefix}{snake(token)}</item>')
        return items

    themes = [HEADER_XML, "<resources>"]
    themes.append("    <!-- Цветовая основа темы: день/ночь переключается ресурсами ds_color_* -->")
    themes.append('    <style name="Base.V0.Theme.Ds" parent="Theme.Material3.DayNight.NoActionBar">')
    themes += color_items("ds_color_")
    themes.append("    </style>")
    themes.append("")
    themes.append("    <!-- Принудительно светлая палитра поверх любой темы (например, превью публикации) -->")
    themes.append('    <style name="ThemeOverlay.Ds.Light" parent="ThemeOverlay.Material3.Light">')
    themes += color_items("ds_light_")
    themes.append("    </style>")
    themes.append("")
    themes.append("    <!-- Принудительно тёмная палитра (камера Студии, полноэкранный просмотр фото) -->")
    themes.append('    <style name="ThemeOverlay.Ds.Dark" parent="ThemeOverlay.Material3.Dark">')
    themes += color_items("ds_dark_")
    themes.append("    </style>")
    themes.append("</resources>")
    return {
        RES / "values/ds_tokens_attrs.xml": "\n".join(attrs) + "\n",
        RES / "values/ds_tokens_themes.xml": "\n".join(themes) + "\n",
    }


def java_tokens(t: Tokens) -> dict[Path, str]:
    d = t.data
    header = "/*\n" + "\n".join(f" * {line}" for line in HEADER_SLASH.splitlines()) + "\n */\n"

    motion = [header, f"package {JAVA_PACKAGE};", ""]
    motion.append("/** Константы движения из tokens.json: пружины и безразмерные масштабы. */")
    motion.append("public final class DsMotionTokens {")
    motion.append("")
    motion.append("  private DsMotionTokens() {}")
    for key, spec in d["motion"]["spring"].items():
        if key.startswith("$"):
            continue
        const = snake(key).upper()
        motion.append("")
        motion.append(f"  /** {spec.get('$description', key)} */")
        motion.append(f"  public static final float SPRING_{const}_STIFFNESS = {fmt_num(spec['stiffness'])}f;")
        motion.append(f"  public static final float SPRING_{const}_DAMPING_RATIO = {fmt_num(spec['dampingRatio'])}f;")
    motion.append("")
    for key, value in d["motion"]["distance"].items():
        if key.endswith("Scale"):
            motion.append(f"  public static final float {snake(key).upper()} = {fmt_num(value)}f;")
    motion.append("")
    for key, value in d["motion"]["duration"].items():
        if key.startswith("$"):
            continue
        motion.append(f"  public static final long DURATION_{snake(key).upper()}_MS = {value}L;")
    motion.append("}")

    haptic = [header, f"package {JAVA_PACKAGE};", ""]
    haptic.append("import android.annotation.SuppressLint;")
    haptic.append("import android.view.HapticFeedbackConstants;")
    haptic.append("")
    haptic.append("/**")
    haptic.append(" * Хаптика ключевых событий: для каждого события — цепочка {константа, минимальный API}.")
    haptic.append(" * Берётся первая константа, доступная на устройстве. См. {@code Haptics}.")
    haptic.append(" */")
    haptic.append('@SuppressLint("InlinedApi")')
    haptic.append("public final class DsHapticTokens {")
    haptic.append("")
    haptic.append("  private DsHapticTokens() {}")
    for key, chain in d["haptics"].items():
        if key.startswith("$"):
            continue
        pairs = []
        for item in chain:
            const, api = item.split("@")
            if const not in KNOWN_HAPTICS:
                raise ValueError(f"Неизвестная константа хаптики: {const}")
            pairs.append(f"{{HapticFeedbackConstants.{const}, {int(api)}}}")
        haptic.append("")
        haptic.append(f"  public static final int[][] {snake(key).upper()} = {{")
        haptic += [f"    {pair}," for pair in pairs]
        haptic.append("  };")
    haptic.append("}")

    return {
        JAVA_DIR / "DsMotionTokens.java": "\n".join(motion) + "\n",
        JAVA_DIR / "DsHapticTokens.java": "\n".join(haptic) + "\n",
    }


# ---------------------------------------------------------------------------
# CSS / JS для макетов


def css_tokens(t: Tokens) -> dict[Path, str]:
    d = t.data
    out = ["/* " + HEADER_SLASH.replace("\n", " ") + " */", ""]

    def theme_block(selector: str, theme: str) -> None:
        out.append(f"{selector} {{")
        out.append(f"  color-scheme: {theme};")
        for c in t.colors.values():
            hexv, alpha = c.value(theme)
            out.append(f"  --ds-color-{kebab(c.name)}: {css_color(hexv, alpha)};")
        idx = 0 if theme == "light" else 1
        for name, light, dark in t.avatar_palette:
            out.append(f"  --ds-color-avatar-{name}: {light if theme == 'light' else dark};")
        out.append(f"  --ds-color-on-avatar: {t.on_avatar[idx]};")
        for key, spec in d["elevation"].items():
            if key.startswith("level"):
                css = spec["css"]
                if theme == "dark" and css != "none":
                    css = re.sub(
                        r"rgba\(28,27,25,\.(\d+)\)",
                        lambda m: f"rgba(0,0,0,{fmt_num(min(0.6, float('0.' + m.group(1)) * 3))})",
                        css,
                    )
                out.append(f"  --ds-elevation-{key}: {css};")
        out.append("}")
        out.append("")

    theme_block(':root, [data-theme="light"]', "light")
    theme_block('[data-theme="dark"]', "dark")

    out.append(":root {")
    for key, value in d["spacing"].items():
        if not key.startswith("$"):
            out.append(f"  --ds-space-{key.replace('_', '-')}: {fmt_num(value)}px;")
    for key, spec in d["layout"].items():
        out.append(f"  --ds-layout-{kebab(key)}: {fmt_num(spec['value'])}px;")
    for key, spec in d["size"].items():
        out.append(f"  --ds-size-{kebab(key)}: {fmt_num(spec['value'])}px;")
    for key, value in d["radius"].items():
        out.append(f"  --ds-radius-{key}: {fmt_num(value)}px;")
    for key, value in d["shape"].items():
        if not key.startswith("$"):
            out.append(f"  --ds-shape-{kebab(key)}: var(--ds-radius-{value});")
    for key, value in d["opacity"].items():
        out.append(f"  --ds-opacity-{kebab(key)}: {fmt_num(value)};")
    for key, value in d["motion"]["duration"].items():
        if not key.startswith("$"):
            out.append(f"  --ds-duration-{kebab(key)}: {value}ms;")
    for key, (x1, y1, x2, y2) in d["motion"]["easing"].items():
        out.append(
            f"  --ds-easing-{kebab(key)}: cubic-bezier({fmt_num(x1)}, {fmt_num(y1)}, {fmt_num(x2)}, {fmt_num(y2)});"
        )
    for key, value in d["motion"]["distance"].items():
        unit = "" if key.endswith("Scale") else "px"
        out.append(f"  --ds-motion-{kebab(key)}: {fmt_num(value)}{unit};")
    for key, fam in t.font_families().items():
        out.append(f"  --ds-font-{key}: {fam['css']};")
    for key, spec in t.type_scale().items():
        out.append(f"  --ds-type-{kebab(key)}-size: {fmt_num(spec['size'])}px;")
        out.append(f"  --ds-type-{kebab(key)}-line: {fmt_num(spec['lineHeight'])}px;")
    out.append("}")
    out.append("")

    out.append("/* Масштаб шрифта: нелинейная таблица Android 14+. Интерлиньяж масштабируется пропорционально. */")
    for scale in sorted(FONT_SCALE_TABLES):
        out.append(f'[data-font-scale="{fmt_num(scale)}"] {{')
        for key, spec in t.type_scale().items():
            size = scale_font(spec["size"], scale)
            line = size * spec["lineHeight"] / spec["size"]
            out.append(f"  --ds-type-{kebab(key)}-size: {size:.1f}px;")
            out.append(f"  --ds-type-{kebab(key)}-line: {line:.1f}px;")
        out.append("}")
    out.append("")

    for key, spec in t.type_scale().items():
        fam = spec["family"]
        out.append(f".ds-t-{kebab(key)} {{")
        out.append(f"  font-family: var(--ds-font-{fam});")
        out.append(f"  font-weight: {spec['weight']};")
        out.append(f"  font-style: {'italic' if spec.get('italic') else 'normal'};")
        out.append(f"  font-size: var(--ds-type-{kebab(key)}-size);")
        out.append(f"  line-height: var(--ds-type-{kebab(key)}-line);")
        out.append(f"  letter-spacing: {fmt_num(spec['letterSpacing'])}em;")
        if spec.get("textTransform"):
            out.append(f"  text-transform: {spec['textTransform']};")
        out.append("}")
    css = "\n".join(out) + "\n"

    snapshot = {
        "meta": t.meta,
        "colors": {
            c.name: {
                "light": css_color(c.light, c.alpha_light),
                "dark": css_color(c.dark, c.alpha_dark),
                "description": c.description,
            }
            for c in t.colors.values()
        },
        "avatarPalette": [{"name": n, "light": lt, "dark": dk} for n, lt, dk in t.avatar_palette],
        "onAvatar": {"light": t.on_avatar[0], "dark": t.on_avatar[1]},
        "primitives": t.primitives,
        "typography": t.data["typography"],
        "spacing": {k: v for k, v in d["spacing"].items() if not k.startswith("$")},
        "radius": d["radius"],
        "size": {k: v["value"] for k, v in d["size"].items()},
        "elevation": {k: v for k, v in d["elevation"].items() if k.startswith("level")},
        "motion": d["motion"],
        "haptics": {k: v for k, v in d["haptics"].items() if not k.startswith("$")},
        "contrast": [
            {k: (round(v, 2) if isinstance(v, float) else v) for k, v in row.items()} for row in t.contrast_report()
        ],
        "fontScaleTables": {fmt_num(k): v for k, v in FONT_SCALE_TABLES.items()},
    }
    js = (
        "/* " + HEADER_SLASH.replace("\n", " ") + " */\n"
        "window.DS_TOKENS = " + json.dumps(snapshot, ensure_ascii=False, indent=1) + ";\n"
    )
    return {MOCKUP_ASSETS / "tokens.css": css, MOCKUP_ASSETS / "tokens.js": js}


# ---------------------------------------------------------------------------
# Документация


def markdown(t: Tokens) -> dict[Path, str]:
    d = t.data
    md: list[str] = []
    md.append("<!-- " + HEADER_SLASH.replace("\n", " ") + " -->")
    md.append("")
    md.append("# Токены")
    md.append("")
    md.append(
        "Единственный источник правды — [`tokens.json`](tokens.json). Генератор "
        "[`tools/designsystem/generate_tokens.py`](../../../tools/designsystem/generate_tokens.py) "
        "раскладывает его в Android-ресурсы `:core:designsystem`, CSS макетов и эту страницу. "
        "В разметке Android и в макетах нет «магических» чисел — только ссылки на токены."
    )
    md.append("")
    md.append("| Слой | Android | CSS |")
    md.append("|---|---|---|")
    md.append("| Семантический цвет | `?attr/dsColorAccent`, `@color/ds_color_accent` | `var(--ds-color-accent)` |")
    md.append("| Отступ | `@dimen/ds_space_4` | `var(--ds-space-4)` |")
    md.append("| Радиус/форма | `@dimen/ds_shape_button` | `var(--ds-shape-button)` |")
    md.append("| Типографика | `@style/TextAppearance.Ds.HeadlineLarge` | `.ds-t-headline-large` |")
    md.append("| Длительность | `@integer/ds_motion_duration_medium` | `var(--ds-duration-medium)` |")
    md.append("| Кривая | `@interpolator/ds_easing_emphasized_decelerate` | `var(--ds-easing-emphasized-decelerate)` |")
    md.append("| Пружина | `DsMotionTokens.SPRING_DROP_STIFFNESS` | — |")
    md.append("")
    md.append(
        "Палитра визуально: [цвета](../screenshots/foundations/foundations-colors.webp), "
        "[типографика](../screenshots/foundations/foundations-typography.webp)."
    )
    md.append("")

    md.append("## Цвет")
    md.append("")
    md.append(
        "Нейтральная база — молочный, графит, тёплый серый; один фирменный акцент — **ультрамарин**. "
        "Акцент появляется только в «моментах» (лайк, непрочитанное, ссылка, фокус, snap), "
        "главное действие — монохромное (графит днём, молочный ночью), чтобы интерфейс не спорил с одеждой."
    )
    md.append("")
    md.append("### Примитивы")
    md.append("")
    md.append("| Примитив | HEX |")
    md.append("|---|---|")
    for key, value in t.primitives.items():
        md.append(f"| `{key}` | `{value}` |")
    md.append("")
    md.append("### Семантические цвета")
    md.append("")
    md.append("| Токен | Android attr | Светлая | Тёмная | Назначение |")
    md.append("|---|---|---|---|---|")
    for c in t.colors.values():
        light = css_color(c.light, c.alpha_light)
        dark = css_color(c.dark, c.alpha_dark)
        md.append(f"| `{c.name}` | `dsColor{pascal(c.name)}` | `{light}` | `{dark}` | {c.description} |")
    md.append("")
    md.append("### Аватары-инициалы")
    md.append("")
    md.append("| Имя | Светлая | Тёмная |")
    md.append("|---|---|---|")
    for name, light, dark in t.avatar_palette:
        md.append(f"| `{name}` | `{light}` | `{dark}` |")
    md.append(f"| инициалы (`onAvatar`) | `{t.on_avatar[0]}` | `{t.on_avatar[1]}` |")
    md.append("")
    md.append("### Material 3 ↔ токены")
    md.append("")
    md.append("| Атрибут M3 | Токен |")
    md.append("|---|---|")
    for attr, token in d["platform"]["android"]["materialColorAttrs"].items():
        md.append(f"| `{attr}` | `{token}` |")
    md.append("")

    md.append("### Контраст (WCAG 2.2 AA)")
    md.append("")
    md.append(
        "Проверяется генератором при каждой сборке токенов. Текст — ≥ 4.5:1 (1.4.3), "
        "элементы интерфейса и крупные иконки — ≥ 3:1 (1.4.11)."
    )
    md.append("")
    md.append("| Передний план | Фон | Тип | Светлая | Тёмная |")
    md.append("|---|---|---|---|---|")
    report = t.contrast_report()
    pairs: OrderedDict[tuple, dict] = OrderedDict()
    for row in report:
        pairs.setdefault((row["fg"], row["bg"], row["kind"]), {})[row["theme"]] = row

    def cell(row: dict) -> str:
        mark = "✅" if row["ok"] else "❌"
        return f"{row['ratio']:.2f} {mark}"

    for (fg, bg, kind), themes in pairs.items():
        label = "текст" if kind == "text" else "UI"
        md.append(
            f"| `{fg}` | `{bg}` | {label} ≥ {fmt_num(TEXT_MIN if kind == 'text' else UI_MIN)} | "
            f"{cell(themes['light'])} | {cell(themes['dark'])} |"
        )
    md.append("")

    md.append("## Типографика")
    md.append("")
    fams = t.font_families()
    md.append("| Роль | Семейство | Источник | Лицензия |")
    md.append("|---|---|---|---|")
    for key, fam in fams.items():
        md.append(f"| `{key}` | {fam['name']} | {fam['upstream']} | {fam['license']} |")
    md.append("")
    md.append(
        "Размеры в sp. Колонка «200 %» — фактический размер в dp при максимальном масштабе шрифта "
        "Android 14+ (нелинейная шкала: крупный текст растёт меньше мелкого)."
    )
    md.append("")
    md.append("| Стиль | Android | Шрифт | Кегль/интерлиньяж | Трекинг | 200 % | Применение |")
    md.append("|---|---|---|---|---|---|---|")
    for key, spec in t.type_scale().items():
        fam = fams[spec["family"]]["name"]
        weight = spec["weight"]
        style = f"{fam} {weight}" + (" italic" if spec.get("italic") else "")
        if spec.get("textTransform") == "uppercase":
            style += ", ВЕРХНИЙ РЕГИСТР"
        scaled = scale_font(spec["size"], 2.0)
        size = f"{fmt_num(spec['size'])}/{fmt_num(spec['lineHeight'])}"
        md.append(
            f"| `{key}` | `TextAppearance.Ds.{pascal(key)}` | {style} | {size} | "
            f"{fmt_num(spec['letterSpacing'])} em | {scaled:.0f} dp | {spec.get('$description', '')} |"
        )
    md.append("")

    md.append("## Отступы и раскладка")
    md.append("")
    md.append("Сетка 4/8 dp. `@dimen/ds_space_N`, `var(--ds-space-N)`.")
    md.append("")
    md.append("| Токен | dp |")
    md.append("|---|---|")
    for key, value in d["spacing"].items():
        if not key.startswith("$"):
            md.append(f"| `space.{key.replace('_', '.')}` → `ds_space_{key}` | {fmt_num(value)} |")
    md.append("")
    md.append("| Раскладка | dp | Назначение |")
    md.append("|---|---|---|")
    for key, spec in d["layout"].items():
        md.append(f"| `ds_layout_{snake(key)}` | {fmt_num(spec['value'])} | {spec.get('$description', '')} |")
    md.append("")
    md.append("| Класс окна | Ширина, dp | Колонки ленты | Колонки сетки |")
    md.append("|---|---|---|---|")
    for key in ("compact", "medium", "expanded"):
        bp = d["breakpoint"][key]
        md.append(f"| {key} | ≥ {bp['min']} | {bp['feedColumns']} | {bp['gridColumns']} |")
    md.append("")

    md.append("## Размеры")
    md.append("")
    md.append("| Токен | dp | Примечание |")
    md.append("|---|---|---|")
    for key, spec in d["size"].items():
        md.append(f"| `ds_size_{snake(key)}` | {fmt_num(spec['value'])} | {spec.get('$description', '')} |")
    md.append("")
    md.append("Пропорции медиа: " + ", ".join(f"{k} — {v}" for k, v in d["aspectRatio"].items()) + ".")
    md.append("")

    md.append("## Радиусы и формы")
    md.append("")
    md.append("| Радиус | dp |")
    md.append("|---|---|")
    for key, value in d["radius"].items():
        md.append(f"| `ds_radius_{key}` | {fmt_num(value)} |")
    md.append("")
    md.append("| Форма | Радиус |")
    md.append("|---|---|")
    for key, value in d["shape"].items():
        if not key.startswith("$"):
            md.append(f"| `ds_shape_{snake(key)}` | `{value}` |")
    md.append("")

    md.append("## Elevation")
    md.append("")
    md.append(d["elevation"]["$description"])
    md.append("")
    md.append("| Уровень | dp | CSS-тень (светлая) |")
    md.append("|---|---|---|")
    for key, spec in d["elevation"].items():
        if key.startswith("level"):
            md.append(f"| `{key}` | {spec['dp']} | `{spec['css']}` |")
    md.append("")
    md.append("| Элемент | Уровень |")
    md.append("|---|---|")
    for key, level in d["elevation"]["semantic"].items():
        md.append(f"| `{key}` | `{level}` |")
    md.append("")

    md.append("## Прозрачности")
    md.append("")
    md.append("| Токен | Значение |")
    md.append("|---|---|")
    for key, value in d["opacity"].items():
        md.append(f"| `ds_opacity_{snake(key)}` | {fmt_num(value)} |")
    md.append("")

    md.append("## Движение")
    md.append("")
    md.append(d["motion"]["duration"]["$description"])
    md.append("")
    md.append("| Длительность | мс |")
    md.append("|---|---|")
    for key, value in d["motion"]["duration"].items():
        if not key.startswith("$"):
            md.append(f"| `ds_motion_duration_{snake(key)}` | {value} |")
    md.append("")
    md.append("| Кривая | cubic-bezier |")
    md.append("|---|---|")
    for key, value in d["motion"]["easing"].items():
        md.append(f"| `ds_easing_{snake(key)}` | ({', '.join(fmt_num(v) for v in value)}) |")
    md.append("")
    md.append("| Пружина | stiffness | dampingRatio | Где |")
    md.append("|---|---|---|---|")
    for key, spec in d["motion"]["spring"].items():
        if not key.startswith("$"):
            stiffness, damping = fmt_num(spec["stiffness"]), fmt_num(spec["dampingRatio"])
            md.append(f"| `{key}` | {stiffness} | {damping} | {spec.get('$description', '')} |")
    md.append("")
    md.append("| Параметр | Значение |")
    md.append("|---|---|")
    for key, value in d["motion"]["distance"].items():
        unit = "" if key.endswith("Scale") else " dp"
        md.append(f"| `{key}` | {fmt_num(value)}{unit} |")
    md.append("")

    md.append("## Хаптика")
    md.append("")
    md.append(d["haptics"]["$description"])
    md.append("")
    md.append("| Событие | Цепочка `HapticFeedbackConstants` (константа@minApi) |")
    md.append("|---|---|")
    for key, chain in d["haptics"].items():
        if not key.startswith("$"):
            md.append(f"| `{key}` | {' → '.join(f'`{c}`' for c in chain)} |")
    md.append("")
    return {TOKENS_DOC: "\n".join(md)}


# ---------------------------------------------------------------------------


def build(data: dict) -> tuple[dict[Path, str], list[dict]]:
    t = Tokens(data)
    report = t.contrast_report()
    outputs: dict[Path, str] = {}
    for part in (
        android_colors,
        android_dimens,
        android_integers,
        android_strings,
        android_interpolators,
        android_type_styles,
        android_attrs_and_themes,
        java_tokens,
        css_tokens,
        markdown,
    ):
        outputs.update(part(t))
    return outputs, report


def load_tokens(path: Path = TOKENS_PATH) -> dict:
    with path.open(encoding="utf-8") as fh:
        return json.load(fh, object_pairs_hook=OrderedDict)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="не писать файлы, а проверить их актуальность")
    args = parser.parse_args(argv)

    outputs, report = build(load_tokens())
    failures = [r for r in report if not r["ok"]]
    for r in failures:
        print(
            f"КОНТРАСТ ✗ {r['theme']}: {r['fg']} на {r['bg']} = {r['ratio']:.2f} (< {r['min']})",
            file=sys.stderr,
        )
    if failures:
        return 1

    stale = []
    for path, content in outputs.items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current != content:
            stale.append(path)
            if not args.check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")

    if args.check:
        if stale:
            for path in stale:
                print(f"устарел: {path.relative_to(ROOT)}", file=sys.stderr)
            print("Запустите: python3 tools/designsystem/generate_tokens.py", file=sys.stderr)
            return 1
        print(f"Токены актуальны ({len(outputs)} файлов, {len(report)} пар контраста — все AA).")
        return 0

    print(f"Сгенерировано/обновлено {len(stale)} из {len(outputs)} файлов; контраст: {len(report)} пар — все AA.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
