/*
 * Рендер скриншотов дизайн-системы: доски экранов, компонентов, движения и кадры анимаций.
 *
 *   npm i --no-save playwright   # или глобальный playwright с Chromium
 *   node docs/design/mockups/render.mjs            # PNG в docs/design/screenshots/.raw
 *   python3 tools/designsystem/pack_screenshots.py # WebP/GIF в docs/design/screenshots
 */
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';
import { mkdirSync, rmSync } from 'node:fs';

let chromium;
try { ({ chromium } = await import('playwright')); } catch {
  ({ chromium } = await import('/opt/node22/lib/node_modules/playwright/index.mjs'));
}

const here = dirname(fileURLToPath(import.meta.url));
const raw = join(here, '..', 'screenshots', '.raw');
const DPR = 2;
rmSync(raw, { recursive: true, force: true });
for (const d of ['screens', 'components', 'foundations', 'motion', 'frames']) mkdirSync(join(raw, d), { recursive: true });

const url = (page, query = '') => pathToFileURL(join(here, page)).href + query;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: DPR });
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

async function open(target) {
  await page.goto(target);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(150);
}

// Доски экранов
await open(url('board.html'));
const ids = await page.evaluate(() => window.DSM.screens.map((s) => s.id));
for (const id of ids) {
  await open(url('board.html', `?screen=${id}`));
  await (await page.$('#board')).screenshot({ path: join(raw, 'screens', `${id}.png`) });
  console.log('screen', id);
}
for (const theme of ['light', 'dark']) {
  await open(url('board.html', `?mode=overview&theme=${theme}`));
  await (await page.$('#board')).screenshot({ path: join(raw, 'screens', `_overview-${theme}.png`) });
}

// Основы и компоненты
await open(url('components.html'));
for (const el of await page.$$('.board[id]')) {
  const id = await el.getAttribute('id');
  const dir = id.startsWith('foundations') ? 'foundations' : 'components';
  await el.screenshot({ path: join(raw, dir, `${id}.png`) });
  console.log(dir, id);
}

// Движение: доски и кадры для GIF
await open(url('motion.html'));
for (const el of await page.$$('.board[id]')) {
  const id = await el.getAttribute('id');
  await el.screenshot({ path: join(raw, 'motion', `${id}.png`) });
  console.log('motion', id);
}
const FPS = 30;
const scenes = {
  drop: { ms: 700, fn: 'dropStage', theme: 'light' },
  heart: { ms: 800, fn: 'heartStage', theme: 'light' },
  shared: { ms: 450, fn: 'sharedStage', theme: 'light', progress: true },
};
// Кадры GIF — в 1x: анимация иллюстративная, вес важнее резкости.
const framePage = await browser.newPage({ viewport: { width: 600, height: 800 }, deviceScaleFactor: 1 });
framePage.on('pageerror', (e) => errors.push(String(e)));
await framePage.goto(url('motion.html'));
await framePage.evaluate(() => document.fonts.ready);
for (const [name, s] of Object.entries(scenes)) {
  mkdirSync(join(raw, 'frames', name), { recursive: true });
  const n = Math.round((s.ms / 1000) * FPS);
  for (let i = 0; i <= n; i++) {
    const t = (i / FPS) * 1000;
    await framePage.evaluate(({ fn, t, progress, total, theme }) => {
      const arg = progress ? Math.min(1, t / window.DS_TOKENS.motion.duration.long) : Math.round(t);
      document.body.innerHTML = `<div id="stage" data-theme="${theme}" style="display:inline-block;padding:16px;background:var(--ds-color-background)">${window.DS_MOTION[fn](arg)}</div>`;
    }, { fn: s.fn, t, progress: !!s.progress, total: s.ms, theme: s.theme });
    await (await framePage.$('#stage')).screenshot({ path: join(raw, 'frames', name, `${String(i).padStart(3, '0')}.png`) });
  }
  console.log('frames', name, n + 1);
}

await browser.close();
if (errors.length) { console.error('Ошибки страницы:\n' + errors.join('\n')); process.exit(1); }
