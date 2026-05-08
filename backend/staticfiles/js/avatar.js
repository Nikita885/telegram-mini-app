function setButtonLoading(btn, text = 'Загрузка...') {
    const original = btn.innerHTML;
    btn.innerHTML = text;
    btn.disabled = true;
    return () => {
        btn.innerHTML = original;
        btn.disabled = false;
    };
}

function validateAvatarFile(file) {
    const ALLOWED = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    const MAX_MB = 5;
    if (!ALLOWED.includes(file.type)) {
        return { ok: false, error: 'Разрешены только JPG, PNG, WEBP, GIF' };
    }
    if (file.size > MAX_MB * 1024 * 1024) {
        return { ok: false, error: `Максимальный размер — ${MAX_MB} МБ` };
    }
    return { ok: true };
}

function updateAvatarPreview(avatarFull, src) {
    if (!avatarFull) return;
    let img = avatarFull.querySelector('.avatar-img');
    const letter = avatarFull.querySelector('.avatar-letter');
    if (!img) {
        img = document.createElement('img');
        img.className = 'avatar-img';
        avatarFull.innerHTML = '';
        avatarFull.appendChild(img);
    }
    img.src = src;
    img.style.display = '';
    if (letter) letter.style.display = 'none';
}

function tgConfirm(message) {
    return new Promise((resolve) => {
        if (window.Telegram?.WebApp?.showConfirm) {
            window.Telegram.WebApp.showConfirm(message, resolve);
        } else {
            resolve(window.confirm(message));
        }
    });
}

function goToProfile() {
    loadPage('/profile/');
    history.pushState({}, '', '/profile/');
}

function initAvatarPage() {
    const avatarPage = document.querySelector('.avatar-page');
    if (!avatarPage) return;

    document.body.classList.add('hide-nav');

    const input       = document.getElementById('avatar-input');
    const avatarFull  = document.querySelector('.avatar-full');
    const backBtn     = document.querySelector('.avatar-back');
    const galleryBtn  = document.getElementById('gallery-btn');
    const telegramBtn = document.getElementById('telegram-btn');
    const deleteBtn   = document.getElementById('delete-btn');

    // ── Галерея ──────────────────────────────
    if (galleryBtn && input) {
        galleryBtn.onclick = () => {
            input.removeAttribute('capture');
            input.click();
        };

        input.onchange = async (e) => {
            const file = e.target.files[0];
            if (!file) return;

            const validation = validateAvatarFile(file);
            if (!validation.ok) {
                alert(validation.error);
                input.value = '';
                return;
            }

            const previewUrl = URL.createObjectURL(file);
            updateAvatarPreview(avatarFull, previewUrl);

            const restore = setButtonLoading(galleryBtn, 'Загрузка...');
            const formData = new FormData();
            formData.append('avatar', file);
            formData.append('type', 'upload');

            try {
                const res = await fetch('/update-avatar/?type=upload', {
                    method: 'POST',
                    body: formData,
                });
                const data = await res.json();

                if (data.status === 'ok') {
                    // Сохраняем url — профиль подхватит без перезагрузки
                    sessionStorage.setItem('avatar_updated', data.avatar_url);
                    goToProfile();
                } else {
                    alert('Ошибка загрузки: ' + data.error);
                }
            } catch (err) {
                console.error(err);
                alert('Не удалось загрузить файл. Проверьте соединение.');
            } finally {
                restore();
                input.value = '';
                URL.revokeObjectURL(previewUrl);
            }
        };
    }

    // ── Telegram ──────────────────────────────
    if (telegramBtn) {
        telegramBtn.onclick = async () => {
            const restore = setButtonLoading(telegramBtn, 'Загрузка...');
            try {
                const res = await fetch('/update-avatar/?type=telegram', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ type: 'telegram' }),
                });
                const data = await res.json();
                if (data.status === 'ok') {
                    // Telegram тоже возвращает avatar_url
                    sessionStorage.setItem('avatar_updated', data.avatar_url);
                    goToProfile();
                } else {
                    alert('Ошибка: ' + data.error);
                }
            } catch {
                alert('Не удалось загрузить аватар из Telegram.');
            } finally {
                restore();
            }
        };
    }

    // ── Удаление ──────────────────────────────
    if (deleteBtn) {
        deleteBtn.onclick = async () => {
            const confirmed = await tgConfirm('Удалить аватар?');
            if (!confirmed) return;

            const restore = setButtonLoading(deleteBtn, 'Удаление...');
            try {
                const res = await fetch('/update-avatar/?type=delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ type: 'delete' }),
                });
                const data = await res.json();
                if (data.status === 'ok') {
                    sessionStorage.removeItem('avatar_updated');
                    goToProfile();
                } else {
                    alert('Ошибка удаления: ' + data.error);
                }
            } catch {
                alert('Ошибка при удалении аватара.');
            } finally {
                restore();
            }
        };
    }

    // ── Назад ────────────────────────────────
    if (backBtn) backBtn.onclick = goToProfile;
}