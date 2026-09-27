/* Настройки. */
(function () {
  const D = window.DS, M = window.DSM;

  const group = (title) => `<div class="group-title"><span class="kicker">${title}</span></div>`;
  const body = (o = {}) => `<div class="scroll">
      <div class="large-title" style="padding-top:0"><div class="ds-t-display-small">Настройки</div></div>
      ${D.row({ avatar: 'me', title: 'Лиза Миронова', sub: '@liza.mir · вход через Telegram', strong: true, chevron: true })}
      ${group('Внешний вид')}
      ${D.row({ icon: 'palette', title: 'Тема', value: 'Как в системе', chevron: true })}
      ${D.row({ icon: 'language', title: 'Язык', value: 'Русский', chevron: true })}
      ${group('Уведомления')}
      ${o.pushOff ? D.row({ icon: 'notifications_off', title: 'Выключены в системе', sub: 'Откройте настройки телефона, чтобы получать уведомления', sub2: true, end: D.btn('Открыть', { variant: 'secondary', size: 'small' }) }) : ''}
      ${D.row({ icon: 'favorite', title: 'Лайки и комментарии', switch: !o.pushOff })}
      ${D.row({ icon: 'person_add', title: 'Новые подписчики', switch: !o.pushOff })}
      ${D.row({ icon: 'chat', title: 'Сообщения', switch: !o.pushOff })}
      ${D.row({ icon: 'sparkle', title: 'Новости Студии', switch: false })}
      ${group('Конфиденциальность')}
      ${D.row({ icon: 'lock', title: 'Закрытый профиль', sub: 'Образы видят только одобренные подписчики', sub2: true, switch: false })}
      ${D.row({ icon: 'block', title: 'Заблокированные', value: '2', chevron: true })}
      ${group('Аккаунт')}
      ${D.row({ icon: 'logout', title: 'Выйти', danger: true })}
      ${D.row({ icon: 'delete', title: 'Удалить аккаунт', danger: true })}
      <div class="ds-t-body-small muted gutter" style="padding:16px 20px 24px">Outfit Share 1.0.0 · Условия · Конфиденциальность</div></div>`;
  const top = () => D.appbar({ nav: 'arrow_back' });

  M.screen({
    id: 'settings',
    section: 'Профиль',
    title: 'Настройки',
    description: 'Большой заголовок антиквой, группы с кикерами. Переключатель управляется всей строкой (одна цель касания, одна фраза для TalkBack). Выход и удаление — цветом danger и только через диалог подтверждения: у них нет «Отменить».',
    states: {
      content: { title: 'Контент', render: () => M.phone({ body: top() + body() }) },
      theme: {
        title: 'Выбор темы',
        render: () => M.phone({
          body: top() + body(),
          overlay: D.sheet({ title: 'Тема', content: [['Как в системе', 'Переключается вместе с телефоном', true], ['Светлая', 'Молочный фон, графитовый текст', false], ['Тёмная', 'Графитовый фон — бережёт глаза вечером', false]].map(([t, s, on]) => D.row({ lead: `<span class="radio ${on ? 'radio--on' : ''}"></span>`, title: t, sub: s })).join('') + '<div style="height:20px"></div>' }),
        }),
      },
      logout: { title: 'Подтверждение выхода', render: () => M.phone({ body: top() + body(), overlay: D.dialog({ title: 'Выйти из аккаунта?', text: 'Черновики на этом телефоне сохранятся. Чтобы вернуться, войдите через Telegram или почту.', confirm: 'Выйти', danger: true }) }) },
      push: { title: 'Push выключены (нет прав)', render: () => M.phone({ body: top() + body({ pushOff: true }) }) },
      offline: { title: 'Офлайн', render: () => M.phone({ body: top() + D.banner('Офлайн — настройки сохранены на телефоне и синхронизируются позже') + body() }) },
    },
    board: [['content', 'light'], ['content', 'dark'], ['theme', 'light'], ['logout', 'light'], ['logout', 'dark'], ['push', 'light'], ['offline', 'light'], ['content', 'light', 2]],
  });
})();
