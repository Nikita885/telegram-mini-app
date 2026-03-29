function initProfilePage() {
    document.body.classList.remove('hide-nav');
    const avatarBtn = document.getElementById('avatar-button');
    const overlayBtn = document.getElementById('avatar-overlay-btn');

    const openAvatar = () => {
        loadPage('/avatar/');
        history.pushState({}, '', '/avatar/');
    };

    if (avatarBtn) avatarBtn.onclick = openAvatar;
    if (overlayBtn) overlayBtn.onclick = openAvatar;
}