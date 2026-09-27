/* СГЕНЕРИРОВАНО tools/designsystem/generate_tokens.py из docs/design/tokens/tokens.json. Не редактировать вручную: правьте tokens.json и перезапустите генератор. */
window.DS_TOKENS = {
 "meta": {
  "name": "Outfit Share Design System",
  "codename": "Atelier",
  "version": "1.0.0",
  "androidPrefix": "ds",
  "grid": 4
 },
 "colors": {
  "background": {
   "light": "#F6F2EB",
   "dark": "#131211",
   "description": "Фон экрана. Молочный днём, почти чёрный графит ночью."
  },
  "onBackground": {
   "light": "#1C1B19",
   "dark": "#F6F2EB",
   "description": "Основной текст и иконки на фоне."
  },
  "surface": {
   "light": "#FCFAF6",
   "dark": "#1C1B19",
   "description": "Поднятые поверхности: bottom sheet, диалоги, меню."
  },
  "onSurface": {
   "light": "#1C1B19",
   "dark": "#F6F2EB",
   "description": "Основной текст на поверхностях."
  },
  "onSurfaceVariant": {
   "light": "#6B6358",
   "dark": "#B8AEA1",
   "description": "Вторичный текст: подписи, мета, плейсхолдеры, неактивные иконки."
  },
  "surfaceSunken": {
   "light": "#EFE9DF",
   "dark": "#221F1D",
   "description": "Утопленные зоны: плейсхолдер фото, поля ввода, плитка вещи."
  },
  "surfaceContainerLowest": {
   "light": "#FCFAF6",
   "dark": "#131211",
   "description": "M3 surfaceContainerLowest."
  },
  "surfaceContainerLow": {
   "light": "#F6F2EB",
   "dark": "#1C1B19",
   "description": "M3 surfaceContainerLow."
  },
  "surfaceContainer": {
   "light": "#F1ECE4",
   "dark": "#262422",
   "description": "M3 surfaceContainer: навбар, карточки настроек."
  },
  "surfaceContainerHigh": {
   "light": "#EAE3D8",
   "dark": "#2F2D2A",
   "description": "M3 surfaceContainerHigh: выбранный элемент навигации, тональные зоны."
  },
  "surfaceContainerHighest": {
   "light": "#E4DCCF",
   "dark": "#3A3733",
   "description": "M3 surfaceContainerHighest: трек слайдера, разделители-плашки."
  },
  "outline": {
   "light": "#857C70",
   "dark": "#8A8175",
   "description": "Контур значимых элементов (поля, secondary-кнопка, чип). Контраст ≥ 3:1."
  },
  "outlineVariant": {
   "light": "#E4DCCF",
   "dark": "#3A3733",
   "description": "Декоративные разделители. Контраст не нормируется."
  },
  "primary": {
   "light": "#1C1B19",
   "dark": "#F6F2EB",
   "description": "Главное действие (primary-кнопка). Графит днём, молочный ночью — монохром как в журнале."
  },
  "onPrimary": {
   "light": "#FCFAF6",
   "dark": "#1C1B19",
   "description": "Текст/иконка на primary."
  },
  "accent": {
   "light": "#2F3BD6",
   "dark": "#A3ABFF",
   "description": "Фирменный ультрамарин. Только «моменты»: лайк, непрочитанное, ссылки, фокус, snap-подсветка, прогресс."
  },
  "onAccent": {
   "light": "#FFFFFF",
   "dark": "#10144F",
   "description": "Контент на accent."
  },
  "accentContainer": {
   "light": "#E6E8FF",
   "dark": "#262C8A",
   "description": "Мягкая подложка акцента: выбранный фильтр-чип «Новое», инфо-баннер."
  },
  "onAccentContainer": {
   "light": "#1A2080",
   "dark": "#E6E8FF",
   "description": "Контент на accentContainer."
  },
  "danger": {
   "light": "#B42318",
   "dark": "#FFB4A9",
   "description": "Ошибки и разрушающие действия (удалить, выйти, заблокировать)."
  },
  "onDanger": {
   "light": "#FCFAF6",
   "dark": "#410A05",
   "description": "Контент на danger."
  },
  "dangerContainer": {
   "light": "#FBE3DF",
   "dark": "#6E140D",
   "description": "Подложка ошибки (баннер, поле с ошибкой)."
  },
  "onDangerContainer": {
   "light": "#6E140D",
   "dark": "#FBE3DF",
   "description": "Контент на dangerContainer."
  },
  "success": {
   "light": "#1A6E3E",
   "dark": "#86D0A2",
   "description": "Успех: вещь опубликована, качество кадра ок, онлайн."
  },
  "successContainer": {
   "light": "#DDF1E4",
   "dark": "#123D25",
   "description": "Подложка успеха."
  },
  "onSuccessContainer": {
   "light": "#0B3A20",
   "dark": "#DDF1E4",
   "description": "Контент на successContainer."
  },
  "warning": {
   "light": "#8A5300",
   "dark": "#F1C267",
   "description": "Предупреждение: слабый свет, низкий fit score, офлайн."
  },
  "warningContainer": {
   "light": "#FBEFD5",
   "dark": "#3F2A06",
   "description": "Подложка предупреждения (офлайн-баннер)."
  },
  "onWarningContainer": {
   "light": "#3A2400",
   "dark": "#FBEFD5",
   "description": "Контент на warningContainer."
  },
  "inverseSurface": {
   "light": "#1C1B19",
   "dark": "#F6F2EB",
   "description": "Снекбар, тултип."
  },
  "inverseOnSurface": {
   "light": "#F6F2EB",
   "dark": "#1C1B19",
   "description": "Текст на inverseSurface."
  },
  "inverseAccent": {
   "light": "#A3ABFF",
   "dark": "#2F3BD6",
   "description": "Действие в снекбаре («Отменить»)."
  },
  "like": {
   "light": "#2F3BD6",
   "dark": "#A3ABFF",
   "description": "Заливка сердца. Совпадает с accent: лайк — главный эмоциональный момент бренда."
  },
  "focusRing": {
   "light": "#2F3BD6",
   "dark": "#A3ABFF",
   "description": "Кольцо клавиатурного/switch-фокуса."
  },
  "scrim": {
   "light": "rgba(28, 27, 25, 0.48)",
   "dark": "rgba(0, 0, 0, 0.64)",
   "description": "Затемнение под bottom sheet и диалогами."
  },
  "photoScrim": {
   "light": "rgba(0, 0, 0, 0.55)",
   "dark": "rgba(0, 0, 0, 0.6)",
   "description": "Нижний градиент на фото для подписи поверх изображения."
  },
  "onPhoto": {
   "light": "#FFFFFF",
   "dark": "#FFFFFF",
   "description": "Текст/иконки поверх фото (всегда на photoScrim)."
  },
  "skeletonBase": {
   "light": "#E4DCCF",
   "dark": "#262422",
   "description": "Основа скелетона: заметно темнее фона, чтобы форма контента читалась."
  },
  "skeletonHighlight": {
   "light": "#FCFAF6",
   "dark": "#2F2D2A",
   "description": "Блик шиммера."
  },
  "canvas": {
   "light": "#EFE9DF",
   "dark": "#221F1D",
   "description": "Фон холста конструктора (студийная «бумага»)."
  },
  "mannequin": {
   "light": "#D8D2C9",
   "dark": "#3A3733",
   "description": "Заливка манекена."
  },
  "snapGuide": {
   "light": "rgba(47, 59, 214, 0.4)",
   "dark": "rgba(163, 171, 255, 0.5)",
   "description": "Подсветка зоны примагничивания на манекене."
  },
  "selection": {
   "light": "#2F3BD6",
   "dark": "#A3ABFF",
   "description": "Рамка выбранного слоя / доводимой точки."
  },
  "cameraBackground": {
   "light": "#0B0B0A",
   "dark": "#0B0B0A",
   "description": "Фон камеры Студии — всегда тёмный, чтобы не засвечивать кадр."
  },
  "onCamera": {
   "light": "#F6F2EB",
   "dark": "#F6F2EB",
   "description": "Контролы поверх камеры."
  }
 },
 "avatarPalette": [
  {
   "name": "sand",
   "light": "#E5D3BC",
   "dark": "#5A4A38"
  },
  {
   "name": "sage",
   "light": "#CCD5C0",
   "dark": "#3F4A3A"
  },
  {
   "name": "mist",
   "light": "#C9D3DC",
   "dark": "#3A4652"
  },
  {
   "name": "blush",
   "light": "#E6CFCB",
   "dark": "#5A3F3C"
  },
  {
   "name": "lavender",
   "light": "#D6D1E6",
   "dark": "#45405A"
  },
  {
   "name": "ochre",
   "light": "#E4CE9C",
   "dark": "#5A4A22"
  },
  {
   "name": "stone",
   "light": "#D8D2C9",
   "dark": "#4A4540"
  }
 ],
 "onAvatar": {
  "light": "#1C1B19",
  "dark": "#F6F2EB"
 },
 "primitives": {
  "milk.50": "#FCFAF6",
  "milk.100": "#F6F2EB",
  "milk.150": "#F1ECE4",
  "milk.200": "#EFE9DF",
  "milk.250": "#EAE3D8",
  "milk.300": "#E4DCCF",
  "stone.300": "#D8D2C9",
  "stone.400": "#B8AEA1",
  "stone.500": "#857C70",
  "stone.550": "#8A8175",
  "stone.600": "#6B6358",
  "stone.700": "#544D45",
  "graphite.600": "#45423D",
  "graphite.700": "#3A3733",
  "graphite.750": "#2F2D2A",
  "graphite.800": "#262422",
  "graphite.850": "#221F1D",
  "graphite.900": "#1C1B19",
  "graphite.950": "#131211",
  "graphite.1000": "#0B0B0A",
  "ultramarine.100": "#E6E8FF",
  "ultramarine.200": "#C9CEFF",
  "ultramarine.300": "#A3ABFF",
  "ultramarine.400": "#7C87FF",
  "ultramarine.500": "#4C57F0",
  "ultramarine.600": "#2F3BD6",
  "ultramarine.700": "#232CA8",
  "ultramarine.800": "#1A2080",
  "ultramarine.850": "#262C8A",
  "ultramarine.900": "#10144F",
  "red.100": "#FBE3DF",
  "red.300": "#FFB4A9",
  "red.600": "#B42318",
  "red.800": "#6E140D",
  "red.900": "#410A05",
  "green.100": "#DDF1E4",
  "green.300": "#86D0A2",
  "green.600": "#1A6E3E",
  "green.800": "#123D25",
  "green.900": "#0B3A20",
  "amber.100": "#FBEFD5",
  "amber.300": "#F1C267",
  "amber.700": "#8A5300",
  "amber.800": "#3F2A06",
  "amber.900": "#3A2400",
  "white": "#FFFFFF",
  "black": "#000000"
 },
 "typography": {
  "fontFamily": {
   "display": {
    "name": "OutfitShare Display",
    "upstream": "Playfair Display",
    "license": "OFL-1.1 (Reserved Font Name «Playfair Display» → производная переименована)",
    "android": "@font/ds_display",
    "css": "'OutfitShare Display', Georgia, 'Times New Roman', serif",
    "weights": [
     400,
     500
    ],
    "italic": [
     400
    ]
   },
   "text": {
    "name": "Inter",
    "upstream": "Inter",
    "license": "OFL-1.1",
    "android": "@font/ds_text",
    "css": "'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
    "weights": [
     400,
     500,
     600
    ],
    "italic": []
   }
  },
  "scale": {
   "displayLarge": {
    "family": "display",
    "weight": 400,
    "size": 48,
    "lineHeight": 52,
    "letterSpacing": -0.01,
    "$description": "Сплэш, онбординг, обложки коллекций."
   },
   "displayMedium": {
    "family": "display",
    "weight": 400,
    "size": 40,
    "lineHeight": 44,
    "letterSpacing": -0.01,
    "$description": "Заголовок экрана в hero-зоне (профиль, Студия)."
   },
   "displaySmall": {
    "family": "display",
    "weight": 400,
    "size": 32,
    "lineHeight": 38,
    "letterSpacing": -0.005,
    "$description": "Большой заголовок экрана (large top app bar)."
   },
   "headlineLarge": {
    "family": "display",
    "weight": 400,
    "size": 28,
    "lineHeight": 34,
    "letterSpacing": 0,
    "$description": "Заголовок раздела, пустые состояния."
   },
   "headlineMedium": {
    "family": "display",
    "weight": 500,
    "size": 24,
    "lineHeight": 30,
    "letterSpacing": 0,
    "$description": "Заголовок диалога, bottom sheet."
   },
   "headlineSmall": {
    "family": "display",
    "weight": 500,
    "size": 20,
    "lineHeight": 26,
    "letterSpacing": 0,
    "$description": "Имя в профиле, заголовок карточки коллекции."
   },
   "editorialQuote": {
    "family": "display",
    "weight": 400,
    "italic": true,
    "size": 20,
    "lineHeight": 28,
    "letterSpacing": 0,
    "$description": "Курсивная подпись к образу, цитата."
   },
   "numeral": {
    "family": "display",
    "weight": 500,
    "size": 22,
    "lineHeight": 26,
    "letterSpacing": 0,
    "$description": "Счётчики профиля (образы, подписчики)."
   },
   "titleLarge": {
    "family": "text",
    "weight": 600,
    "size": 18,
    "lineHeight": 24,
    "letterSpacing": 0,
    "$description": "Заголовок top app bar, заголовок строки."
   },
   "titleMedium": {
    "family": "text",
    "weight": 600,
    "size": 16,
    "lineHeight": 22,
    "letterSpacing": 0.005,
    "$description": "Имя автора, название вещи."
   },
   "titleSmall": {
    "family": "text",
    "weight": 500,
    "size": 14,
    "lineHeight": 20,
    "letterSpacing": 0.01,
    "$description": "Подзаголовки списков."
   },
   "bodyLarge": {
    "family": "text",
    "weight": 400,
    "size": 16,
    "lineHeight": 24,
    "letterSpacing": 0,
    "$description": "Основной текст, сообщения чата, поля ввода."
   },
   "bodyMedium": {
    "family": "text",
    "weight": 400,
    "size": 14,
    "lineHeight": 20,
    "letterSpacing": 0.005,
    "$description": "Комментарии, описания, строки списков."
   },
   "bodySmall": {
    "family": "text",
    "weight": 400,
    "size": 12,
    "lineHeight": 16,
    "letterSpacing": 0.01,
    "$description": "Мета: время, счётчики, подсказки."
   },
   "labelLarge": {
    "family": "text",
    "weight": 500,
    "size": 14,
    "lineHeight": 20,
    "letterSpacing": 0.01,
    "$description": "Кнопки, табы."
   },
   "labelMedium": {
    "family": "text",
    "weight": 500,
    "size": 12,
    "lineHeight": 16,
    "letterSpacing": 0.02,
    "$description": "Чипы, бейджи-текст, подписи иконок навбара."
   },
   "labelSmall": {
    "family": "text",
    "weight": 600,
    "size": 11,
    "lineHeight": 16,
    "letterSpacing": 0.08,
    "textTransform": "uppercase",
    "$description": "Кикер/надзаголовок в журнальном стиле: «НОВОЕ», «СТУДИЯ»."
   }
  }
 },
 "spacing": {
  "0": 0,
  "0_5": 2,
  "1": 4,
  "2": 8,
  "3": 12,
  "4": 16,
  "5": 20,
  "6": 24,
  "8": 32,
  "10": 40,
  "12": 48,
  "16": 64,
  "20": 80,
  "24": 96
 },
 "radius": {
  "none": 0,
  "xs": 2,
  "s": 6,
  "m": 12,
  "l": 20,
  "xl": 28,
  "pill": 999
 },
 "size": {
  "touchTarget": 48,
  "iconSmall": 16,
  "iconMedium": 20,
  "icon": 24,
  "iconLarge": 32,
  "avatarXs": 24,
  "avatarS": 32,
  "avatarM": 40,
  "avatarL": 56,
  "avatarXl": 88,
  "avatarXxl": 120,
  "buttonHeightSmall": 40,
  "buttonHeight": 48,
  "buttonHeightLarge": 56,
  "iconButton": 48,
  "chipHeight": 36,
  "inputHeight": 56,
  "appBarHeight": 56,
  "appBarLargeHeight": 120,
  "bottomNavHeight": 72,
  "tabHeight": 48,
  "listItemMinHeight": 56,
  "listItemTwoLineMinHeight": 72,
  "thumbnailSmall": 48,
  "thumbnail": 64,
  "illustration": 160,
  "sheetHandleWidth": 32,
  "sheetHandleHeight": 4,
  "badgeDot": 8,
  "badgeHeight": 20,
  "strokeHairline": 1,
  "strokeThin": 1.5,
  "strokeThick": 2,
  "focusRingWidth": 2,
  "keypointHandle": 28,
  "shutterButton": 72,
  "heartBurst": 96,
  "progressTrack": 4
 },
 "elevation": {
  "level0": {
   "dp": 0,
   "css": "none"
  },
  "level1": {
   "dp": 1,
   "css": "0 1px 2px rgba(28,27,25,.08), 0 1px 3px rgba(28,27,25,.06)"
  },
  "level2": {
   "dp": 3,
   "css": "0 2px 6px rgba(28,27,25,.08), 0 1px 2px rgba(28,27,25,.06)"
  },
  "level3": {
   "dp": 6,
   "css": "0 6px 16px rgba(28,27,25,.10), 0 2px 4px rgba(28,27,25,.06)"
  },
  "level4": {
   "dp": 8,
   "css": "0 10px 24px rgba(28,27,25,.12), 0 3px 6px rgba(28,27,25,.06)"
  },
  "level5": {
   "dp": 12,
   "css": "0 16px 40px rgba(28,27,25,.16), 0 4px 10px rgba(28,27,25,.06)"
  }
 },
 "motion": {
  "duration": {
   "$description": "мс. Все анимации масштабируются системной настройкой «Масштаб анимации» и выключаются при «Убрать анимации».",
   "micro": 100,
   "short": 150,
   "medium": 250,
   "long": 350,
   "extraLong": 500,
   "shimmer": 1400,
   "heartBurst": 700,
   "stagger": 30,
   "undoWindow": 5000,
   "snackbar": 4000,
   "splashMin": 600
  },
  "easing": {
   "standard": [
    0.2,
    0.0,
    0.0,
    1.0
   ],
   "standardDecelerate": [
    0.0,
    0.0,
    0.0,
    1.0
   ],
   "standardAccelerate": [
    0.3,
    0.0,
    1.0,
    1.0
   ],
   "emphasizedDecelerate": [
    0.05,
    0.7,
    0.1,
    1.0
   ],
   "emphasizedAccelerate": [
    0.3,
    0.0,
    0.8,
    0.15
   ],
   "linear": [
    0.0,
    0.0,
    1.0,
    1.0
   ]
  },
  "spring": {
   "$description": "Параметры androidx.dynamicanimation.SpringForce. stiffness — жёсткость, dampingRatio < 1 — отскок.",
   "drop": {
    "stiffness": 450,
    "dampingRatio": 0.55,
    "$description": "Вещь «падает» на манекен и садится с лёгким отскоком."
   },
   "snap": {
    "stiffness": 800,
    "dampingRatio": 0.7,
    "$description": "Слой примагничивается к опорным точкам."
   },
   "heart": {
    "stiffness": 1200,
    "dampingRatio": 0.4,
    "$description": "Пружинка сердца при лайке."
   },
   "press": {
    "stiffness": 1500,
    "dampingRatio": 1.0,
    "$description": "Сжатие карточки/кнопки при нажатии."
   },
   "sheet": {
    "stiffness": 600,
    "dampingRatio": 0.9,
    "$description": "Доводка bottom sheet и перетаскиваемых панелей."
   }
  },
  "distance": {
   "enterOffset": 24,
   "dropOffset": 56,
   "pressScale": 0.97,
   "heartPeakScale": 1.25,
   "heartBurstScale": 1.6,
   "dropStartScale": 1.04
  }
 },
 "haptics": {
  "snap": [
   "SEGMENT_TICK@34",
   "CLOCK_TICK@21"
  ],
  "like": [
   "CONFIRM@30",
   "VIRTUAL_KEY@21"
  ],
  "publish": [
   "CONFIRM@30",
   "LONG_PRESS@21"
  ],
  "shutter": [
   "CONFIRM@30",
   "VIRTUAL_KEY@21"
  ],
  "reject": [
   "REJECT@30",
   "LONG_PRESS@21"
  ],
  "dragStart": [
   "DRAG_START@34",
   "GESTURE_START@30",
   "LONG_PRESS@21"
  ],
  "toggleOn": [
   "TOGGLE_ON@34",
   "CLOCK_TICK@21"
  ],
  "toggleOff": [
   "TOGGLE_OFF@34",
   "CLOCK_TICK@21"
  ]
 },
 "contrast": [
  {
   "kind": "text",
   "fg": "onBackground",
   "bg": "background",
   "theme": "light",
   "ratio": 15.42,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onBackground",
   "bg": "background",
   "theme": "dark",
   "ratio": 16.77,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "background",
   "theme": "light",
   "ratio": 15.42,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "background",
   "theme": "dark",
   "ratio": 16.77,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surface",
   "theme": "light",
   "ratio": 16.51,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surface",
   "theme": "dark",
   "ratio": 15.42,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceSunken",
   "theme": "light",
   "ratio": 14.25,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceSunken",
   "theme": "dark",
   "ratio": 14.68,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceContainer",
   "theme": "light",
   "ratio": 14.64,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceContainer",
   "theme": "dark",
   "ratio": 13.86,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceContainerHigh",
   "theme": "light",
   "ratio": 13.5,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceContainerHigh",
   "theme": "dark",
   "ratio": 12.3,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceContainerHighest",
   "theme": "light",
   "ratio": 12.65,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurface",
   "bg": "surfaceContainerHighest",
   "theme": "dark",
   "ratio": 10.61,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "background",
   "theme": "light",
   "ratio": 5.3,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "background",
   "theme": "dark",
   "ratio": 8.56,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surface",
   "theme": "light",
   "ratio": 5.67,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surface",
   "theme": "dark",
   "ratio": 7.87,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surfaceSunken",
   "theme": "light",
   "ratio": 4.9,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surfaceSunken",
   "theme": "dark",
   "ratio": 7.5,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surfaceContainer",
   "theme": "light",
   "ratio": 5.03,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surfaceContainer",
   "theme": "dark",
   "ratio": 7.08,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surfaceContainerHigh",
   "theme": "light",
   "ratio": 4.64,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSurfaceVariant",
   "bg": "surfaceContainerHigh",
   "theme": "dark",
   "ratio": 6.28,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onPrimary",
   "bg": "primary",
   "theme": "light",
   "ratio": 16.51,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onPrimary",
   "bg": "primary",
   "theme": "dark",
   "ratio": 15.42,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "accent",
   "bg": "background",
   "theme": "light",
   "ratio": 6.93,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "accent",
   "bg": "background",
   "theme": "dark",
   "ratio": 8.76,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "accent",
   "bg": "surface",
   "theme": "light",
   "ratio": 7.41,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "accent",
   "bg": "surface",
   "theme": "dark",
   "ratio": 8.05,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAccent",
   "bg": "accent",
   "theme": "light",
   "ratio": 7.73,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAccent",
   "bg": "accent",
   "theme": "dark",
   "ratio": 7.96,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAccentContainer",
   "bg": "accentContainer",
   "theme": "light",
   "ratio": 11.11,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAccentContainer",
   "bg": "accentContainer",
   "theme": "dark",
   "ratio": 9.59,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "danger",
   "bg": "background",
   "theme": "light",
   "ratio": 5.89,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "danger",
   "bg": "background",
   "theme": "dark",
   "ratio": 11.01,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "danger",
   "bg": "surface",
   "theme": "light",
   "ratio": 6.31,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "danger",
   "bg": "surface",
   "theme": "dark",
   "ratio": 10.12,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "danger",
   "bg": "surfaceSunken",
   "theme": "light",
   "ratio": 5.44,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "danger",
   "bg": "surfaceSunken",
   "theme": "dark",
   "ratio": 9.64,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onDanger",
   "bg": "danger",
   "theme": "light",
   "ratio": 6.31,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onDanger",
   "bg": "danger",
   "theme": "dark",
   "ratio": 9.72,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onDangerContainer",
   "bg": "dangerContainer",
   "theme": "light",
   "ratio": 9.7,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onDangerContainer",
   "bg": "dangerContainer",
   "theme": "dark",
   "ratio": 9.7,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "success",
   "bg": "background",
   "theme": "light",
   "ratio": 5.63,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "success",
   "bg": "background",
   "theme": "dark",
   "ratio": 10.3,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "success",
   "bg": "surface",
   "theme": "light",
   "ratio": 6.02,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "success",
   "bg": "surface",
   "theme": "dark",
   "ratio": 9.47,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "success",
   "bg": "surfaceSunken",
   "theme": "light",
   "ratio": 5.2,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "success",
   "bg": "surfaceSunken",
   "theme": "dark",
   "ratio": 9.02,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSuccessContainer",
   "bg": "successContainer",
   "theme": "light",
   "ratio": 10.84,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onSuccessContainer",
   "bg": "successContainer",
   "theme": "dark",
   "ratio": 10.34,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "warning",
   "bg": "background",
   "theme": "light",
   "ratio": 5.67,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "warning",
   "bg": "background",
   "theme": "dark",
   "ratio": 11.27,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "warning",
   "bg": "surface",
   "theme": "light",
   "ratio": 6.07,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "warning",
   "bg": "surface",
   "theme": "dark",
   "ratio": 10.37,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onWarningContainer",
   "bg": "warningContainer",
   "theme": "light",
   "ratio": 12.85,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onWarningContainer",
   "bg": "warningContainer",
   "theme": "dark",
   "ratio": 11.91,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "inverseOnSurface",
   "bg": "inverseSurface",
   "theme": "light",
   "ratio": 15.42,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "inverseOnSurface",
   "bg": "inverseSurface",
   "theme": "dark",
   "ratio": 15.42,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "inverseAccent",
   "bg": "inverseSurface",
   "theme": "light",
   "ratio": 8.05,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "inverseAccent",
   "bg": "inverseSurface",
   "theme": "dark",
   "ratio": 6.93,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onCamera",
   "bg": "cameraBackground",
   "theme": "light",
   "ratio": 17.65,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onCamera",
   "bg": "cameraBackground",
   "theme": "dark",
   "ratio": 17.65,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "background",
   "theme": "light",
   "ratio": 3.68,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "background",
   "theme": "dark",
   "ratio": 4.88,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "surface",
   "theme": "light",
   "ratio": 3.94,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "surface",
   "theme": "dark",
   "ratio": 4.49,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "surfaceSunken",
   "theme": "light",
   "ratio": 3.4,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "surfaceSunken",
   "theme": "dark",
   "ratio": 4.27,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "surfaceContainerHigh",
   "theme": "light",
   "ratio": 3.22,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "outline",
   "bg": "surfaceContainerHigh",
   "theme": "dark",
   "ratio": 3.58,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "primary",
   "bg": "background",
   "theme": "light",
   "ratio": 15.42,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "primary",
   "bg": "background",
   "theme": "dark",
   "ratio": 16.77,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "accent",
   "bg": "surfaceSunken",
   "theme": "light",
   "ratio": 6.4,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "accent",
   "bg": "surfaceSunken",
   "theme": "dark",
   "ratio": 7.67,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "like",
   "bg": "background",
   "theme": "light",
   "ratio": 6.93,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "like",
   "bg": "background",
   "theme": "dark",
   "ratio": 8.76,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "like",
   "bg": "surface",
   "theme": "light",
   "ratio": 7.41,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "like",
   "bg": "surface",
   "theme": "dark",
   "ratio": 8.05,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "focusRing",
   "bg": "background",
   "theme": "light",
   "ratio": 6.93,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "focusRing",
   "bg": "background",
   "theme": "dark",
   "ratio": 8.76,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "selection",
   "bg": "canvas",
   "theme": "light",
   "ratio": 6.4,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "selection",
   "bg": "canvas",
   "theme": "dark",
   "ratio": 7.67,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "success",
   "bg": "cameraBackground",
   "theme": "light",
   "ratio": 3.14,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "success",
   "bg": "cameraBackground",
   "theme": "dark",
   "ratio": 10.84,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "warning",
   "bg": "cameraBackground",
   "theme": "light",
   "ratio": 3.11,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "ui",
   "fg": "warning",
   "bg": "cameraBackground",
   "theme": "dark",
   "ratio": 11.86,
   "min": 3.0,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/sand",
   "theme": "light",
   "ratio": 11.78,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/sage",
   "theme": "light",
   "ratio": 11.35,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/mist",
   "theme": "light",
   "ratio": 11.34,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/blush",
   "theme": "light",
   "ratio": 11.6,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/lavender",
   "theme": "light",
   "ratio": 11.57,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/ochre",
   "theme": "light",
   "ratio": 11.15,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/stone",
   "theme": "light",
   "ratio": 11.46,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/sand",
   "theme": "dark",
   "ratio": 7.61,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/sage",
   "theme": "dark",
   "ratio": 8.36,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/mist",
   "theme": "dark",
   "ratio": 8.64,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/blush",
   "theme": "dark",
   "ratio": 8.51,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/lavender",
   "theme": "dark",
   "ratio": 8.82,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/ochre",
   "theme": "dark",
   "ratio": 7.72,
   "min": 4.5,
   "ok": true
  },
  {
   "kind": "text",
   "fg": "onAvatar",
   "bg": "avatar/stone",
   "theme": "dark",
   "ratio": 8.49,
   "min": 4.5,
   "ok": true
  }
 ],
 "fontScaleTables": {
  "1.15": [
   9.2,
   11.5,
   13.8,
   16.4,
   19.8,
   21.8,
   25.2,
   30,
   100
  ],
  "1.3": [
   10.4,
   13,
   15.6,
   18.8,
   21.6,
   23.6,
   26.4,
   30,
   100
  ],
  "1.5": [
   12,
   15,
   18,
   22,
   24,
   26,
   28,
   30,
   100
  ],
  "1.8": [
   14.4,
   18,
   21.6,
   24.4,
   27.6,
   30.8,
   32.8,
   34.8,
   100
  ],
  "2": [
   16,
   20,
   24,
   26,
   30,
   34,
   36,
   38,
   100
  ]
 }
};
