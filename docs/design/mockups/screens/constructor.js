/* Конструктор образа и публикация. */
(function () {
  const D = window.DS, M = window.DSM, A = window.DS_ART;

  // Холст 360×400: манекен 400×500 вписан с масштабом 0.8 и отступом 20 px по X.
  const px = (x) => 20 + x * 0.8, py = (y) => y * 0.8;
  const frame = (box) => {
    const [x1, y1, x2, y2] = box;
    const l = px(x1), t = py(y1), w = (x2 - x1) * 0.8, h = (y2 - y1) * 0.8;
    const handle = (x, y) => `<i style="left:${x - 5}px;top:${y - 5}px"></i>`;
    return `<div class="layer-frame" style="left:${l}px;top:${t}px;width:${w}px;height:${h}px">${handle(0, 0)}${handle(w, 0)}${handle(0, h)}${handle(w, h)}</div>`;
  };

  const top = (o = {}) => D.appbar({
    nav: 'close', title: o.title || 'Образ',
    actions: [D.iconBtn('undo', { label: 'Отменить действие', disabled: o.noUndo }), D.iconBtn('redo', { label: 'Повторить действие', disabled: true }), `<span style="padding-right:8px">${D.btn('Далее', { variant: 'primary', size: 'small', disabled: o.noUndo })}</span>`],
  });

  const catalogTiles = (selected = []) => {
    const items = [['trench', 'camel'], ['sweater', 'cream'], ['shirt', 'ecru'], ['blazer', 'navy'], ['knitVest', 'rust'], ['tee', 'white']];
    return `<div class="grid" style="grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;padding:12px var(--ds-layout-gutter) 0">${items.map(([k, c], i) => D.itemCard({ kind: k, color: c, selected: selected.includes(i) })).join('')}</div>`;
  };

  const panel = (o = {}) => `<div style="flex:1;background:var(--ds-color-surface);border-radius:var(--ds-shape-sheet) var(--ds-shape-sheet) 0 0;box-shadow:var(--ds-elevation-level2);display:flex;flex-direction:column;overflow:hidden;margin-top:-12px;position:relative;z-index:2">
      <div class="sheet__handle" style="height:20px"></div>
      ${D.tabs(['Все', 'Верх', 'Низ', 'Платья', 'Обувь', 'Аксессуары'], 1, { scroll: true })}
      ${o.offline ? D.banner('Офлайн — каталог из кэша', {}) : ''}
      ${o.loading ? `<div class="grid skeleton-shimmer" style="grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;padding:12px var(--ds-layout-gutter) 0">${[0, 1, 2, 3, 4, 5].map(() => '<div class="sk sk--tile ratio-1-1"></div>').join('')}</div>` : catalogTiles(o.selected || [1])}</div>`;

  const canvas = (spec, overlays = '') => `<div class="canvas" style="height:400px;flex:none">${A.outfit(Object.assign({ bare: true }, spec))}
      <span style="position:absolute;top:12px;left:12px">${D.chip('Женский', { icon: 'mannequin' })}</span>
      <span style="position:absolute;left:12px;bottom:24px">${D.chip('Слои · ' + (spec.items || []).length, { icon: 'layers' })}</span>
      ${overlays}</div>`;

  const look = { items: [['shirt', 'ecru'], ['skirtMidi', 'olive'], ['boots', 'chocolate']] };

  M.screen({
    id: 'constructor',
    section: 'Создание',
    title: 'Конструктор',
    description: 'Холст — студийная «бумага» (canvas), каталог — в нижней панели. Вещь из каталога «падает» на манекен пружиной drop и садится по опорным точкам; при перетаскивании зона крепления подсвечивается акцентом, в момент захвата — хаптика SNAP. Выбранный слой — рамка цвета selection с ручками 48 dp зоны касания.',
    states: {
      content: { title: 'Выбран слой', render: () => M.phone({ body: top() + canvas(look, frame(A.META.skirtMidi.box)) + panel({ selected: [2] }) }) },
      empty: {
        title: 'Пустой холст',
        render: () => M.phone({
          body: top({ noUndo: true }) + canvas({ items: [] }, `<div style="position:absolute;left:60px;right:60px;bottom:72px;text-align:center;background:color-mix(in srgb, var(--ds-color-canvas) 88%, transparent);border-radius:var(--ds-radius-m);padding:10px 12px"><div class="quote">Выберите вещь внизу — она сама сядет на манекен</div></div>`) + panel({ selected: [] }),
        }),
      },
      snapping: {
        title: 'Перетаскивание: snap',
        render: () => M.phone({
          body: top() + canvas(Object.assign({}, look, { ghost: { kind: 'trench', color: 'camel', dx: 6, dy: -22 } }),
            `<div class="snap-zone" style="left:${px(140)}px;top:${py(96)}px;width:${0.8 * 120}px;height:28px"></div>
             <div class="snap-zone" style="left:${px(146)}px;top:${py(196)}px;width:${0.8 * 108}px;height:20px"></div>
             <span style="position:absolute;right:12px;top:12px">${D.tbadge('Плечи · талия', 'accent', 'magnet')}</span>`) + panel({ selected: [0, 2] }),
        }),
      },
      layers: {
        title: 'Шторка слоёв',
        render: () => M.phone({
          body: top() + canvas(look) + panel({ selected: [2] }),
          overlay: D.sheet({
            title: 'Слои',
            content: [
              ['boots', 'chocolate', 'Ботинки кожаные', 'Обувь · слой 1'],
              ['skirtMidi', 'olive', 'Юбка миди', 'Низ · слой 1'],
              ['shirt', 'ecru', 'Рубашка из хлопка', 'Верх · слой 1'],
            ].reverse().map(([k, c, t, s], i) => D.row({
              lead: `<span class="row__icon">${D.icon('drag_handle')}</span><span class="row__thumb" style="background:var(--ds-color-surface-sunken)">${A.item(k, c)}</span>`,
              title: t, sub: s, strong: true, style: i === 1 ? 'background:var(--ds-color-surface-container-high)' : '',
              end: D.iconBtn('visibility', { label: 'Скрыть слой', variant: 'muted' }) + D.iconBtn('delete', { label: 'Удалить слой', variant: 'muted' }),
            })).join('') + '<div class="ds-t-body-small muted gutter" style="padding:8px 20px 24px">Перетащите, чтобы изменить порядок. Зона тела определяет, что поверх чего.</div>',
          }),
        }),
      },
      removed: {
        title: 'Слой удалён + «Отменить»',
        render: () => M.phone({ body: top() + canvas({ items: [['shirt', 'ecru'], ['boots', 'chocolate']] }) + panel({ selected: [2] }), overlay: D.snackbar('Юбка миди убрана из образа', { action: 'Отменить', bottom: 32, timer: 40 }) }),
      },
      loading: { title: 'Загрузка каталога', render: () => M.phone({ body: top({ noUndo: true }) + canvas({ items: [] }) + panel({ loading: true }) }) },
      offline: { title: 'Офлайн', render: () => M.phone({ body: top() + canvas(look) + panel({ offline: true, selected: [2] }) }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['empty', 'light'], ['snapping', 'light'], ['layers', 'light'], ['removed', 'light'], ['loading', 'light'], ['offline', 'dark']],
  });

  // ------------------------------------------------------------------ Публикация
  const publishForm = (o = {}) => `
    <div class="scroll">
      <div class="gutter publish-head" style="display:flex;gap:16px;padding-top:var(--ds-space-2)">
        <div style="width:112px;flex:none">${D.photo('olive')}</div>
        <div style="flex:1;min-width:0">${D.field({ label: 'Подпись', value: o.caption ? 'Рубашка навыпуск, юбка миди и грубые ботинки — любимое сочетание на сентябрь' : '', placeholder: 'Расскажите об образе…', focused: o.focused, counter: o.caption ? '74 / 2200' : '', tall: true })}</div>
      </div>
      <div class="group-title"><span class="kicker">Хэштеги</span></div>
      ${D.chips([D.chip('#осень', { variant: 'input', close: true }), D.chip('#миди', { variant: 'input', close: true }), D.chip('#ботинки', { variant: 'input', close: true })], { wrap: true, style: 'padding:0 20px' })}
      ${D.chips([D.chip('+ #капсула', { variant: 'suggestion' }), D.chip('+ #офис', { variant: 'suggestion' }), D.chip('+ #винтаж', { variant: 'suggestion' })], { style: 'padding-top:8px' })}
      <div style="height:8px"></div>
      ${D.row({ icon: 'collections', title: 'Коллекция', value: 'Осень 2026', chevron: true })}
      ${D.row({ icon: 'visibility', title: 'Кто видит', value: 'Все', chevron: true })}
      ${D.row({ icon: 'remix', title: 'Разрешить ремиксы', sub: 'Другие смогут собрать свой образ из ваших вещей', switch: true, sub2: true })}
    </div>
    <div class="gutter" style="padding:var(--ds-space-3) var(--ds-layout-gutter) var(--ds-space-4)">${o.button || D.btn('Опубликовать', { variant: 'primary', size: 'large', block: true })}</div>`;

  M.screen({
    id: 'publish',
    section: 'Создание',
    title: 'Публикация образа',
    description: 'Превью, подпись, хэштеги с подсказками, коллекция и видимость. Кнопка «Опубликовать» уходит в состояние загрузки, ширина не меняется; по успеху — хаптика PUBLISH и экран-подтверждение. Без сети образ сохраняется черновиком и публикуется сам.',
    states: {
      content: { title: 'Форма', render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', title: 'Публикация' }) + publishForm({ caption: true }) }) },
      empty: { title: 'Пустая подпись', render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', title: 'Публикация' }) + publishForm({ focused: true }) }) },
      publishing: { title: 'Публикуем', render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', title: 'Публикация' }) + publishForm({ caption: true, button: D.btn('Публикуем…', { variant: 'primary', size: 'large', block: true, loading: true }) }) }) },
      error: {
        title: 'Ошибка + повтор',
        render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', title: 'Публикация' }) + publishForm({ caption: true }), overlay: D.snackbar('Не удалось опубликовать. Черновик сохранён', { action: 'Повторить', bottom: 100 }) }),
      },
      offline: {
        title: 'Офлайн',
        render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', title: 'Публикация' }) + D.banner('Нет сети — опубликуем автоматически, когда она появится') + publishForm({ caption: true, button: D.btn('Опубликовать позже', { variant: 'primary', size: 'large', block: true, icon: 'schedule' }) }) }),
      },
      success: {
        title: 'Опубликовано',
        render: () => M.phone({
          body: D.appbar({ nav: 'close' }) + `<div class="gutter" style="padding-top:var(--ds-space-4)"><div style="width:200px;margin:0 auto">${D.photo('olive')}</div></div>`
            + `<div style="text-align:center" class="gutter"><div class="kicker" style="margin-top:var(--ds-space-6);color:var(--ds-color-success)">${D.icon('check_circle', 'ic--s')} Опубликовано</div><div class="ds-t-headline-large" style="margin-top:var(--ds-space-2)">Образ в ленте ваших подписчиков</div></div><div style="flex:1"></div>`
            + `<div class="gutter" style="display:flex;flex-direction:column;gap:8px;padding-bottom:16px">${D.btn('Смотреть образ', { variant: 'primary', size: 'large', block: true })}${D.btn('Собрать ещё', { variant: 'ghost', block: true })}</div>`,
        }),
      },
    },
    board: [['content', 'light'], ['content', 'dark'], ['empty', 'light'], ['publishing', 'light'], ['error', 'light'], ['offline', 'light'], ['success', 'light'], ['content', 'light', 2]],
  });
})();
