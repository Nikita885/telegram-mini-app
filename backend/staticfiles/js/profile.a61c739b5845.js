function initProfilePage() {
    document.body.classList.remove('hide-nav');

    const newAvatarUrl = sessionStorage.getItem('avatar_updated');
    if (newAvatarUrl) {
        const container = document.querySelector('.avatar-container');
        if (container) {
            // Полностью заменяем innerHTML — браузер не успеет загрузить старый src
            container.innerHTML = `<img src="${newAvatarUrl}?t=${Date.now()}" class="avatar-img" style="width:100%;height:100%;object-fit:cover;">`;
            // Очищаем sessionStorage, чтобы не было повторной подмены
            sessionStorage.removeItem('avatar_updated');
        }
    }

    const avatarBtn = document.getElementById('avatar-button');
    const overlayBtn = document.getElementById('avatar-overlay-btn');

    const openAvatar = () => {
        loadPage('/avatar/');
        history.pushState({}, '', '/avatar/');
    };

    if (avatarBtn) avatarBtn.onclick = openAvatar;
    if (overlayBtn) overlayBtn.onclick = openAvatar;
}