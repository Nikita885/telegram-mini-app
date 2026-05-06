function initSearchPage() {
    const searchPage = document.querySelector('.search-page');
    if (!searchPage) return;

    const input      = document.getElementById('search-input');
    const clearBtn   = document.getElementById('search-clear');
    const results    = document.getElementById('search-results');
    const emptyState = document.getElementById('empty-state');

    let debounceTimer = null;

    // ── Input handler ─────────────────────────────────────
    input.addEventListener('input', () => {
        const q = input.value.trim();

        clearBtn.classList.toggle('hidden', q.length === 0);

        clearTimeout(debounceTimer);

        if (!q) {
            showEmpty();
            return;
        }

        debounceTimer = setTimeout(() => doSearch(q), 300);
    });

    // ── Clear button ──────────────────────────────────────
    clearBtn.addEventListener('click', () => {
        input.value = '';
        clearBtn.classList.add('hidden');
        showEmpty();
        input.focus();
    });

    // ── Restore focus after SPA navigation ───────────────
    input.focus();

    // ─────────────────────────────────────────────────────

    function showEmpty() {
        results.innerHTML = '';
        const el = document.createElement('div');
        el.className = 'search-empty-state';
        el.id = 'empty-state';
        el.innerHTML = `
            <div class="empty-icon"><i class="ri-group-line"></i></div>
            <p class="empty-title">Найдите людей</p>
            <p class="empty-sub">Введите имя или @username</p>
        `;
        results.appendChild(el);
    }

    function showLoading() {
        results.innerHTML = `
            <div class="search-loading">
                <div class="search-spinner"></div>
            </div>
        `;
    }

    function showNoResults(q) {
        results.innerHTML = `
            <div class="search-no-results">
                <div class="empty-icon"><i class="ri-user-search-line"></i></div>
                <p class="empty-title">Никого не найдено</p>
                <p class="empty-sub">По запросу «${escapeHtml(q)}» нет результатов</p>
            </div>
        `;
    }

    async function doSearch(q) {
        showLoading();
        try {
            const resp = await fetch(`/api/search/?q=${encodeURIComponent(q)}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            if (!resp.ok) throw new Error('Server error');
            const data = await resp.json();

            if (!data.users || data.users.length === 0) {
                showNoResults(q);
                return;
            }

            renderUsers(data.users);
        } catch (e) {
            console.error('Search error:', e);
            results.innerHTML = `
                <div class="search-no-results">
                    <p class="empty-title">Ошибка поиска</p>
                    <p class="empty-sub">Попробуйте ещё раз</p>
                </div>
            `;
        }
    }

    function renderUsers(users) {
        results.innerHTML = '';
        users.forEach(user => {
            results.appendChild(createUserCard(user));
        });
    }

    function createUserCard(user) {
        const card = document.createElement('div');
        card.className = 'user-card';
        card.dataset.telegramId = user.telegram_id;

        const displayName = [user.first_name, user.last_name].filter(Boolean).join(' ') || user.username || 'Пользователь';
        const avatarHtml = user.avatar_url
            ? `<img src="${user.avatar_url}" alt="">`
            : `<span>${(displayName[0] || '?').toUpperCase()}</span>`;

        const followLabel  = user.is_following ? 'Подписан' : 'Подписаться';
        const followClass  = user.is_following ? 'unfollow' : 'follow';

        card.innerHTML = `
            <div class="user-card-avatar" style="background-color: ${user.avatar_color}">
                ${avatarHtml}
            </div>
            <div class="user-card-info">
                <div class="user-card-name">${escapeHtml(displayName)}</div>
                ${user.username ? `<div class="user-card-username">@${escapeHtml(user.username)}</div>` : ''}
            </div>
            <button class="follow-btn ${followClass}" data-id="${user.telegram_id}" data-following="${user.is_following}">
                ${followLabel}
            </button>
        `;

        const btn = card.querySelector('.follow-btn');
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleFollow(btn);
        });

        return card;
    }

    async function toggleFollow(btn) {
        const telegramId = btn.dataset.id;
        const isFollowing = btn.dataset.following === 'true';

        btn.disabled = true;

        try {
            const resp = await fetch('/api/follow/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                },
                body: JSON.stringify({ telegram_id: parseInt(telegramId) }),
            });

            const data = await resp.json();

            if (data.status === 'followed') {
                btn.dataset.following = 'true';
                btn.textContent = 'Подписан';
                btn.classList.replace('follow', 'unfollow');
            } else if (data.status === 'unfollowed') {
                btn.dataset.following = 'false';
                btn.textContent = 'Подписаться';
                btn.classList.replace('unfollow', 'follow');
            } else {
                console.error('Follow error:', data.error);
            }
        } catch (e) {
            console.error('Follow request failed:', e);
        } finally {
            btn.disabled = false;
        }
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }
}