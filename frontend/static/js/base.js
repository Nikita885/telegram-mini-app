// ── Global badge polling (messages + notifications) ───────────────────────────

var _globalBadgeInterval = null;
var _globalDialogsSocket = null;

function _startGlobalPolling() {
    _pollGlobalBadges();
    if (_globalBadgeInterval) clearInterval(_globalBadgeInterval);
    _globalBadgeInterval = setInterval(_pollGlobalBadges, 5000);
    _connectGlobalDialogsSocket();
}

function _connectGlobalDialogsSocket() {
    if (_globalDialogsSocket && _globalDialogsSocket.readyState <= 1) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    _globalDialogsSocket = new WebSocket(`${protocol}//${window.location.host}/ws/dialogs/`);

    _globalDialogsSocket.onmessage = (e) => {
        const data = JSON.parse(e.data);
        if (data.type === 'dialog_updated') {
            // Мгновенно обновляем badge
            _pollGlobalBadges();
            // Если открыта страница сообщений — обновляем список
            if (document.querySelector('.messages-page') && typeof updateDialogInList === 'function') {
                updateDialogInList(data.dialog);
            }
        }
    };

    _globalDialogsSocket.onclose = () => {
        setTimeout(_connectGlobalDialogsSocket, 3000);
    };
}

async function _pollGlobalBadges() {
    // 1. Messages unread count
    try {
        const resp = await fetch('/api/dialogs/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();
        const total = (data.dialogs || []).reduce((sum, d) => sum + (d.unread_count || 0), 0);
        _updateNavMsgBadge(total);
    } catch (e) {}

    // 2. Notification bell badge (home page)
    try {
        const resp2 = await fetch('/api/notifications/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data2 = await resp2.json();
        if (typeof _updateNotifBadge === 'function') {
            _updateNotifBadge(data2.unread_count || 0);
        }
    } catch (e) {}
}

function _updateNavMsgBadge(count) {
    const badge = document.getElementById('nav-msg-badge');
    if (!badge) return;
    if (count > 0) {
        badge.textContent = count > 99 ? '99+' : String(count);
        badge.style.display = 'flex';
    } else {
        badge.style.display = 'none';
    }
}

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
    initConstructorPage();
    initHomePage();
}

// ── SPA navigation ────────────────────────────────────────────────────────────

function _showLoader() {
    const loader = document.getElementById('page-loader');
    if (loader) loader.style.display = 'flex';
}

function _hideLoader() {
    const loader = document.getElementById('page-loader');
    if (loader) loader.style.display = 'none';
}

async function loadPage(url) {
    _showLoader();
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
    } finally {
        _hideLoader();
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
    _startGlobalPolling();
    
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