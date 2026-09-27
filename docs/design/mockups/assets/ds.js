/*
 * Компоненты макетов (HTML-строки) — зеркало Android-компонентов :core:designsystem.
 * Использование: DS.btn('Опубликовать', {variant: 'primary'}), DS.outfitCard(...), ...
 */
(function () {
  'use strict';
  const I = window.DS_ICONS;
  const IL = window.DS_ILLUSTRATIONS;
  const A = window.DS_ART;

  const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  function icon(name, cls = '') {
    const g = I[name];
    if (!g) throw new Error('Нет иконки ' + name);
    return `<svg class="ic ${cls}" viewBox="${g.viewBox}" aria-hidden="true"><path d="${g.d}"/></svg>`;
  }

  // ------------------------------------------------------------ Люди
  // Стабильный цвет аватара — тот же алгоритм, что AvatarPalette.indexFor в Android.
  function paletteIndex(id, size) {
    const M = (1n << 64n);
    let x = (BigInt(id) * 0x9E3779B97F4A7C15n) % M;
    x = x ^ (x >> 32n);
    let signed = x >= (1n << 63n) ? x - M : x;
    let r = signed % BigInt(size);
    if (r < 0n) r += BigInt(size);
    return Number(r);
  }
  const AVATAR_NAMES = window.DS_TOKENS.avatarPalette.map((p) => p.name);

  function initials(name) {
    const words = name.trim().split(/[\s_.\-@]+/).filter((w) => /[\p{L}\p{N}]/u.test(w));
    const first = (w) => [...w].find((ch) => /[\p{L}\p{N}]/u.test(ch));
    if (!words.length) return '';
    let s = first(words[0]);
    if (words.length > 1) s += first(words[words.length - 1]);
    return s.toUpperCase();
  }

  const USERS = {
    me: { id: 90210, name: 'Лиза Миронова', handle: 'liza.mir', bio: 'Собираю капсулы из винтажа и базы. Москва.' },
    anna: { id: 101, name: 'Анна Ковалёва', handle: 'anna.kov', photo: 'slip' },
    mila: { id: 102, name: 'Мила Орлова', handle: 'mila_o' },
    dan: { id: 103, name: 'Даниил Шестаков', handle: 'dan.sh', photo: 'street' },
    vera: { id: 104, name: 'Вера Лис', handle: 'verafox' },
    oleg: { id: 105, name: 'Олег Ким', handle: 'oleg.kim' },
    sonya: { id: 106, name: 'Софья Беляева', handle: 'sonya.b', photo: 'olive' },
    artem: { id: 107, name: 'Артём Власов', handle: 'artem.v' },
    nika: { id: 108, name: 'Ника Зуева', handle: 'nika_z' },
    studio: { id: 1, name: 'Студия Outfit Share', handle: 'studio' },
  };

  function avatar(user, size = 'm', opts = {}) {
    const u = typeof user === 'string' ? USERS[user] : user;
    const color = AVATAR_NAMES[paletteIndex(u.id, AVATAR_NAMES.length)];
    const photo = u.photo && !opts.initials
      ? `<span class="avatar__photo">${A.outfit(Object.assign({}, A.LOOKS[u.photo], { crop: 'bust' }))}</span>` : '';
    const online = opts.online ? '<span class="avatar__online"></span>' : '';
    return `<span class="avatar avatar--${size}" style="background: var(--ds-color-avatar-${color})" role="img" aria-label="Аватар: ${esc(u.name)}">${photo ? '' : initials(u.name)}${photo}${online}</span>`;
  }

  // ------------------------------------------------------------ Хром телефона
  function statusbar(opts = {}) {
    const cls = opts.overlay ? 'statusbar statusbar--overlay' : 'statusbar';
    return `<div class="${cls}" ${opts.color ? `style="color:${opts.color}"` : ''}><span class="num">9:41</span>
      <span class="statusbar__icons"><i style="width:14px;height:10px;clip-path:polygon(0 100%,100% 0,100% 100%)"></i><i style="width:12px;height:10px;border-radius:2px"></i><i style="width:20px;height:10px;border-radius:3px;opacity:.9"></i></span></div>`;
  }
  const gesturebar = (color) => `<div class="gesturebar" ${color ? `style="color:${color}"` : ''}></div>`;

  function appbar(o = {}) {
    const nav = o.nav ? iconBtn(o.nav, { label: o.nav === 'close' ? 'Закрыть' : 'Назад' }) : '<span style="width:var(--ds-space-3)"></span>';
    const title = o.title
      ? `<div class="appbar__title ${o.editorial ? 'appbar__title--editorial' : ''} ${o.center ? 'appbar__title--center' : ''}">${o.title}${o.sub ? `<span class="appbar__sub">${o.sub}</span>` : ''}</div>`
      : '<div style="flex:1"></div>';
    const actions = (o.actions || []).join('');
    return `<div class="appbar ${o.lifted ? 'appbar--lifted' : ''}" ${o.style ? `style="${o.style}"` : ''}>${nav}${title}${actions}</div>`;
  }

  // ------------------------------------------------------------ Кнопки
  const spinner = () => '<svg class="spinner" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-dasharray="40 100" transform="rotate(-90 12 12)"/></svg>';

  function btn(label, o = {}) {
    const v = o.variant || 'primary';
    const cls = ['btn', `btn--${v}`, o.size ? `btn--${o.size}` : '', o.block ? 'btn--block' : '', o.pressed ? 'btn--pressed' : '', o.focused ? 'btn--focused' : ''].join(' ');
    const lead = o.loading ? spinner() : o.icon ? icon(o.icon) : '';
    return `<span class="${cls}" ${o.disabled ? 'disabled' : ''} ${o.style ? `style="${o.style}"` : ''}>${lead}${label ? `<span>${label}</span>` : ''}</span>`;
  }

  function iconBtn(name, o = {}) {
    const cls = ['icon-btn', o.variant ? `icon-btn--${o.variant}` : ''].join(' ');
    const badge = o.dot ? '<span class="dot"></span>' : '';
    return `<span class="${cls}" role="button" aria-label="${esc(o.label || name)}" ${o.disabled ? 'disabled' : ''} ${o.style ? `style="${o.style}"` : ''}>${icon(name, o.icls || '')}${badge}</span>`;
  }

  function chip(label, o = {}) {
    const cls = ['chip', o.selected ? 'chip--selected' : '', o.variant ? `chip--${o.variant}` : ''].join(' ');
    return `<span class="${cls}">${o.icon ? icon(o.icon) : ''}${label}${o.close ? icon('close', 'ic--s') : ''}</span>`;
  }
  const chips = (items, o = {}) => `<div class="chips ${o.wrap ? 'chips--wrap' : ''}" ${o.style ? `style="${o.style}"` : ''}>${items.join('')}</div>`;

  function tabs(labels, selected = 0, o = {}) {
    return `<div class="tabs ${o.scroll ? 'tabs--scroll' : ''}" role="tablist">${labels.map((l, i) => {
      const [text, count] = Array.isArray(l) ? l : [l, null];
      return `<span class="tab ${i === selected ? 'tab--selected' : ''}" role="tab">${text}${count != null ? ` <span class="num">${count}</span>` : ''}</span>`;
    }).join('')}</div>`;
  }

  function tbadge(text, variant = '', ic) {
    return `<span class="tbadge ${variant ? `tbadge--${variant}` : ''}">${ic ? icon(ic) : ''}${text}</span>`;
  }

  // ------------------------------------------------------------ Фото и карточки
  function photo(look, o = {}) {
    const spec = typeof look === 'string' ? A.LOOKS[look] : look;
    return `<div class="photo ratio-${o.ratio || '4-5'}" ${o.style ? `style="${o.style}"` : ''}>${A.outfit(Object.assign({}, spec, o.crop ? { crop: o.crop } : {}))}${o.scrim ? '<div class="photo__scrim"></div>' : ''}${o.badge ? `<span class="photo__badge">${tbadge(o.badge, 'inverse')}</span>` : ''}${o.heart ? `<span class="photo__heart">${icon('favorite_filled', '')}</span>` : ''}${o.overlay || ''}</div>`;
  }

  function like(count, o = {}) {
    const cls = ['like', o.on ? 'like--on' : '', o.pop ? 'like--pop' : '', o.photo ? 'like--photo' : ''].join(' ');
    return `<span class="${cls}" role="switch" aria-checked="${!!o.on}" aria-label="Нравится">${icon(o.on ? 'favorite_filled' : 'favorite')}${count ? `<span>${count}</span>` : ''}</span>`;
  }

  function outfitCard(look, o = {}) {
    const author = o.author ? USERS[o.author] : null;
    if (o.compact && author) {
      // В сетке 2×N лайк — не кнопка, а мета: ставится двойным тапом или в просмотре образа.
      const compactMeta = `<div class="outfit-card__meta" style="gap:8px;min-height:0;margin-top:8px">${avatar(author, 'xs')}<div class="outfit-card__who"><div class="outfit-card__author" style="font-weight:500">${author.name}</div></div>${o.likes ? `<span class="outfit-card__sub" style="display:inline-flex;align-items:center;gap:2px;flex:none;${o.liked ? 'color:var(--ds-color-like)' : ''}">${icon(o.liked ? 'favorite_filled' : 'favorite', 'ic--s')}<span class="num" style="color:var(--ds-color-on-surface-variant)">${o.likes}</span></span>` : ''}</div>`;
      return `<article class="outfit-card outfit-card--compact">${photo(look, { badge: o.badge })}${compactMeta}</article>`;
    }
    const meta = author ? `<div class="outfit-card__meta">${avatar(author, 's')}
        <div class="outfit-card__who"><div class="outfit-card__author">${author.name}</div>${o.meta ? `<div class="outfit-card__sub">${o.meta}</div>` : ''}</div>
        ${like(o.likes, { on: o.liked, pop: o.pop })}</div>` : '';
    const caption = o.caption ? `<div class="outfit-card__caption">${o.caption}</div>` : '';
    return `<article class="outfit-card ${o.compact ? 'outfit-card--compact' : ''}">${photo(look, { badge: o.badge, heart: o.heart })}${meta}${caption}</article>`;
  }

  function itemCard(o) {
    const cls = ['item-card', o.selected ? 'item-card--selected' : ''].join(' ');
    return `<div class="${cls}"><div class="item-card__tile">${A.item(o.kind, o.color)}
      ${o.selected ? `<span class="item-card__check">${icon('check_circle')}</span>` : ''}
      ${o.badge ? `<span class="item-card__badge">${tbadge(o.badge, o.badgeVariant || '')}</span>` : ''}
      ${o.action ? `<span class="item-card__action">${iconBtn('add', { variant: 'tonal', label: 'Добавить в образ' })}</span>` : ''}</div>
      ${o.title ? `<div class="item-card__title">${o.title}</div>` : ''}${o.sub ? `<div class="item-card__sub">${o.sub}</div>` : ''}</div>`;
  }

  // ------------------------------------------------------------ Строки и поля
  function row(o) {
    const lead = o.avatar ? avatar(o.avatar, o.avatarSize || 'm', { online: o.online })
      : o.icon ? `<span class="row__icon">${icon(o.icon)}</span>` : o.lead || '';
    const sub = o.sub ? `<div class="row__sub ${o.sub2 ? 'row__sub--2' : 'row__one'}">${o.sub}</div>` : '';
    const end = [o.value ? `<span class="row__value">${o.value}</span>` : '', o.meta ? `<span class="row__meta">${o.meta}</span>` : '', o.end || '', o.switch != null ? `<span class="switch ${o.switch ? 'switch--on' : ''}" role="switch" aria-checked="${o.switch}"></span>` : '', o.chevron ? `<span class="row__icon">${icon('chevron_right', 'ic--m')}</span>` : ''].join('');
    return `<div class="row ${o.tall ? 'row--tall' : ''} ${o.danger ? 'row--danger' : ''}" ${o.style ? `style="${o.style}"` : ''}>${lead}<div class="row__body"><div class="${o.strong ? 'row__title--strong' : o.body ? 'row__title--body' : 'row__title'} ${o.oneLine ? 'row__one' : ''}">${o.title}</div>${sub}</div>${end ? `<div class="row__end">${end}</div>` : ''}</div>`;
  }

  function field(o) {
    const box = ['field__box', o.focused ? 'field__box--focused' : '', o.error ? 'field__box--error' : ''].join(' ');
    const value = o.value
      ? `<div class="field__value">${o.value}${o.focused ? '<span class="caret"></span>' : ''}</div>`
      : `<div class="field__value field__value--placeholder">${o.placeholder || ''}${o.focused ? '<span class="caret"></span>' : ''}</div>`;
    const label = o.label && (o.value || o.focused) ? `<div class="field__label">${o.label}</div>` : '';
    const help = o.error ? `<div class="field__help field__help--error"><span>${o.error}</span>${o.counter || ''}</div>`
      : o.help || o.counter ? `<div class="field__help"><span>${o.help || ''}</span><span>${o.counter || ''}</span></div>` : '';
    return `<label class="field" ${o.style ? `style="${o.style}"` : ''}><div class="${box}" ${o.tall ? 'style="min-height:96px;justify-content:flex-start"' : ''}>${label}${value}${o.trail ? `<span class="field__trail">${o.trail}</span>` : ''}</div>${help}</label>`;
  }

  function search(text, o = {}) {
    return `<div class="search" ${o.style ? `style="${o.style}"` : ''}>${icon('search', 'ic--m')}<span class="search__text ${text && !o.placeholder ? 'search__text--filled' : ''}">${text || 'Образы, люди, вещи, #теги'}${o.focused ? '<span class="caret"></span>' : ''}</span>${text && !o.placeholder ? iconBtn('close', { label: 'Очистить', icls: 'ic--m' }) : ''}</div>`;
  }

  function bottomnav(active = 'feed', o = {}) {
    const items = [
      ['feed', 'Лента', 'home', 'home_filled'],
      ['search', 'Поиск', 'search', 'search'],
      ['create', 'Создать', 'add', 'add'],
      ['chats', 'Диалоги', 'chat', 'chat_filled'],
      ['profile', 'Профиль', 'person', 'person_filled'],
    ];
    return `<nav class="bottomnav">${items.map(([key, label, ic, icOn]) => {
      const on = key === active;
      const badge = o.badges && o.badges[key] ? (o.badges[key] === 'dot' ? '<span class="dot"></span>' : `<span class="count-badge">${o.badges[key]}</span>`) : '';
      return `<span class="bottomnav__item ${on ? 'bottomnav__item--on' : ''} ${key === 'create' ? 'bottomnav__create' : ''}" aria-label="${label}"><span class="bottomnav__pill">${icon(on ? icOn : ic)}${badge}</span><span class="bottomnav__label">${label}</span></span>`;
    }).join('')}</nav>`;
  }

  // ------------------------------------------------------------ Обратная связь
  function state(o) {
    return `<div class="state" ${o.style ? `style="${o.style}"` : ''}>${o.art ? `<div class="state__art">${IL[o.art]}</div>` : ''}
      <div class="state__title">${o.title}</div>${o.text ? `<div class="state__text">${o.text}</div>` : ''}
      ${o.action || o.secondary ? `<div class="state__actions">${o.action ? btn(o.action, { variant: o.actionVariant || 'primary', icon: o.actionIcon }) : ''}${o.secondary ? btn(o.secondary, { variant: 'ghost' }) : ''}</div>` : ''}</div>`;
  }

  function banner(text, o = {}) {
    return `<div class="banner ${o.variant ? `banner--${o.variant}` : ''}" role="status">${icon(o.icon || 'cloud_off', 'ic--m')}<span class="banner__text">${text}</span>${o.action ? btn(o.action, { variant: 'ghost' }) : ''}</div>`;
  }

  function snackbar(text, o = {}) {
    return `<div class="snackbar" role="status" style="bottom:${o.bottom != null ? o.bottom : 88}px"><span class="snackbar__text">${text}</span>${o.action ? `<span class="snackbar__action">${o.action}</span>` : ''}${o.timer != null ? `<span class="snackbar__timer" style="width:${o.timer}%"></span>` : ''}</div>`;
  }

  function sheet(o) {
    return `<div class="scrim"></div><div class="sheet" style="${o.height ? `height:${o.height}px` : ''}" role="dialog" aria-label="${esc(o.title || '')}"><div class="sheet__handle" role="button" aria-label="Свернуть"></div>${o.title ? `<div class="sheet__title">${o.title}</div>` : ''}${o.content}</div>`;
  }

  function dialog(o) {
    return `<div class="scrim"></div><div class="dialog" role="alertdialog"><div class="dialog__title">${o.title}</div>${o.text ? `<div class="dialog__text">${o.text}</div>` : ''}<div class="dialog__actions">${btn(o.cancel || 'Отмена', { variant: 'ghost' })}${btn(o.confirm, { variant: o.danger ? 'ghost-danger' : 'ghost' })}</div></div>`;
  }

  // ------------------------------------------------------------ Скелетоны
  function skOutfit() {
    return `<div><div class="sk ratio-4-5"></div><div style="display:flex;align-items:center;gap:12px;min-height:48px;margin-top:8px"><div class="sk sk--circle" style="width:32px;height:32px"></div><div style="flex:1"><div class="sk sk--line" style="width:62%"></div><div class="sk sk--line-s" style="width:38%;margin-top:8px"></div></div></div></div>`;
  }
  function skRow(o = {}) {
    return `<div class="row" style="${o.style || ''}"><div class="sk sk--circle" style="width:40px;height:40px;flex:none"></div><div class="row__body"><div class="sk sk--line" style="width:${o.w1 || 58}%"></div><div class="sk sk--line-s" style="width:${o.w2 || 82}%;margin-top:10px"></div></div>${o.end ? '<div class="sk" style="width:88px;height:36px;border-radius:18px"></div>' : ''}</div>`;
  }
  function skItem() {
    return `<div><div class="sk sk--tile ratio-1-1"></div><div class="sk sk--line" style="width:70%;margin-top:10px"></div><div class="sk sk--line-s" style="width:45%;margin-top:8px"></div></div>`;
  }

  window.DS = {
    esc, icon, spinner, avatar, initials, paletteIndex, USERS, statusbar, gesturebar, appbar, btn, iconBtn, chip, chips, tabs,
    tbadge, photo, like, outfitCard, itemCard, row, field, search, bottomnav, state, banner, snackbar, sheet, dialog,
    skOutfit, skRow, skItem, art: A,
  };
})();
