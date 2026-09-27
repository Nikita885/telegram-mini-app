/* Диалоги и чат. */
(function () {
  const D = window.DS, M = window.DSM, A = window.DS_ART;

  // ------------------------------------------------------------------ Диалоги
  const dialog = (u, text, time, o = {}) => D.row({
    avatar: u, online: o.online, title: D.USERS[u].name, strong: true, oneLine: true,
    sub: `${o.outfit ? `${D.icon('hanger', 'ic--s')} ` : ''}${o.typing ? '<span style="color:var(--ds-color-accent)">печатает…</span>' : text}`,
    end: `<span style="display:flex;flex-direction:column;align-items:flex-end;gap:6px"><span class="row__meta">${time}</span>${o.unread ? `<span class="count-badge" aria-label="${o.unread} непрочитанных">${o.unread}</span>` : o.read ? `<span style="color:var(--ds-color-on-surface-variant)">${D.icon('done_all', 'ic--s')}</span>` : '<span style="height:16px"></span>'}</span>`,
    style: o.unread ? '' : '',
  });
  const list = () => `<div class="scroll">
      <div class="group-title" style="padding-top:4px"><span class="kicker">Закреплённые</span></div>
      ${dialog('anna', 'Образ: Тренч и широкие брюки', '12:40', { outfit: true, unread: 2, online: true })}
      <div class="group-title" style="padding-top:12px"><span class="kicker">Все</span></div>
      ${dialog('mila', '', '12:02', { typing: true, unread: 1, online: true })}
      ${dialog('dan', 'Скинь ссылку на лоферы', '11:15', { read: true })}
      ${dialog('sonya', 'Спасибо! Сохранила в коллекцию', 'вчера', {})}
      ${dialog('oleg', 'Образ: Чёрный блейзер и брюки', 'пн', { outfit: true, read: true })}
      ${dialog('nika', 'Ок, договорились 👌', '24 сен', {})}</div>`;
  const top = () => D.appbar({ title: 'Диалоги', editorial: true, actions: [D.iconBtn('search', { label: 'Поиск по диалогам' }), D.iconBtn('edit', { label: 'Новое сообщение' })] });

  M.screen({
    id: 'dialogs',
    section: 'Сообщения',
    title: 'Диалоги',
    description: 'Закреплённые сверху, затем по времени. Непрочитанное — счётчик акцентного цвета (число, не только цвет), прочитанное — двойная галочка. Поделённый образ — иконка вешалки перед текстом. «Печатает…» — акцентом, обновляется в реальном времени через WebSocket.',
    states: {
      content: { title: 'Контент', render: () => M.phone({ nav: 'chats', badges: { chats: 3 }, body: top() + list() }) },
      empty: { title: 'Пусто', render: () => M.phone({ nav: 'chats', badges: {}, body: top() + D.state({ art: 'empty_messages', title: 'Пока нет диалогов', text: 'Напишите автору образа или поделитесь своим — прямо из просмотра.', action: 'Найти людей' }) }) },
      loading: { title: 'Загрузка', render: () => M.phone({ nav: 'chats', badges: {}, body: top() + `<div class="skeleton-shimmer" style="padding-top:12px">${[48, 60, 40, 56, 52, 44].map((w) => D.skRow({ w1: w, w2: 70 })).join('')}</div>` }) },
      offline: { title: 'Офлайн с кэшем', render: () => M.phone({ nav: 'chats', body: top() + D.banner('Офлайн — новые сообщения придут, когда появится сеть') + list() }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ nav: 'chats', badges: {}, body: top() + D.state({ art: 'error', title: 'Диалоги не загрузились', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['empty', 'light'], ['loading', 'light'], ['offline', 'light'], ['error', 'light'], ['content', 'light', 2]],
  });

  // ------------------------------------------------------------------ Чат
  const chatTop = (o = {}) => `<div class="appbar appbar--lifted">${D.iconBtn('arrow_back', { label: 'Назад' })}${D.avatar('anna', 's', { online: !o.offline })}
      <div class="appbar__title" style="padding-left:12px;font-size:var(--ds-type-title-medium-size);line-height:var(--ds-type-title-medium-line)">Анна Ковалёва<span class="appbar__sub">${o.offline ? 'ожидание сети…' : o.typing ? '<span style="color:var(--ds-color-accent)">печатает…</span>' : 'в сети'}</span></div>
      ${D.iconBtn('more_vert', { label: 'Ещё' })}</div>`;
  const inMsg = (t, time) => `<div class="bubble bubble--in">${t}<span class="bubble__time">${time}</span></div>`;
  const outMsg = (t, time, o = {}) => `<div class="bubble bubble--out ${o.failed ? 'bubble--failed' : ''}">${t}<span class="bubble__time">${time}${o.queued ? D.icon('schedule') : o.failed ? '' : D.icon('done_all')}</span></div>${o.failed ? `<div class="msg-status">${D.icon('error', 'ic--s')} Не отправлено · <b style="font-weight:600">Повторить</b></div>` : ''}`;
  const shared = () => `<div class="bubble bubble--in" style="padding:6px;width:212px"><div style="width:200px">${D.photo('trench', { style: 'border-radius:14px 14px 4px 4px' })}</div>
      <div style="padding:8px 6px 2px"><div class="ds-t-title-small">Тренч и широкие брюки</div><div class="ds-t-body-small muted">Образ @anna.kov · 5 вещей</div></div></div>`;
  const composer = (o = {}) => `<div class="composer">${D.iconBtn('hanger', { label: 'Поделиться образом' })}<div class="composer__field ${o.text ? 'composer__field--filled' : ''}">${o.text || 'Сообщение'}${o.text ? '<span class="caret"></span>' : ''}</div>${D.iconBtn('send', { label: 'Отправить', disabled: !o.text, variant: o.text ? 'filled' : '' })}</div>`;
  const thread = (o = {}) => `<div class="scroll" style="display:flex;flex-direction:column;justify-content:flex-end;gap:6px;padding:8px 12px">
      <span class="date-sep">Сегодня</span>
      ${inMsg('Смотри, что собрала на завтра 👀', '12:31')}${shared()}
      ${outMsg('Лоферы идеальные. Это из каталога?', '12:38')}
      ${inMsg('Да, «Лоферы классические» — они в вещах образа', '12:40')}
      ${o.extra || ''}${o.typing ? '<div class="bubble bubble--in" style="padding:0"><span class="typing"><i></i><i></i><i></i></span></div>' : ''}</div>`;

  M.screen({
    id: 'chat',
    section: 'Сообщения',
    title: 'Чат',
    description: 'Входящие — тональная поверхность, исходящие — primary (графит/молочный), хвостик — радиус bubbleTail. Поделённый образ — карточка с фото прямо в ленте сообщений. Неотправленное остаётся на месте с «Повторить»; без сети сообщение встаёт в очередь (часы вместо галочек).',
    states: {
      content: { title: 'Контент', render: () => M.phone({ body: chatTop() + thread() + composer() }) },
      typing: { title: 'Собеседник печатает', render: () => M.phone({ body: chatTop({ typing: true }) + thread({ typing: true }) + composer({ text: 'Беру!' }) }) },
      failed: { title: 'Не отправлено', render: () => M.phone({ body: chatTop() + thread({ extra: outMsg('Тогда беру такие же 🙌', '12:44', { failed: true }) }) + composer() }) },
      offline: { title: 'Офлайн: очередь', render: () => M.phone({ body: chatTop({ offline: true }) + D.banner('Нет сети — отправим, когда она появится') + thread({ extra: outMsg('Тогда беру такие же 🙌', '12:44', { queued: true }) }) + composer() }) },
      empty: {
        title: 'Новый диалог',
        render: () => M.phone({ body: chatTop() + `<div class="state" style="flex:1">${D.avatar('anna', 'xl')}<div class="state__title" style="margin-top:16px">Анна Ковалёва</div><div class="state__text">@anna.kov · 186 образов</div></div>` + D.chips([D.chip('👋 Привет!', { variant: 'suggestion' }), D.chip('Поделиться образом', { variant: 'suggestion', icon: 'hanger' })], { style: 'justify-content:center;padding-bottom:8px' }) + composer() }),
      },
      loading: { title: 'Загрузка', render: () => M.phone({ body: chatTop() + `<div class="scroll skeleton-shimmer" style="display:flex;flex-direction:column;justify-content:flex-end;gap:8px;padding:8px 12px"><div class="sk" style="width:60%;height:40px;border-radius:20px"></div><div class="sk" style="width:212px;height:300px;border-radius:20px"></div><div class="sk" style="width:55%;height:40px;border-radius:20px;align-self:flex-end"></div><div class="sk" style="width:70%;height:40px;border-radius:20px"></div></div>` + composer() }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['typing', 'light'], ['failed', 'light'], ['offline', 'light'], ['empty', 'light'], ['loading', 'light'], ['content', 'light', 2]],
  });
})();
