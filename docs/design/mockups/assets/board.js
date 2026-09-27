/* Рендер доски экрана / одиночного кадра / индекса. Параметры URL: screen, state, theme, scale, mode. */
(function () {
  'use strict';
  const q = new URLSearchParams(location.search);
  const root = document.getElementById('root');
  const THEME_LABEL = { light: 'Светлая', dark: 'Тёмная' };

  function renderPhone(def, stateKey, theme, scale) {
    const st = def.states[stateKey];
    if (!st) throw new Error(`Нет состояния ${stateKey} у ${def.id}`);
    const ctx = { theme, scale: scale || 1 };
    const cls = st.camera ? 'phone phone--camera' : 'phone';
    return `<div class="${cls}" data-theme="${theme}" ${scale && scale !== 1 ? `data-font-scale="${scale}"` : ''} data-screen="${def.id}" data-state="${stateKey}">${st.render(ctx)}</div>`;
  }

  function board(def) {
    const cells = def.board.map(([stateKey, theme, scale]) => {
      const st = def.states[stateKey];
      const pills = `<span class="board__pill ${theme === 'dark' ? 'board__pill--dark' : ''}">${THEME_LABEL[theme]}</span>${scale && scale !== 1 ? `<span class="board__pill board__pill--scale">Шрифт ${Math.round(scale * 100)} %</span>` : ''}`;
      return `<div class="board__cell"><div class="board__cap">${st.title}${pills}</div>${renderPhone(def, stateKey, theme, scale)}</div>`;
    }).join('');
    return `<div class="board" id="board"><div class="board__head"><div class="board__section">${def.section}</div><div class="board__title">${def.title}</div>${def.description ? `<div class="board__desc">${def.description}</div>` : ''}</div><div class="board__row">${cells}</div></div>`;
  }

  const screenId = q.get('screen');
  if (q.get('mode') === 'overview') {
    // Обзор: главное состояние каждого экрана в светлой (или заданной) теме, сеткой.
    const theme = q.get('theme') || 'light';
    const cells = window.DSM.screens.map((d) => {
      const [stateKey] = d.board[0];
      return `<div class="board__cell"><div class="board__cap">${d.title}</div>${renderPhone(d, stateKey, theme, 1)}</div>`;
    }).join('');
    root.innerHTML = `<div class="board" id="board"><div class="board__head"><div class="board__section">Outfit Share · ${theme === 'dark' ? 'тёмная' : 'светлая'} тема</div><div class="board__title">Все экраны</div></div><div style="display:grid;grid-template-columns:repeat(8, 360px);gap:40px 32px">${cells}</div></div>`;
    return;
  }
  if (!screenId) {
    const bySection = {};
    for (const s of window.DSM.screens) (bySection[s.section] = bySection[s.section] || []).push(s);
    root.innerHTML = `<div class="index"><h1>Outfit Share — экраны</h1><p>Макеты собраны из токенов и компонентов дизайн-системы. Откройте доску экрана:</p>${Object.entries(bySection).map(([sec, list]) => `<h3>${sec}</h3><ul>${list.map((s) => `<li><a href="?screen=${s.id}">${s.title}</a> — ${Object.values(s.states).map((st) => st.title).join(', ')}</li>`).join('')}</ul>`).join('')}</div>`;
    return;
  }
  const def = window.DSM.screens.find((s) => s.id === screenId);
  if (!def) { root.textContent = 'Нет экрана ' + screenId; return; }
  if (q.get('mode') === 'frame') {
    root.innerHTML = `<div id="board" style="display:inline-block;padding:24px;background:#E7E4DE">${renderPhone(def, q.get('state') || Object.keys(def.states)[0], q.get('theme') || 'light', parseFloat(q.get('scale') || '1'))}</div>`;
  } else {
    root.innerHTML = board(def);
  }
  document.title = def.title;
})();
