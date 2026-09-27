/* Сплэш, онбординг, вход. */
(function () {
  const D = window.DS, M = window.DSM, A = window.DS_ART;

  // ------------------------------------------------------------------ Сплэш
  const hanger = `<svg viewBox="0 0 240 240" width="96" height="96" aria-hidden="true" style="color:var(--ds-color-on-background)">
    <path d="M110,84 A10,10 0 1,1 126,92 Q120,96 120,104 M120,104 L66,146 L174,146 Z" fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
    <circle cx="120" cy="160" r="5" fill="var(--ds-color-accent)"/></svg>`;

  M.screen({
    id: 'splash',
    section: 'Вход',
    title: 'Сплэш',
    description: 'Системный Splash Screen (Android 12+) показывает знак-вешалку на фоне темы, затем — журнальная «обложка» с вордмарком, пока грузится сессия (не дольше duration.splashMin без сети).',
    states: {
      system: {
        title: 'Системный сплэш',
        render: () => M.phone({ body: `<div class="state" style="padding:0">${hanger}</div>` }),
      },
      cover: {
        title: 'Обложка',
        render: () => M.phone({
          status: { overlay: true }, fullBleed: true,
          body: `<div style="position:absolute;inset:0">${D.photo('trench', { ratio: '4-5', style: 'position:absolute;inset:0;border-radius:0;aspect-ratio:auto' })}
            <div style="position:absolute;inset:0;background:linear-gradient(to bottom, rgba(0,0,0,.05) 40%, rgba(0,0,0,.6))"></div>
            <div style="position:absolute;left:24px;right:24px;bottom:64px;color:#fff">
              <div class="kicker" style="color:rgba(255,255,255,.8)">Выпуск осень · 2026</div>
              <div class="ds-t-display-large" style="margin-top:8px">Outfit<br><em style="font-style:italic">Share</em></div>
              <div class="quote" style="margin-top:12px;opacity:.9">Соберите образ — поделитесь стилем.</div>
              <div class="progress" style="margin-top:28px;background:rgba(255,255,255,.25)"><i style="width:42%;background:#fff"></i></div>
            </div></div>`,
        }),
      },
    },
    board: [['cover', 'light'], ['cover', 'dark'], ['system', 'light'], ['system', 'dark']],
  });

  // ------------------------------------------------------------------ Онбординг
  const slides = [
    { look: 'trench', n: '01', title: 'Собирайте образы на манекене', text: 'Выбирайте вещи из каталога — они садятся на манекен по опорным точкам, без ручной подгонки.' },
    { look: 'olive', n: '02', title: 'Вещи слоями, как в жизни', text: 'Рубашка под жилетом, пальто поверх — конструктор сам раскладывает слои по зонам тела.' },
    { look: 'slip', n: '03', title: 'Делитесь и вдохновляйтесь', text: 'Публикуйте образы, сохраняйте чужие в коллекции и делайте ремиксы.' },
  ];

  function onboarding(i) {
    const s = slides[i];
    const last = i === slides.length - 1;
    return M.phone({
      status: { overlay: true },
      body: `<div style="position:relative;flex:none;height:420px">${D.photo(s.look, { ratio: '4-5', style: 'position:absolute;inset:0;border-radius:0;aspect-ratio:auto' })}
          ${last ? '' : `<span style="position:absolute;top:36px;right:12px">${D.btn('Пропустить', { variant: 'ghost', style: 'color:#1C1B19' })}</span>`}</div>
        <div class="gutter" style="flex:1;display:flex;flex-direction:column;padding-top:var(--ds-space-6)">
          <div class="kicker">${s.n} / 03</div>
          <div class="ds-t-display-small" style="margin-top:var(--ds-space-2)">${s.title}</div>
          <div class="ds-t-body-large muted" style="margin-top:var(--ds-space-3)">${s.text}</div>
          <div style="flex:1"></div>
          <div style="display:flex;align-items:center;justify-content:space-between;padding-bottom:var(--ds-space-4)">
            <span class="pager" aria-label="Шаг ${i + 1} из 3">${slides.map((_, k) => `<i class="${k === i ? 'on' : ''}"></i>`).join('')}</span>
            ${D.btn(last ? 'Начать' : 'Далее', { variant: 'primary', size: 'large' })}
          </div>
        </div>`,
    });
  }

  M.screen({
    id: 'onboarding',
    section: 'Вход',
    title: 'Онбординг',
    description: 'Три разворота журнала: фото сверху, крупный заголовок антиквой, одна мысль на экран. «Пропустить» всегда доступно; свайп и кнопка «Далее» равнозначны.',
    states: {
      s1: { title: 'Шаг 1', render: () => onboarding(0) },
      s2: { title: 'Шаг 2', render: () => onboarding(1) },
      s3: { title: 'Шаг 3', render: () => onboarding(2) },
    },
    board: [['s1', 'light'], ['s2', 'light'], ['s3', 'light'], ['s1', 'dark'], ['s1', 'light', 2]],
  });

  // ------------------------------------------------------------------ Вход
  const tgIcon = D.icon('send');
  const loginHead = `<div class="gutter" style="padding-top:var(--ds-space-10)">
      <span class="wordmark" style="font-size:22px">Outfit <em>Share</em></span>
      <div class="ds-t-display-small" style="margin-top:var(--ds-space-10)">Войдите, чтобы сохранять образы и делиться ими</div>
    </div>`;
  const legal = `<div class="ds-t-body-small muted gutter" style="text-align:center;padding-bottom:var(--ds-space-4)">Продолжая, вы принимаете <span class="link" style="font:inherit">Условия</span> и <span class="link" style="font:inherit">Политику конфиденциальности</span></div>`;

  function login(o = {}) {
    return M.phone({
      body: `${o.banner || ''}${loginHead}
        <div class="gutter" style="margin-top:var(--ds-space-8);display:flex;flex-direction:column;gap:var(--ds-space-3)">
          ${D.btn(o.tgLoading ? 'Ждём подтверждения в Telegram…' : 'Войти через Telegram', { variant: 'primary', size: 'large', block: true, icon: o.tgLoading ? null : 'send', loading: o.tgLoading, disabled: o.disabled })}
          <div style="display:flex;align-items:center;gap:12px;margin:var(--ds-space-2) 0" class="muted ds-t-body-small"><span class="divider" style="flex:1"></span>или по почте<span class="divider" style="flex:1"></span></div>
          ${D.field({ label: 'Электронная почта', value: o.email, placeholder: 'Электронная почта', focused: o.focused, error: o.emailError })}
          ${D.btn('Получить код', { variant: 'secondary', size: 'large', block: true, disabled: o.disabled || !o.email })}
        </div><div style="flex:1"></div>${legal}`,
    });
  }

  function otp(o = {}) {
    const digits = o.digits || ['4', '8', '1', '', '', ''];
    const cells = digits.map((d, i) => {
      const focus = !o.error && i === digits.findIndex((x) => !x);
      return `<span class="otp__cell ${focus ? 'otp__cell--focused' : ''} ${o.error ? 'otp__cell--error' : ''}">${d}${focus ? '<span class="caret"></span>' : ''}</span>`;
    }).join('');
    return M.phone({
      body: `${D.appbar({ nav: 'arrow_back' })}
        <div class="gutter">
          <div class="ds-t-display-small">Код из письма</div>
          <div class="ds-t-body-large muted" style="margin-top:var(--ds-space-3)">Отправили 6 цифр на <b style="color:var(--ds-color-on-surface);font-weight:500">liza.mir@mail.ru</b></div>
          <div class="otp" style="margin-top:var(--ds-space-8)" aria-label="Код подтверждения">${cells}</div>
          ${o.error ? `<div class="field__help field__help--error" style="padding-left:0">Неверный код. Осталось 2 попытки.</div>` : ''}
          <div style="margin-top:var(--ds-space-6)">${o.error ? D.btn('Отправить новый код', { variant: 'ghost', style: 'margin-left:-12px' }) : `<span class="ds-t-body-medium muted">Отправить снова через <span class="num">0:42</span></span>`}</div>
        </div><div style="flex:1"></div>
        <div class="gutter" style="padding-bottom:var(--ds-space-4)">${D.btn(o.verifying ? 'Проверяем…' : 'Войти', { variant: 'primary', size: 'large', block: true, loading: o.verifying, disabled: !o.verifying && digits.some((x) => !x) })}</div>`,
    });
  }

  M.screen({
    id: 'login',
    section: 'Вход',
    title: 'Вход',
    description: 'Два пути: через Telegram-бота (основной, одна кнопка) и одноразовый код на почту. Кнопки не прыгают по ширине в состоянии загрузки; ошибки — текстом у поля, не только цветом.',
    states: {
      default: { title: 'Выбор способа', render: () => login({}) },
      email: { title: 'Ввод почты', render: () => login({ email: 'liza.mir@mail.ru', focused: true }) },
      telegram: { title: 'Ждём Telegram', render: () => login({ tgLoading: true }) },
      otp: { title: 'Код', render: () => otp() },
      otpError: { title: 'Неверный код', render: () => otp({ error: true, digits: ['4', '8', '1', '9', '0', '2'] }) },
      offline: { title: 'Офлайн', render: () => login({ disabled: true, banner: D.banner('Нет подключения — вход станет доступен, когда появится сеть') }) },
    },
    board: [['default', 'light'], ['default', 'dark'], ['email', 'light'], ['telegram', 'light'], ['otp', 'light'], ['otpError', 'light'], ['offline', 'light'], ['default', 'light', 2]],
  });
})();
