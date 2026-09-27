"""Тесты генератора токенов: математика контраста и шрифтов, правила сетки, актуальность файлов."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import generate_tokens as gt  # noqa: E402


@pytest.fixture(scope="module")
def data() -> dict:
    return gt.load_tokens()


@pytest.fixture(scope="module")
def tokens(data) -> gt.Tokens:
    return gt.Tokens(data)


def test_contrast_ratio_reference_values():
    assert gt.contrast_ratio("#000000", "#FFFFFF") == pytest.approx(21.0)
    assert gt.contrast_ratio("#777777", "#777777") == pytest.approx(1.0)
    # Порядок аргументов не важен.
    assert gt.contrast_ratio("#2F3BD6", "#F6F2EB") == pytest.approx(gt.contrast_ratio("#F6F2EB", "#2F3BD6"))


def test_naming_helpers():
    assert gt.snake("surfaceContainerHigh") == "surface_container_high"
    assert gt.kebab("onAccentContainer") == "on-accent-container"
    assert gt.pascal("displayLarge") == "DisplayLarge"
    assert gt.android_argb("#1C1B19", 0.48) == "#7A1C1B19"
    assert gt.css_color("#000000", 0.55) == "rgba(0, 0, 0, 0.55)"


@pytest.mark.parametrize(
    ("sp", "scale", "expected"),
    [
        (14, 1.0, 14),
        (14, 2.0, 26),
        (8, 2.0, 16),
        (30, 2.0, 38),
        (100, 2.0, 100),
        (16, 1.3, 20.2),
    ],
)
def test_nonlinear_font_scale(sp, scale, expected):
    assert gt.scale_font(sp, scale) == pytest.approx(expected, abs=0.05)


def test_font_scale_is_monotonic():
    for scale in gt.FONT_SCALE_TABLES:
        sizes = [gt.scale_font(sp, scale) for sp in range(8, 101)]
        assert sizes == sorted(sizes), scale
        # Крупный текст растёт не сильнее мелкого.
        assert gt.scale_font(48, scale) / 48 <= gt.scale_font(12, scale) / 12


def test_all_contrast_pairs_pass_wcag_aa(tokens):
    failures = [r for r in tokens.contrast_report() if not r["ok"]]
    assert failures == []


def test_contrast_covers_both_themes(tokens):
    themes = {r["theme"] for r in tokens.contrast_report()}
    assert themes == {"light", "dark"}


def test_references_point_to_existing_tokens(data, tokens):
    names = set(tokens.colors)
    for kind in ("text", "ui"):
        for fg, bg in data["color"]["contrast"][kind]:
            assert fg in names and bg in names
    for attr, token in data["platform"]["android"]["materialColorAttrs"].items():
        assert token in names, attr


def test_spacing_and_layout_follow_4dp_grid(data):
    for key, value in data["spacing"].items():
        if key.startswith("$") or key == "0_5":  # 2 dp — только для оптической подгонки иконок
            continue
        assert value % 4 == 0, key
    for key, spec in data["layout"].items():
        assert spec["value"] % 4 == 0, key


def test_component_sizes_follow_grid_and_touch_target(data):
    strokes = {
        "strokeHairline",
        "strokeThin",
        "strokeThick",
        "focusRingWidth",
        "sheetHandleHeight",
        "progressTrack",
        "badgeDot",
    }
    for key, spec in data["size"].items():
        if key in strokes:
            continue
        assert spec["value"] % 4 == 0, key
    assert data["size"]["touchTarget"]["value"] >= 48
    assert data["size"]["iconButton"]["value"] >= data["size"]["touchTarget"]["value"]


def test_type_scale_uses_available_font_files(tokens):
    font_dir = gt.RES / "font"
    for key, spec in tokens.type_scale().items():
        family = tokens.font_families()[spec["family"]]
        res = gt.font_resource(family, spec).removeprefix("@font/")
        assert (font_dir / f"{res}.ttf").exists(), f"{key}: нет {res}.ttf (запустите build_fonts.py)"
        assert spec["lineHeight"] % 2 == 0, key
        assert spec["lineHeight"] >= spec["size"], key


def test_motion_tokens_are_sane(data):
    durations = {k: v for k, v in data["motion"]["duration"].items() if not k.startswith("$")}
    assert durations["micro"] < durations["short"] < durations["medium"] < durations["long"] < durations["extraLong"]
    for key, spring in data["motion"]["spring"].items():
        if key.startswith("$"):
            continue
        assert spring["stiffness"] > 0
        assert 0 < spring["dampingRatio"] <= 1, key
    # «Лёгкая пружина» при падении вещи — отскок есть, но небольшой.
    assert 0.4 <= data["motion"]["spring"]["drop"]["dampingRatio"] < 1


def test_haptic_chains_end_with_legacy_fallback(data):
    for key, chain in data["haptics"].items():
        if key.startswith("$"):
            continue
        apis = [int(item.split("@")[1]) for item in chain]
        assert apis == sorted(apis, reverse=True), key
        assert apis[-1] <= 26, f"{key}: нет фолбэка для minSdk 26"


def test_generated_files_are_up_to_date(data):
    outputs, _ = gt.build(data)
    stale = [
        str(path.relative_to(gt.ROOT))
        for path, content in outputs.items()
        if not path.exists() or path.read_text(encoding="utf-8") != content
    ]
    assert stale == [], "Запустите python3 tools/designsystem/generate_tokens.py"
