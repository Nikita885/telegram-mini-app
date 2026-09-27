/* Студия (для администраторов каталога): камера, очередь, проверка, доводка точек, публикация. */
(function () {
  const D = window.DS, M = window.DSM, A = window.DS_ART;

  // ------------------------------------------------------------------ Камера
  const quality = (items) => `<div style="display:flex;gap:6px;flex-wrap:wrap;justify-content:center">${items.map(([ok, text]) => `<span class="quality ${ok ? 'quality--ok' : 'quality--warn'}">${D.icon(ok ? 'check_circle' : 'warning')}${text}</span>`).join('')}</div>`;
  const cameraBody = (o = {}) => `<div class="viewfinder">${A.rawCapture('sweater', 'cream', { wall: o.dim ? '#8E877C' : '#D8D2C6' })}
      ${o.dim ? '<div style="position:absolute;inset:0;background:rgba(0,0,0,.35)"></div>' : ''}
      ${o.flash ? '<div style="position:absolute;inset:0;background:rgba(255,255,255,.75)"></div>' : ''}
      <div class="frame-guide"></div>
      <div style="position:absolute;top:36px;left:8px;right:8px;display:flex;justify-content:space-between;align-items:center">
        ${D.iconBtn('close', { variant: 'photo', label: 'Закрыть' })}<span class="kicker" style="color:#F6F2EB">Студия</span>${D.iconBtn(o.dim ? 'flash_on' : 'flash_off', { variant: 'photo', label: 'Вспышка' })}</div>
      <div style="position:absolute;top:92px;left:12px;right:12px">${quality(o.checks)}</div>
      <div style="position:absolute;left:24px;right:24px;bottom:176px;text-align:center" class="ds-t-body-medium"><span class="cam-pill">${o.hint || 'Разложите вещь целиком в рамке, на ровном фоне'}</span></div>
      <div style="position:absolute;left:0;right:0;bottom:0;height:160px;background:linear-gradient(to top, rgba(11,11,10,.92), rgba(11,11,10,0));display:flex;align-items:center;justify-content:space-around;padding-top:24px">
        <span style="width:48px;height:48px;border-radius:8px;overflow:hidden;position:relative;box-shadow:0 0 0 2px rgba(246,242,235,.6)">${A.item('tee', 'white')}</span>
        <span class="shutter" role="button" aria-label="Сделать снимок" style="${o.flash ? 'transform:scale(.92)' : ''}"></span>
        <span style="position:relative">${D.iconBtn('queue', { variant: 'photo', label: 'Очередь, 3 в работе' })}<span class="count-badge" style="position:absolute;top:2px;right:0">3</span></span></div></div>`;

  M.screen({
    id: 'studio-camera',
    section: 'Студия',
    title: 'Студия: камера',
    description: 'Камера всегда в тёмной палитре (ThemeOverlay.Ds.Dark), независимо от темы телефона. Проверки качества — живые чипы поверх кадра: свет, фон, кадрирование; предупреждение — иконкой и текстом. Затвор 72 dp, хаптика SHUTTER. Без прав камеры и для не-админов — состояния «нет прав».',
    states: {
      ready: { title: 'Готово к съёмке', camera: true, render: () => M.phone({ status: { overlay: true, color: '#F6F2EB' }, fullBleed: true, body: cameraBody({ checks: [[true, 'Свет'], [true, 'Фон'], [true, 'Вещь в кадре']] }) }) },
      warning: { title: 'Мало света', camera: true, render: () => M.phone({ status: { overlay: true, color: '#F6F2EB' }, fullBleed: true, body: cameraBody({ dim: true, checks: [[false, 'Мало света'], [true, 'Фон'], [false, 'Отойдите дальше']], hint: 'Включите вспышку или подойдите к окну' }) }) },
      capture: { title: 'Снимок', camera: true, render: () => M.phone({ status: { overlay: true, color: '#F6F2EB' }, fullBleed: true, body: cameraBody({ flash: true, checks: [[true, 'Свет'], [true, 'Фон'], [true, 'Вещь в кадре']], hint: 'Снято — загружаем в очередь' }) }) },
      permission: {
        title: 'Нет доступа к камере', camera: true,
        render: () => M.phone({ status: { color: '#F6F2EB' }, gestureColor: '#F6F2EB', body: `<div data-theme="dark" style="flex:1;display:flex;flex-direction:column;color:var(--ds-color-on-surface)">${D.appbar({ nav: 'close', title: 'Студия' })}${D.state({ art: 'camera', title: 'Нужен доступ к камере', text: 'Студия снимает вещь и сразу проверяет свет и кадр. Фото не уходят никуда, кроме каталога.', action: 'Разрешить', secondary: 'Выбрать из галереи' })}</div>` }),
      },
      forbidden: {
        title: 'Не администратор',
        render: () => M.phone({ nav: 'profile', body: D.appbar({ nav: 'arrow_back', title: 'Студия' }) + D.state({ art: 'locked', title: 'Студия — для администраторов каталога', text: 'Здесь фотографируют и публикуют вещи. Если вы стилист или магазин — напишите нам.', action: 'Написать в поддержку', actionVariant: 'secondary' }) }),
      },
    },
    board: [['ready', 'light'], ['warning', 'light'], ['capture', 'dark'], ['permission', 'light'], ['forbidden', 'light'], ['forbidden', 'dark']],
  });

  // ------------------------------------------------------------------ Очередь
  const job = (kind, color, title, status, o = {}) => D.row({
    lead: `<span class="row__thumb" style="width:56px;height:56px">${A.rawCapture(kind, color)}</span>`,
    title, strong: true, oneLine: true, sub2: true,
    sub: `${status}${o.progress != null ? `<div class="progress" style="margin-top:8px" role="progressbar" aria-valuenow="${o.progress}"><i style="width:${o.progress}%"></i></div>` : ''}${o.steps != null ? `<div class="steps" style="margin-top:8px">${[0, 1, 2, 3, 4].map((i) => `<i class="${i < o.steps ? 'on' : ''}"></i>`).join('')}</div>` : ''}`,
    end: o.end || '',
    style: 'align-items:flex-start',
  });
  const jobs = () => [
    job('sweater', 'cream', 'Свитер из мериноса', 'Загрузка · <span class="num">64 %</span> · 2,1 из 3,3 МБ', { progress: 64 }),
    job('skirtMidi', 'olive', 'Юбка миди', 'Вырезаем фон · шаг 2 из 5', { steps: 2 }),
    job('blazer', 'navy', 'Блейзер', 'Подгоняем к манекену · шаг 4 из 5', { steps: 4 }),
    job('trench', 'camel', 'Тренч классический', `<span style="color:var(--ds-color-success)">${D.icon('check_circle', 'ic--s')} Готово к проверке · посадка 92 %</span>`, { end: D.icon('chevron_right', 'ic--m') }),
    job('boots', 'black', 'Ботинки', `<span style="color:var(--ds-color-danger)">${D.icon('error', 'ic--s')} Вещь не найдена в кадре</span>`, { end: D.btn('Переснять', { variant: 'secondary', size: 'small' }) }),
  ].join('');
  const queueTop = (o = {}) => D.appbar({ nav: 'arrow_back', title: 'Очередь', editorial: true }) + D.tabs([['В работе', o.empty ? 0 : 4], ['Готово', 12], ['Ошибки', 1]], 0);

  M.screen({
    id: 'studio-queue',
    section: 'Студия',
    title: 'Студия: очередь',
    description: 'Живой прогресс по WebSocket: загрузка — процент и мегабайты, обработка — шаги пайплайна (фон → контур → опорные точки → посадка → превью). Готовое — с fit score, ошибка — с понятной причиной и действием. Загрузка в фоне переживает закрытие экрана.',
    states: {
      content: { title: 'В работе', render: () => M.phone({ body: queueTop() + `<div class="scroll">${jobs()}</div><span class="fab">${D.icon('camera')}Снять</span>` }) },
      empty: { title: 'Пусто', render: () => M.phone({ body: queueTop({ empty: true }) + D.state({ art: 'camera', title: 'Очередь пуста', text: 'Сфотографируйте вещь — через пару минут она сядет на манекен.', action: 'Открыть камеру', actionIcon: 'camera' }) }) },
      offline: { title: 'Офлайн: пауза', render: () => M.phone({ body: queueTop() + D.banner('Загрузка на паузе — продолжим, когда появится сеть') + `<div class="scroll">${jobs()}</div>` }) },
      loading: { title: 'Загрузка', render: () => M.phone({ body: queueTop() + `<div class="skeleton-shimmer" style="padding-top:8px">${[62, 48, 56, 40].map((w) => `<div class="row"><div class="sk" style="width:56px;height:56px;border-radius:6px;flex:none"></div><div class="row__body"><div class="sk sk--line" style="width:${w}%"></div><div class="sk sk--line-s" style="width:80%;margin-top:10px"></div></div></div>`).join('')}</div>` }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['empty', 'light'], ['offline', 'light'], ['loading', 'light']],
  });

  // ------------------------------------------------------------------ Проверка
  const reviewTop = () => D.appbar({ nav: 'arrow_back', title: 'Проверка', actions: [D.iconBtn('more_vert', { label: 'Ещё' })] });
  const reviewCanvas = (o = {}) => `<div class="canvas" style="height:380px;flex:none">${o.before ? A.rawCapture('sweater', 'cream') : A.outfit({ bare: true, items: [['sweater', 'cream'], ['wideTrousers', 'stone']] })}
      ${o.before ? `<span style="position:absolute;top:12px;left:12px">${D.tbadge('Исходный кадр', 'inverse', 'camera')}</span>` : `<span style="position:absolute;top:12px;left:12px">${D.tbadge(o.low ? 'Посадка 61 %' : 'Посадка 92 %', o.low ? 'warning' : 'success', o.low ? 'warning' : 'check_circle')}</span>`}
      ${o.low ? '<div class="snap-zone" style="left:108px;top:72px;width:150px;height:30px;background:color-mix(in srgb, var(--ds-color-warning) 45%, transparent)"></div>' : ''}</div>`;
  const reviewBody = (o = {}) => M.phone({
    body: reviewTop() + `<div style="display:flex;justify-content:center;padding:8px 0">${D.chips([D.chip('До', { selected: o.before }), D.chip('После', { selected: !o.before })], { style: 'padding:0' })}</div>`
      + reviewCanvas(o)
      + `<div class="scroll">${o.low ? D.banner('Плечи не совпали с манекеном — поправьте опорные точки', { icon: 'warning' }) : ''}
        <div class="gutter" style="padding-top:12px"><div class="ds-t-headline-small">Свитер из мериноса</div></div>
        ${D.chips([D.chip('Верх'), D.chip('Слой 2'), D.chip('Плечи · торс'), D.chip('Кремовый')], { style: 'padding-top:8px' })}</div>
      <div class="gutter" style="display:flex;gap:8px;padding:12px 20px 16px">${D.btn('Доводка точек', { variant: 'secondary', size: 'large', style: 'flex:1', icon: 'keypoint' })}${D.btn('Далее', { variant: 'primary', size: 'large', style: 'flex:1', disabled: o.low })}</div>`,
  });

  M.screen({
    id: 'studio-review',
    section: 'Студия',
    title: 'Студия: проверка',
    description: '«До / После»: исходный кадр и вещь, посаженная на манекен. Fit score — бейдж со значком и числом (не только цвет). При низкой посадке проблемная зона подсвечивается warning, «Далее» недоступна до доводки точек. Категория, слой и зоны определены пайплайном и редактируются чипами.',
    states: {
      after: { title: 'После: хорошая посадка', render: () => reviewBody() },
      before: { title: 'До: исходный кадр', render: () => reviewBody({ before: true }) },
      low: { title: 'Низкая посадка', render: () => reviewBody({ low: true }) },
      offline: { title: 'Офлайн', render: () => M.phone({ body: reviewTop() + D.banner('Офлайн — результат из кэша, публикация после появления сети') + reviewCanvas() + `<div class="gutter" style="padding-top:12px"><div class="ds-t-headline-small">Свитер из мериноса</div></div><div style="flex:1"></div><div class="gutter" style="display:flex;gap:8px;padding:12px 20px 16px">${D.btn('Доводка точек', { variant: 'secondary', size: 'large', style: 'flex:1', icon: 'keypoint' })}${D.btn('Далее', { variant: 'primary', size: 'large', style: 'flex:1' })}</div>` }) },
      processing: {
        title: 'Ещё обрабатывается',
        render: () => M.phone({ body: reviewTop() + `<div class="canvas skeleton-shimmer" style="height:380px;flex:none"><div class="sk" style="position:absolute;inset:40px 90px;border-radius:40px"></div></div>` + `<div class="gutter" style="padding-top:20px"><div class="ds-t-title-medium">Подгоняем к манекену</div><div class="ds-t-body-small muted" style="margin-top:4px">Шаг 4 из 5 · обычно меньше минуты</div><div class="steps" style="margin-top:12px"><i class="on"></i><i class="on"></i><i class="on"></i><i class="on"></i><i></i></div></div>` }),
      },
    },
    board: [['after', 'light'], ['after', 'dark'], ['before', 'light'], ['low', 'light'], ['processing', 'light'], ['offline', 'light']],
  });

  // ------------------------------------------------------------------ Доводка точек
  // Холст 360×440 показывает область манекена viewBox «90 60 220 270» (масштаб ≈1.63).
  const VB = [90, 60, 220, 270], K = 440 / 270, OX = (360 - 220 * K) / 2;
  const at = (x, y) => [OX + (x - VB[0]) * K, (y - VB[1]) * K];
  const garment = (kind, color) => {
    const parts = A.G[kind](A.PALETTE[color] || color);
    return { defs: parts.map((p) => p.defs).join(''), svg: parts.map((p) => p.svg).join('') };
  };
  const kp = ([x, y], label, active) => `<span class="keypoint ${active ? 'keypoint--active' : ''}" style="left:${x}px;top:${y}px" role="slider" aria-label="${label || 'Опорная точка'}"></span>${label ? `<span class="keypoint-label" style="left:${x}px;top:${y}px">${label}</span>` : ''}`;
  const points = (active) => [
    kp(at(146, 112), 'Плечо Л', active === 'sl'), kp(at(254, 112), 'Плечо П'), kp(at(200, 104), 'Ворот'),
    kp(at(150, 258), 'Низ Л'), kp(at(250, 258), 'Низ П'), kp(at(137, 293), 'Манжета'), kp(at(263, 293), 'Манжета'),
  ].join('');
  const loupe = () => {
    const g = garment('sweater', 'cream'), m = A.mannequin();
    return `<div style="position:absolute;left:216px;top:16px;width:116px;height:116px;border-radius:50%;overflow:hidden;box-shadow:0 0 0 3px #fff, 0 6px 18px rgba(0,0,0,.3);background:var(--ds-color-canvas)" aria-hidden="true">
      <svg viewBox="128 94 36 36" width="116" height="116" style="position:absolute;inset:0"><defs>${m.defs}${g.defs}</defs><g opacity=".35">${m.svg}</g>${g.svg}</svg>
      <span style="position:absolute;left:50%;top:50%;width:18px;height:18px;margin:-9px 0 0 -9px;border-radius:50%;border:2px solid var(--ds-color-accent)"></span></div>`;
  };
  const kpCanvas = (o = {}) => {
    const g = garment('sweater', 'cream'), m = A.mannequin();
    return `<div class="canvas" style="height:440px;flex:none">
      <svg viewBox="${VB.join(' ')}" style="position:absolute;inset:0;width:100%;height:100%" preserveAspectRatio="xMidYMid meet"><defs>${m.defs}${g.defs}</defs>${o.mannequin ? `<g opacity=".35">${m.svg}</g>` : ''}${g.svg}</svg>
      ${points(o.active)}${o.active ? loupe() : ''}
      <span style="position:absolute;left:12px;bottom:12px">${D.chip('Манекен', { icon: 'mannequin', selected: o.mannequin })}</span></div>`;
  };

  M.screen({
    id: 'studio-keypoints',
    section: 'Студия',
    title: 'Студия: доводка точек',
    description: 'Опорные точки вещи (плечи, воротник, талия, низ) перетаскиваются пальцем; маркер 28 dp, зона касания 48 dp. Активная точка — акцентом и лупой, чтобы палец не закрывал край ткани. Полупрозрачный манекен под вещью показывает, куда точка «сядет». Каждое перемещение — в undo.',
    states: {
      content: { title: 'Точки', render: () => M.phone({ body: D.appbar({ nav: 'close', title: 'Доводка', actions: [D.iconBtn('undo', { label: 'Отменить' }), D.iconBtn('redo', { label: 'Повторить', disabled: true })] }) + kpCanvas({ mannequin: true }) + `<div class="gutter ds-t-body-medium muted" style="padding-top:12px">Перетащите точку к краю ткани. Манекен под вещью — ориентир.</div><div style="flex:1"></div><div class="gutter" style="display:flex;gap:8px;padding-bottom:16px">${D.btn('Сбросить', { variant: 'ghost', size: 'large' })}${D.btn('Готово', { variant: 'primary', size: 'large', style: 'flex:1' })}</div>` }) },
      dragging: { title: 'Перетаскивание + лупа', render: () => M.phone({ body: D.appbar({ nav: 'close', title: 'Доводка', actions: [D.iconBtn('undo', { label: 'Отменить' }), D.iconBtn('redo', { label: 'Повторить', disabled: true })] }) + kpCanvas({ mannequin: true, active: 'sl' }) + `<div class="gutter" style="padding-top:12px"><span class="ds-t-title-small">Плечо слева</span> <span class="ds-t-body-small muted num">x 134 · y 104</span></div><div style="flex:1"></div><div class="gutter" style="display:flex;gap:8px;padding-bottom:16px">${D.btn('Сбросить', { variant: 'ghost', size: 'large' })}${D.btn('Готово', { variant: 'primary', size: 'large', style: 'flex:1' })}</div>` }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['dragging', 'light']],
  });

  // ------------------------------------------------------------------ Публикация вещи
  const swatch = (c, on) => `<span style="width:32px;height:32px;border-radius:50%;background:${c};box-shadow:${on ? '0 0 0 2px var(--ds-color-background), 0 0 0 4px var(--ds-color-on-surface)' : 'inset 0 0 0 1px rgba(0,0,0,.12)'};flex:none" role="radio" aria-checked="${on}"></span>`;
  const itemForm = (o = {}) => M.phone({
    body: D.appbar({ nav: 'arrow_back', title: 'Публикация вещи' }) + `<div class="scroll gutter">
      <div style="display:flex;gap:16px;align-items:center;padding-top:4px"><div style="width:96px;flex:none">${D.itemCard({ kind: 'sweater', color: 'cream' })}</div><div style="flex:1">${D.field({ label: 'Название', value: 'Свитер из мериноса' })}</div></div>
      <div class="group-title" style="padding-left:0;padding-right:0"><span class="kicker">Категория</span></div>
      ${D.chips(['Верх', 'Низ', 'Платья', 'Обувь', 'Аксессуары'].map((t, i) => D.chip(t, { selected: i === 0 })), { wrap: true })}
      <div class="group-title" style="padding-left:0;padding-right:0"><span class="kicker">Цвет</span></div>
      <div style="display:flex;gap:12px">${['#EDE3D1', '#222120', '#9B9A96', '#B98D5F', '#27324A', '#6C6A43'].map((c, i) => swatch(c, i === 0)).join('')}</div>
      <div class="group-title" style="padding-left:0;padding-right:0"><span class="kicker">Посадка</span></div></div>
      ${D.row({ icon: 'layers', title: 'Слой', value: '2 — поверх базы', chevron: true })}
      ${D.row({ icon: 'mannequin', title: 'Для кого', value: 'Унисекс', chevron: true })}
      <div class="gutter" style="padding:8px 20px 16px">${o.button || D.btn('Опубликовать в каталог', { variant: 'primary', size: 'large', block: true })}</div>`,
    overlay: o.overlay || '',
  });

  M.screen({
    id: 'studio-publish',
    section: 'Студия',
    title: 'Студия: публикация вещи',
    description: 'Атрибуты предзаполнены пайплайном — администратор только проверяет. Цвет — образцы с кольцом выбора (форма, а не только цвет). После публикации вещь сразу доступна всем в конструкторе: подтверждение показывает её на манекене, хаптика PUBLISH.',
    states: {
      content: { title: 'Форма', render: () => itemForm() },
      publishing: { title: 'Публикуем', render: () => itemForm({ button: D.btn('Публикуем…', { variant: 'primary', size: 'large', block: true, loading: true }) }) },
      success: {
        title: 'Опубликовано',
        render: () => M.phone({ body: D.appbar({ nav: 'close' }) + `<div class="canvas" style="height:360px;flex:none;margin:0 20px;border-radius:var(--ds-shape-item-tile)">${A.outfit({ bare: true, items: [['sweater', 'cream']] })}</div><div class="gutter" style="text-align:center"><div class="kicker" style="margin-top:20px;color:var(--ds-color-success)">${D.icon('check_circle', 'ic--s')} В каталоге</div><div class="ds-t-headline-large" style="margin-top:8px">Свитер уже доступен в конструкторе</div></div><div style="flex:1"></div><div class="gutter" style="display:flex;flex-direction:column;gap:8px;padding-bottom:16px">${D.btn('Снять следующую', { variant: 'primary', size: 'large', block: true, icon: 'camera' })}${D.btn('К очереди', { variant: 'ghost', block: true })}</div>` }),
      },
      error: { title: 'Ошибка', render: () => itemForm({ overlay: D.snackbar('Каталог не ответил. Вещь сохранена в очереди', { action: 'Повторить', bottom: 100 }) }) },
      offline: { title: 'Офлайн', render: () => itemForm({ button: D.btn('Опубликовать, когда появится сеть', { variant: 'primary', size: 'large', block: true, icon: 'schedule' }) }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['publishing', 'light'], ['success', 'light'], ['error', 'light'], ['offline', 'light']],
  });
})();
