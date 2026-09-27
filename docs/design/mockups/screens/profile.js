/* Профиль (свой/чужой), редактирование профиля. */
(function () {
  const D = window.DS, M = window.DSM;
  const LOOKS = ['olive', 'trench', 'rust', 'denim', 'slip', 'forest', 'summer', 'night', 'lilac'];

  const header = (user, o = {}) => `<div class="gutter" style="padding-top:var(--ds-space-2)">
      <div class="profile-head" style="display:flex;align-items:center;gap:var(--ds-space-5)">${D.avatar(user, 'xl')}
        <div style="flex:1;display:flex;justify-content:space-between">
          <div class="counter"><b>${o.posts || 42}</b><span>образа</span></div>
          <div class="counter"><b>${o.followers || '1,2 тыс.'}</b><span>подписчики</span></div>
          <div class="counter"><b>${o.following || 318}</b><span>подписки</span></div>
        </div></div>
      <div class="ds-t-headline-small" style="margin-top:var(--ds-space-4)">${D.USERS[user].name}${o.verified ? ` <span style="color:var(--ds-color-accent)">${D.icon('verified', 'ic--m')}</span>` : ''}</div>
      <div class="ds-t-body-medium" style="margin-top:var(--ds-space-1)">${o.bio || D.USERS[user].bio || 'Минимализм, лён и хорошие ботинки.'}</div>
      <div class="btn-row" style="display:flex;gap:var(--ds-space-2);margin-top:var(--ds-space-4)">${o.buttons}</div></div>`;

  const grid = (looks = LOOKS, o = {}) => `<div class="grid grid--3" style="margin-top:2px">${looks.map((l, i) => `<div style="position:relative">${D.photo(l, { style: 'border-radius:0' })}${o.badges && o.badges[i] ? `<span style="position:absolute;top:6px;right:6px;color:#fff;filter:drop-shadow(0 1px 2px rgba(0,0,0,.4))">${D.icon(o.badges[i], 'ic--m')}</span>` : ''}</div>`).join('')}</div>`;

  const ownButtons = D.btn('Редактировать', { variant: 'secondary', size: 'small', style: 'flex:1' }) + D.btn('Поделиться', { variant: 'secondary', size: 'small', style: 'flex:1' });
  const ownTop = () => D.appbar({ title: 'liza.mir', actions: [D.iconBtn('add', { label: 'Новый образ' }), D.iconBtn('settings', { label: 'Настройки' })] });

  M.screen({
    id: 'profile',
    section: 'Профиль',
    title: 'Профиль (свой)',
    description: 'Обложка журнала о себе: крупный аватар, счётчики набраны антиквой, сетка образов 3×N без зазоров-«рамок». Вкладки: образы, коллекции, черновики. Черновики, не ушедшие без сети, помечены бейджем.',
    states: {
      content: { title: 'Образы', render: () => M.phone({ nav: 'profile', body: ownTop() + `<div class="scroll">${header('me', { buttons: ownButtons })}<div style="height:16px"></div>${D.tabs(['Образы', 'Коллекции', 'Черновики'], 0)}${grid(LOOKS, { badges: { 1: 'remix' } })}</div>` }) },
      drafts: {
        title: 'Черновики',
        render: () => M.phone({
          nav: 'profile',
          body: ownTop() + `<div class="scroll">${header('me', { buttons: ownButtons })}<div style="height:16px"></div>${D.tabs(['Образы', 'Коллекции', ['Черновики', 2]], 2)}
            ${D.row({ lead: `<span class="row__thumb" style="width:64px;height:80px">${D.art.outfit(D.art.LOOKS.street)}</span>`, title: 'Без названия', sub: 'Изменён вчера · 5 вещей', strong: true, end: D.tbadge('Черновик') })}
            ${D.row({ lead: `<span class="row__thumb" style="width:64px;height:80px">${D.art.outfit(D.art.LOOKS.summer)}</span>`, title: 'Лето у моря', sub: 'Ждёт сети — опубликуется сам', strong: true, end: D.tbadge('В очереди', 'warning', 'schedule') })}</div>`,
        }),
      },
      empty: {
        title: 'Пусто',
        render: () => M.phone({ nav: 'profile', body: ownTop() + header('me', { buttons: ownButtons, posts: 0, followers: 3, following: 12 }) + `<div style="height:16px"></div>${D.tabs(['Образы', 'Коллекции', 'Черновики'], 0)}` + D.state({ art: 'empty_outfits', title: 'Соберите первый образ', text: 'Вещи из каталога сами сядут на манекен.', action: 'Открыть конструктор', style: 'padding-top:24px' }) }),
      },
      offline: { title: 'Офлайн с кэшем', render: () => M.phone({ nav: 'profile', body: ownTop() + D.banner('Офлайн — профиль из кэша, счётчики обновятся позже') + `<div class="scroll">${header('me', { buttons: ownButtons })}<div style="height:16px"></div>${D.tabs(['Образы', 'Коллекции', 'Черновики'], 0)}${grid(LOOKS)}</div>` }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ nav: 'profile', body: ownTop() + D.state({ art: 'error', title: 'Профиль не загрузился', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }) },
      loading: {
        title: 'Загрузка',
        render: () => M.phone({
          nav: 'profile',
          body: ownTop() + `<div class="skeleton-shimmer gutter" style="padding-top:8px"><div style="display:flex;align-items:center;gap:20px"><div class="sk sk--circle" style="width:88px;height:88px"></div><div style="flex:1;display:flex;justify-content:space-between">${[0, 1, 2].map(() => '<div style="width:56px"><div class="sk sk--line" style="width:70%;height:20px"></div><div class="sk sk--line-s" style="margin-top:8px"></div></div>').join('')}</div></div>
            <div class="sk sk--line" style="width:50%;height:18px;margin-top:20px"></div><div class="sk sk--line-s" style="width:80%;margin-top:12px"></div>
            <div style="display:flex;gap:8px;margin-top:20px"><div class="sk" style="flex:1;height:40px;border-radius:20px"></div><div class="sk" style="flex:1;height:40px;border-radius:20px"></div></div></div>
            <div style="height:16px"></div>${D.tabs(['Образы', 'Коллекции', 'Черновики'], 0)}<div class="grid grid--3 skeleton-shimmer" style="margin-top:2px">${[0, 1, 2, 3, 4, 5].map(() => '<div class="sk ratio-4-5" style="border-radius:0"></div>').join('')}</div>`,
        }),
      },
    },
    board: [['content', 'light'], ['content', 'dark'], ['drafts', 'light'], ['empty', 'light'], ['loading', 'light'], ['offline', 'light'], ['error', 'light'], ['content', 'light', 2]],
  });

  // ------------------------------------------------------------------ Чужой профиль
  const otherTop = () => D.appbar({ nav: 'arrow_back', title: 'anna.kov', actions: [D.iconBtn('more_vert', { label: 'Ещё' })] });
  const other = (buttons, body, o = {}) => M.phone({ nav: 'feed', body: otherTop() + `<div class="scroll">${header('anna', Object.assign({ buttons, followers: '12 тыс.', following: 210, posts: 186, verified: true, bio: 'Стилист. Капсулы на каждый сезон · Санкт-Петербург' }, o))}<div style="height:16px"></div>${body}</div>` });

  M.screen({
    id: 'user',
    section: 'Профиль',
    title: 'Профиль (чужой)',
    description: 'Главное действие — «Подписаться» (primary). После подписки кнопка становится secondary «Вы подписаны» — действие обратимо без подтверждения. Закрытый профиль и блокировка — отдельные состояния «нет прав».',
    states: {
      content: { title: 'Не подписаны', render: () => other(D.btn('Подписаться', { variant: 'primary', size: 'small', style: 'flex:1' }) + D.btn('Сообщение', { variant: 'secondary', size: 'small', style: 'flex:1' }), D.tabs(['Образы', 'Коллекции'], 0) + grid(['slip', 'trench', 'olive', 'lilac', 'rust', 'denim'])) },
      following: { title: 'Подписаны', render: () => other(D.btn('Вы подписаны', { variant: 'secondary', size: 'small', icon: 'check', style: 'flex:1' }) + D.btn('Сообщение', { variant: 'secondary', size: 'small', style: 'flex:1' }), D.tabs(['Образы', 'Коллекции'], 1) + `<div class="grid grid--2 gutter" style="padding-top:16px">${['Осень 2026', 'Офис без скуки'].map((t, i) => `<div><div class="photo ratio-3-4">${D.art.outfit(D.art.LOOKS[i ? 'denim' : 'trench'])}</div><div class="ds-t-title-small" style="margin-top:8px">${t}</div><div class="ds-t-body-small muted">${i ? 18 : 34} образа</div></div>`).join('')}</div>`) },
      private: { title: 'Закрытый профиль', render: () => other(D.btn('Отправить заявку', { variant: 'primary', size: 'small', style: 'flex:1' }), D.state({ art: 'locked', title: 'Закрытый профиль', text: 'Образы видят только подписчики, которых одобрила Анна.', style: 'padding-top:24px' }), { posts: '—', followers: '12 тыс.', following: '—' }) },
      blocked: { title: 'Заблокирован', render: () => other(D.btn('Разблокировать', { variant: 'secondary', size: 'small', style: 'flex:1' }), D.state({ art: 'locked', title: 'Вы заблокировали пользователя', text: 'Анна не видит ваши образы и не может писать вам.', style: 'padding-top:24px' })) },
      offline: { title: 'Офлайн с кэшем', render: () => M.phone({ nav: 'feed', body: otherTop() + D.banner('Офлайн — подписка применится, когда появится сеть') + `<div class="scroll">${header('anna', { buttons: D.btn('Подписаться', { variant: 'primary', size: 'small', style: 'flex:1' }) + D.btn('Сообщение', { variant: 'secondary', size: 'small', style: 'flex:1' }), followers: '12 тыс.', following: 210, posts: 186, verified: true, bio: 'Стилист. Капсулы на каждый сезон · Санкт-Петербург' })}<div style="height:16px"></div>${D.tabs(['Образы', 'Коллекции'], 0)}${grid(['slip', 'trench', 'olive'])}</div>` }) },
      error: { title: 'Ошибка сети', render: () => M.phone({ nav: 'feed', body: otherTop() + D.state({ art: 'error', title: 'Профиль не загрузился', text: 'Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['following', 'light'], ['private', 'light'], ['blocked', 'light'], ['offline', 'light'], ['error', 'light']],
  });

  // ------------------------------------------------------------------ Редактирование
  const editTop = (o = {}) => D.appbar({ nav: 'close', title: 'Профиль', actions: [`<span style="padding-right:8px">${D.btn('Готово', { variant: 'primary', size: 'small', loading: o.saving, disabled: o.disabled })}</span>`] });
  const editBody = (o = {}) => `<div class="scroll gutter">
      <div style="display:flex;flex-direction:column;align-items:center;padding:var(--ds-space-4) 0">${D.avatar('me', 'xl')}${D.btn('Изменить фото', { variant: 'ghost', size: 'small', style: 'margin-top:8px' })}</div>
      <div class="stack">
        ${D.field({ label: 'Имя', value: 'Лиза Миронова' })}
        ${D.field({ label: 'Имя пользователя', value: o.usernameError ? '@liza' : '@liza.mir', focused: o.usernameError, error: o.usernameError ? 'Имя @liza уже занято' : null, help: o.usernameError ? null : 'Латиница, цифры, точка и _' })}
        ${o.usernameError ? D.chips([D.chip('@liza.style', { variant: 'suggestion' }), D.chip('@liza_mir', { variant: 'suggestion' }), D.chip('@lizamir', { variant: 'suggestion' })], { wrap: true, style: 'margin-top:8px' }) : ''}
        ${D.field({ label: 'О себе', value: 'Собираю капсулы из винтажа и базы. Москва.', counter: '44 / 150', tall: true })}
      </div></div>`;

  M.screen({
    id: 'profile-edit',
    section: 'Профиль',
    title: 'Редактирование профиля',
    description: '«Готово» активна только при изменениях. Ошибка имени пользователя — текстом под полем и подсказками свободных вариантов. Смена фото — bottom sheet; удаление фото — разрушающее действие цвета danger.',
    states: {
      content: { title: 'Форма', render: () => M.phone({ body: editTop() + editBody() }) },
      error: { title: 'Имя занято', render: () => M.phone({ body: editTop({ disabled: true }) + editBody({ usernameError: true }) }) },
      saving: { title: 'Сохранение', render: () => M.phone({ body: editTop({ saving: true }) + editBody() }) },
      photo: {
        title: 'Шторка фото',
        render: () => M.phone({ body: editTop() + editBody(), overlay: D.sheet({ title: 'Фото профиля', content: D.row({ icon: 'camera', title: 'Сделать фото' }) + D.row({ icon: 'gallery', title: 'Выбрать из галереи' }) + D.row({ icon: 'delete', title: 'Удалить фото', danger: true }) + '<div style="height:20px"></div>' }) }),
      },
      offline: { title: 'Офлайн', render: () => M.phone({ body: editTop() + D.banner('Нет сети — сохраним изменения, когда она появится') + editBody() }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['error', 'light'], ['saving', 'light'], ['photo', 'light'], ['offline', 'light'], ['content', 'light', 2]],
  });
})();
