/* Доски основ и компонентов дизайн-системы. Каждая доска — .board[id], рендер — render.mjs. */
(function () {
  'use strict';
  const D = window.DS, T = window.DS_TOKENS, IL = window.DS_ILLUSTRATIONS, I = window.DS_ICONS, A = window.DS_ART;
  const root = document.getElementById('root');

  const surface = (theme, html, style = '') => `<div class="ds-surface" data-theme="${theme}" style="background:var(--ds-color-background);color:var(--ds-color-on-surface);font-family:var(--ds-font-text);border-radius:20px;padding:28px;box-shadow:0 0 0 1px rgba(0,0,0,.06);${style}">${html}</div>`;
  const both = (render, style) => `<div class="board__row">${['light', 'dark'].map((t) => `<div class="board__cell"><div class="board__cap">${t === 'light' ? 'Светлая' : 'Тёмная'}</div>${surface(t, render(t), style)}</div>`).join('')}</div>`;
  const board = (id, section, title, desc, body) => `<div class="board" id="${id}"><div class="board__head"><div class="board__section">${section}</div><div class="board__title">${title}</div>${desc ? `<div class="board__desc">${desc}</div>` : ''}</div>${body}</div>`;
  const label = (t) => `<div class="ds-t-label-small" style="color:var(--ds-color-on-surface-variant);margin:20px 0 10px">${t}</div>`;
  const row = (html, gap = 12) => `<div style="display:flex;flex-wrap:wrap;gap:${gap}px;align-items:center">${html}</div>`;

  // ------------------------------------------------------------------ Цвета
  function contrastOf(fg, bg, theme) {
    const r = T.contrast.find((c) => c.fg === fg && c.bg === bg && c.theme === theme);
    return r ? r.ratio.toFixed(1) : null;
  }
  const groups = [
    ['Поверхности', ['background', 'surface', 'surfaceSunken', 'surfaceContainer', 'surfaceContainerHigh', 'surfaceContainerHighest']],
    ['Контент', ['onSurface', 'onSurfaceVariant', 'outline', 'outlineVariant']],
    ['Действие и акцент', ['primary', 'onPrimary', 'accent', 'onAccent', 'accentContainer', 'onAccentContainer', 'like', 'focusRing']],
    ['Статусы', ['danger', 'dangerContainer', 'success', 'successContainer', 'warning', 'warningContainer']],
    ['Инверсия и служебные', ['inverseSurface', 'inverseOnSurface', 'inverseAccent', 'scrim', 'skeletonBase', 'canvas', 'mannequin', 'cameraBackground']],
  ];
  const swatch = (name, theme) => {
    const c = T.colors[name][theme];
    const onText = contrastOf(name.startsWith('on') ? name : 'onSurface', name.startsWith('on') ? 'background' : name, theme);
    const vsBg = contrastOf(name, 'background', theme);
    return `<div style="width:132px"><div style="height:64px;border-radius:10px;background:${c};box-shadow:inset 0 0 0 1px rgba(128,128,128,.25)"></div>
      <div class="ds-t-title-small" style="margin-top:8px;font-size:12px;overflow-wrap:anywhere">${name}</div><div class="ds-t-body-small num" style="color:var(--ds-color-on-surface-variant)">${c}${vsBg ? ` · ${vsBg}:1` : ''}</div></div>`;
  };
  const colors = board('foundations-colors', 'Основы', 'Цвет', 'Молочный, графит, тёплый серый и один акцент — ультрамарин. Подпись под образцом — HEX и контраст с фоном экрана там, где пара нормируется WCAG (генератор проверяет 102 пары в обеих темах).',
    both((t) => groups.map(([g, names]) => label(g) + row(names.map((n) => swatch(n, t)).join(''), 12)).join('') + label('Аватары-инициалы') + row(T.avatarPalette.map((p) => `<span class="avatar avatar--l" style="background:${p[t]};color:${T.onAvatar[t]}">${p.name.slice(0, 2).toUpperCase()}</span>`).join(''), 12), 'width:800px'));

  // ------------------------------------------------------------------ Типографика
  const scale = T.typography.scale;
  const sample = { display: 'Осенняя капсула', text: 'Съешь же ещё этих мягких французских булок' };
  const typeRows = Object.entries(scale).map(([k, s]) => `<div style="display:grid;grid-template-columns:170px 1fr;gap:20px;align-items:baseline;padding:10px 0;border-top:1px solid var(--ds-color-outline-variant)">
      <div><div class="ds-t-title-small">${k}</div><div class="ds-t-body-small num" style="color:var(--ds-color-on-surface-variant)">${s.family === 'display' ? 'Display' : 'Inter'} ${s.weight}${s.italic ? ' italic' : ''} · ${s.size}/${s.lineHeight}</div></div>
      <div class="ds-t-${k.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())}" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${s.family === 'display' ? sample.display : k.startsWith('label') ? 'Опубликовать образ' : sample.text}</div></div>`).join('');
  const typography = board('foundations-typography', 'Основы', 'Типографика', 'Антиква OutfitShare Display (производная Playfair Display, OFL) — заголовки, числа и инициалы: контраст штрихов как в журнале. Inter — интерфейс и текст. Полная кириллица в обоих семействах; знак ₽ есть только в Inter — цены набираются им. Размеры в sp, интерлиньяж по сетке 4 dp.',
    both(() => typeRows, 'width:900px'));

  // ------------------------------------------------------------------ Отступы, радиусы, тени
  const spacing = Object.entries(T.spacing).filter(([k]) => k !== '0').map(([k, v]) => `<div style="display:flex;align-items:center;gap:12px;height:26px"><span class="ds-t-body-small num" style="width:88px;color:var(--ds-color-on-surface-variant)">space.${k.replace('_', '.')}</span><span style="height:12px;width:${v * 2}px;background:var(--ds-color-accent);border-radius:2px"></span><span class="ds-t-body-small num">${v} dp</span></div>`).join('');
  const radii = Object.entries(T.radius).map(([k, v]) => `<div style="text-align:center"><div style="width:72px;height:72px;background:var(--ds-color-surface-container-high);border-radius:${Math.min(v, 36)}px;box-shadow:inset 0 0 0 1px var(--ds-color-outline)"></div><div class="ds-t-body-small" style="margin-top:6px">${k} · ${v === 999 ? '∞' : v}</div></div>`).join('');
  const elev = Object.entries(T.elevation).map(([k, v]) => `<div style="text-align:center"><div style="width:96px;height:72px;background:var(--ds-color-surface);border-radius:12px;box-shadow:var(--ds-elevation-${k})"></div><div class="ds-t-body-small" style="margin-top:10px">${k} · ${v.dp} dp</div></div>`).join('');
  const layout = board('foundations-layout', 'Основы', 'Отступы, радиусы, тени', 'Сетка 4/8 dp: в разметке только токены space.* и layout.* (JVM-тест модуля запрещает литеральные dp/sp в layout- и style-файлах). Фото почти без скругления (2 dp) — журнальная кромка; кнопки и чипы — пилюли; шторки — 20 dp. Тени тёплые и только у плавающих слоёв.',
    both(() => label('Отступы') + spacing + label('Радиусы') + row(radii, 16) + label('Elevation') + row(elev, 20), 'width:620px'));

  // ------------------------------------------------------------------ Иконки
  const icons = board('foundations-icons', 'Основы', 'Иконки', 'Material Symbols Outlined, вес 300 (Apache 2.0) — тонкая линия в тон антикве. 24 dp в зоне касания 48 dp. Выбранные состояния (навигация, лайк, закладка) — залитая версия: состояние читается формой, а не только цветом.',
    both(() => `<div style="display:grid;grid-template-columns:repeat(10, 64px);gap:14px 8px">${Object.keys(I).map((n) => `<div style="text-align:center">${D.icon(n)}<div style="font:400 9px/12px var(--ds-font-text);color:var(--ds-color-on-surface-variant);margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${n}</div></div>`).join('')}</div>`, 'width:770px'));

  // ------------------------------------------------------------------ Иллюстрации
  const illustrations = board('foundations-illustrations', 'Основы', 'Иллюстрации пустых состояний', 'Журнальная линия 2 dp, «бумажная» заливка surfaceSunken и одна деталь акцента. Цвета — атрибуты темы, поэтому одна VectorDrawable работает в светлой и тёмной теме.',
    both(() => `<div style="display:grid;grid-template-columns:repeat(4, 150px);gap:18px">${Object.entries(IL).map(([n, svg]) => `<div style="text-align:center"><div style="width:120px;height:120px;margin:0 auto">${svg.replace('width="160" height="160"', 'width="120" height="120"')}</div><div class="ds-t-body-small" style="color:var(--ds-color-on-surface-variant)">${n}</div></div>`).join('')}</div>`, 'width:700px'));

  // ------------------------------------------------------------------ Кнопки
  const buttons = board('components-buttons', 'Компоненты', 'Кнопки', 'Primary — монохромная (графит днём, молочный ночью): акцент не спорит с одеждой. Высота 48 dp (large 56, small 40 с инсетами до 48). Загрузка не меняет ширину. Иконка-кнопка — 48×48 с contentDescription; поверх фото — на полупрозрачной подложке.',
    both(() => [
      label('Варианты'), row([D.btn('Опубликовать'), D.btn('Сохранить', { variant: 'secondary' }), D.btn('Пропустить', { variant: 'ghost' }), D.btn('Удалить', { variant: 'danger' })].join('')),
      label('Состояния: нажата · фокус · недоступна · загрузка'), row([D.btn('Нажата', { pressed: true }), D.btn('Фокус', { focused: true }), D.btn('Недоступна', { disabled: true }), D.btn('Публикуем…', { loading: true })].join('')),
      label('Secondary: нажата · недоступна'), row([D.btn('Подписаться', { variant: 'secondary', pressed: true }), D.btn('Подписаться', { variant: 'secondary', disabled: true }), D.btn('Вы подписаны', { variant: 'secondary', icon: 'check', size: 'small' })].join('')),
      label('Размеры'), row([D.btn('Large 56', { size: 'large' }), D.btn('Medium 48'), D.btn('Small 40', { size: 'small' })].join('')),
      label('Иконки-кнопки: стандарт · tonal · filled · на фото · недоступна'), row([D.iconBtn('more_vert'), D.iconBtn('bookmark', { variant: 'tonal' }), D.iconBtn('send', { variant: 'filled' }), `<span style="padding:8px;border-radius:12px;background:#B98D5F">${D.iconBtn('close', { variant: 'photo' })}</span>`, D.iconBtn('redo', { disabled: true })].join('')),
      label('Лайк: не отмечен · отмечен · «пружинка» · на фото'), row([D.like(128), D.like('1,2 тыс.', { on: true }), D.like('1,2 тыс.', { on: true, pop: true }), `<span style="padding:8px;border-radius:12px;background:#B98D5F">${D.like(128, { photo: true, on: true })}</span>`].join('')),
    ].join(''), 'width:640px'));

  // ------------------------------------------------------------------ Чипы
  const chips = board('components-chips', 'Компоненты', 'Чипы-фильтры', 'Выбранный фильтр — инверсная заливка (меняется форма, а не только оттенок), без галочки. Видимая высота 36 dp, зона касания 48 dp. Input-чипы — хэштеги с удалением, suggestion — подсказки.',
    both(() => [
      label('Фильтр: выбран · не выбран · недоступен'), row([D.chip('Верх', { selected: true }), D.chip('Низ'), D.chip('Обувь'), D.chip('Аксессуары', { variant: 'disabled' })].join(''), 8),
      label('С иконкой'), row([D.chip('Женский', { icon: 'mannequin', selected: true }), D.chip('Слои · 3', { icon: 'layers' }), D.chip('Фильтры', { icon: 'tune' })].join(''), 8),
      label('Input и suggestion'), row([D.chip('#осень', { variant: 'input', close: true }), D.chip('#капсула', { variant: 'input', close: true }), D.chip('+ #офис', { variant: 'suggestion' }), D.chip('+ #винтаж', { variant: 'suggestion' })].join(''), 8),
    ].join(''), 'width:560px'));

  // ------------------------------------------------------------------ Карточки
  const cards = board('components-cards', 'Компоненты', 'Карточки образа и вещи', 'Карточка образа: фото 4:5 — герой, под ним автор, лайк (кнопка-переключатель 48 dp) и подпись до двух строк; в сетке 2×N — компактный вид. Карточка вещи: вырезанная вещь на «бумажной» плитке 1:1; выбор — рамка selection 2 dp и галочка.',
    both(() => `<div style="display:grid;grid-template-columns:260px 150px 150px;gap:24px;align-items:start">
        <div>${label('Образ')}${D.outfitCard('trench', { author: 'anna', meta: '2 ч · 5 вещей', likes: '1,2 тыс.', liked: true, caption: 'Тренч, который работает с чем угодно. <span class="hashtag">#осень</span>' })}</div>
        <div>${label('Компакт · бейдж')}${D.outfitCard('denim', { author: 'dan', likes: 348, compact: true, badge: 'Ремикс' })}<div style="height:16px"></div>${D.outfitCard('slip', { author: 'mila', likes: 64, compact: true, liked: true })}</div>
        <div>${label('Вещь: обычная · выбрана · бейдж · действие')}${D.itemCard({ kind: 'sweater', color: 'cream', title: 'Свитер из мериноса', sub: 'Верх · слой 2' })}<div style="height:12px"></div>${D.itemCard({ kind: 'skirtMidi', color: 'olive', title: 'Юбка миди', sub: 'в образе', selected: true })}<div style="height:12px"></div>${D.itemCard({ kind: 'trench', color: 'camel', title: 'Тренч', sub: 'Посадка 92 %', badge: 'Новое', badgeVariant: 'accent', action: true })}</div></div>`, 'width:680px'));

  // ------------------------------------------------------------------ Аватары и бейджи
  const avatars = board('components-avatars-badges', 'Компоненты', 'Аватары и бейджи', 'Аватар — фото в круге или инициалы антиквой на цвете палитры: цвет стабилен для пользователя (хеш id, один алгоритм в Android и макетах). Рядом с именем аватар декоративен для TalkBack. Бейджи: счётчик на навигации (акцент, до «99+»), точка «есть новое», текстовые — статусы.',
    both(() => [
      label('Размеры xs · s · m · l · xl · xxl'), row(['xs', 's', 'm', 'l', 'xl', 'xxl'].map((s) => D.avatar('vera', s)).join(''), 16),
      label('Фото · в сети · стопка'), row([D.avatar('anna', 'l'), D.avatar('oleg', 'l', { online: true }), `<span class="avatar-stack">${D.avatar('mila', 'm')}${D.avatar('dan', 'm')}${D.avatar('sonya', 'm')}</span>`].join(''), 20),
      label('Палитра инициалов'), row(['anna', 'mila', 'dan', 'vera', 'oleg', 'sonya', 'artem', 'nika', 'me'].map((u) => D.avatar(u, 'm', { initials: true })).join(''), 10),
      label('Счётчики и точка'), row([`<span class="bottomnav__pill" style="background:var(--ds-color-surface-container-high)">${D.icon('chat_filled')}<span class="count-badge">3</span></span>`, `<span class="bottomnav__pill">${D.icon('notifications')}<span class="count-badge">99+</span></span>`, `<span class="bottomnav__pill">${D.icon('home')}<span class="dot"></span></span>`].join(''), 16),
      label('Текстовые бейджи'), row([D.tbadge('Новое', 'accent'), D.tbadge('Ремикс', 'inverse'), D.tbadge('Черновик'), D.tbadge('Готово', 'success', 'check_circle'), D.tbadge('В очереди', 'warning', 'schedule'), D.tbadge('Ошибка', 'danger', 'error')].join(''), 8),
    ].join(''), 'width:600px'));

  // ------------------------------------------------------------------ Навигация: табы, нижняя навигация, шторка
  const nav = board('components-navigation', 'Компоненты', 'Табы, навигация, bottom sheet', 'Табы — текст и индикатор 2 dp цвета текста; при нехватке места — прокрутка (MODE_AUTO). Нижняя навигация: подписи всегда видны, выбранная вкладка — пилюля и залитая иконка; при шрифте ≥ 150 % — только иконки. Шторка: ручка (доступна TalkBack), заголовок антиквой, скругление 20 dp сверху, затемнение scrim.',
    both(() => [
      label('Табы фиксированные · со счётчиками · прокручиваемые'), D.tabs(['Подписки', 'Для вас'], 0), '<div style="height:12px"></div>', D.tabs([['Подписчики', '12 тыс.'], ['Подписки', 210]], 1), '<div style="height:12px"></div>', D.tabs(['Все', 'Верх', 'Низ', 'Платья', 'Обувь', 'Аксессуары'], 2, { scroll: true }),
      label('Нижняя навигация'), `<div style="width:360px;border-radius:12px;overflow:hidden">${D.bottomnav('feed', { badges: { chats: 3, profile: 'dot' } })}</div>`,
      label('Bottom sheet'), `<div style="position:relative;width:360px;height:300px;border-radius:16px;overflow:hidden;background:var(--ds-color-background)">${D.photo('olive', { style: 'border-radius:0' })}${D.sheet({ title: 'Сохранить в коллекцию', content: D.row({ lead: `<span class="row__thumb">${A.outfit(A.LOOKS.trench)}</span>`, title: 'Сохранённое', sub: '64 образа', strong: true, end: D.icon('check', '') }) + D.row({ lead: `<span class="row__thumb">${A.outfit(A.LOOKS.forest)}</span>`, title: 'Осень 2026', sub: '18 образов', strong: true }) })}</div>`,
    ].join(''), 'width:420px'));

  // ------------------------------------------------------------------ Обратная связь
  const box = (html, h = 180) => `<div style="position:relative;width:360px;height:${h}px;border-radius:16px;overflow:hidden;background:var(--ds-color-background);box-shadow:inset 0 0 0 1px var(--ds-color-outline-variant)">${html}</div>`;
  const feedback = board('components-feedback', 'Компоненты', 'Снекбар, диалог, баннеры', '«Отменить» вместо подтверждений: действие выполняется сразу, на сервер уходит после окна отмены (5 с; с TalkBack — дольше). Диалог подтверждения — только для необратимого, кнопка называет действие глаголом, разрушающее — цвета danger. Баннеры — офлайн, информация, ошибка.',
    both(() => [
      label('Снекбар с «Отменить» и таймером окна отмены'), box(D.snackbar('Комментарий удалён', { action: 'Отменить', bottom: 16, timer: 64 }), 88),
      label('Снекбар ошибки с повтором'), box(D.snackbar('Не удалось опубликовать. Черновик сохранён', { action: 'Повторить', bottom: 16 }), 96),
      label('Диалог: разрушающее действие'), box(D.dialog({ title: 'Удалить черновик?', text: 'Образ исчезнет со всех устройств. Это нельзя отменить.', confirm: 'Удалить', danger: true }).replace('left: 28px', ''), 250),
      label('Баннеры'), `<div style="width:360px;display:flex;flex-direction:column;gap:8px">${D.banner('Офлайн — показываем сохранённое', { action: 'Повторить' })}${D.banner('Уведомления выключены в системе', { icon: 'notifications_off', action: 'Включить', variant: 'info' })}${D.banner('Плечи не совпали — поправьте точки', { icon: 'warning', variant: 'danger' })}</div>`,
    ].join(''), 'width:420px'));

  // ------------------------------------------------------------------ Состояния
  const states = board('components-states', 'Компоненты', 'Состояния экрана и скелетоны', 'StateView: одно место для загрузки, пустоты, ошибки, офлайна и «нет прав». Скелетон повторяет форму контента; блик бежит только по блокам, при «Убрать анимации» — статичен. Заголовок сообщения — live region.',
    both(() => `<div style="display:grid;grid-template-columns:repeat(3, 250px);gap:18px">
      ${[['Скелетон ленты', `<div class="skeleton-shimmer" style="padding:16px">${D.skOutfit()}</div>`],
         ['Скелетон списка', `<div class="skeleton-shimmer">${D.skRow()}${D.skRow({ w1: 40 })}${D.skRow({ w1: 66 })}</div>`],
         ['Скелетон вещей', `<div class="skeleton-shimmer grid grid--2" style="padding:16px">${D.skItem()}${D.skItem()}</div>`],
         ['Пусто', D.state({ art: 'empty_feed', title: 'Здесь пока пусто', text: 'Подпишитесь на авторов.', action: 'Найти людей' })],
         ['Ошибка сети', D.state({ art: 'error', title: 'Не загрузилось', text: 'Проверьте подключение.', action: 'Повторить', actionIcon: 'refresh' })],
         ['Нет прав', D.state({ art: 'locked', title: 'Нет доступа', text: 'Профиль закрыт автором.', action: 'Отправить заявку', actionVariant: 'secondary' })]]
        .map(([t, h]) => `<div><div class="ds-t-label-small" style="color:var(--ds-color-on-surface-variant);margin-bottom:8px">${t}</div><div style="height:360px;border-radius:14px;overflow:hidden;box-shadow:inset 0 0 0 1px var(--ds-color-outline-variant);display:flex;flex-direction:column;transform:scale(1);zoom:.86">${h}</div></div>`).join('')}</div>`, 'width:860px'));

  // ------------------------------------------------------------------ Поля и строки
  const inputs = board('components-inputs', 'Компоненты', 'Поля ввода, переключатели, строки', 'Поле — утопленная плашка, при фокусе линия 2 dp, ошибка — текстом под полем (не только цветом). Переключатель управляется всей строкой: одна цель касания и одна фраза для TalkBack. Строка списка растёт с текстом, минимум 56 dp.',
    both(() => `<div style="width:320px">${[
      label('Поле: пустое · фокус · заполнено · ошибка'),
      D.field({ label: 'Электронная почта', placeholder: 'Электронная почта' }), '<div style="height:12px"></div>',
      D.field({ label: 'Электронная почта', value: 'liza.mir@', focused: true }), '<div style="height:12px"></div>',
      D.field({ label: 'Имя', value: 'Лиза Миронова', help: 'Видно всем', counter: '13 / 40' }), '<div style="height:12px"></div>',
      D.field({ label: 'Имя пользователя', value: '@liza', error: 'Имя @liza уже занято' }),
      label('Поиск · код'), D.search('тренч'), '<div style="height:12px"></div>', `<div class="otp">${['4', '8', '1', '', '', ''].map((d, i) => `<span class="otp__cell ${i === 3 ? 'otp__cell--focused' : ''}">${d}</span>`).join('')}</div>`,
    ].join('')}</div><div style="width:360px;margin-top:8px">${[
      label('Строки'), D.row({ icon: 'palette', title: 'Тема', value: 'Как в системе', chevron: true }), D.row({ icon: 'favorite', title: 'Лайки и комментарии', switch: true }), D.row({ icon: 'lock', title: 'Закрытый профиль', sub: 'Образы видят одобренные подписчики', switch: false }), D.row({ avatar: 'vera', title: 'Вера Лис', sub: 'verafox', strong: true, end: D.btn('Подписаться', { size: 'small' }) }), D.row({ icon: 'logout', title: 'Выйти', danger: true }),
    ].join('')}</div>`, 'width:420px'));

  root.innerHTML = [colors, typography, layout, icons, illustrations, buttons, chips, cards, avatars, nav, feedback, states, inputs].join('');
})();
