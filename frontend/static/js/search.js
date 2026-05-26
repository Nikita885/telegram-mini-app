function initSearchPage() {
    const searchPage = document.querySelector('.search-page');
    if (!searchPage) return;

    const input    = document.getElementById('search-input');
    const clearBtn = document.getElementById('search-clear');
    const results  = document.getElementById('search-results');

    // ── Inject tabs ────────────────────────────────────────────────────────────
    let tabsEl = searchPage.querySelector('.search-tabs');
    if (!tabsEl) {
        tabsEl = document.createElement('div');
        tabsEl.className = 'search-tabs';
        tabsEl.innerHTML = `
            <button class="search-tab active" data-tab="people">Люди</button>
            <button class="search-tab" data-tab="posts">Посты</button>
        `;
        searchPage.querySelector('.search-header').insertAdjacentElement('afterend', tabsEl);
    }

    let activeTab = 'people';
    let debounceTimer = null;

    tabsEl.querySelectorAll('.search-tab').forEach(btn => {
        btn.addEventListener('click', () => {
            activeTab = btn.dataset.tab;
            tabsEl.querySelectorAll('.search-tab').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // Update placeholder
            input.placeholder = activeTab === 'people'
                ? 'Имя, @username...'
                : 'Хештег или описание...';

            const q = input.value.trim();
            if (q) doSearch(q);
            else showEmpty();
        });
    });

    // ── Input handler ──────────────────────────────────────────────────────────
    input.addEventListener('input', () => {
        const q = input.value.trim();
        clearBtn.classList.toggle('hidden', q.length === 0);

        // Auto-switch to posts tab when query starts with #
        if (q.startsWith('#') && activeTab !== 'posts') {
            activeTab = 'posts';
            tabsEl.querySelectorAll('.search-tab').forEach(b => {
                b.classList.toggle('active', b.dataset.tab === 'posts');
            });
        }

        clearTimeout(debounceTimer);
        if (!q) { showEmpty(); return; }
        debounceTimer = setTimeout(() => doSearch(q), 300);
    });

    clearBtn.addEventListener('click', () => {
        input.value = '';
        clearBtn.classList.add('hidden');
        showEmpty();
        input.focus();
    });

    setTimeout(() => input.focus(), 50);

    // ── Search dispatcher ──────────────────────────────────────────────────────
    async function doSearch(q) {
        showLoading();
        if (activeTab === 'posts') {
            await searchPosts(q);
        } else {
            await searchPeople(q);
        }
    }

    // ── People search ──────────────────────────────────────────────────────────
    async function searchPeople(q) {
        const query = q.startsWith('@') ? q.slice(1) : q;
        try {
            const resp = await fetch(`/api/search/?q=${encodeURIComponent(query)}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
            });
            if (!resp.ok) throw new Error('Server error');
            const data = await resp.json();

            if (!data.users?.length) { showNoResults(q, 'people'); return; }
            renderUsers(data.users);
        } catch (e) {
            showError();
        }
    }

    // ── Posts search ───────────────────────────────────────────────────────────
    async function searchPosts(q) {
        try {
            const resp = await fetch(`/api/search/posts/?q=${encodeURIComponent(q)}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
            });
            if (!resp.ok) throw new Error('Server error');
            const data = await resp.json();

            if (!data.posts?.length) { showNoResults(q, 'posts'); return; }
            renderPosts(data.posts);
        } catch (e) {
            showError();
        }
    }

    // ── Render users ───────────────────────────────────────────────────────────
    function renderUsers(users) {
        results.innerHTML = '';
        users.forEach(u => results.appendChild(createUserCard(u)));
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
            <div class="user-card-avatar" style="background-color:${user.avatar_color}">${avatarHtml}</div>
            <div class="user-card-info">
                <div class="user-card-name">${escapeHtml(displayName)}</div>
                ${user.username ? `<div class="user-card-username">@${escapeHtml(user.username)}</div>` : ''}
            </div>
            <button class="follow-btn ${user.is_following ? 'unfollow' : 'follow'}"
                data-id="${user.telegram_id}"
                data-following="${user.is_following}"
            >${user.is_following ? 'Отписаться' : 'Подписаться'}</button>
        `;

        card.addEventListener('click', e => {
            if (e.target.closest('.follow-btn')) return;
            const url = `/user/${user.telegram_id}/`;
            loadPage(url);
            history.pushState({}, '', url);
        });

        card.querySelector('.follow-btn').addEventListener('click', e => {
            e.stopPropagation();
            toggleFollow(card.querySelector('.follow-btn'));
        });

        return card;
    }

    // ── Render posts ───────────────────────────────────────────────────────────
    function renderPosts(posts) {
        results.innerHTML = '';
        const grid = document.createElement('div');
        grid.className = 'search-posts-grid';

        posts.forEach((post, index) => {
            const thumb = document.createElement('div');
            thumb.className = 'search-post-thumb';
            thumb.innerHTML = `
                <img src="${post.final_image || '/static/images/mannequin_male.png'}" alt="" draggable="false">
                <div class="post-likes"><i class="ri-heart-fill"></i>${post.likes_count || 0}</div>
            `;
            thumb.addEventListener('click', () => {
                openPostViewerWithPosts(posts, index);
            });
            grid.appendChild(thumb);
        });

        results.appendChild(grid);
    }

    // ── Follow toggle ──────────────────────────────────────────────────────────
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
            }
        } catch (e) {
            console.error('Follow error:', e);
        } finally {
            btn.disabled = false;
        }
    }

    // ── UI states ──────────────────────────────────────────────────────────────
    function showEmpty() {
        const icon  = activeTab === 'posts' ? 'ri-hashtag' : 'ri-group-line';
        const title = activeTab === 'posts' ? 'Найдите посты' : 'Найдите людей';
        const sub   = activeTab === 'posts'
            ? 'Введите хештег или слово из описания'
            : 'Введите имя или @username';
        results.innerHTML = `
            <div class="search-empty-state">
                <div class="empty-icon"><i class="${icon}"></i></div>
                <p class="empty-title">${title}</p>
                <p class="empty-sub">${sub}</p>
            </div>`;
    }

    function showLoading() {
        results.innerHTML = `<div class="search-loading"><div class="search-spinner"></div></div>`;
    }

    function showNoResults(q, type) {
        const icon = type === 'posts' ? 'ri-image-search-line' : 'ri-user-search-line';
        results.innerHTML = `
            <div class="search-no-results">
                <div class="empty-icon"><i class="${icon}"></i></div>
                <p class="empty-title">Ничего не найдено</p>
                <p class="empty-sub">По запросу «${escapeHtml(q)}» нет результатов</p>
            </div>`;
    }

    function showError() {
        results.innerHTML = `
            <div class="search-no-results">
                <p class="empty-title">Ошибка поиска</p>
                <p class="empty-sub">Попробуйте ещё раз</p>
            </div>`;
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }
}
