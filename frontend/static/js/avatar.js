function initAvatarPage() {
    const avatarPage = document.querySelector('.avatar-page');
    if (!avatarPage) return;

    document.body.classList.add('hide-nav');

    const input = document.getElementById('avatar-input');
    const avatarFull = document.querySelector('.avatar-full');

    const colorBtn = document.getElementById('color-btn');
    const colorPicker = document.getElementById('color-picker');

    const backBtn = document.querySelector('.avatar-back');
    const galleryBtn = document.getElementById('gallery-btn');
    const telegramBtn = document.getElementById('telegram-btn');
    const deleteBtn = document.getElementById('delete-btn');

    let colorTimeout = null;

    // =========================
    // 📱 ГАЛЕРЕЯ / ЗАГРУЗКА
    // =========================
    if (galleryBtn && input) {
        galleryBtn.onclick = () => {
            input.removeAttribute('capture');
            input.click();
        };
    }

    if (input) {
        input.onchange = async (e) => {
            const file = e.target.files[0];
            if (!file) return;

            // Показываем индикатор загрузки
            const loadingText = galleryBtn.innerHTML;
            galleryBtn.innerHTML = '⏳ Загрузка...';
            galleryBtn.disabled = true;

            const formData = new FormData();
            formData.append('avatar', file);
            formData.append('type', 'upload');

            try {
                const response = await fetch('/update-avatar/?type=upload', {
                    method: 'POST',
                    body: formData
                });

                const data = await response.json();

                if (data.status === 'ok') {
                    loadPage('/profile/');
                    history.pushState({}, '', '/profile/');
                } else {
                    console.error(data.error);
                    alert('Ошибка загрузки: ' + data.error);
                }

            } catch (err) {
                console.error('Ошибка загрузки:', err);
                alert('Ошибка загрузки файла');
            } finally {
                galleryBtn.innerHTML = loadingText;
                galleryBtn.disabled = false;
                input.value = ''; // Очищаем input
            }
        };
    }

    // =========================
    // 🎨 ЦВЕТ
    // =========================
    if (colorBtn && colorPicker) {

        colorBtn.onclick = () => {
            colorPicker.classList.add('active');
            colorPicker.click();
        };

        colorPicker.oninput = (e) => {
            const color = e.target.value;

            // Визуальное обновление
            const img = avatarFull.querySelector('.avatar-img');
            if (img) {
                img.style.display = 'none';
            }
            avatarFull.style.backgroundImage = '';
            avatarFull.style.backgroundColor = color;

            clearTimeout(colorTimeout);

            colorTimeout = setTimeout(async () => {
                try {
                    const res = await fetch('/update-avatar/?type=color', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ color, type: 'color' })
                    });

                    const data = await res.json();

                    if (data.status !== 'ok') {
                        console.error(data.error);
                        alert('Ошибка изменения цвета');
                    }

                } catch (err) {
                    console.error(err);
                    alert('Ошибка изменения цвета');
                }
            }, 250);
        };

        colorPicker.onchange = () => {
            colorPicker.classList.remove('active');
        };
    }

    // =========================
    // 🤖 TELEGRAM AVATAR
    // =========================
    if (telegramBtn) {
        telegramBtn.onclick = async () => {
            const originalText = telegramBtn.innerHTML;
            telegramBtn.innerHTML = '⏳ Загрузка...';
            telegramBtn.disabled = true;

            try {
                const res = await fetch('/update-avatar/?type=telegram', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ type: 'telegram' })
                });

                const data = await res.json();

                if (data.status === 'ok') {
                    loadPage('/profile/');
                    history.pushState({}, '', '/profile/');
                } else {
                    console.error(data.error);
                    alert('Ошибка: ' + data.error);
                }

            } catch (err) {
                console.error(err);
                alert('Ошибка загрузки из Telegram');
            } finally {
                telegramBtn.innerHTML = originalText;
                telegramBtn.disabled = false;
            }
        };
    }

    // =========================
    // 🗑️ УДАЛЕНИЕ АВАТАРА
    // =========================
    if (deleteBtn) {
        deleteBtn.onclick = async () => {
            // Подтверждение удаления
            const confirmed = confirm('Вы уверены, что хотите удалить аватар?');
            if (!confirmed) return;

            const originalText = deleteBtn.innerHTML;
            deleteBtn.innerHTML = '⏳ Удаление...';
            deleteBtn.disabled = true;

            try {
                const res = await fetch('/update-avatar/?type=delete', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ type: 'delete' })
                });

                const data = await res.json();

                if (data.status === 'ok') {
                    // Обновляем страницу для отображения изменений
                    loadPage('/profile/');
                    history.pushState({}, '', '/profile/');
                } else {
                    console.error(data.error);
                    alert('Ошибка удаления: ' + data.error);
                }

            } catch (err) {
                console.error(err);
                alert('Ошибка при удалении аватара');
            } finally {
                deleteBtn.innerHTML = originalText;
                deleteBtn.disabled = false;
            }
        };
    }

    // =========================
    // 🔙 BACK
    // =========================
    if (backBtn) {
        backBtn.onclick = () => {
            loadPage('/profile/');
            history.pushState({}, '', '/profile/');
        };
    }
}