<!-- СГЕНЕРИРОВАНО tools/designsystem/generate_tokens.py из docs/design/tokens/tokens.json. Не редактировать вручную: правьте tokens.json и перезапустите генератор. -->

# Токены

Единственный источник правды — [`tokens.json`](tokens.json). Генератор [`tools/designsystem/generate_tokens.py`](../../../tools/designsystem/generate_tokens.py) раскладывает его в Android-ресурсы `:core:designsystem`, CSS макетов и эту страницу. В разметке Android и в макетах нет «магических» чисел — только ссылки на токены.

| Слой | Android | CSS |
|---|---|---|
| Семантический цвет | `?attr/dsColorAccent`, `@color/ds_color_accent` | `var(--ds-color-accent)` |
| Отступ | `@dimen/ds_space_4` | `var(--ds-space-4)` |
| Радиус/форма | `@dimen/ds_shape_button` | `var(--ds-shape-button)` |
| Типографика | `@style/TextAppearance.Ds.HeadlineLarge` | `.ds-t-headline-large` |
| Длительность | `@integer/ds_motion_duration_medium` | `var(--ds-duration-medium)` |
| Кривая | `@interpolator/ds_easing_emphasized_decelerate` | `var(--ds-easing-emphasized-decelerate)` |
| Пружина | `DsMotionTokens.SPRING_DROP_STIFFNESS` | — |

Палитра визуально: [цвета](../screenshots/foundations/foundations-colors.webp), [типографика](../screenshots/foundations/foundations-typography.webp).

## Цвет

Нейтральная база — молочный, графит, тёплый серый; один фирменный акцент — **ультрамарин**. Акцент появляется только в «моментах» (лайк, непрочитанное, ссылка, фокус, snap), главное действие — монохромное (графит днём, молочный ночью), чтобы интерфейс не спорил с одеждой.

### Примитивы

| Примитив | HEX |
|---|---|
| `milk.50` | `#FCFAF6` |
| `milk.100` | `#F6F2EB` |
| `milk.150` | `#F1ECE4` |
| `milk.200` | `#EFE9DF` |
| `milk.250` | `#EAE3D8` |
| `milk.300` | `#E4DCCF` |
| `stone.300` | `#D8D2C9` |
| `stone.400` | `#B8AEA1` |
| `stone.500` | `#857C70` |
| `stone.550` | `#8A8175` |
| `stone.600` | `#6B6358` |
| `stone.700` | `#544D45` |
| `graphite.600` | `#45423D` |
| `graphite.700` | `#3A3733` |
| `graphite.750` | `#2F2D2A` |
| `graphite.800` | `#262422` |
| `graphite.850` | `#221F1D` |
| `graphite.900` | `#1C1B19` |
| `graphite.950` | `#131211` |
| `graphite.1000` | `#0B0B0A` |
| `ultramarine.100` | `#E6E8FF` |
| `ultramarine.200` | `#C9CEFF` |
| `ultramarine.300` | `#A3ABFF` |
| `ultramarine.400` | `#7C87FF` |
| `ultramarine.500` | `#4C57F0` |
| `ultramarine.600` | `#2F3BD6` |
| `ultramarine.700` | `#232CA8` |
| `ultramarine.800` | `#1A2080` |
| `ultramarine.850` | `#262C8A` |
| `ultramarine.900` | `#10144F` |
| `red.100` | `#FBE3DF` |
| `red.300` | `#FFB4A9` |
| `red.600` | `#B42318` |
| `red.800` | `#6E140D` |
| `red.900` | `#410A05` |
| `green.100` | `#DDF1E4` |
| `green.300` | `#86D0A2` |
| `green.600` | `#1A6E3E` |
| `green.800` | `#123D25` |
| `green.900` | `#0B3A20` |
| `amber.100` | `#FBEFD5` |
| `amber.300` | `#F1C267` |
| `amber.700` | `#8A5300` |
| `amber.800` | `#3F2A06` |
| `amber.900` | `#3A2400` |
| `white` | `#FFFFFF` |
| `black` | `#000000` |

### Семантические цвета

| Токен | Android attr | Светлая | Тёмная | Назначение |
|---|---|---|---|---|
| `background` | `dsColorBackground` | `#F6F2EB` | `#131211` | Фон экрана. Молочный днём, почти чёрный графит ночью. |
| `onBackground` | `dsColorOnBackground` | `#1C1B19` | `#F6F2EB` | Основной текст и иконки на фоне. |
| `surface` | `dsColorSurface` | `#FCFAF6` | `#1C1B19` | Поднятые поверхности: bottom sheet, диалоги, меню. |
| `onSurface` | `dsColorOnSurface` | `#1C1B19` | `#F6F2EB` | Основной текст на поверхностях. |
| `onSurfaceVariant` | `dsColorOnSurfaceVariant` | `#6B6358` | `#B8AEA1` | Вторичный текст: подписи, мета, плейсхолдеры, неактивные иконки. |
| `surfaceSunken` | `dsColorSurfaceSunken` | `#EFE9DF` | `#221F1D` | Утопленные зоны: плейсхолдер фото, поля ввода, плитка вещи. |
| `surfaceContainerLowest` | `dsColorSurfaceContainerLowest` | `#FCFAF6` | `#131211` | M3 surfaceContainerLowest. |
| `surfaceContainerLow` | `dsColorSurfaceContainerLow` | `#F6F2EB` | `#1C1B19` | M3 surfaceContainerLow. |
| `surfaceContainer` | `dsColorSurfaceContainer` | `#F1ECE4` | `#262422` | M3 surfaceContainer: навбар, карточки настроек. |
| `surfaceContainerHigh` | `dsColorSurfaceContainerHigh` | `#EAE3D8` | `#2F2D2A` | M3 surfaceContainerHigh: выбранный элемент навигации, тональные зоны. |
| `surfaceContainerHighest` | `dsColorSurfaceContainerHighest` | `#E4DCCF` | `#3A3733` | M3 surfaceContainerHighest: трек слайдера, разделители-плашки. |
| `outline` | `dsColorOutline` | `#857C70` | `#8A8175` | Контур значимых элементов (поля, secondary-кнопка, чип). Контраст ≥ 3:1. |
| `outlineVariant` | `dsColorOutlineVariant` | `#E4DCCF` | `#3A3733` | Декоративные разделители. Контраст не нормируется. |
| `primary` | `dsColorPrimary` | `#1C1B19` | `#F6F2EB` | Главное действие (primary-кнопка). Графит днём, молочный ночью — монохром как в журнале. |
| `onPrimary` | `dsColorOnPrimary` | `#FCFAF6` | `#1C1B19` | Текст/иконка на primary. |
| `accent` | `dsColorAccent` | `#2F3BD6` | `#A3ABFF` | Фирменный ультрамарин. Только «моменты»: лайк, непрочитанное, ссылки, фокус, snap-подсветка, прогресс. |
| `onAccent` | `dsColorOnAccent` | `#FFFFFF` | `#10144F` | Контент на accent. |
| `accentContainer` | `dsColorAccentContainer` | `#E6E8FF` | `#262C8A` | Мягкая подложка акцента: выбранный фильтр-чип «Новое», инфо-баннер. |
| `onAccentContainer` | `dsColorOnAccentContainer` | `#1A2080` | `#E6E8FF` | Контент на accentContainer. |
| `danger` | `dsColorDanger` | `#B42318` | `#FFB4A9` | Ошибки и разрушающие действия (удалить, выйти, заблокировать). |
| `onDanger` | `dsColorOnDanger` | `#FCFAF6` | `#410A05` | Контент на danger. |
| `dangerContainer` | `dsColorDangerContainer` | `#FBE3DF` | `#6E140D` | Подложка ошибки (баннер, поле с ошибкой). |
| `onDangerContainer` | `dsColorOnDangerContainer` | `#6E140D` | `#FBE3DF` | Контент на dangerContainer. |
| `success` | `dsColorSuccess` | `#1A6E3E` | `#86D0A2` | Успех: вещь опубликована, качество кадра ок, онлайн. |
| `successContainer` | `dsColorSuccessContainer` | `#DDF1E4` | `#123D25` | Подложка успеха. |
| `onSuccessContainer` | `dsColorOnSuccessContainer` | `#0B3A20` | `#DDF1E4` | Контент на successContainer. |
| `warning` | `dsColorWarning` | `#8A5300` | `#F1C267` | Предупреждение: слабый свет, низкий fit score, офлайн. |
| `warningContainer` | `dsColorWarningContainer` | `#FBEFD5` | `#3F2A06` | Подложка предупреждения (офлайн-баннер). |
| `onWarningContainer` | `dsColorOnWarningContainer` | `#3A2400` | `#FBEFD5` | Контент на warningContainer. |
| `inverseSurface` | `dsColorInverseSurface` | `#1C1B19` | `#F6F2EB` | Снекбар, тултип. |
| `inverseOnSurface` | `dsColorInverseOnSurface` | `#F6F2EB` | `#1C1B19` | Текст на inverseSurface. |
| `inverseAccent` | `dsColorInverseAccent` | `#A3ABFF` | `#2F3BD6` | Действие в снекбаре («Отменить»). |
| `like` | `dsColorLike` | `#2F3BD6` | `#A3ABFF` | Заливка сердца. Совпадает с accent: лайк — главный эмоциональный момент бренда. |
| `focusRing` | `dsColorFocusRing` | `#2F3BD6` | `#A3ABFF` | Кольцо клавиатурного/switch-фокуса. |
| `scrim` | `dsColorScrim` | `rgba(28, 27, 25, 0.48)` | `rgba(0, 0, 0, 0.64)` | Затемнение под bottom sheet и диалогами. |
| `photoScrim` | `dsColorPhotoScrim` | `rgba(0, 0, 0, 0.55)` | `rgba(0, 0, 0, 0.6)` | Нижний градиент на фото для подписи поверх изображения. |
| `onPhoto` | `dsColorOnPhoto` | `#FFFFFF` | `#FFFFFF` | Текст/иконки поверх фото (всегда на photoScrim). |
| `skeletonBase` | `dsColorSkeletonBase` | `#E4DCCF` | `#262422` | Основа скелетона: заметно темнее фона, чтобы форма контента читалась. |
| `skeletonHighlight` | `dsColorSkeletonHighlight` | `#FCFAF6` | `#2F2D2A` | Блик шиммера. |
| `canvas` | `dsColorCanvas` | `#EFE9DF` | `#221F1D` | Фон холста конструктора (студийная «бумага»). |
| `mannequin` | `dsColorMannequin` | `#D8D2C9` | `#3A3733` | Заливка манекена. |
| `snapGuide` | `dsColorSnapGuide` | `rgba(47, 59, 214, 0.4)` | `rgba(163, 171, 255, 0.5)` | Подсветка зоны примагничивания на манекене. |
| `selection` | `dsColorSelection` | `#2F3BD6` | `#A3ABFF` | Рамка выбранного слоя / доводимой точки. |
| `cameraBackground` | `dsColorCameraBackground` | `#0B0B0A` | `#0B0B0A` | Фон камеры Студии — всегда тёмный, чтобы не засвечивать кадр. |
| `onCamera` | `dsColorOnCamera` | `#F6F2EB` | `#F6F2EB` | Контролы поверх камеры. |

### Аватары-инициалы

| Имя | Светлая | Тёмная |
|---|---|---|
| `sand` | `#E5D3BC` | `#5A4A38` |
| `sage` | `#CCD5C0` | `#3F4A3A` |
| `mist` | `#C9D3DC` | `#3A4652` |
| `blush` | `#E6CFCB` | `#5A3F3C` |
| `lavender` | `#D6D1E6` | `#45405A` |
| `ochre` | `#E4CE9C` | `#5A4A22` |
| `stone` | `#D8D2C9` | `#4A4540` |
| инициалы (`onAvatar`) | `#1C1B19` | `#F6F2EB` |

### Material 3 ↔ токены

| Атрибут M3 | Токен |
|---|---|
| `colorPrimary` | `primary` |
| `colorOnPrimary` | `onPrimary` |
| `colorPrimaryContainer` | `surfaceContainerHigh` |
| `colorOnPrimaryContainer` | `onSurface` |
| `colorPrimaryInverse` | `inverseAccent` |
| `colorSecondary` | `accent` |
| `colorOnSecondary` | `onAccent` |
| `colorSecondaryContainer` | `surfaceContainerHigh` |
| `colorOnSecondaryContainer` | `onSurface` |
| `colorTertiary` | `accent` |
| `colorOnTertiary` | `onAccent` |
| `colorTertiaryContainer` | `accentContainer` |
| `colorOnTertiaryContainer` | `onAccentContainer` |
| `colorError` | `danger` |
| `colorOnError` | `onDanger` |
| `colorErrorContainer` | `dangerContainer` |
| `colorOnErrorContainer` | `onDangerContainer` |
| `android:colorBackground` | `background` |
| `colorOnBackground` | `onBackground` |
| `colorSurface` | `background` |
| `colorOnSurface` | `onSurface` |
| `colorSurfaceVariant` | `surfaceSunken` |
| `colorOnSurfaceVariant` | `onSurfaceVariant` |
| `colorSurfaceInverse` | `inverseSurface` |
| `colorOnSurfaceInverse` | `inverseOnSurface` |
| `colorOutline` | `outline` |
| `colorOutlineVariant` | `outlineVariant` |
| `colorSurfaceBright` | `surface` |
| `colorSurfaceDim` | `surfaceSunken` |
| `colorSurfaceContainerLowest` | `surfaceContainerLowest` |
| `colorSurfaceContainerLow` | `surfaceContainerLow` |
| `colorSurfaceContainer` | `surfaceContainer` |
| `colorSurfaceContainerHigh` | `surfaceContainerHigh` |
| `colorSurfaceContainerHighest` | `surfaceContainerHighest` |
| `colorControlNormal` | `onSurfaceVariant` |
| `colorControlActivated` | `primary` |
| `android:textColorPrimary` | `onSurface` |
| `android:textColorSecondary` | `onSurfaceVariant` |
| `android:textColorHint` | `onSurfaceVariant` |
| `android:textColorLink` | `accent` |
| `android:colorAccent` | `accent` |

### Контраст (WCAG 2.2 AA)

Проверяется генератором при каждой сборке токенов. Текст — ≥ 4.5:1 (1.4.3), элементы интерфейса и крупные иконки — ≥ 3:1 (1.4.11).

| Передний план | Фон | Тип | Светлая | Тёмная |
|---|---|---|---|---|
| `onBackground` | `background` | текст ≥ 4.5 | 15.42 ✅ | 16.77 ✅ |
| `onSurface` | `background` | текст ≥ 4.5 | 15.42 ✅ | 16.77 ✅ |
| `onSurface` | `surface` | текст ≥ 4.5 | 16.51 ✅ | 15.42 ✅ |
| `onSurface` | `surfaceSunken` | текст ≥ 4.5 | 14.25 ✅ | 14.68 ✅ |
| `onSurface` | `surfaceContainer` | текст ≥ 4.5 | 14.64 ✅ | 13.86 ✅ |
| `onSurface` | `surfaceContainerHigh` | текст ≥ 4.5 | 13.50 ✅ | 12.30 ✅ |
| `onSurface` | `surfaceContainerHighest` | текст ≥ 4.5 | 12.65 ✅ | 10.61 ✅ |
| `onSurfaceVariant` | `background` | текст ≥ 4.5 | 5.30 ✅ | 8.56 ✅ |
| `onSurfaceVariant` | `surface` | текст ≥ 4.5 | 5.67 ✅ | 7.87 ✅ |
| `onSurfaceVariant` | `surfaceSunken` | текст ≥ 4.5 | 4.90 ✅ | 7.50 ✅ |
| `onSurfaceVariant` | `surfaceContainer` | текст ≥ 4.5 | 5.03 ✅ | 7.08 ✅ |
| `onSurfaceVariant` | `surfaceContainerHigh` | текст ≥ 4.5 | 4.64 ✅ | 6.28 ✅ |
| `onPrimary` | `primary` | текст ≥ 4.5 | 16.51 ✅ | 15.42 ✅ |
| `accent` | `background` | текст ≥ 4.5 | 6.93 ✅ | 8.76 ✅ |
| `accent` | `surface` | текст ≥ 4.5 | 7.41 ✅ | 8.05 ✅ |
| `onAccent` | `accent` | текст ≥ 4.5 | 7.73 ✅ | 7.96 ✅ |
| `onAccentContainer` | `accentContainer` | текст ≥ 4.5 | 11.11 ✅ | 9.59 ✅ |
| `danger` | `background` | текст ≥ 4.5 | 5.89 ✅ | 11.01 ✅ |
| `danger` | `surface` | текст ≥ 4.5 | 6.31 ✅ | 10.12 ✅ |
| `danger` | `surfaceSunken` | текст ≥ 4.5 | 5.44 ✅ | 9.64 ✅ |
| `onDanger` | `danger` | текст ≥ 4.5 | 6.31 ✅ | 9.72 ✅ |
| `onDangerContainer` | `dangerContainer` | текст ≥ 4.5 | 9.70 ✅ | 9.70 ✅ |
| `success` | `background` | текст ≥ 4.5 | 5.63 ✅ | 10.30 ✅ |
| `success` | `surface` | текст ≥ 4.5 | 6.02 ✅ | 9.47 ✅ |
| `success` | `surfaceSunken` | текст ≥ 4.5 | 5.20 ✅ | 9.02 ✅ |
| `onSuccessContainer` | `successContainer` | текст ≥ 4.5 | 10.84 ✅ | 10.34 ✅ |
| `warning` | `background` | текст ≥ 4.5 | 5.67 ✅ | 11.27 ✅ |
| `warning` | `surface` | текст ≥ 4.5 | 6.07 ✅ | 10.37 ✅ |
| `onWarningContainer` | `warningContainer` | текст ≥ 4.5 | 12.85 ✅ | 11.91 ✅ |
| `inverseOnSurface` | `inverseSurface` | текст ≥ 4.5 | 15.42 ✅ | 15.42 ✅ |
| `inverseAccent` | `inverseSurface` | текст ≥ 4.5 | 8.05 ✅ | 6.93 ✅ |
| `onCamera` | `cameraBackground` | текст ≥ 4.5 | 17.65 ✅ | 17.65 ✅ |
| `outline` | `background` | UI ≥ 3 | 3.68 ✅ | 4.88 ✅ |
| `outline` | `surface` | UI ≥ 3 | 3.94 ✅ | 4.49 ✅ |
| `outline` | `surfaceSunken` | UI ≥ 3 | 3.40 ✅ | 4.27 ✅ |
| `outline` | `surfaceContainerHigh` | UI ≥ 3 | 3.22 ✅ | 3.58 ✅ |
| `primary` | `background` | UI ≥ 3 | 15.42 ✅ | 16.77 ✅ |
| `accent` | `surfaceSunken` | UI ≥ 3 | 6.40 ✅ | 7.67 ✅ |
| `like` | `background` | UI ≥ 3 | 6.93 ✅ | 8.76 ✅ |
| `like` | `surface` | UI ≥ 3 | 7.41 ✅ | 8.05 ✅ |
| `focusRing` | `background` | UI ≥ 3 | 6.93 ✅ | 8.76 ✅ |
| `selection` | `canvas` | UI ≥ 3 | 6.40 ✅ | 7.67 ✅ |
| `success` | `cameraBackground` | UI ≥ 3 | 3.14 ✅ | 10.84 ✅ |
| `warning` | `cameraBackground` | UI ≥ 3 | 3.11 ✅ | 11.86 ✅ |
| `onAvatar` | `avatar/sand` | текст ≥ 4.5 | 11.78 ✅ | 7.61 ✅ |
| `onAvatar` | `avatar/sage` | текст ≥ 4.5 | 11.35 ✅ | 8.36 ✅ |
| `onAvatar` | `avatar/mist` | текст ≥ 4.5 | 11.34 ✅ | 8.64 ✅ |
| `onAvatar` | `avatar/blush` | текст ≥ 4.5 | 11.60 ✅ | 8.51 ✅ |
| `onAvatar` | `avatar/lavender` | текст ≥ 4.5 | 11.57 ✅ | 8.82 ✅ |
| `onAvatar` | `avatar/ochre` | текст ≥ 4.5 | 11.15 ✅ | 7.72 ✅ |
| `onAvatar` | `avatar/stone` | текст ≥ 4.5 | 11.46 ✅ | 8.49 ✅ |

## Типографика

| Роль | Семейство | Источник | Лицензия |
|---|---|---|---|
| `display` | OutfitShare Display | Playfair Display | OFL-1.1 (Reserved Font Name «Playfair Display» → производная переименована) |
| `text` | Inter | Inter | OFL-1.1 |

Размеры в sp. Колонка «200 %» — фактический размер в dp при максимальном масштабе шрифта Android 14+ (нелинейная шкала: крупный текст растёт меньше мелкого).

| Стиль | Android | Шрифт | Кегль/интерлиньяж | Трекинг | 200 % | Применение |
|---|---|---|---|---|---|---|
| `displayLarge` | `TextAppearance.Ds.DisplayLarge` | OutfitShare Display 400 | 48/52 | -0.01 em | 54 dp | Сплэш, онбординг, обложки коллекций. |
| `displayMedium` | `TextAppearance.Ds.DisplayMedium` | OutfitShare Display 400 | 40/44 | -0.01 em | 47 dp | Заголовок экрана в hero-зоне (профиль, Студия). |
| `displaySmall` | `TextAppearance.Ds.DisplaySmall` | OutfitShare Display 400 | 32/38 | -0.005 em | 40 dp | Большой заголовок экрана (large top app bar). |
| `headlineLarge` | `TextAppearance.Ds.HeadlineLarge` | OutfitShare Display 400 | 28/34 | 0 em | 37 dp | Заголовок раздела, пустые состояния. |
| `headlineMedium` | `TextAppearance.Ds.HeadlineMedium` | OutfitShare Display 500 | 24/30 | 0 em | 36 dp | Заголовок диалога, bottom sheet. |
| `headlineSmall` | `TextAppearance.Ds.HeadlineSmall` | OutfitShare Display 500 | 20/26 | 0 em | 34 dp | Имя в профиле, заголовок карточки коллекции. |
| `editorialQuote` | `TextAppearance.Ds.EditorialQuote` | OutfitShare Display 400 italic | 20/28 | 0 em | 34 dp | Курсивная подпись к образу, цитата. |
| `numeral` | `TextAppearance.Ds.Numeral` | OutfitShare Display 500 | 22/26 | 0 em | 35 dp | Счётчики профиля (образы, подписчики). |
| `titleLarge` | `TextAppearance.Ds.TitleLarge` | Inter 600 | 18/24 | 0 em | 30 dp | Заголовок top app bar, заголовок строки. |
| `titleMedium` | `TextAppearance.Ds.TitleMedium` | Inter 600 | 16/22 | 0.005 em | 28 dp | Имя автора, название вещи. |
| `titleSmall` | `TextAppearance.Ds.TitleSmall` | Inter 500 | 14/20 | 0.01 em | 26 dp | Подзаголовки списков. |
| `bodyLarge` | `TextAppearance.Ds.BodyLarge` | Inter 400 | 16/24 | 0 em | 28 dp | Основной текст, сообщения чата, поля ввода. |
| `bodyMedium` | `TextAppearance.Ds.BodyMedium` | Inter 400 | 14/20 | 0.005 em | 26 dp | Комментарии, описания, строки списков. |
| `bodySmall` | `TextAppearance.Ds.BodySmall` | Inter 400 | 12/16 | 0.01 em | 24 dp | Мета: время, счётчики, подсказки. |
| `labelLarge` | `TextAppearance.Ds.LabelLarge` | Inter 500 | 14/20 | 0.01 em | 26 dp | Кнопки, табы. |
| `labelMedium` | `TextAppearance.Ds.LabelMedium` | Inter 500 | 12/16 | 0.02 em | 24 dp | Чипы, бейджи-текст, подписи иконок навбара. |
| `labelSmall` | `TextAppearance.Ds.LabelSmall` | Inter 600, ВЕРХНИЙ РЕГИСТР | 11/16 | 0.08 em | 22 dp | Кикер/надзаголовок в журнальном стиле: «НОВОЕ», «СТУДИЯ». |

## Отступы и раскладка

Сетка 4/8 dp. `@dimen/ds_space_N`, `var(--ds-space-N)`.

| Токен | dp |
|---|---|
| `space.0` → `ds_space_0` | 0 |
| `space.0.5` → `ds_space_0_5` | 2 |
| `space.1` → `ds_space_1` | 4 |
| `space.2` → `ds_space_2` | 8 |
| `space.3` → `ds_space_3` | 12 |
| `space.4` → `ds_space_4` | 16 |
| `space.5` → `ds_space_5` | 20 |
| `space.6` → `ds_space_6` | 24 |
| `space.8` → `ds_space_8` | 32 |
| `space.10` → `ds_space_10` | 40 |
| `space.12` → `ds_space_12` | 48 |
| `space.16` → `ds_space_16` | 64 |
| `space.20` → `ds_space_20` | 80 |
| `space.24` → `ds_space_24` | 96 |

| Раскладка | dp | Назначение |
|---|---|---|
| `ds_layout_gutter` | 20 | Боковые поля экрана (compact). |
| `ds_layout_gutter_medium` | 32 | Боковые поля на планшете (medium/expanded). |
| `ds_layout_section_gap` | 40 | Вертикальный воздух между журнальными секциями. |
| `ds_layout_stack_gap` | 16 | Зазор между блоками в стеке. |
| `ds_layout_inline_gap` | 8 | Зазор между элементами в строке. |
| `ds_layout_grid_gap` | 12 | Зазор сетки карточек. |
| `ds_layout_list_item_padding_vertical` | 12 | Вертикальный паддинг строки списка. |
| `ds_layout_max_content_width` | 640 | Максимальная ширина читаемой колонки на планшете. |
| `ds_layout_max_dialog_width` | 560 | Максимальная ширина диалога/снекбара. |

| Класс окна | Ширина, dp | Колонки ленты | Колонки сетки |
|---|---|---|---|
| compact | ≥ 0 | 1 | 2 |
| medium | ≥ 600 | 2 | 3 |
| expanded | ≥ 840 | 3 | 4 |

## Размеры

| Токен | dp | Примечание |
|---|---|---|
| `ds_size_touch_target` | 48 | Минимальная зона касания (WCAG 2.5.8 / Material). |
| `ds_size_icon_small` | 16 |  |
| `ds_size_icon_medium` | 20 |  |
| `ds_size_icon` | 24 |  |
| `ds_size_icon_large` | 32 |  |
| `ds_size_avatar_xs` | 24 |  |
| `ds_size_avatar_s` | 32 |  |
| `ds_size_avatar_m` | 40 |  |
| `ds_size_avatar_l` | 56 |  |
| `ds_size_avatar_xl` | 88 |  |
| `ds_size_avatar_xxl` | 120 |  |
| `ds_size_button_height_small` | 40 | Визуальная высота; зона касания добирается инсетами space.1 до 48. |
| `ds_size_button_height` | 48 |  |
| `ds_size_button_height_large` | 56 |  |
| `ds_size_icon_button` | 48 |  |
| `ds_size_chip_height` | 36 |  |
| `ds_size_input_height` | 56 |  |
| `ds_size_app_bar_height` | 56 |  |
| `ds_size_app_bar_large_height` | 120 |  |
| `ds_size_bottom_nav_height` | 72 |  |
| `ds_size_tab_height` | 48 |  |
| `ds_size_list_item_min_height` | 56 |  |
| `ds_size_list_item_two_line_min_height` | 72 |  |
| `ds_size_thumbnail_small` | 48 |  |
| `ds_size_thumbnail` | 64 |  |
| `ds_size_illustration` | 160 |  |
| `ds_size_sheet_handle_width` | 32 |  |
| `ds_size_sheet_handle_height` | 4 |  |
| `ds_size_badge_dot` | 8 |  |
| `ds_size_badge_height` | 20 |  |
| `ds_size_stroke_hairline` | 1 |  |
| `ds_size_stroke_thin` | 1.5 |  |
| `ds_size_stroke_thick` | 2 |  |
| `ds_size_focus_ring_width` | 2 |  |
| `ds_size_keypoint_handle` | 28 | Визуальный маркер опорной точки (зона касания 48). |
| `ds_size_shutter_button` | 72 |  |
| `ds_size_heart_burst` | 96 | Сердце, всплывающее по двойному тапу на фото. |
| `ds_size_progress_track` | 4 |  |

Пропорции медиа: outfit — 4:5, item — 1:1, collectionCover — 3:4, onboardingHero — 3:4, studioCapture — 3:4.

## Радиусы и формы

| Радиус | dp |
|---|---|
| `ds_radius_none` | 0 |
| `ds_radius_xs` | 2 |
| `ds_radius_s` | 6 |
| `ds_radius_m` | 12 |
| `ds_radius_l` | 20 |
| `ds_radius_xl` | 28 |
| `ds_radius_pill` | 999 |

| Форма | Радиус |
|---|---|
| `ds_shape_photo` | `xs` |
| `ds_shape_card` | `xs` |
| `ds_shape_item_tile` | `s` |
| `ds_shape_button` | `pill` |
| `ds_shape_chip` | `pill` |
| `ds_shape_input` | `s` |
| `ds_shape_badge` | `pill` |
| `ds_shape_snackbar` | `s` |
| `ds_shape_dialog` | `m` |
| `ds_shape_sheet` | `l` |
| `ds_shape_bubble` | `l` |
| `ds_shape_bubble_tail` | `s` |
| `ds_shape_avatar` | `pill` |
| `ds_shape_thumbnail` | `s` |

## Elevation

Журнальная эстетика — плоско. Тени тёплые, только у плавающих слоёв. В тёмной теме глубина передаётся тоном поверхности (surfaceContainer*), тень ослаблена.

| Уровень | dp | CSS-тень (светлая) |
|---|---|---|
| `level0` | 0 | `none` |
| `level1` | 1 | `0 1px 2px rgba(28,27,25,.08), 0 1px 3px rgba(28,27,25,.06)` |
| `level2` | 3 | `0 2px 6px rgba(28,27,25,.08), 0 1px 2px rgba(28,27,25,.06)` |
| `level3` | 6 | `0 6px 16px rgba(28,27,25,.10), 0 2px 4px rgba(28,27,25,.06)` |
| `level4` | 8 | `0 10px 24px rgba(28,27,25,.12), 0 3px 6px rgba(28,27,25,.06)` |
| `level5` | 12 | `0 16px 40px rgba(28,27,25,.16), 0 4px 10px rgba(28,27,25,.06)` |

| Элемент | Уровень |
|---|---|
| `card` | `level0` |
| `appBarScrolled` | `level1` |
| `draggedLayer` | `level4` |
| `fab` | `level3` |
| `snackbar` | `level3` |
| `sheet` | `level4` |
| `dialog` | `level5` |
| `menu` | `level3` |

## Прозрачности

| Токен | Значение |
|---|---|
| `ds_opacity_disabled_content` | 0.38 |
| `ds_opacity_disabled_container` | 0.12 |
| `ds_opacity_hover_overlay` | 0.08 |
| `ds_opacity_focus_overlay` | 0.12 |
| `ds_opacity_pressed_overlay` | 0.12 |
| `ds_opacity_dragged_overlay` | 0.16 |
| `ds_opacity_ghost_layer` | 0.6 |

## Движение

мс. Все анимации масштабируются системной настройкой «Масштаб анимации» и выключаются при «Убрать анимации».

| Длительность | мс |
|---|---|
| `ds_motion_duration_micro` | 100 |
| `ds_motion_duration_short` | 150 |
| `ds_motion_duration_medium` | 250 |
| `ds_motion_duration_long` | 350 |
| `ds_motion_duration_extra_long` | 500 |
| `ds_motion_duration_shimmer` | 1400 |
| `ds_motion_duration_heart_burst` | 700 |
| `ds_motion_duration_stagger` | 30 |
| `ds_motion_duration_undo_window` | 5000 |
| `ds_motion_duration_snackbar` | 4000 |
| `ds_motion_duration_splash_min` | 600 |

| Кривая | cubic-bezier |
|---|---|
| `ds_easing_standard` | (0.2, 0, 0, 1) |
| `ds_easing_standard_decelerate` | (0, 0, 0, 1) |
| `ds_easing_standard_accelerate` | (0.3, 0, 1, 1) |
| `ds_easing_emphasized_decelerate` | (0.05, 0.7, 0.1, 1) |
| `ds_easing_emphasized_accelerate` | (0.3, 0, 0.8, 0.15) |
| `ds_easing_linear` | (0, 0, 1, 1) |

| Пружина | stiffness | dampingRatio | Где |
|---|---|---|---|
| `drop` | 450 | 0.55 | Вещь «падает» на манекен и садится с лёгким отскоком. |
| `snap` | 800 | 0.7 | Слой примагничивается к опорным точкам. |
| `heart` | 1200 | 0.4 | Пружинка сердца при лайке. |
| `press` | 1500 | 1 | Сжатие карточки/кнопки при нажатии. |
| `sheet` | 600 | 0.9 | Доводка bottom sheet и перетаскиваемых панелей. |

| Параметр | Значение |
|---|---|
| `enterOffset` | 24 dp |
| `dropOffset` | 56 dp |
| `pressScale` | 0.97 |
| `heartPeakScale` | 1.25 |
| `heartBurstScale` | 1.6 |
| `dropStartScale` | 1.04 |

## Хаптика

Событие → HapticFeedbackConstants с цепочкой фолбэков по API. Вибрация только на ключевых действиях.

| Событие | Цепочка `HapticFeedbackConstants` (константа@minApi) |
|---|---|
| `snap` | `SEGMENT_TICK@34` → `CLOCK_TICK@21` |
| `like` | `CONFIRM@30` → `VIRTUAL_KEY@21` |
| `publish` | `CONFIRM@30` → `LONG_PRESS@21` |
| `shutter` | `CONFIRM@30` → `VIRTUAL_KEY@21` |
| `reject` | `REJECT@30` → `LONG_PRESS@21` |
| `dragStart` | `DRAG_START@34` → `GESTURE_START@30` → `LONG_PRESS@21` |
| `toggleOn` | `TOGGLE_ON@34` → `CLOCK_TICK@21` |
| `toggleOff` | `TOGGLE_OFF@34` → `CLOCK_TICK@21` |
