const tg = window.Telegram.WebApp;
tg.ready();
tg.expand();

async function authorizeUser() {
    try {
        const initData = tg.initData || '';

        if (!initData || initData.trim() === '') {
            document.body.innerHTML = `
                <div style="color:#ff4444; padding:40px; text-align:center; font-size:18px;">
                    initData пустой.<br><br>
                    Откройте мини-апп через inline-кнопку бота.
                </div>`;
            return;
        }

        const response = await fetch('/api/authorize/', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ initData: initData })
        });

        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
            console.error('Ошибка авторизации:', result);
            document.body.innerHTML = `
                <div style="color:#ff4444; padding:40px; text-align:center;">
                    Ошибка авторизации: ${result.error || 'Unknown'}
                </div>`;
            return;
        }

        tg.expand();
        window.location.href = '/home/';

    } catch (error) {
        console.error('Критическая ошибка:', error);
        document.body.innerHTML = `
            <div style="color:#ff4444; padding:40px; text-align:center;">
                Критическая ошибка.<br>
                Посмотрите консоль (F12).
            </div>`;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    authorizeUser();
});