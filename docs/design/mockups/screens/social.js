/* Подписки/подписчики, поиск, коллекции, уведомления. */
(function () {
  const D = window.DS, M = window.DSM, A = window.DS_ART;

  // ------------------------------------------------------------------ Подписки / подписчики
  const people = [
    ['mila', 'mila_o', 'following'], ['dan', 'dan.sh · взаимно', 'following'], ['vera', 'verafox', 'follow'],
    ['oleg', 'oleg.kim', 'follow'], ['sonya', 'sonya.b · подписана на вас', 'follow'], ['artem', 'artem.v', 'following'], ['nika', 'nika_z', 'follow'],
  ];
  const personRow = ([u, sub, st]) => D.row({
    avatar: u, title: D.USERS[u].name, sub, strong: true, oneLine: true,
    end: st === 'following' ? D.btn('Вы подписаны', { variant: 'secondary', size: 'small' }) : D.btn('Подписаться', { variant: 'primary', size: 'small' }),
  });
  const followTop = () => D.appbar({ nav: 'arrow_back', title: 'anna.kov' }) + D.tabs([['Подписчики', '12,4K'], ['Подписки', 210]], 0);

  M.screen({
    id: 'follows',
    section: 'Люди',
    title: 'Подписки и подписчики',
    description: 'Две вкладки со счётчиками, поиск по списку. Кнопка в строке меняет вид, а не цвет: primary «Подписаться» → secondary «Вы подписаны». Взаимность — текстом в подзаголовке.',
    states: {
      content: { title: 'Подписчики', render: () => M.phone({ body: followTop() + `<div class="gutter" style="padding-top:12px">${D.search('', { placeholder: true })}</div><div class="scroll" style="padding-top:4px">${people.map(personRow).join('')}</div>` }) },
      search: { title: 'Поиск по списку', render: () => M.phone({ body: followTop() + `<div class="gutter" style="padding-top:12px">${D.search('ми', { focused: true })}</div><div class="scroll" style="padding-top:4px">${[people[0], ['me', 'liza.mir · это вы', 'none']].map((p) => p[2] === 'none' ? D.row({ avatar: p[0], title: D.USERS[p[0]].name, sub: p[1], strong: true }) : personRow(p)).join('')}</div>` }) },
      empty: { title: 'Пусто', render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', title: 'liza.mir' }) + D.tabs([['Подписчики', 0], ['Подписки', 12]], 0) + D.state({ art: 'people', title: 'Пока никого', text: 'Поделитесь профилем — подписчики появятся здесь.', action: 'Поделиться профилем', actionIcon: 'share' }) }) },
      loading: { title: 'Загрузка', render: () => M.phone({ body: followTop() + `<div class="skeleton-shimmer" style="padding-top:12px">${[58, 44, 66, 50, 40, 62].map((w) => D.skRow({ w1: w, w2: 40, end: true })).join('')}</div>` }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ body: followTop() + D.state({ art: 'error', title: 'Список не загрузился', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }) },
      offline: { title: 'Офлайн с кэшем', render: () => M.phone({ body: followTop() + D.banner('Офлайн — подписка применится, когда появится сеть') + `<div class="scroll">${people.slice(0, 6).map(personRow).join('')}</div>` }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['search', 'light'], ['empty', 'light'], ['loading', 'light'], ['error', 'light'], ['offline', 'light'], ['content', 'light', 2]],
  });

  // ------------------------------------------------------------------ Поиск
  const searchTop = (text, o = {}) => `<div class="gutter" style="padding-top:var(--ds-space-3)">${o.large ? '<div class="ds-t-display-small" style="margin-bottom:var(--ds-space-4)">Поиск</div>' : ''}${D.search(text, o)}</div>`;
  const filters = (sel) => D.chips(['Образы', 'Люди', 'Вещи', 'Теги'].map((t, i) => D.chip(t, { selected: i === sel })), { style: 'padding-top:12px;padding-bottom:4px' });

  M.screen({
    id: 'search',
    section: 'Люди',
    title: 'Поиск',
    description: 'До ввода — недавнее, тренды и люди; после — фильтр-чипы «Образы · Люди · Вещи · Теги» (выбор — инверсная заливка, не только цвет) и результаты. Ничего не нашлось — подсказка переформулировать и популярные теги.',
    states: {
      initial: {
        title: 'До ввода',
        render: () => M.phone({
          nav: 'search',
          body: searchTop('', { large: true, placeholder: true }) + `<div class="scroll">
            <div class="section__head" style="padding-top:20px"><span class="kicker">Недавние</span><span class="link">Очистить</span></div>
            ${['тренч', '@anna.kov', '#капсула'].map((q) => D.row({ icon: 'history', title: q, end: D.iconBtn('close', { label: 'Удалить из истории', variant: 'muted', icls: 'ic--m' }), style: 'min-height:48px;padding-top:0;padding-bottom:0' })).join('')}
            <div class="section__head" style="padding-top:16px"><span class="kicker">В тренде</span></div>
            ${D.chips(['#осень', '#тренч', '#минимализм', '#винтаж', '#офис', '#лён'].map((t) => D.chip(t, { variant: 'suggestion' })), { wrap: true, style: 'padding:0 20px;gap:8px' })}
            <div class="section__head" style="padding-top:24px"><span class="kicker">Люди для вас</span></div>
            <div style="display:flex;gap:16px;padding:0 20px">${['vera', 'oleg', 'sonya', 'nika'].map((u) => `<div style="width:72px;text-align:center">${D.avatar(u, 'l')}<div class="ds-t-body-small row__one" style="margin-top:6px">${D.USERS[u].name.split(' ')[0]}</div></div>`).join('')}</div></div>`,
        }),
      },
      results: {
        title: 'Результаты: образы',
        render: () => M.phone({ nav: 'search', body: searchTop('тренч') + filters(0) + `<div class="scroll gutter" style="padding-top:8px"><div class="ds-t-body-small muted" style="margin-bottom:8px">128 образов</div><div class="grid grid--2">${['trench', 'olive', 'forest', 'denim'].map((l, i) => D.outfitCard(l, { compact: true, author: ['anna', 'sonya', 'mila', 'dan'][i], likes: [128, 64, 31, 12][i], liked: i === 0 })).join('')}</div></div>` }),
      },
      items: {
        title: 'Результаты: вещи',
        render: () => M.phone({ nav: 'search', body: searchTop('тренч') + filters(2) + `<div class="scroll gutter" style="padding-top:8px"><div class="grid grid--2">${[['trench', 'camel', 'Тренч классический', 'в 214 образах'], ['trench', 'khaki', 'Тренч укороченный', 'в 58 образах'], ['trench', 'black', 'Тренч чёрный', 'в 97 образах'], ['trench', 'stone', 'Тренч оверсайз', 'Новое']].map(([k, c, t, s], i) => D.itemCard({ kind: k, color: c, title: t, sub: s, badge: i === 3 ? 'Новое' : null, badgeVariant: 'accent' })).join('')}</div></div>` }),
      },
      empty: {
        title: 'Ничего не найдено',
        render: () => M.phone({ nav: 'search', body: searchTop('тренч в клетку') + filters(0) + D.state({ art: 'empty_search', title: 'Ничего не нашлось', text: 'Попробуйте короче или по-другому: «клетка», «тренч».', style: 'flex:none;padding-top:24px;padding-bottom:16px' }) + D.chips(['#тренч', '#клетка', '#осень'].map((t) => D.chip(t, { variant: 'suggestion' })), { style: 'justify-content:center' }) }),
      },
      loading: { title: 'Загрузка', render: () => M.phone({ nav: 'search', body: searchTop('тренч') + filters(0) + `<div class="grid grid--2 gutter skeleton-shimmer" style="padding-top:28px">${[0, 1, 2, 3].map(() => '<div><div class="sk ratio-4-5"></div><div class="sk sk--line" style="width:70%;margin-top:10px"></div></div>').join('')}</div>` }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ nav: 'search', body: searchTop('тренч') + filters(0) + D.state({ art: 'error', title: 'Поиск не ответил', text: 'Попробуйте ещё раз через пару секунд.', action: 'Повторить', actionIcon: 'refresh' }) }) },
      offline: { title: 'Офлайн', render: () => M.phone({ nav: 'search', body: searchTop('тренч') + filters(0) + D.state({ art: 'offline', title: 'Поиск без сети не работает', text: 'Недавние запросы и сохранённое доступны в профиле.', action: 'Повторить', actionIcon: 'refresh' }) }) },
    },
    board: [['initial', 'light'], ['initial', 'dark'], ['results', 'light'], ['items', 'light'], ['empty', 'light'], ['loading', 'light'], ['error', 'light'], ['offline', 'light'], ['results', 'light', 2]],
  });

  // ------------------------------------------------------------------ Коллекции
  const cover = (looks) => `<div class="photo ratio-3-4" style="display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:2px;background:var(--ds-color-background)">${looks.map((l) => `<div style="position:relative;overflow:hidden">${A.outfit(A.LOOKS[l])}</div>`).join('')}</div>`;
  const collection = (title, n, looks, o = {}) => `<div>${cover(looks)}<div class="ds-t-title-small" style="margin-top:8px;display:flex;align-items:center;gap:6px">${title}${o.lock ? D.icon('lock', 'ic--s') : ''}</div><div class="ds-t-body-small muted">${n}</div></div>`;
  const collTop = () => D.appbar({ nav: 'arrow_back', title: 'Коллекции', actions: [D.iconBtn('add', { label: 'Новая коллекция' })] });

  M.screen({
    id: 'collections',
    section: 'Люди',
    title: 'Коллекции',
    description: 'Обложка коллекции — мозаика 2×2 из последних образов. «Сохранённое» создаётся автоматически. Приватные коллекции помечены замком (иконка + текст для TalkBack).',
    states: {
      content: {
        title: 'Список',
        render: () => M.phone({ body: collTop() + `<div class="scroll gutter" style="padding-top:8px"><div class="grid grid--2" style="row-gap:20px">
          ${collection('Сохранённое', '64 образа', ['trench', 'slip', 'olive', 'denim'])}${collection('Осень 2026', '18 образов', ['forest', 'rust', 'trench', 'lilac'])}
          ${collection('Офис без скуки', '9 образов', ['denim', 'night', 'lilac', 'olive'], { lock: true })}${collection('Море', '5 образов', ['summer', 'slip', 'summer', 'lilac'])}</div></div>` }),
      },
      detail: {
        title: 'Коллекция',
        render: () => M.phone({ body: D.appbar({ nav: 'arrow_back', actions: [D.iconBtn('share', { label: 'Поделиться' }), D.iconBtn('more_vert', { label: 'Ещё' })] }) + `<div class="scroll"><div class="large-title"><div class="kicker">Коллекция · 18 образов</div><div class="ds-t-display-small" style="margin-top:4px">Осень 2026</div><div class="ds-t-body-medium muted" style="margin-top:6px">Слои, шерсть и немного ржавчины.</div></div><div class="grid grid--2 gutter">${['forest', 'rust', 'trench', 'lilac'].map((l) => D.outfitCard(l, { compact: true })).join('')}</div></div>` }),
      },
      empty: { title: 'Пусто', render: () => M.phone({ body: collTop() + D.state({ art: 'empty_collections', title: 'Сохраняйте образы', text: 'Нажмите на закладку под образом — он попадёт в «Сохранённое» или в вашу коллекцию.', action: 'Смотреть ленту' }) }) },
      create: {
        title: 'Новая коллекция',
        render: () => M.phone({
          body: collTop() + `<div class="scroll gutter" style="padding-top:8px"><div class="grid grid--2" style="row-gap:20px">${collection('Сохранённое', '64 образа', ['trench', 'slip', 'olive', 'denim'])}${collection('Осень 2026', '18 образов', ['forest', 'rust', 'trench', 'lilac'])}</div></div>`,
          overlay: D.sheet({ title: 'Новая коллекция', content: `<div class="gutter">${D.field({ label: 'Название', value: 'Капсула на выходные', focused: true, counter: '19 / 40' })}</div>${D.row({ icon: 'lock', title: 'Приватная', sub: 'Видите только вы', switch: false })}<div class="gutter" style="padding:8px 20px 20px">${D.btn('Создать', { variant: 'primary', size: 'large', block: true })}</div>` }),
        }),
      },
      offline: { title: 'Офлайн с кэшем', render: () => M.phone({ body: collTop() + D.banner('Офлайн — новые коллекции создадим, когда появится сеть') + `<div class="scroll gutter" style="padding-top:8px"><div class="grid grid--2" style="row-gap:20px">${collection('Сохранённое', '64 образа', ['trench', 'slip', 'olive', 'denim'])}${collection('Осень 2026', '18 образов', ['forest', 'rust', 'trench', 'lilac'])}</div></div>` }) },
      loading: { title: 'Загрузка', render: () => M.phone({ body: collTop() + `<div class="grid grid--2 gutter skeleton-shimmer" style="padding-top:8px;row-gap:20px">${[0, 1, 2, 3].map(() => '<div><div class="sk ratio-3-4"></div><div class="sk sk--line" style="width:60%;margin-top:10px"></div><div class="sk sk--line-s" style="width:40%;margin-top:8px"></div></div>').join('')}</div>` }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ body: collTop() + D.state({ art: 'error', title: 'Коллекции не загрузились', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['detail', 'light'], ['empty', 'light'], ['create', 'light'], ['loading', 'light'], ['offline', 'light'], ['error', 'light']],
  });

  // ------------------------------------------------------------------ Уведомления
  const thumb = (look) => `<span class="row__thumb" style="width:44px;height:55px">${A.outfit(A.LOOKS[look])}</span>`;
  const unread = (row) => `<div style="position:relative">${row}<span class="dot" style="position:absolute;left:8px;top:50%;margin-top:-4px"></span></div>`;
  const notifList = () => `<div class="scroll">
      <div class="group-title" style="padding-top:8px"><span class="kicker">Новые</span></div>
      ${unread(D.row({ lead: `<span class="avatar-stack">${D.avatar('mila', 's')}${D.avatar('dan', 's')}</span>`, body: true, title: '<b style="font-weight:600">mila_o</b> и ещё 12 оценили ваш образ', sub: '2 мин', end: thumb('olive'), sub2: true }))}
      ${unread(D.row({ avatar: 'dan', body: true, title: '<b style="font-weight:600">dan.sh</b> прокомментировал: «Сделал ремикс с пуховиком»', sub: '32 мин', end: thumb('olive') }))}
      ${unread(D.row({ lead: `<span class="avatar avatar--m" style="background:var(--ds-color-accent-container);color:var(--ds-color-on-accent-container)">${D.icon('sparkle')}</span>`, body: true, title: 'Вещь «Тренч классический» опубликована в каталоге', sub: '1 ч · Студия', end: `<span class="row__thumb">${A.item('trench', 'camel')}</span>` }))}
      <div class="group-title"><span class="kicker">На этой неделе</span></div>
      ${D.row({ avatar: 'vera', body: true, title: '<b style="font-weight:600">verafox</b> подписалась на вас', sub: '2 дн', end: D.btn('Подписаться', { variant: 'primary', size: 'small' }) })}
      ${D.row({ avatar: 'sonya', body: true, title: '<b style="font-weight:600">sonya.b</b> сделала ремикс вашего образа', sub: '3 дн', end: thumb('rust') })}
      ${D.row({ avatar: 'oleg', body: true, title: '<b style="font-weight:600">oleg.kim</b> сохранил образ в «Офис»', sub: '5 дн', end: thumb('denim') })}</div>`;
  const notifTop = () => D.appbar({ nav: 'arrow_back', title: 'Уведомления', actions: [D.iconBtn('done_all', { label: 'Прочитать все' })] });

  M.screen({
    id: 'notifications',
    section: 'Люди',
    title: 'Уведомления',
    description: 'Группы «Новые / На этой неделе / Раньше». Непрочитанное — точка акцента слева и жирное имя. Однотипные события схлопываются («и ещё 12»). Если push выключены в системе — баннер с переходом в настройки (состояние «нет прав»).',
    states: {
      content: { title: 'Контент', render: () => M.phone({ body: notifTop() + notifList() }) },
      permission: { title: 'Push выключены', render: () => M.phone({ body: notifTop() + D.banner('Уведомления выключены в настройках телефона', { icon: 'notifications_off', action: 'Включить', variant: 'info' }) + notifList() }) },
      offline: { title: 'Офлайн с кэшем', render: () => M.phone({ body: notifTop() + D.banner('Офлайн — показываем сохранённое') + notifList() }) },
      empty: { title: 'Пусто', render: () => M.phone({ body: notifTop() + D.state({ art: 'empty_notifications', title: 'Пока тихо', text: 'Здесь появятся лайки, комментарии, подписки и новости Студии.' }) }) },
      loading: { title: 'Загрузка', render: () => M.phone({ body: notifTop() + `<div class="skeleton-shimmer" style="padding-top:12px">${[70, 54, 62, 48, 66].map((w) => D.skRow({ w1: w, w2: 30 })).join('')}</div>` }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ body: notifTop() + D.state({ art: 'error', title: 'Не загрузились', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['permission', 'light'], ['empty', 'light'], ['loading', 'light'], ['offline', 'light'], ['error', 'light'], ['content', 'light', 2]],
  });
})();
