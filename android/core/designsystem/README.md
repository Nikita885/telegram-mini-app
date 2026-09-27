# `:core:designsystem`

Дизайн-система Outfit Share для Android: токены, тема Material 3, компоненты, движение, хаптика и
правила доступности. Java 17, View-система, minSdk 26, compileSdk 36. Документация и макеты —
[`docs/design`](../../../docs/design/README.md).

## Состав

```
src/main/
├─ java/app/outfitshare/core/designsystem/
│  ├─ tokens/        DsMotionTokens, DsHapticTokens            ← генерируются из tokens.json
│  ├─ theme/         DsTheme, ThemeMode, SystemBars
│  ├─ motion/        Motion, DropAnimation, HeartBurst, SharedElements
│  ├─ haptics/       Haptics, HapticChain
│  ├─ a11y/          A11y
│  └─ component/     avatar, badge, banner, button, card, chip, dialog, image, like, list,
│                    navigation, sheet, skeleton, snackbar, state
└─ res/
   ├─ values/ds_tokens_*.xml, values-night/, values-w600dp/, values-w840dp/  ← генерируются
   ├─ values/themes*.xml, styles_*.xml, shapes.xml, attrs_components.xml, strings.xml
   ├─ font/          OutfitShare Display (из Playfair Display), Inter             ← build_fonts.py
   ├─ drawable/      ds_ic_* (Material Symbols), ds_illustration_* (illustrations.py), фоны
   ├─ color/, animator/, interpolator/, layout/
licenses/            OFL для шрифтов, Apache 2.0 для иконок
```

## Подключение

```kotlin
dependencies { implementation(project(":core:designsystem")) }
```

```xml
<application android:theme="@style/Theme.Ds.Starting"> <!-- installSplashScreen() в Activity -->
```

`android.nonTransitiveRClass=true`: ресурсы модуля — `app.outfitshare.core.designsystem.R`.

## Правила

* Цвет — только `?attr/dsColor*` / `@color/ds_color_*` или атрибуты Material, которые тема уже
  связала с токенами. Размеры — только `@dimen/ds_*`. Текст — только `TextAppearance.Ds.*` /
  `?attr/textAppearance*`.
* Сгенерированные файлы (`ds_tokens_*`, `tokens/Ds*Tokens.java`, `font/`, `ds_ic_*`,
  `ds_illustration_*`) руками не редактируются — см. `tools/designsystem`.
* Новый интерактивный компонент — минимум 48 dp, описание для TalkBack, состояние не только цветом,
  поведение при шрифте ≥ 150 % и при «Убрать анимации».

## Проверки

```bash
./gradlew :core:designsystem:testDebugUnitTest   # логика компонентов + правила ресурсов
./gradlew :core:designsystem:lint                 # a11y-проверки Lint повышены до ошибок (lint.xml)
./gradlew spotlessCheck :core:designsystem:checkstyle
python3 tools/designsystem/generate_tokens.py --check
```

`DesignSystemResourcesTest` читает ресурсы модуля и проверяет контраст основных пар в обеих темах,
отсутствие литеральных dp/sp в разметке и стилях, минимальные зоны касания интерактивных стилей,
sp для текста и сетку 4 dp для отступов.

## Версии зависимостей

Каталог [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) составлен без доступа к
Google Maven (в среде разработки он был закрыт), поэтому AGP 8.11.1, Material 1.12.0 и AndroidX
нужно подтвердить при первой синхронизации. Код модуля при этом скомпилирован `javac` против Android
API 36 и проверен google-java-format и Checkstyle; ссылки между ресурсами сверены скриптом.
