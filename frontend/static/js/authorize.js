// Получаем доступ к Telegram WebApp API
const tg = window.Telegram.WebApp;

// Инициализируем WebApp
tg.ready();

// Функция для авторизации пользователя
async function authorizeUser() {
    try {
        // Получаем данные пользователя из Telegram WebApp
        const user = tg.initData && tg.initDataUnsafe?.user;
        
        if (!user) {
            console.error('Пользователь не найден в Telegram WebApp');
            tg.close();
            return;
        }
        
        // Подготавливаем данные для отправки на сервер
        const userData = {
            telegram_id: user.id,
            username: user.username || null,
            first_name: user.first_name || null,
            last_name: user.last_name || null,
            language_code: user.language_code || null,
        };
        
        // Отправляем запрос на сервер
        const response = await fetch('/api/authorize/', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRFToken': getCookie('csrftoken'),
            },
            body: JSON.stringify(userData),
        });
        
        if (!response.ok) {
            const error = await response.json();
            console.error('Ошибка авторизации:', error);
            tg.close();
            return;
        }
        
        const result = await response.json();
        
        // Получаем telegram_id и перенаправляемся на home страницу
        const telegramId = result.telegram_id;
        window.location.href = `/home/${telegramId}/`;
        
    } catch (error) {
        console.error('Ошибка при авторизации:', error);
        tg.close();
    }
}

// Функция для получения CSRF токена из cookies
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

// Запускаем авторизацию при загрузке страницы
document.addEventListener('DOMContentLoaded', authorizeUser);
