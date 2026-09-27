/*
 * Процедурные «фотографии» для макетов: образ = манекен + слои вещей на студийном фоне.
 * Так устроены и настоящие образы Outfit Share (вещи, посаженные на манекен), поэтому
 * макеты честно показывают, как фото-герой живёт в интерфейсе. Лицензионно чисто: всё
 * рисуется кодом.
 *
 * Координаты тела — холст 400×500 (пропорции образа 4:5), ось симметрии x = 200.
 */
(function () {
  'use strict';

  const PALETTE = {
    camel: '#B98D5F', cream: '#EDE3D1', ecru: '#E4DACA', black: '#222120', ink: '#2A2C33',
    navy: '#27324A', olive: '#6C6A43', burgundy: '#6B2531', denim: '#4D6A8A', denimLight: '#8FA6BF',
    white: '#F6F4EF', grey: '#9B9A96', charcoal: '#4A4946', chocolate: '#5A3F2E', sage: '#9FAE95',
    rust: '#A4552F', blush: '#E3C2BA', butter: '#E9D8A6', stone: '#C9C0B2', khaki: '#A99A74',
    forest: '#2F4A3A', lilac: '#B7AECF', sky: '#A9C1D6', tan: '#C8A57C',
  };

  const BACKDROPS = {
    sand: ['#E9DCC8', '#D9C7AE'], stone: ['#E3DFD8', '#CFC8BD'], sage: ['#DCE0D2', '#C3CAB6'],
    blush: ['#EEDDD6', '#DDC5BB'], mist: ['#DCE2E7', '#C5CED6'], clay: ['#E4CDB8', '#CFAF95'],
    ink: ['#3A3935', '#262522'], butter: ['#EFE5C8', '#DDCFA6'],
  };

  let uid = 0;
  const id = (p) => `${p}${++uid}`;

  // Затемнение/осветление HEX на долю.
  function shade(hex, amount) {
    const n = parseInt(hex.slice(1), 16);
    let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    const t = amount < 0 ? 0 : 255, p = Math.abs(amount);
    r = Math.round((t - r) * p + r); g = Math.round((t - g) * p + g); b = Math.round((t - b) * p + b);
    return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
  }

  function fabric(color) {
    const gid = id('g');
    const defs = `<linearGradient id="${gid}" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${shade(color, 0.12)}"/><stop offset=".55" stop-color="${color}"/>
      <stop offset="1" stop-color="${shade(color, -0.18)}"/></linearGradient>`;
    return { defs, fill: `url(#${gid})` };
  }

  function part(d, color, extra = '') {
    const f = fabric(color);
    return { defs: f.defs, svg: `<path d="${d}" fill="${f.fill}" ${extra}/>` };
  }

  function line(d, color, w = 1.2, op = 0.45) {
    return { defs: '', svg: `<path d="${d}" fill="none" stroke="${color}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" opacity="${op}"/>` };
  }

  // ---------------------------------------------------------------- Манекен
  function mannequin(color) {
    const c = color || '#D6CDBF';
    const f = fabric(c);
    const body = `M192,86 L208,86 L209,103 Q232,108 250,112 Q262,116 262,130
      L266,196 Q268,240 270,280 L276,316 Q278,326 270,326 L266,300 L258,238 Q254,214 250,190
      L250,205 Q248,228 252,252 Q256,272 250,300 L244,372 L238,452 Q246,466 240,470 L214,470
      Q210,462 214,452 L210,372 L203,294 L197,294 L190,372 L186,452 Q190,462 186,470 L160,470
      Q154,466 162,452 L156,372 L150,300 Q144,272 148,252 Q152,228 150,205 L150,190
      Q146,214 142,238 L134,300 L130,326 Q122,326 124,316 L130,280 Q132,240 134,196 L138,130
      Q138,116 150,112 Q168,108 191,103 Z`;
    return {
      defs: f.defs,
      svg: `<g><ellipse cx="200" cy="60" rx="20" ry="25" fill="${f.fill}"/>
        <path d="${body}" fill="${f.fill}"/>
        <path d="M150,205 Q200,214 250,205" fill="none" stroke="${shade(c, -0.12)}" stroke-width="1" opacity=".5"/></g>`,
    };
  }

  // ---------------------------------------------------------------- Вещи
  const G = {};

  G.tee = (c) => [
    part(`M176,104 Q200,118 224,104 L252,112 L276,164 L256,174 L248,152 L250,258 Q200,266 150,258
      L152,152 L144,174 L124,164 L148,112 Z`, c),
    line('M176,104 Q200,118 224,104', shade(c, -0.35), 2, 0.5),
  ];

  G.sweater = (c) => [
    part(`M174,102 Q200,112 226,102 L254,112 Q266,120 266,138 L272,292 L254,294 L248,160
      L252,262 Q200,272 148,262 L152,160 L146,294 L128,292 L134,138 Q134,120 146,112 Z`, c),
    line('M174,102 Q200,112 226,102 M150,256 Q200,266 250,256 M131,280 L147,281 M253,281 L269,280', shade(c, -0.3), 1.6, 0.5),
    line('M160,150 L160,250 M172,150 L172,254 M184,150 L184,256 M196,152 L196,257 M208,152 L208,257 M220,150 L220,256 M232,150 L232,254 M244,150 L244,250', shade(c, -0.25), 0.8, 0.25),
  ];

  G.shirt = (c) => [
    part(`M180,100 L200,116 L220,100 L254,112 Q266,120 266,138 L272,288 L255,290 L248,160
      L250,262 Q200,270 150,262 L152,160 L145,290 L128,288 L134,138 Q134,120 146,112 Z`, c),
    part('M180,100 L200,116 L190,128 L172,106 Z M220,100 L200,116 L210,128 L228,106 Z', shade(c, 0.1)),
    line('M200,116 L200,262', shade(c, -0.3), 1.2, 0.5),
    { defs: '', svg: [140, 168, 196, 224].map((y) => `<circle cx="204" cy="${y}" r="1.8" fill="${shade(c, -0.35)}"/>`).join('') },
  ];

  G.tank = (c) => [
    part(`M178,110 Q200,124 222,110 L234,114 Q240,150 250,158 L252,258 Q200,266 148,258
      L150,158 Q160,150 166,114 Z`, c),
  ];

  G.blazer = (c) => [
    part(`M176,100 L200,150 L224,100 L256,112 Q268,120 268,140 L274,292 L256,294 L250,172
      L256,276 Q228,282 200,280 Q172,282 144,276 L150,172 L144,294 L126,292 L132,140 Q132,120 144,112 Z`, c),
    part('M176,100 L200,150 L190,196 L168,132 Z M224,100 L200,150 L210,196 L232,132 Z', shade(c, -0.12)),
    line('M200,150 L200,280 M160,214 L184,214', shade(c, -0.4), 1.2, 0.5),
    { defs: '', svg: `<circle cx="206" cy="206" r="2.4" fill="${shade(c, -0.4)}"/><circle cx="206" cy="232" r="2.4" fill="${shade(c, -0.4)}"/>` },
  ];

  G.trench = (c) => [
    part(`M174,98 L200,150 L226,98 L258,110 Q270,118 270,140 L276,296 L257,298 L252,176
      L262,300 Q268,350 276,396 Q200,408 124,396 Q132,350 138,300 L148,176 L143,298 L124,296
      L130,140 Q130,118 142,110 Z`, c),
    part('M174,98 L200,150 L188,200 L164,128 Z M226,98 L200,150 L212,200 L236,128 Z', shade(c, -0.1)),
    part('M140,202 Q200,212 260,202 L260,214 Q200,224 140,214 Z', shade(c, -0.2)),
    line('M212,200 L214,398 M150,296 Q200,306 250,296', shade(c, -0.35), 1.2, 0.45),
    { defs: '', svg: [232, 262, 292].map((y) => `<circle cx="222" cy="${y}" r="2.6" fill="${shade(c, -0.4)}"/><circle cx="186" cy="${y}" r="2.6" fill="${shade(c, -0.4)}"/>`).join('') },
  ];

  G.puffer = (c) => [
    part(`M172,96 Q200,104 228,96 L262,112 Q276,122 276,146 L282,292 L260,296 L254,178
      L258,288 Q200,300 142,288 L146,178 L140,296 L118,292 L124,146 Q124,122 138,112 Z`, c),
    line('M146,150 Q200,160 254,150 M146,190 Q200,200 254,190 M146,230 Q200,240 254,230 M146,268 Q200,278 254,268 M200,100 L200,292', shade(c, -0.3), 1.4, 0.45),
  ];

  G.wideTrousers = (c) => [
    part(`M166,200 L234,200 Q250,226 254,262 L262,456 L208,458 L201,300 L199,300 L192,458
      L138,456 L146,262 Q150,226 166,200 Z`, c),
    line('M166,208 L234,208 M200,208 L200,300 M170,300 L166,452 M230,300 L234,452', shade(c, -0.3), 1.1, 0.35),
  ];

  G.jeans = (c) => [
    part(`M166,200 L234,200 Q250,226 252,262 L244,372 L240,456 L210,456 L203,300 L197,300
      L190,456 L160,456 L156,372 L148,262 Q150,226 166,200 Z`, c),
    line('M166,208 L234,208 M200,208 L200,300 M176,214 Q180,232 196,236 M224,214 Q220,232 204,236', shade(c, -0.35), 1.1, 0.5),
    line('M174,300 L170,452 M226,300 L230,452', shade(c, 0.35), 1, 0.3),
  ];

  G.shorts = (c) => [
    part('M166,200 L234,200 Q250,226 254,262 L258,318 L206,320 L200,292 L194,320 L142,318 L146,262 Q150,226 166,200 Z', c),
  ];

  G.skirtMidi = (c) => [
    part('M168,198 L232,198 Q246,260 278,380 Q200,396 122,380 Q154,260 168,198 Z', c),
    line('M168,206 L232,206 M186,210 L170,384 M214,210 L230,384 M200,210 L200,388', shade(c, -0.25), 1, 0.35),
  ];

  G.skirtPleated = (c) => [
    part('M168,198 L232,198 L262,350 Q200,362 138,350 Z', c),
    line('M168,206 L232,206 M176,206 L150,352 M188,206 L175,356 M200,206 L200,358 M212,206 L225,356 M224,206 L250,352', shade(c, -0.3), 1, 0.4),
  ];

  G.dress = (c) => [
    part(`M178,106 Q200,126 222,106 L236,110 Q244,150 252,166 Q248,196 244,206 Q262,300 282,404
      Q200,420 118,404 Q138,300 156,206 Q152,196 148,166 Q156,150 164,110 Z`, c),
    line('M156,206 Q200,214 244,206', shade(c, -0.3), 1.4, 0.45),
    line('M178,212 L162,404 M222,212 L238,404', shade(c, -0.2), 1, 0.25),
  ];

  G.knitVest = (c) => [
    part('M176,104 L200,140 L224,104 L240,110 Q246,146 252,156 L252,262 Q200,272 148,262 L148,156 Q154,146 160,110 Z', c),
    line('M150,254 Q200,264 250,254', shade(c, -0.3), 2, 0.4),
  ];

  G.sneakers = (c) => [
    part('M160,452 L188,452 Q196,462 192,476 L148,478 Q140,474 146,466 Q156,462 160,452 Z', c),
    part('M212,452 L240,452 Q244,462 254,466 Q260,474 252,478 L208,476 Q204,462 212,452 Z', c),
    line('M146,472 L192,472 M208,472 L254,472', shade(c, -0.4), 2, 0.5),
  ];

  G.boots = (c) => [
    part('M158,392 L190,392 L190,462 Q194,470 190,478 L146,478 Q140,470 150,464 L158,452 Z', c),
    part('M210,392 L242,392 L242,452 L250,464 Q260,470 254,478 L210,478 Q206,470 210,462 Z', c),
    line('M158,398 L190,398 M210,398 L242,398', shade(c, 0.2), 1.4, 0.4),
  ];

  G.loafers = (c) => [
    part('M162,456 L188,456 Q194,466 190,476 L150,478 Q142,474 150,466 Q158,464 162,456 Z', c),
    part('M212,456 L238,456 Q242,464 250,466 Q258,474 250,478 L210,476 Q206,466 212,456 Z', c),
  ];

  G.heels = (c) => [
    part('M164,454 L186,454 Q192,466 186,474 L154,476 Q148,470 158,466 Z M170,472 L174,484 L178,484 L176,472 Z', c),
    part('M214,454 L236,454 L242,466 Q252,470 246,476 L214,474 Q208,466 214,454 Z M222,472 L224,484 L228,484 L230,472 Z', c),
  ];

  G.bag = (c) => [
    line('M236,108 Q262,180 274,236', shade(c, -0.3), 2.4, 0.9),
    part('M258,232 L296,232 Q300,232 300,238 L302,286 Q302,292 296,292 L258,292 Q252,292 252,286 L254,238 Q254,232 258,232 Z', c),
    line('M254,250 L302,250', shade(c, -0.3), 1.2, 0.5),
  ];

  G.tote = (c) => [
    line('M112,228 Q126,196 140,228', shade(c, -0.3), 2.4, 0.9),
    part('M104,228 L148,228 L154,300 L98,300 Z', c),
  ];

  G.beret = (c) => [part('M174,44 Q176,26 202,26 Q230,28 228,46 Q214,40 174,44 Z', c)];

  G.cap = (c) => [
    part('M178,48 Q180,30 200,30 Q220,30 222,48 Z', c),
    part('M218,46 L240,48 Q236,52 220,52 Z', shade(c, -0.15)),
  ];

  G.scarf = (c) => [
    part('M176,98 Q200,112 224,98 L228,110 Q200,126 172,110 Z M210,112 L218,112 L222,170 L208,172 Z', c),
    line('M210,160 L222,158 M209,166 L222,165', shade(c, -0.3), 1, 0.5),
  ];

  G.belt = (c) => [
    part('M166,200 L234,200 L234,208 L166,208 Z', c),
    { defs: '', svg: `<rect x="195" y="199" width="10" height="10" rx="1.5" fill="none" stroke="#C9B37E" stroke-width="1.6"/>` },
  ];

  // Какие зоны занимает вещь — для порядка слоёв и кадрирования плитки вещи.
  const META = {
    tee: { layer: 30, box: [110, 96, 290, 272] }, sweater: { layer: 32, box: [120, 92, 280, 300] },
    shirt: { layer: 31, box: [120, 92, 280, 296] }, tank: { layer: 30, box: [136, 100, 264, 270] },
    knitVest: { layer: 34, box: [136, 96, 264, 276] }, blazer: { layer: 40, box: [118, 92, 282, 300] },
    trench: { layer: 42, box: [116, 90, 284, 410] }, puffer: { layer: 42, box: [110, 88, 290, 304] },
    wideTrousers: { layer: 20, box: [130, 192, 270, 464] }, jeans: { layer: 20, box: [140, 192, 260, 464] },
    shorts: { layer: 20, box: [134, 192, 266, 328] }, skirtMidi: { layer: 21, box: [114, 190, 286, 398] },
    skirtPleated: { layer: 21, box: [130, 190, 270, 366] }, dress: { layer: 35, box: [110, 98, 290, 420] },
    sneakers: { layer: 50, box: [136, 444, 264, 486] }, boots: { layer: 50, box: [136, 384, 264, 486] },
    loafers: { layer: 50, box: [136, 448, 264, 486] }, heels: { layer: 50, box: [140, 446, 260, 490] },
    belt: { layer: 45, box: [160, 190, 240, 216] }, bag: { layer: 60, box: [226, 100, 310, 300] },
    tote: { layer: 60, box: [92, 188, 160, 308] }, scarf: { layer: 55, box: [166, 90, 234, 180] },
    beret: { layer: 58, box: [166, 18, 236, 56] }, cap: { layer: 58, box: [170, 22, 246, 58] },
  };

  function render(kind, color) {
    const parts = G[kind](PALETTE[color] || color);
    return { defs: parts.map((p) => p.defs).join(''), svg: parts.map((p) => p.svg).join('') };
  }

  function grain(fid, opacity) {
    return `<filter id="${fid}" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" stitchTiles="stitch"/>
      <feColorMatrix type="saturate" values="0"/>
      <feComponentTransfer><feFuncA type="table" tableValues="0 ${opacity}"/></feComponentTransfer>
      <feComposite in2="SourceGraphic" operator="in"/></filter>`;
  }

  /**
   * Фото образа 4:5.
   * spec: { backdrop: 'sand', items: [['trench','camel'], ...], mannequin?: '#hex', crop?: 'full'|'bust' }
   */
  function outfit(spec) {
    const bd = BACKDROPS[spec.backdrop] || BACKDROPS.sand;
    const bgId = id('bg'), shId = id('sh'), grId = id('gr'), dsId = id('ds');
    const items = (spec.items || []).slice().sort((a, b) => META[a[0]].layer - META[b[0]].layer);
    const m = mannequin(spec.mannequin);
    const rendered = items.map(([k, c]) => render(k, c));
    const view = spec.crop === 'bust' ? '60 10 280 350' : '0 0 400 500';
    const ghost = spec.ghost ? render(spec.ghost.kind, spec.ghost.color) : null;
    if (spec.bare) {
      // Холст конструктора: без фона и зерна, фон даёт токен canvas.
      return `<svg class="art" viewBox="0 0 400 500" preserveAspectRatio="xMidYMid meet" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Манекен с вещами">
        <defs><radialGradient id="${shId}"><stop offset="0" stop-color="#000" stop-opacity=".18"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>
        <filter id="${dsId}" x="-10%" y="-10%" width="120%" height="120%"><feDropShadow dx="2" dy="4" stdDeviation="4" flood-color="#000" flood-opacity=".14"/></filter>
        ${m.defs}${rendered.map((r) => r.defs).join('')}${ghost ? ghost.defs : ''}</defs>
        <ellipse cx="200" cy="478" rx="92" ry="10" fill="url(#${shId})"/>
        <g filter="url(#${dsId})">${m.svg}${rendered.map((r) => r.svg).join('')}</g>
        ${ghost ? `<g opacity=".62" transform="translate(${spec.ghost.dx || 0} ${spec.ghost.dy || 0})" filter="url(#${dsId})">${ghost.svg}</g>` : ''}</svg>`;
    }
    return `<svg class="art" viewBox="${view}" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${spec.label || 'Образ'}">
      <defs>
        <linearGradient id="${bgId}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${bd[0]}"/><stop offset=".78" stop-color="${bd[1]}"/><stop offset="1" stop-color="${shade(bd[1], -0.06)}"/></linearGradient>
        <radialGradient id="${shId}"><stop offset="0" stop-color="#000" stop-opacity=".28"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>
        <filter id="${dsId}" x="-10%" y="-10%" width="120%" height="120%"><feDropShadow dx="2" dy="4" stdDeviation="4" flood-color="#000" flood-opacity=".18"/></filter>
        ${grain(grId, 0.07)}
        ${m.defs}${rendered.map((r) => r.defs).join('')}
      </defs>
      <rect width="400" height="500" fill="url(#${bgId})"/>
      <ellipse cx="200" cy="478" rx="92" ry="12" fill="url(#${shId})"/>
      <g filter="url(#${dsId})">${m.svg}${rendered.map((r) => r.svg).join('')}</g>
      <rect width="400" height="500" filter="url(#${grId})"/>
    </svg>`;
  }

  /** Вещь-вырезка (PNG с прозрачным фоном после Студии) для плитки 1:1. */
  function item(kind, color, opts = {}) {
    const [x1, y1, x2, y2] = META[kind].box;
    const w = x2 - x1, h = y2 - y1, side = Math.max(w, h) * 1.12;
    const cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
    const r = render(kind, color);
    const dsId = id('ds');
    return `<svg class="art art--item" viewBox="${cx - side / 2} ${cy - side / 2} ${side} ${side}" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${opts.label || kind}">
      <defs><filter id="${dsId}" x="-10%" y="-10%" width="120%" height="130%"><feDropShadow dx="1" dy="3" stdDeviation="3" flood-color="#000" flood-opacity=".16"/></filter>${r.defs}</defs>
      <g filter="url(#${dsId})">${r.svg}</g></svg>`;
  }

  /** Исходное фото вещи в Студии: вещь на столе/вешалке с неровным фоном и тенью. */
  function rawCapture(kind, color, opts = {}) {
    const [x1, y1, x2, y2] = META[kind].box;
    const w = x2 - x1, h = y2 - y1, side = Math.max(w, h) * 1.35;
    const cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
    const r = render(kind, color);
    const bgId = id('rb'), grId = id('gr');
    const vb = [cx - side / 2, cy - side * 0.66, side, side * 1.333];
    return `<svg class="art" viewBox="${vb.join(' ')}" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
      <defs><radialGradient id="${bgId}" cx=".4" cy=".35" r=".9"><stop offset="0" stop-color="${opts.wall || '#D8D2C6'}"/><stop offset="1" stop-color="${shade(opts.wall || '#D8D2C6', -0.3)}"/></radialGradient>${grain(grId, 0.12)}${r.defs}</defs>
      <rect x="${vb[0]}" y="${vb[1]}" width="${vb[2]}" height="${vb[3]}" fill="url(#${bgId})"/>
      <path d="M${cx - 6},${y1 - 26} q6,-10 12,0 q0,6 -6,10 l0,6" fill="none" stroke="#6b6358" stroke-width="2"/>
      <g transform="rotate(-2 ${cx} ${cy})">${r.svg}</g>
      <rect x="${vb[0]}" y="${vb[1]}" width="${vb[2]}" height="${vb[3]}" filter="url(#${grId})"/></svg>`;
  }

  // Готовые образы для макетов (стабильные — одинаковые в светлой и тёмной теме).
  const LOOKS = {
    trench: { backdrop: 'sand', label: 'Тренч, свитер и широкие брюки', items: [['trench', 'camel'], ['sweater', 'cream'], ['wideTrousers', 'ecru'], ['loafers', 'black'], ['bag', 'chocolate']] },
    denim: { backdrop: 'mist', label: 'Джинсы, белая футболка и блейзер', items: [['blazer', 'navy'], ['tee', 'white'], ['jeans', 'denim'], ['sneakers', 'white']] },
    slip: { backdrop: 'blush', label: 'Платье-комбинация и кардиган', items: [['dress', 'butter'], ['heels', 'black'], ['bag', 'burgundy'], ['beret', 'burgundy']] },
    street: { backdrop: 'stone', label: 'Пуховик и спортивный комплект', items: [['puffer', 'black'], ['tee', 'grey'], ['wideTrousers', 'charcoal'], ['sneakers', 'white'], ['cap', 'black']] },
    olive: { backdrop: 'sage', label: 'Рубашка, юбка миди и ботинки', items: [['shirt', 'ecru'], ['skirtMidi', 'olive'], ['boots', 'chocolate'], ['belt', 'chocolate'], ['tote', 'tan']] },
    rust: { backdrop: 'clay', label: 'Жилет, рубашка и плиссе', items: [['shirt', 'white'], ['knitVest', 'rust'], ['skirtPleated', 'khaki'], ['loafers', 'chocolate']] },
    night: { backdrop: 'ink', label: 'Чёрный блейзер и брюки', items: [['blazer', 'black'], ['tank', 'white'], ['wideTrousers', 'black'], ['heels', 'black'], ['bag', 'ecru']] },
    summer: { backdrop: 'butter', label: 'Футболка, шорты и кеды', items: [['tee', 'sky'], ['shorts', 'ecru'], ['sneakers', 'white'], ['tote', 'cream'], ['cap', 'navy']] },
    forest: { backdrop: 'stone', label: 'Свитер и джинсы', items: [['sweater', 'forest'], ['jeans', 'denimLight'], ['boots', 'black'], ['scarf', 'cream']] },
    lilac: { backdrop: 'mist', label: 'Рубашка и плиссированная юбка', items: [['shirt', 'lilac'], ['skirtPleated', 'grey'], ['loafers', 'black']] },
  };

  window.DS_ART = { outfit, item, rawCapture, mannequin: (c) => mannequin(c), LOOKS, PALETTE, BACKDROPS, META, G, shade };
})();
