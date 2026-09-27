/* Просмотр образа и комментарии. */
(function () {
  const D = window.DS, M = window.DSM;

  const photoTop = (o = {}) => `<div style="position:relative;flex:none">
      ${D.photo('trench', { heart: o.heart, style: 'border-radius:0' })}
      <div style="position:absolute;top:32px;left:8px;right:8px;display:flex;justify-content:space-between">
        ${D.iconBtn('arrow_back', { variant: 'photo', label: 'Назад' })}${D.iconBtn('more_vert', { variant: 'photo', label: 'Ещё' })}</div>
      <span style="position:absolute;right:12px;bottom:12px">${D.tbadge('5 вещей', 'inverse', 'hanger')}</span></div>`;

  const authorRow = () => `<div class="row" style="padding-top:var(--ds-space-3);padding-bottom:0">${D.avatar('anna', 'm')}
      <div class="row__body"><div class="row__title--strong">Анна Ковалёва</div><div class="row__sub">@anna.kov</div></div>
      ${D.btn('Подписаться', { variant: 'secondary', size: 'small' })}</div>`;

  const actions = (o = {}) => `<div style="display:flex;align-items:center;padding:0 var(--ds-space-2)">
      ${D.like('1,2 тыс.', { on: o.liked !== false, pop: o.pop })}
      <span class="like">${D.icon('comment')}<span>24</span></span>
      <span class="like">${D.icon('remix')}<span>8</span></span>
      <span style="flex:1"></span>${D.iconBtn(o.saved ? 'bookmark_filled' : 'bookmark', { label: 'Сохранить в коллекцию' })}${D.iconBtn('share', { label: 'Поделиться' })}</div>`;

  const caption = () => `<div class="gutter ds-t-body-large" style="margin-top:var(--ds-space-1)">Тренч, который работает с чем угодно: широкие брюки, лоферы, мягкий свитер. <span class="hashtag">#осень</span> <span class="hashtag">#капсула</span> <span class="hashtag">#тренч</span></div>`;

  const itemsRow = () => `<div class="section"><div class="section__head"><span class="ds-t-headline-small">Вещи в образе</span><span class="link">Все 5</span></div>
      <div style="display:flex;gap:12px;padding:0 var(--ds-layout-gutter);overflow:hidden">
        ${[['trench', 'camel', 'Тренч классический', 'Верхняя одежда'], ['sweater', 'cream', 'Свитер из мериноса', 'Верх · слой 2'], ['wideTrousers', 'ecru', 'Брюки широкие', 'Низ'], ['loafers', 'black', 'Лоферы', 'Обувь']]
          .map(([k, c, t, s]) => `<div style="width:124px;flex:none">${D.itemCard({ kind: k, color: c, title: t, sub: s })}</div>`).join('')}</div></div>`;

  const similar = () => `<div class="section"><div class="section__head"><span class="ds-t-headline-small">Похожие образы</span></div>
      <div class="grid grid--2 gutter">${D.outfitCard('olive', { compact: true })}${D.outfitCard('forest', { compact: true })}</div></div>`;

  const view = (o = {}) => M.phone({
    status: { overlay: true }, overlay: o.overlay || '',
    body: `${o.banner ? `<div style="position:absolute;top:28px;left:0;right:0;z-index:4">${o.banner}</div>` : ''}${photoTop(o)}${authorRow()}${actions(o)}${caption()}<div class="gutter ds-t-body-small muted" style="margin-top:var(--ds-space-2)">12 октября · Москва</div>`,
  });

  M.screen({
    id: 'outfit',
    section: 'Основное',
    title: 'Просмотр образа',
    description: 'Фото приходит из карточки ленты shared element-переходом (container transform, 350 мс, emphasized). Над фото — только круглые кнопки на полупрозрачной подложке, контраст не зависит от снимка. Ниже — автор, действия, подпись, вещи образа и похожие образы.',
    states: {
      content: { title: 'Контент', render: () => view() },
      heart: { title: 'Двойной тап: лайк', render: () => view({ heart: true, pop: true }) },
      scrolled: {
        title: 'Прокрутка: вещи',
        render: () => M.phone({
          body: D.appbar({ nav: 'arrow_back', title: 'Образ Анны', lifted: true, actions: [D.iconBtn('bookmark', { label: 'Сохранить' }), D.iconBtn('more_vert', { label: 'Ещё' })] })
            + `<div class="scroll">${actions()}${caption()}${itemsRow()}${similar()}</div>`,
        }),
      },
      saved: {
        title: 'Сохранено + снекбар',
        render: () => view({ saved: true, overlay: D.snackbar('Сохранено в «Осень 2026»', { action: 'Изменить', bottom: 32 }) }),
      },
      more: {
        title: 'Шторка «Ещё»',
        render: () => view({
          overlay: D.sheet({
            title: null,
            content: [
              D.row({ icon: 'bookmark', title: 'Сохранить в коллекцию' }),
              D.row({ icon: 'remix', title: 'Сделать ремикс', sub: 'Откроется конструктор с этими вещами' }),
              D.row({ icon: 'link', title: 'Скопировать ссылку' }),
              D.row({ icon: 'share', title: 'Отправить в диалог' }),
              '<div class="divider divider--inset"></div>',
              D.row({ icon: 'flag', title: 'Пожаловаться', danger: true }),
              '<div style="height:20px"></div>',
            ].join(''),
          }),
        }),
      },
      loading: {
        title: 'Загрузка',
        render: () => M.phone({
          status: { overlay: true },
          body: `<div class="skeleton-shimmer" aria-label="Загрузка…"><div class="sk ratio-4-5" style="border-radius:0"></div>${D.skRow({ end: true })}<div class="gutter"><div class="sk sk--line" style="width:92%"></div><div class="sk sk--line" style="width:64%;margin-top:10px"></div></div></div>`,
        }),
      },
      error: {
        title: 'Ошибка сети',
        render: () => M.phone({ body: D.appbar({ nav: 'arrow_back' }) + D.state({ art: 'error', title: 'Образ не загрузился', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }),
      },
      forbidden: {
        title: 'Нет доступа',
        render: () => M.phone({ body: D.appbar({ nav: 'arrow_back' }) + D.state({ art: 'locked', title: 'Образ недоступен', text: 'Автор скрыл его или открыл только для подписчиков.', action: 'Профиль автора', actionVariant: 'secondary' }) }),
      },
      offline: { title: 'Офлайн с кэшем', render: () => view({ banner: D.banner('Офлайн — лайк отправим, когда появится сеть') }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['heart', 'light'], ['scrolled', 'light'], ['more', 'light'], ['saved', 'dark'], ['loading', 'light'], ['error', 'light'], ['forbidden', 'light'], ['offline', 'light'], ['content', 'light', 2]],
  });

  // ------------------------------------------------------------------ Комментарии
  const comment = (who, time, text, o = {}) => `<div class="row" style="align-items:flex-start;${o.reply ? 'padding-left:68px;' : ''}${o.failed ? 'opacity:.9;' : ''}">
      ${D.avatar(who, o.reply ? 'xs' : 's')}
      <div class="row__body"><div class="ds-t-body-small"><b style="font-weight:600;color:var(--ds-color-on-surface)">${D.USERS[who].handle}</b> <span class="muted">${time}</span>${o.author ? ` ${D.tbadge('Автор', 'accent')}` : ''}</div>
        <div class="ds-t-body-medium" style="margin-top:2px">${text}</div>
        ${o.failed ? `<div class="msg-status" style="align-self:flex-start;margin-top:4px">${D.icon('error', 'ic--s')} Не отправлено · <b style="font-weight:600">Повторить</b></div>` : `<div class="ds-t-label-medium muted" style="margin-top:6px">Ответить</div>`}</div>
      ${o.failed ? '' : `<span class="like ${o.liked ? 'like--on' : ''}" style="min-width:40px;padding:0;flex-direction:column;gap:0;font-size:11px">${D.icon(o.liked ? 'favorite_filled' : 'favorite', 'ic--s')}${o.likes || ''}</span>`}</div>`;

  const composer = (o = {}) => `<div class="composer">${D.avatar('me', 's')}<div class="composer__field ${o.text ? 'composer__field--filled' : ''}">${o.text || 'Добавьте комментарий…'}${o.text ? '<span class="caret"></span>' : ''}</div>${D.iconBtn('send', { label: 'Отправить', disabled: !o.text, variant: o.text ? 'filled' : '' })}</div>`;

  const commentsSheet = (content, o = {}) => view({
    overlay: `<div class="scrim"></div><div class="sheet" style="height:640px" role="dialog" aria-label="Комментарии"><div class="sheet__handle"></div>
      <div class="sheet__title" style="display:flex;align-items:baseline;gap:8px">Комментарии <span class="ds-t-body-medium muted num">${o.count != null ? o.count : 24}</span></div>
      <div style="flex:1;overflow:hidden;display:flex;flex-direction:column">${content}</div>${composer(o)}${o.snackbar || ''}</div>`,
  });

  const list = () => [
    comment('mila', '1 ч', 'Идеальная база на межсезонье. Где брали лоферы?', { likes: 12, liked: true }),
    comment('anna', '48 мин', 'Лоферы из каталога — «Лоферы классические», есть в вещах образа ✨', { reply: true, author: true, likes: 4 }),
    comment('dan', '32 мин', 'Сделал ремикс с пуховиком — посмотрите в профиле', { likes: 2 }),
    comment('sonya', '5 мин', 'Сохраню в коллекцию «Осень»', {}),
  ].join('');

  M.screen({
    id: 'comments',
    section: 'Основное',
    title: 'Комментарии',
    description: 'Bottom sheet поверх образа: фото остаётся видимым сверху. Ответы — с отступом, автор образа помечен бейджем. Удаление — сразу, со снекбаром «Отменить»; неотправленный комментарий остаётся в списке с «Повторить».',
    states: {
      content: { title: 'Контент', render: () => commentsSheet(list()) },
      typing: { title: 'Ввод', render: () => commentsSheet(list(), { text: 'Лоферы просто огонь' }) },
      empty: { title: 'Пусто', render: () => commentsSheet(D.state({ art: 'empty_comments', title: 'Пока тихо', text: 'Напишите первым — автор увидит уведомление.' }), { count: 0 }) },
      loading: { title: 'Загрузка', render: () => commentsSheet(`<div class="skeleton-shimmer">${D.skRow()}${D.skRow({ w1: 40, w2: 70 })}${D.skRow({ w1: 52 })}</div>`) },
      failed: { title: 'Не отправлено', render: () => commentsSheet([comment('mila', '1 ч', 'Идеальная база на межсезонье. Где брали лоферы?', { likes: 12 }), comment('me', 'сейчас', 'Лоферы просто огонь', { failed: true })].join('')) },
      deleted: {
        title: 'Удалено + «Отменить»',
        render: () => commentsSheet([comment('mila', '1 ч', 'Идеальная база на межсезонье. Где брали лоферы?', { likes: 12 }), comment('dan', '32 мин', 'Сделал ремикс с пуховиком — посмотрите в профиле', { likes: 2 })].join(''), { snackbar: D.snackbar('Комментарий удалён', { action: 'Отменить', bottom: 76, timer: 64 }) }),
      },
      error: { title: 'Ошибка сети', render: () => commentsSheet(D.state({ art: 'error', title: 'Не загрузились', text: 'Попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' })) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['typing', 'light'], ['empty', 'light'], ['loading', 'light'], ['failed', 'light'], ['deleted', 'light'], ['error', 'dark']],
  });
})();
