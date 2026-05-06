function detectPlatform() {
    const userAgent = navigator.userAgent.toLowerCase();
    const isIOS = /iphone|ipad|ipod/.test(userAgent);
    if (isIOS) {
        document.documentElement.classList.add('ios');
    }
}

function initPage() {
    initProfilePage();
    initAvatarPage();
    initSearchPage();
}

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

        // Dynamically load page-specific CSS if not yet loaded
        const cssLinks = doc.querySelectorAll('link[rel="stylesheet"]');
        cssLinks.forEach(link => {
            const href = link.getAttribute('href');
            if (href && !document.querySelector(`link[href="${href}"]`)) {
                const newLink = document.createElement('link');
                newLink.rel = 'stylesheet';
                newLink.href = href;
                document.head.appendChild(newLink);
            }
        });

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

function updateIcons() {
    const navItems = document.querySelectorAll('.bottom-navigation__item');

    navItems.forEach(item => {
        const icon = item.querySelector('.bottom-navigation__icon');
        const actionBtn = item.querySelector('.bottom-navigation__action');

        if (item.classList.contains('active')) {
            if (icon) {
                if (icon.classList.contains('ri-home-line')) icon.classList.replace('ri-home-line', 'ri-home-2-fill');
                else if (icon.classList.contains('ri-search-line')) icon.classList.replace('ri-search-line', 'ri-search-2-fill');
                else if (icon.classList.contains('ri-message-2-line')) icon.classList.replace('ri-message-2-line', 'ri-message-2-fill');
                else if (icon.classList.contains('ri-user-line')) icon.classList.replace('ri-user-line', 'ri-user-2-fill');
            }
            if (actionBtn) actionBtn.classList.add('active');
        } else {
            if (icon) {
                if (icon.classList.contains('ri-home-2-fill')) icon.classList.replace('ri-home-2-fill', 'ri-home-line');
                else if (icon.classList.contains('ri-search-2-fill')) icon.classList.replace('ri-search-2-fill', 'ri-search-line');
                else if (icon.classList.contains('ri-message-2-fill')) icon.classList.replace('ri-message-2-fill', 'ri-message-2-line');
                else if (icon.classList.contains('ri-user-2-fill')) icon.classList.replace('ri-user-2-fill', 'ri-user-line');
            }
            if (actionBtn) actionBtn.classList.remove('active');
        }
    });
}

function setActiveButton() {
    const currentPath = window.location.pathname;
    const navItems = document.querySelectorAll('.bottom-navigation__item');

    navItems.forEach(item => {
        item.classList.remove('active');
        const href = item.getAttribute('href');
        if (href === currentPath) {
            item.classList.add('active');
        }
    });

    updateIcons();
}

function handleNavClick(event) {
    event.preventDefault();
    const item = event.currentTarget;
    const href = item.getAttribute('href');
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

    const navItems = document.querySelectorAll('.bottom-navigation__item');
    navItems.forEach(item => {
        item.addEventListener('click', handleNavClick);
    });
});