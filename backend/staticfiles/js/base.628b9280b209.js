function detectPlatform() {
    if (/iphone|ipad|ipod/.test(navigator.userAgent.toLowerCase())) {
        document.documentElement.classList.add('ios');
    }
}

function initPage() {
    // Stop chat polling whenever we navigate away
    if (typeof _stopChatPolling === 'function') _stopChatPolling();

    document.body.classList.remove('hide-nav');

    initProfilePage();
    initAvatarPage();
    initSearchPage();
    initUserProfilePage();
    initMessagesPage();
    initChatPage();
    initConnectionsPage();
}

// ── SPA navigation ────────────────────────────────────────────────────────────

async function loadPage(url) {
    try {
        const response = await fetch(url, {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        if (!response.ok) throw new Error('Server error');

        const html = await response.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');
        const newContent = doc.getElementById('main-content');

        if (newContent) {
            document.getElementById('main-content').innerHTML = newContent.innerHTML;
        }

        const isProfileRelated = url.startsWith('/profile') || url.startsWith('/avatar');
        if (!isProfileRelated) sessionStorage.removeItem('avatar_updated');

        initPage();
        setActiveButton();

    } catch (error) {
        console.error('Ошибка загрузки:', error);
        window.location.href = url;
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

function updateIcons() {
    const swaps = {
        'ri-home-line':      'ri-home-2-fill',
        'ri-search-line':    'ri-search-2-fill',
        'ri-message-2-line': 'ri-message-2-fill',
        'ri-user-line':      'ri-user-2-fill',
    };
    const rev = Object.fromEntries(Object.entries(swaps).map(([a, b]) => [b, a]));

    document.querySelectorAll('.bottom-navigation__item').forEach(item => {
        const icon = item.querySelector('.bottom-navigation__icon');
        const btn  = item.querySelector('.bottom-navigation__action');
        const on   = item.classList.contains('active');

        if (icon) {
            const map = on ? swaps : rev;
            Object.entries(map).forEach(([from, to]) => {
                if (icon.classList.contains(from)) icon.classList.replace(from, to);
            });
        }
        if (btn) btn.classList.toggle('active', on);
    });
}

function setActiveButton() {
    const path = window.location.pathname;
    document.querySelectorAll('.bottom-navigation__item').forEach(item => {
        item.classList.toggle('active', item.getAttribute('href') === path);
    });
    updateIcons();
}

function handleNavClick(e) {
    e.preventDefault();
    const href = e.currentTarget.getAttribute('href');
    if (!href) return;
    loadPage(href);
    history.pushState({}, '', href);
    setActiveButton();
}

window.addEventListener('popstate', () => {
    loadPage(window.location.pathname);
    setActiveButton();
});

document.addEventListener('DOMContentLoaded', () => {
    detectPlatform();
    setActiveButton();
    initPage();
    
    // Navigation buttons
    document.querySelectorAll('.bottom-navigation__item').forEach(item => {
        item.addEventListener('click', handleNavClick);
    });
    
    // Handle clicks on stat links
    document.addEventListener('click', (e) => {
        const statLink = e.target.closest('.stat-link');
        if (statLink) {
            e.preventDefault();
            const href = statLink.getAttribute('href');
            if (href) {
                loadPage(href);
                history.pushState({}, '', href);
                setActiveButton();
            }
        }
    });
});