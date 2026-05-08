// ═══════════════════════════════════════════════════════════════════════════════
//  CONNECTIONS PAGE
// ═══════════════════════════════════════════════════════════════════════════════

function initConnectionsPage() {
    const page = document.querySelector('.connections-page');
    if (!page) return;

    document.body.classList.add('hide-nav');

    const backBtn = document.querySelector('.connections-back');
    const tabBtns = document.querySelectorAll('.tab-btn');
    const input = document.getElementById('connections-search-input');
    const clearBtn = document.getElementById('connections-search-clear');
    const list = document.getElementById('connections-list');

    let currentTab = document.querySelector('.tab-btn.active')?.dataset.tab || 'following';
    let allUsers = [];
    let debounceTimer = null;

    // ── Back button ────────────────────────────────────────────────────────
    if (backBtn) {
        backBtn.addEventListener('click', () => {
            loadPage('/profile/');
            history.pushState({}, '', '/profile/');
        });
    }

    // ── Tab switching ──────────────────────────────────────────────────────
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            if (btn.classList.contains('active')) return;

            tabBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentTab = btn.dataset.tab;

            // Clear search
            input.value = '';
            clearBtn.classList.add('hidden');

            // Load new data
            loadConnections(currentTab);
        });
    });

    // ── Search ─────────────────────────────────────────────────────────────
    input.addEventListener('input', () => {
        const q = input.value.trim();
        clearBtn.classList.toggle('hidden', q.length === 0);

        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => filterUsers(q), 300);
    });

    clearBtn.addEventListener('click', () => {
        input.value = '';
        clearBtn.classList.add('hidden');
        filterUsers('');
        input.focus();
    });

    // ── Load initial data ──────────────────────────────────────────────────
    loadConnections(currentTab);

    // ── Functions ──────────────────────────────────────────────────────────

    async function loadConnections(tab) {
        showLoading();
        try {
            const resp = await fetch(`/api/connections/${tab}/`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            const data = await resp.json();

            allUsers = data.users || [];
            filterUsers('');
        } catch (e) {
            console.error('Load connections error:', e);
            showError();
        }
    }

    function filterUsers(query) {
        const q = query.toLowerCase();
        const filtered = q
            ? allUsers.filter(u => {
                const name = [u.first_name, u.last_name].filter(Boolean).join(' ').toLowerCase();
                const username = (u.username || '').toLowerCase();
                return name.includes(q) || username.includes(q);
            })
            : allUsers;

        renderUsers(filtered);
    }

    function renderUsers(users) {
        list.innerHTML = '';

        if (users.length === 0) {
            const query = input.value.trim();
            const emptyMsg = query
                ? `По запросу «${escHtml(query)}» никого не найдено`
                : getEmptyMessage(currentTab);

            list.innerHTML = `
                <div class="connections-empty">
                    <div class="empty-icon">
                        <i class="${getEmptyIcon(currentTab)}"></i>
                    </div>
                    <p class="empty-title">${emptyMsg}</p>
                    ${!query ? `<p>${getEmptySubtext(currentTab)}</p>` : ''}
                </div>`;
            return;
        }

        users.forEach(u => list.appendChild(createUserCard(u)));
    }

    function createUserCard(user) {
        const card = document.createElement('div');
        card.className = 'user-card';

        const displayName = [user.first_name, user.last_name].filter(Boolean).join(' ')
            || user.username || 'Пользователь';

        const avatarHtml = user.avatar_url
            ? `<img src="${user.avatar_url}" alt="">`
            : `<span>${(displayName[0] || '?').toUpperCase()}</span>`;

        card.innerHTML = `
            <div class="user-card-avatar" style="background-color:${user.avatar_color}">
                ${avatarHtml}
            </div>
            <div class="user-card-info">
                <div class="user-card-name">${escHtml(displayName)}</div>
                ${user.username
                    ? `<div class="user-card-username">@${escHtml(user.username)}</div>`
                    : ''}
            </div>
            <button
                class="follow-btn ${user.is_following ? 'unfollow' : 'follow'}"
                data-id="${user.telegram_id}"
                data-following="${user.is_following}"
            >${user.is_following ? 'Отписаться' : 'Подписаться'}</button>
        `;

        // Navigate to profile on card click (but not follow button)
        card.addEventListener('click', (e) => {
            if (e.target.closest('.follow-btn')) return;
            const url = `/user/${user.telegram_id}/`;
            loadPage(url);
            history.pushState({}, '', url);
        });

        card.querySelector('.follow-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            toggleFollow(card.querySelector('.follow-btn'));
        });

        return card;
    }

    async function toggleFollow(btn) {
        const telegramId = parseInt(btn.dataset.id);
        btn.disabled = true;

        try {
            const resp = await fetch('/api/follow/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                },
                body: JSON.stringify({ telegram_id: telegramId }),
            });
            const data = await resp.json();

            if (data.status === 'followed') {
                btn.dataset.following = 'true';
                btn.textContent = 'Отписаться';
                btn.classList.replace('follow', 'unfollow');
            } else if (data.status === 'unfollowed') {
                btn.dataset.following = 'false';
                btn.textContent = 'Подписаться';
                btn.classList.replace('unfollow', 'follow');

                // Remove from list if we're on following/friends tab and unfollowed
                if (currentTab === 'following' || currentTab === 'friends') {
                    const card = btn.closest('.user-card');
                    if (card) {
                        card.remove();
                        // Update allUsers array
                        allUsers = allUsers.filter(u => u.telegram_id !== telegramId);
                        // Check if list is now empty
                        if (allUsers.length === 0) {
                            renderUsers([]);
                        }
                    }
                }
            }
        } catch (e) {
            console.error('Follow request failed:', e);
        } finally {
            btn.disabled = false;
        }
    }

    function showLoading() {
        list.innerHTML = `
            <div class="connections-loading">
                <div class="search-spinner"></div>
            </div>`;
    }

    function showError() {
        list.innerHTML = `
            <div class="connections-empty">
                <p class="empty-title">Ошибка загрузки</p>
                <p>Попробуйте ещё раз</p>
            </div>`;
    }

    function getEmptyMessage(tab) {
        const messages = {
            following: 'Нет подписок',
            followers: 'Нет подписчиков',
            friends: 'Нет друзей'
        };
        return messages[tab] || 'Пусто';
    }

    function getEmptySubtext(tab) {
        const subtexts = {
            following: 'Найдите интересных людей в поиске',
            followers: 'Пока никто не подписался на вас',
            friends: 'Подпишитесь на тех, кто подписан на вас'
        };
        return subtexts[tab] || '';
    }

    function getEmptyIcon(tab) {
        const icons = {
            following: 'ri-user-follow-line',
            followers: 'ri-user-add-line',
            friends: 'ri-team-line'
        };
        return icons[tab] || 'ri-user-line';
    }

    function escHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }
}