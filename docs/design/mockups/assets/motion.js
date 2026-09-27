/*
 * Движение: графики пружин и кривых из токенов + раскадровки ключевых анимаций.
 * Физика пружины — та же, что у androidx.dynamicanimation.SpringForce (масса 1):
 *   ω = √stiffness, ζ = dampingRatio, x(t) — затухающий осциллятор.
 */
(function () {
  'use strict';
  const D = window.DS, T = window.DS_TOKENS, A = window.DS_ART;
  const M = T.motion;

  /** Смещение пружины от x0 к 0 в момент t (секунды), без начальной скорости. */
  function spring(stiffness, zeta, x0, t, v0 = 0) {
    const w = Math.sqrt(stiffness);
    if (zeta < 1) {
      const wd = w * Math.sqrt(1 - zeta * zeta);
      return Math.exp(-zeta * w * t) * (x0 * Math.cos(wd * t) + ((zeta * w * x0 + v0) / wd) * Math.sin(wd * t));
    }
    return (x0 + (v0 + w * x0) * t) * Math.exp(-w * t);
  }

  /** Кубическая Безье (0,0)-(x1,y1)-(x2,y2)-(1,1): y для заданного x. */
  function bezier([x1, y1, x2, y2], x) {
    const cx = (t) => 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t;
    const cy = (t) => 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t;
    let lo = 0, hi = 1;
    for (let i = 0; i < 40; i++) { const mid = (lo + hi) / 2; if (cx(mid) < x) lo = mid; else hi = mid; }
    return cy((lo + hi) / 2);
  }

  // ------------------------------------------------------------------ Графики
  const COLORS = ['var(--ds-color-accent)', 'var(--ds-color-on-surface)', 'var(--ds-color-success)', 'var(--ds-color-danger)', 'var(--ds-color-warning)'];
  function chart(series, o) {
    const W = 520, H = 240, P = 36;
    const sx = (x) => P + (x / o.xmax) * (W - 2 * P);
    const sy = (y) => H - P - ((y - o.ymin) / (o.ymax - o.ymin)) * (H - 2 * P);
    const grid = [o.ymin, (o.ymin + o.ymax) / 2, o.ymax].map((y) => `<line x1="${P}" x2="${W - P}" y1="${sy(y)}" y2="${sy(y)}" stroke="var(--ds-color-outline-variant)"/><text x="${P - 6}" y="${sy(y) + 4}" text-anchor="end" font-size="10" fill="var(--ds-color-on-surface-variant)">${Math.round(y * 100) / 100}</text>`).join('');
    const ticks = o.xticks.map((x) => `<text x="${sx(x)}" y="${H - P + 16}" text-anchor="middle" font-size="10" fill="var(--ds-color-on-surface-variant)">${x}${o.xunit}</text>`).join('');
    const lines = series.map((s, i) => {
      const pts = [];
      for (let k = 0; k <= 160; k++) { const x = (k / 160) * o.xmax; pts.push(`${sx(x).toFixed(1)},${sy(s.f(x)).toFixed(1)}`); }
      return `<polyline points="${pts.join(' ')}" fill="none" stroke="${COLORS[i % COLORS.length]}" stroke-width="2.2" stroke-linejoin="round"/>`;
    }).join('');
    const legend = series.map((s, i) => `<span style="display:inline-flex;align-items:center;gap:6px;margin-right:14px"><i style="width:14px;height:3px;border-radius:2px;background:${COLORS[i % COLORS.length]};display:inline-block"></i>${s.name}</span>`).join('');
    return `<svg viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" font-family="Inter">${grid}${ticks}${lines}</svg><div class="ds-t-body-small" style="margin-top:6px">${legend}</div>`;
  }

  const springs = Object.entries(M.spring).filter(([k]) => !k.startsWith('$'));
  const springChart = chart(springs.map(([k, s]) => ({ name: `${k} · k ${s.stiffness}, ζ ${s.dampingRatio}`, f: (ms) => 1 - spring(s.stiffness, s.dampingRatio, 1, ms / 1000) })), { xmax: 600, ymin: 0, ymax: 1.4, xticks: [0, 150, 300, 450, 600], xunit: ' мс' });
  const easings = Object.entries(M.easing).filter(([k]) => k !== 'linear');
  const easingChart = chart(easings.map(([k, c]) => ({ name: k, f: (x) => bezier(c, x) })), { xmax: 1, ymin: 0, ymax: 1, xticks: [0, 0.25, 0.5, 0.75, 1], xunit: '' });
  const durations = Object.entries(M.duration).filter(([k]) => !k.startsWith('$') && M.duration[k] <= 700);

  // ------------------------------------------------------------------ Сцены (кадры)
  // Момент первого касания цели — здесь срабатывает хаптика SNAP.
  const DROP_CROSS_MS = (() => {
    for (let t = 1; t < 1000; t++) if (spring(M.spring.drop.stiffness, M.spring.drop.dampingRatio, -M.distance.dropOffset, t / 1000) >= 0) return t;
    return 0;
  })();

  /** «Падение» юбки на манекен: t в мс. */
  function dropStage(t) {
    const s = M.spring.drop, off = M.distance.dropOffset;
    const y = spring(s.stiffness, s.dampingRatio, -off, t / 1000);
    const scale = 1 + (M.distance.dropStartScale - 1) * spring(s.stiffness, s.dampingRatio, 1, t / 1000);
    const alpha = Math.min(1, t / M.duration.short);
    const landed = Math.abs(t - DROP_CROSS_MS) <= 30;
    const skirt = A.G.skirtMidi(A.PALETTE.olive).map((p) => p.defs ? `<defs>${p.defs}</defs>${p.svg}` : p.svg).join('');
    return `<div class="canvas" style="width:240px;height:300px;border-radius:14px">
      ${A.outfit({ bare: true, items: [['shirt', 'ecru'], ['boots', 'chocolate']] })}
      <svg viewBox="0 0 400 500" style="position:absolute;inset:0;width:100%;height:100%" preserveAspectRatio="xMidYMid meet">
        <g transform="translate(0 ${(y / 0.6).toFixed(2)}) translate(200 290) scale(${scale.toFixed(4)}) translate(-200 -290)" opacity="${alpha.toFixed(2)}">${skirt}</g></svg>
      ${landed ? `<span style="position:absolute;right:10px;top:10px">${D.tbadge('Snap · хаптика', 'accent', 'magnet')}</span>` : ''}
      <span class="ds-t-body-small num" style="position:absolute;left:10px;top:8px;color:var(--ds-color-on-surface-variant)">${t} мс · y ${y.toFixed(1)} dp</span></div>`;
  }

  /** Всплеск сердца по двойному тапу. */
  function heartStage(t) {
    const s = M.spring.heart, total = M.duration.heartBurst, fade = M.duration.medium;
    let scale = 1 - spring(s.stiffness, s.dampingRatio, 1, t / 1000), alpha = Math.min(1, t / M.duration.short);
    if (t > total - fade) {
      const p = bezier(M.easing.emphasizedAccelerate, (t - (total - fade)) / fade);
      scale = scale + (M.distance.heartBurstScale - scale) * p;
      alpha = 1 - p;
    }
    return `<div style="position:relative;width:240px;height:300px;border-radius:4px;overflow:hidden">${A.outfit(A.LOOKS.slip)}
      <span style="position:absolute;left:50%;top:50%;width:96px;height:96px;margin:-48px 0 0 -48px;color:#fff;opacity:${Math.max(0, alpha).toFixed(2)};transform:scale(${Math.max(0, scale).toFixed(3)});filter:drop-shadow(0 4px 12px rgba(0,0,0,.25))">${D.icon('favorite_filled', '').replace('class="ic "', 'class="ic" style="width:96px;height:96px"')}</span>
      <span class="ds-t-body-small num" style="position:absolute;left:10px;top:8px;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.4)">${t} мс · ×${Math.max(0, scale).toFixed(2)}</span></div>`;
  }

  /** Container transform: карточка ленты (20, 140, 320×400) → фото экрана (0, 0, 360×450). p ∈ [0,1]. */
  function sharedStage(p) {
    const e = bezier(M.easing.emphasizedDecelerate, p);
    const from = [20, 132, 320, 400], to = [0, 0, 360, 450];
    const r = from.map((v, i) => v + (to[i] - v) * e);
    const radius = 2 * (1 - e);
    const feedAlpha = 1, viewAlpha = Math.max(0, (p - 0.35) / 0.65);
    return `<div class="phone" data-theme="light" style="width:360px;height:640px;border-radius:24px">
      <div style="position:absolute;inset:0;opacity:${feedAlpha}">${D.statusbar()}${D.appbar({ title: '<span class="wordmark">Outfit <em>Share</em></span>' })}${D.tabs(['Подписки', 'Для вас'], 0)}</div>
      <div style="position:absolute;left:0;right:0;top:450px;bottom:0;background:var(--ds-color-background);opacity:${viewAlpha.toFixed(2)};padding-top:12px">
        <div class="row" style="padding-top:0">${D.avatar('anna', 'm')}<div class="row__body"><div class="row__title--strong">Анна Ковалёва</div><div class="row__sub">@anna.kov</div></div>${D.btn('Подписаться', { variant: 'secondary', size: 'small' })}</div>
        <div style="display:flex;padding:0 8px">${D.like('1,2 тыс.', { on: true })}<span class="like">${D.icon('comment')}<span>24</span></span></div></div>
      <div style="position:absolute;left:${r[0]}px;top:${r[1]}px;width:${r[2]}px;height:${r[3]}px;border-radius:${radius}px;overflow:hidden;box-shadow:0 ${8 * (1 - e)}px ${24 * (1 - e)}px rgba(0,0,0,${(0.12 * Math.sin(Math.PI * p)).toFixed(3)})">${A.outfit(A.LOOKS.trench)}</div>
      <span class="ds-t-body-small num" style="position:absolute;right:12px;bottom:10px;color:var(--ds-color-on-surface-variant)">${Math.round(p * M.duration.long)} мс</span></div>`;
  }

  const filmstrip = (frames) => `<div style="display:flex;gap:16px;align-items:flex-start">${frames.join('')}</div>`;
  const surface = (theme, html) => `<div data-theme="${theme}" style="background:var(--ds-color-background);color:var(--ds-color-on-surface);font-family:var(--ds-font-text);border-radius:20px;padding:28px;box-shadow:0 0 0 1px rgba(0,0,0,.06)">${html}</div>`;
  const board = (id, title, desc, body) => `<div class="board" id="${id}"><div class="board__head"><div class="board__section">Движение</div><div class="board__title">${title}</div><div class="board__desc">${desc}</div></div>${body}</div>`;

  const curves = board('motion-curves', 'Кривые и пружины', 'Длительности масштабируются системной настройкой «Масштаб анимации», при «Убрать анимации» состояние меняется мгновенно. Пружины (androidx.dynamicanimation) не имеют фиксированной длительности — график показывает путь к цели: drop и heart с заметным перелётом, press и sheet — без отскока.',
    `<div class="board__row">${surface('light', `<div class="ds-t-title-medium" style="margin-bottom:10px">Пружины: доля пути к цели</div>${springChart}`)}${surface('light', `<div class="ds-t-title-medium" style="margin-bottom:10px">Кривые (cubic-bezier)</div>${easingChart}`)}${surface('light', `<div class="ds-t-title-medium" style="margin-bottom:14px">Длительности</div>${durations.map(([k, v]) => `<div style="display:flex;align-items:center;gap:10px;height:28px"><span class="ds-t-body-small" style="width:92px;color:var(--ds-color-on-surface-variant)">${k}</span><span style="height:10px;width:${v / 2.2}px;background:var(--ds-color-on-surface);border-radius:5px"></span><span class="ds-t-body-small num">${v} мс</span></div>`).join('')}`)}</div>`);

  const drop = board('motion-drop', '«Падение» вещи на манекен', `Вещь появляется на ${M.distance.dropOffset} dp выше точки крепления, проявляется за duration.short и садится пружиной drop (k ${M.spring.drop.stiffness}, ζ ${M.spring.drop.dampingRatio}) с лёгким проседанием ниже цели — ощущение веса ткани. В момент первого касания цели — хаптика SNAP. Масштаб ${M.distance.dropStartScale} → 1 той же пружиной.`,
    surface('light', filmstrip([0, 40, 80, DROP_CROSS_MS, 180, 260, 420].map(dropStage))));

  const heart = board('motion-heart', 'Сердце при лайке', `Двойной тап по фото: сердце 96 dp вырастает пружиной heart (k ${M.spring.heart.stiffness}, ζ ${M.spring.heart.dampingRatio}) с перелётом, держится и растворяется, увеличиваясь до ×${M.distance.heartBurstScale} (emphasizedAccelerate). Кнопка лайка одновременно делает «пружинку» ×${M.distance.heartPeakScale} → 1 и хаптику LIKE. При отключённых анимациях всплеск не показывается — лайк отражает кнопка.`,
    surface('light', filmstrip([0, 60, 120, 200, 380, 520, 640].map(heartStage))));

  const shared = board('motion-shared', 'Shared element: карточка → образ', `Контейнерная трансформация Material (${M.duration.long} мс, emphasizedDecelerate): фото карточки растёт в фото экрана просмотра, скругление 2 → 0, лента удерживается Hold, контент экрана появляется fade-through после 35 % пути. Переход откладывается, пока фото не загрузилось (postponeEnterTransition).`,
    surface('light', filmstrip([0, 0.15, 0.3, 0.5, 0.75, 1].map((p) => `<div style="zoom:.62">${sharedStage(p)}</div>`))));

  window.DS_MOTION = { dropStage, heartStage, sharedStage, spring, bezier, DROP_CROSS_MS };
  const root = document.getElementById('root');
  if (root) root.innerHTML = [curves, drop, heart, shared].join('');
})();
