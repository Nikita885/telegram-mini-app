// Получение CSRF-токена
function getCookie(name) {
    let cookieValue = null;
    if (document.cookie && document.cookie !== '') {
        const cookies = document.cookie.split(';');
        for (let i = 0; i < cookies.length; i++) {
            const cookie = cookies[i].trim();
            if (cookie.substring(0, name.length + 1) === (name + '=')) {
                cookieValue = decodeURIComponent(cookie.substring(name.length + 1));
                break;
            }
        }
    }
    return cookieValue;
}

// Синхронизация пользователя Telegram с БД (при запуске приложения)
async function saveTelegramUser() {
    const tg = window.Telegram && window.Telegram.WebApp;
    const user = tg && tg.initDataUnsafe && tg.initDataUnsafe.user;

    if (!user) {
        console.warn('No Telegram user data available');
        return null;
    }

    const payload = {
        telegram_id: user.id,
        username: user.username || null,
        first_name: user.first_name || null,
        last_name: user.last_name || null,
    };

    try {
        // Проверяем, есть ли пользователь в БД
        const checkResp = await fetch(`/api/telegram-user/${user.id}/`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' },
        });

        if (checkResp.status === 404) {
            // Создаём нового пользователя в БД
            const createResp = await fetch('/api/telegram-user/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRFToken': getCookie('csrftoken'),
                },
                body: JSON.stringify(payload),
            });

            if (!createResp.ok) {
                throw new Error(`Create failed: ${createResp.status}`);
            }
            console.log('User created in DB');
            return await createResp.json();
        }

        if (checkResp.ok) {
            // Обновляем существующего пользователя в БД
            const updateResp = await fetch('/api/telegram-user/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRFToken': getCookie('csrftoken'),
                },
                body: JSON.stringify(payload),
            });

            if (!updateResp.ok) {
                throw new Error(`Update failed: ${updateResp.status}`);
            }
            console.log('User updated in DB');
            return await updateResp.json();
        }
    } catch (error) {
        console.error('Telegram user sync error:', error);
        return null;
    }
}