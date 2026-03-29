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

            const formData = new FormData();
            formData.append('avatar', file);

            try {
                const response = await fetch('/upload-avatar/', {
                    method: 'POST',
                    body: formData
                });

                const data = await response.json();

                if (data.status === 'ok') {

                    // 🧹 чистим цвет
                    avatarFull.style.backgroundColor = '';
                    avatarFull.style.backgroundImage = `url(${data.avatar_url})`;
                    avatarFull.style.backgroundSize = 'cover';
                    avatarFull.style.backgroundPosition = 'center';

                    setTimeout(() => {
                        loadPage('/profile/');
                        history.pushState({}, '', '/profile/');
                    }, 200);

                } else {
                    console.error(data.error);
                }

            } catch (err) {
                console.error('Ошибка загрузки:', err);
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

            // 🧹 убираем картинку
            avatarFull.style.backgroundImage = '';
            avatarFull.style.backgroundColor = color;

            // debounce запрос
            clearTimeout(colorTimeout);

            colorTimeout = setTimeout(async () => {
                try {
                    const res = await fetch('/update-avatar-color/', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ color })
                    });

                    const data = await res.json();

                    if (data.status !== 'ok') {
                        console.error(data.error);
                    }

                } catch (err) {
                    console.error(err);
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
            try {
                const res = await fetch('/update-avatar/', {
                    method: 'POST'
                });

                const data = await res.json();

                if (data.status === 'ok') {

                    avatarFull.style.backgroundColor = '';
                    avatarFull.style.backgroundImage = `url(${data.avatar_url})`;
                    avatarFull.style.backgroundSize = 'cover';
                    avatarFull.style.backgroundPosition = 'center';

                    setTimeout(() => {
                        loadPage('/profile/');
                        history.pushState({}, '', '/profile/');
                    }, 200);

                } else {
                    console.error(data.error);
                }

            } catch (err) {
                console.error(err);
            }
        };
    }

    // =========================
    // 🔙 BACK
    // =========================
    if (backBtn) {
        backBtn.onclick = () => {
            document.body.classList.remove('hide-nav');

            loadPage('/profile/');
            history.pushState({}, '', '/profile/');
        };
    }
}