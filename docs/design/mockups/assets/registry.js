/* Реестр экранов макетов: DSM.screen({...}) регистрирует экран и его состояния. */
(function () {
  'use strict';
  const screens = [];

  /**
   * def: { id, title, section, description, states: { key: { title, render(ctx) } }, board: [[state, theme, scale?], ...] }
   * render(ctx) возвращает внутренности .phone; ctx = { theme, scale }.
   */
  function screen(def) {
    if (!def.board) {
      const keys = Object.keys(def.states);
      def.board = [[keys[0], 'light'], [keys[0], 'dark'], ...keys.slice(1).map((k) => [k, 'light'])];
    }
    screens.push(def);
  }

  /** Сборка содержимого телефона: статус-бар, экран, нижняя навигация, жестовая полоса. */
  function phone(o) {
    const D = window.DS;
    const status = o.status === false ? '' : D.statusbar(o.status || {});
    const nav = o.nav ? D.bottomnav(o.nav, { badges: o.badges || { chats: 3, feed: null } }) : '';
    const overlay = o.overlay || '';
    const gesture = o.gesture === false ? '' : o.fullBleed
      ? `<div style="position:absolute;left:0;right:0;bottom:0;z-index:6">${D.gesturebar(o.gestureColor || '#fff')}</div>`
      : D.gesturebar(o.gestureColor);
    return `${status}<div class="screen" ${o.screenStyle ? `style="${o.screenStyle}"` : ''}>${o.body}</div>${nav}${gesture}${overlay}`;
  }

  window.DSM = { screens, screen, phone };
})();
