function detectPlatform() {
    const userAgent = navigator.userAgent.toLowerCase();
    if (/iphone|ipad|ipod/.test(userAgent)) {
        document.documentElement.classList.add('ios');
    }
}

function initPage() {
    initProfilePage();
    initAvatarPage();
    initSearchPage();
    initUserProfilePage();
}

// ── SPA navigation ────────────────────────────────────────────────────────────

async function loadPage(url) {
    try {
        const response = await fetch(url, {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });

        if (!response.ok) throw new Error('Ошибка сервера');

        const html = await response.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');
        const newContent = doc.getElementById('main-content');

        if (newContent) {
            document.getElementById('main-content').innerHTML = newContent.innerHTML;
        }

        initPage();

        const isProfileRelated = url.startsWith('/profile') || url.startsWith('/avatar');
        if (!isProfileRelated) {
            sessionStorage.removeItem('avatar_updated');
        }

        setActiveButton();

    } catch (error) {
        console.error('Ошибка загрузки страницы:', error);
        window.location.href = url;
    }
}

// ── Navigation icons ──────────────────────────────────────────────────────────

function updateIcons() {
    document.querySelectorAll('.bottom-navigation__item').forEach(item => {
        const icon      = item.querySelector('.bottom-navigation__icon');
        const actionBtn = item.querySelector('.bottom-navigation__action');
        const active    = item.classList.contains('active');

        if (icon) {
            const swaps = {
                'ri-home-line':      'ri-home-2-fill',
                'ri-search-line':    'ri-search-2-fill',
                'ri-message-2-line': 'ri-message-2-fill',
                'ri-user-line':      'ri-user-2-fill',
            };
            const reverseSwaps = Object.fromEntries(
                Object.entries(swaps).map(([a, b]) => [b, a])
            );

            if (active) {
                Object.entries(swaps).forEach(([from, to]) => {
                    if (icon.classList.contains(from)) icon.classList.replace(from, to);
                });
            } else {
                Object.entries(reverseSwaps).forEach(([from, to]) => {
                    if (icon.classList.contains(from)) icon.classList.replace(from, to);
                });
            }
        }

        if (actionBtn) actionBtn.classList.toggle('active', active);
    });
}

function setActiveButton() {
    const currentPath = window.location.pathname;
    document.querySelectorAll('.bottom-navigation__item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('href') === currentPath) {
            item.classList.add('active');
        }
    });
    updateIcons();
}

function handleNavClick(event) {
    event.preventDefault();
    const href = event.currentTarget.getAttribute('href');
    if (!href) return;
    loadPage(href);
    history.pushState({}, '', href);
    setActiveButton();
}

// ── Popstate ──────────────────────────────────────────────────────────────────

window.addEventListener('popstate', () => {
    loadPage(window.location.pathname);
    setActiveButton();
});

// ── Boot ──────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    detectPlatform();
    setActiveButton();
    initPage();

    document.querySelectorAll('.bottom-navigation__item').forEach(item => {
        item.addEventListener('click', handleNavClick);
    });
});