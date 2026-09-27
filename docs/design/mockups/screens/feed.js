/* Лента: подписки и рекомендации. */
(function () {
  const D = window.DS, M = window.DSM;

  const top = () => D.appbar({
    title: '<span class="wordmark">Outfit <em>Share</em></span>',
    actions: [D.iconBtn('notifications', { dot: true, label: 'Уведомления, есть новые' })],
  });

  const feedBody = (o = {}) => `
    <div class="scroll gutter" style="padding-top:var(--ds-space-4)">
      ${D.outfitCard('trench', { author: 'anna', meta: '2 ч · 5 вещей', likes: '1,2 тыс.', liked: true, caption: 'Тренч, который работает с чем угодно: широкие брюки, лоферы, мягкий свитер. <span class="hashtag">#осень</span> <span class="hashtag">#капсула</span>' })}
      <div style="height:var(--ds-space-8)"></div>
      ${D.outfitCard('denim', { author: 'dan', meta: '5 ч · ремикс @mila_o', likes: 348, badge: 'Ремикс' })}
    </div>`;

  M.screen({
    id: 'feed',
    section: 'Основное',
    title: 'Лента',
    description: 'Фото образа — главный герой: 4:5 на всю ширину колонки, интерфейс вокруг — монохромный. Двойной тап по фото — лайк с большим сердцем; тап — просмотр образа через shared element. Вкладки «Подписки / Для вас».',
    states: {
      content: { title: 'Контент', render: () => M.phone({ nav: 'feed', body: top() + D.tabs(['Подписки', 'Для вас'], 0) + feedBody() }) },
      loading: {
        title: 'Загрузка',
        render: () => M.phone({ nav: 'feed', body: top() + D.tabs(['Подписки', 'Для вас'], 0) + `<div class="scroll gutter skeleton-shimmer" style="padding-top:var(--ds-space-4)" aria-label="Загрузка…">${D.skOutfit()}<div style="height:32px"></div>${D.skOutfit()}</div>` }),
      },
      empty: {
        title: 'Пусто: нет подписок',
        render: () => M.phone({ nav: 'feed', body: top() + D.tabs(['Подписки', 'Для вас'], 0) + D.state({ art: 'empty_feed', title: 'Здесь будут образы тех, на кого вы подпишетесь', text: 'Загляните в рекомендации или найдите друзей из Telegram.', action: 'Смотреть «Для вас»', secondary: 'Найти людей' }) }),
      },
      error: {
        title: 'Ошибка сети',
        render: () => M.phone({ nav: 'feed', body: top() + D.tabs(['Подписки', 'Для вас'], 0) + D.state({ art: 'error', title: 'Лента не загрузилась', text: 'Сервер не ответил вовремя. Проверьте подключение и попробуйте ещё раз.', action: 'Повторить', actionIcon: 'refresh' }) }),
      },
      offline: {
        title: 'Офлайн с кэшем',
        render: () => M.phone({ nav: 'feed', body: top() + D.tabs(['Подписки', 'Для вас'], 0) + D.banner('Офлайн — показываем сохранённое', { action: 'Повторить' }) + feedBody() }),
      },
      offlineEmpty: {
        title: 'Офлайн без кэша',
        render: () => M.phone({ nav: 'feed', body: top() + D.tabs(['Подписки', 'Для вас'], 0) + D.state({ art: 'offline', title: 'Нет подключения', text: 'Лента обновится сама, как только появится сеть.', action: 'Повторить', actionIcon: 'refresh' }) }),
      },
    },
    board: [['content', 'light'], ['content', 'dark'], ['loading', 'light'], ['empty', 'light'], ['error', 'light'], ['offline', 'light'], ['offlineEmpty', 'dark'], ['content', 'light', 2]],
  });
})();
