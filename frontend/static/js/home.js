// ═══════════════════════════════════════════════════════════════════════════════
//  HOME FEED — TikTok-style recommendations
// ═══════════════════════════════════════════════════════════════════════════════

var homePosts = [];
var homeCurrentIndex = 0;
var _homeMaxPostId = 0;
var _homeFeedInterval = null;
var _homeNotifInterval = null;

function initHomePage() {
    const page = document.getElementById('home-page');
    if (!page) return;

    _stopHomeFeedPolling();

    _ensureNotifBell(page);
    loadHomeFeed();
    _startNotifPolling();
}

// ── Notification bell ──────────────────────────────────────────────────────────
function _ensureNotifBell(page) {
    if (page.querySelector('.home-notif-btn')) return;
    const btn = document.createElement('button');
    btn.className = 'home-notif-btn';
    btn.innerHTML = `<i class="ri-notification-3-line"></i><span class="home-notif-badge" id="home-notif-badge"></span>`;
    btn.addEventListener('click', openNotifPanel);
    page.appendChild(btn);
}

function _updateNotifBadge(count) {
    const badge = document.getElementById('home-notif-badge');
    if (!badge) return;
    if (count > 0) {
        badge.textContent = count > 99 ? '99+' : String(count);
        badge.style.display = 'flex';
    } else {
        badge.style.display = 'none';
    }
}

function _startNotifPolling() {
    _stopNotifPolling();
    _pollNotifCount();
    _homeNotifInterval = setInterval(_pollNotifCount, 30000);
}

function _stopNotifPolling() {
    if (_homeNotifInterval) { clearInterval(_homeNotifInterval); _homeNotifInterval = null; }
}

async function _pollNotifCount() {
    // Keep polling regardless of page — _updateNotifBadge is a no-op when the
    // badge element doesn't exist, so there's no harm running this globally.
    try {
        const resp = await fetch('/api/notifications/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();
        _updateNotifBadge(data.unread_count || 0);
    } catch (e) {}
}

// ── Notifications panel ────────────────────────────────────────────────────────
async function openNotifPanel() {
    let panel = document.getElementById('home-notif-panel');
    if (!panel) {
        panel = document.createElement('div');
        panel.id = 'home-notif-panel';
        panel.className = 'notif-panel';
        panel.style.position = 'fixed';
        panel.style.zIndex = '1001';
        panel.innerHTML = `
            <div class="notif-panel-header">
                <span>Уведомления</span>
                <button class="notif-panel-close" id="notif-panel-close"><i class="ri-close-line"></i></button>
            </div>
            <div class="notif-list" id="notif-list"></div>
        `;
        panel.querySelector('#notif-panel-close').addEventListener('click', closeNotifPanel);
        document.body.appendChild(panel);
    }

    panel.classList.add('open');
    await _loadNotifications();

    fetch('/api/notifications/', {
        method: 'POST',
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
    }).then(() => _updateNotifBadge(0)).catch(() => {});
}

function closeNotifPanel() {
    document.getElementById('home-notif-panel')?.classList.remove('open');
}

async function _loadNotifications() {
    const list = document.getElementById('notif-list');
    if (!list) return;
    list.innerHTML = '<div class="notif-empty">Загрузка...</div>';

    try {
        const resp = await fetch('/api/notifications/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();

        if (!data.notifications?.length) {
            list.innerHTML = '<div class="notif-empty">Нет уведомлений</div>';
            return;
        }

        list.innerHTML = '';
        data.notifications.forEach(n => list.appendChild(_createNotifItem(n)));
    } catch (e) {
        list.innerHTML = '<div class="notif-empty">Ошибка загрузки</div>';
    }
}

function _createNotifItem(n) {
    const el = document.createElement('div');
    el.className = `notif-item${n.is_read ? '' : ' unread'}`;

    const u = n.sender;
    const avatarHtml = u.avatar
        ? `<img src="${u.avatar}" alt="">`
        : (u.first_name || u.username || '?')[0].toUpperCase();

    let text = '';
    if (n.type === 'like') {
        text = `<b>${escHtmlHome(u.first_name || u.username || '')}</b> лайкнул ваш пост`;
    } else if (n.type === 'comment') {
        const preview = n.comment?.text ? `«${escHtmlHome(n.comment.text.slice(0, 40))}»` : '';
        text = `<b>${escHtmlHome(u.first_name || u.username || '')}</b> прокомментировал: ${preview}`;
    } else if (n.type === 'follow') {
        text = `<b>${escHtmlHome(u.first_name || u.username || '')}</b> подписался на вас`;
    }

    const thumbHtml = n.post?.image
        ? `<img class="notif-post-thumb" src="${n.post.image}" alt="">`
        : '';

    el.innerHTML = `
        <div class="notif-avatar" style="background:${u.avatar_color || '#999'}">${avatarHtml}</div>
        <div class="notif-content">
            <div class="notif-text">${text}</div>
            <div class="notif-time">${n.created_at}</div>
        </div>
        ${thumbHtml}
    `;

    el.addEventListener('click', () => {
        closeNotifPanel();
        _handleNotifClick(n);
    });

    return el;
}

async function _handleNotifClick(n) {
    if (n.type === 'follow') {
        const url = `/user/${n.sender.telegram_id}/`;
        loadPage(url);
        history.pushState({}, '', url);
        return;
    }

    if (!n.post?.id) return;

    try {
        const resp = await fetch(`/api/outfit/${n.post.id}/`, {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();
        if (data.post) {
            openPostViewerWithPosts([data.post], 0);
            if (n.type === 'comment') {
                setTimeout(() => openCommentsPanel(n.comment?.id), 350);
            }
        }
    } catch (e) {
        console.error('Load post for notif error:', e);
    }
}

// ── Feed loading ───────────────────────────────────────────────────────────────
async function loadHomeFeed() {
    const page = document.getElementById('home-page');
    const feed = document.getElementById('home-feed');
    if (!feed) return;

    const newBtn = document.getElementById('home-new-posts-btn');
    if (newBtn) newBtn.style.display = 'none';

    try {
        const resp = await fetch('/api/home/feed/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
        const data = await resp.json();

        if (!data.posts?.length) {
            feed.innerHTML = `
                <div class="home-empty">
                    <i class="ri-t-shirt-line"></i>
                    <p>Пока нет постов</p>
                    <p class="home-empty-sub">Найдите людей в поиске и подпишитесь на них</p>
                </div>`;
            return;
        }

        homePosts = data.posts;
        _homeMaxPostId = Math.max(...homePosts.map(p => p.id));
        feed.innerHTML = '';

        const observer = new IntersectionObserver(entries => {
            entries.forEach(e => {
                if (e.isIntersecting && e.intersectionRatio >= 0.6) {
                    homeCurrentIndex = parseInt(e.target.dataset.index);
                }
            });
        }, { root: feed, threshold: 0.6 });

        data.posts.forEach((post, index) => {
            const slide = createHomeSlide(post, index);
            observer.observe(slide);
            feed.appendChild(slide);
        });

        // Ensure "new posts" button
        if (page && !document.getElementById('home-new-posts-btn')) {
            const btn = document.createElement('button');
            btn.id = 'home-new-posts-btn';
            btn.className = 'home-new-posts-btn';
            btn.innerHTML = '<i class="ri-arrow-up-line"></i> Новые посты';
            btn.style.display = 'none';
            btn.addEventListener('click', loadHomeFeed);
            page.appendChild(btn);
        }

        _startHomeFeedPolling();

    } catch (e) {
        if (feed) feed.innerHTML = `<div class="home-empty"><p>Ошибка загрузки</p></div>`;
        console.error('Home feed error:', e);
    }
}

// ── New-posts polling ──────────────────────────────────────────────────────────
function _startHomeFeedPolling() {
    _stopHomeFeedPolling();
    _homeFeedInterval = setInterval(async () => {
        if (!document.getElementById('home-feed')) { _stopHomeFeedPolling(); return; }
        if (!_homeMaxPostId) return;
        try {
            const resp = await fetch(`/api/home/feed/?since_id=${_homeMaxPostId}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            const d = await resp.json();
            if (d.new_count > 0) {
                const btn = document.getElementById('home-new-posts-btn');
                if (btn) {
                    btn.innerHTML = `<i class="ri-arrow-up-line"></i> ${d.new_count} новых постов`;
                    btn.style.display = 'flex';
                }
            }
        } catch (e) {}
    }, 30000);
}

function _stopHomeFeedPolling() {
    if (_homeFeedInterval) { clearInterval(_homeFeedInterval); _homeFeedInterval = null; }
}

// ── Slide creation ─────────────────────────────────────────────────────────────
function createHomeSlide(post, index) {
    const slide = document.createElement('div');
    slide.className = 'post-feed-slide home-slide';
    slide.dataset.index = index;
    slide.dataset.postId = post.id;

    const imageUrl = post.final_image || '/static/images/mannequin_male.png';
    const isLiked = post.is_liked;
    const outfitItems = post.outfit_items || [];

    let avatarContent;
    if (post.user?.avatar) {
        avatarContent = `<img src="${post.user.avatar}" alt="">`;
    } else {
        avatarContent = (post.user?.first_name || post.user?.username || '?')[0].toUpperCase();
    }

    const hashtagsHtml = (post.hashtags || [])
        .map(t => `<span class="post-hashtag">#${escHtmlHome(t.tag)}</span>`)
        .join('');

    const isOwn = post.user?.telegram_id && String(post.user.telegram_id) === String(window.__currentUserId);

    // Build carousel item pages
    const itemPagesHtml = outfitItems.map(item => {
        const itemImg = item.image
            ? `<img src="${item.image}" alt="" draggable="false">`
            : `<div class="carousel-item-placeholder"><i class="ri-t-shirt-line"></i></div>`;
        const buyHtml = item.buy_link
            ? `<a class="carousel-item-buy" href="${item.buy_link}" target="_blank" rel="noopener"><i class="ri-shopping-bag-line"></i> Где купить</a>`
            : '';
        const details = [
            item.category ? `<span class="carousel-item-tag">${escHtmlHome(item.category)}</span>` : '',
            item.style ? `<span class="carousel-item-tag">${escHtmlHome(item.style)}</span>` : '',
            item.color ? `<span class="carousel-item-tag">${escHtmlHome(item.color)}</span>` : '',
        ].filter(Boolean).join('');
        return `
        <div class="carousel-page carousel-item-page">
            <div class="carousel-item-img">${itemImg}</div>
            <div class="carousel-item-info">
                <div class="carousel-item-name">${escHtmlHome(item.name)}</div>
                ${details ? `<div class="carousel-item-tags">${details}</div>` : ''}
                ${item.item_description ? `<div class="carousel-item-desc">${escHtmlHome(item.item_description)}</div>` : ''}
                ${buyHtml}
            </div>
        </div>`;
    }).join('');

    // Dots
    const totalPages = 1 + outfitItems.length;
    const dotsHtml = totalPages > 1
        ? `<div class="carousel-dots">${Array.from({length: totalPages}, (_, i) => `<div class="carousel-dot${i === 0 ? ' active' : ''}"></div>`).join('')}</div>`
        : '';

    slide.innerHTML = `
        <div class="post-carousel">
            <div class="carousel-page carousel-outfit-page">
                <img src="${imageUrl}" alt="" draggable="false">
            </div>
            ${itemPagesHtml}
        </div>
        ${dotsHtml}
        <div class="post-slide-actions">
            <button class="post-action-btn home-profile-btn">
                <div class="post-action-avatar" style="background:${post.user?.avatar_color || '#999'}">
                    ${avatarContent}
                </div>
            </button>
            <button class="post-action-btn home-like-btn ${isLiked ? 'liked' : ''}">
                <div class="post-action-icon">
                    <i class="${isLiked ? 'ri-heart-fill' : 'ri-heart-line'}"></i>
                </div>
                <span class="post-action-count">${post.likes_count || 0}</span>
            </button>
            <button class="post-action-btn home-comment-btn">
                <div class="post-action-icon">
                    <i class="ri-chat-3-line"></i>
                </div>
                <span class="post-action-count">${post.comments_count || 0}</span>
            </button>
            <button class="post-action-btn home-share-btn">
                <div class="post-action-icon">
                    <i class="ri-send-plane-line"></i>
                </div>
            </button>
            ${isOwn ? `
            <button class="post-action-btn home-delete-btn">
                <div class="post-action-icon"><i class="ri-delete-bin-line"></i></div>
            </button>` : ''}
        </div>
        <div class="post-slide-info">
            <div class="post-slide-author">${escHtmlHome(post.user?.first_name || post.user?.username || '')}</div>
            ${post.description ? `<div class="post-slide-description">${escHtmlHome(post.description)}</div>` : ''}
            ${hashtagsHtml ? `<div class="post-hashtags">${hashtagsHtml}</div>` : ''}
        </div>
    `;

    // ── Carousel drag scroll (PC) + dot tracking ──
    const carousel = slide.querySelector('.post-carousel');
    const dots = slide.querySelectorAll('.carousel-dot');
    if (carousel) {
        setupCarouselDragScroll(carousel);
        if (dots.length > 0) {
            carousel.addEventListener('scroll', () => {
                const pageWidth = carousel.offsetWidth;
                const activeIndex = Math.round(carousel.scrollLeft / pageWidth);
                dots.forEach((d, i) => d.classList.toggle('active', i === activeIndex));
            }, { passive: true });
        }
    }

    // ── Double-tap to like on outfit page ──
    let lastTapTime = 0;
    slide.querySelector('.carousel-outfit-page').addEventListener('click', e => {
        if (carousel && carousel.dataset.dragging === 'true') {
            carousel.dataset.dragging = 'false';
            return;
        }
        const now = Date.now();
        if (now - lastTapTime < 350) {
            toggleHomeLike(slide, post);
            showHeartPop(e.clientX, e.clientY, slide);
        }
        lastTapTime = now;
    });

    // Profile
    slide.querySelector('.home-profile-btn').addEventListener('click', () => {
        if (post.user?.telegram_id) {
            const url = `/user/${post.user.telegram_id}/`;
            loadPage(url);
            history.pushState({}, '', url);
        }
    });

    // Like
    slide.querySelector('.home-like-btn').addEventListener('click', () => {
        toggleHomeLike(slide, post);
    });

    // Comment
    slide.querySelector('.home-comment-btn').addEventListener('click', () => {
        homeCurrentIndex = index;
        openHomeComments(post);
    });

    // Share
    slide.querySelector('.home-share-btn').addEventListener('click', () => {
        openShareModal(post);
    });

    // Delete (own post)
    slide.querySelector('.home-delete-btn')?.addEventListener('click', async () => {
        if (!confirm('Удалить этот пост?')) return;
        await deletePost(post.id);
        homePosts.splice(index, 1);
        slide.remove();
    });

    return slide;
}

// ── Likes ──────────────────────────────────────────────────────────────────────
async function toggleHomeLike(slide, post) {
    const btn = slide.querySelector('.home-like-btn');
    const icon = btn?.querySelector('i');
    const countEl = btn?.querySelector('.post-action-count');
    const wasLiked = btn?.classList.contains('liked');
    const prev = parseInt(countEl?.textContent || '0');

    btn?.classList.toggle('liked', !wasLiked);
    if (icon) icon.className = wasLiked ? 'ri-heart-line' : 'ri-heart-fill';
    if (countEl) countEl.textContent = wasLiked ? Math.max(0, prev - 1) : prev + 1;

    try {
        const resp = await fetch(`/api/outfit/${post.id}/like/`, {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
        const data = await resp.json();
        if (data.liked !== undefined) {
            btn?.classList.toggle('liked', data.liked);
            if (icon) icon.className = data.liked ? 'ri-heart-fill' : 'ri-heart-line';
            if (countEl) countEl.textContent = data.likes_count;
            post.likes_count = data.likes_count;
            post.is_liked = data.liked;
        }
    } catch (e) {
        btn?.classList.toggle('liked', wasLiked);
        if (icon) icon.className = wasLiked ? 'ri-heart-fill' : 'ri-heart-line';
        if (countEl) countEl.textContent = prev;
    }
}

// ── Comments ───────────────────────────────────────────────────────────────────
var _homeCommentsPost = null;

function openHomeComments(post) {
    _homeCommentsPost = post;
    const panel = ensureHomeCommentsPanel();
    panel.classList.add('open');
    loadHomeComments(post.id, panel);
}

function closeHomeComments() {
    const panel = document.getElementById('home-comments-panel');
    if (panel) panel.classList.remove('open');
    _homeCommentsPost = null;
}

function ensureHomeCommentsPanel() {
    let panel = document.getElementById('home-comments-panel');
    if (panel) return panel;

    panel = document.createElement('div');
    panel.id = 'home-comments-panel';
    panel.className = 'comments-panel';
    panel.style.position = 'fixed';
    panel.style.zIndex = '1001';

    panel.innerHTML = `
        <div class="comments-header">
            <span>Комментарии</span>
            <button class="comments-header-close" id="home-comments-close">
                <i class="ri-close-line"></i>
            </button>
        </div>
        <div class="comments-list" id="home-comments-list"></div>
        <div class="comments-input-row">
            <input type="text" id="home-comment-input" placeholder="Добавить комментарий..." autocomplete="off">
            <button class="comment-send-btn" id="home-comment-send">
                <i class="ri-send-plane-2-line"></i>
            </button>
        </div>
    `;

    panel.querySelector('#home-comments-close').addEventListener('click', closeHomeComments);
    panel.querySelector('#home-comment-send').addEventListener('click', submitHomeComment);
    panel.querySelector('#home-comment-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') submitHomeComment();
    });

    document.body.appendChild(panel);
    return panel;
}

async function loadHomeComments(postId, panel) {
    const list = panel.querySelector('#home-comments-list');
    if (!list) return;

    list.innerHTML = '<div class="comments-empty">Загрузка...</div>';

    try {
        const resp = await fetch(`/api/outfit/${postId}/comments/`);
        const data = await resp.json();

        if (!data.comments?.length) {
            list.innerHTML = '<div class="comments-empty">Комментариев пока нет</div>';
            return;
        }

        list.innerHTML = '';
        data.comments.forEach(c => {
            const el = createCommentItem(c, postId, closeHomeComments);
            list.appendChild(el);
        });
        list.scrollTop = list.scrollHeight;
    } catch (e) {
        list.innerHTML = '<div class="comments-empty">Ошибка загрузки</div>';
    }
}

async function submitHomeComment() {
    if (!_homeCommentsPost) return;
    const panel = document.getElementById('home-comments-panel');
    const input = panel?.querySelector('#home-comment-input');
    const text = input?.value.trim();
    if (!text) return;

    input.value = '';
    input.disabled = true;

    try {
        const resp = await fetch(`/api/outfit/${_homeCommentsPost.id}/comments/`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: JSON.stringify({ text }),
        });
        const comment = await resp.json();

        if (comment.id) {
            comment.likes_count = 0;
            comment.is_liked = false;
            const list = panel?.querySelector('#home-comments-list');
            list?.querySelector('.comments-empty')?.remove();
            const el = createCommentItem(comment, _homeCommentsPost.id, closeHomeComments);
            list?.appendChild(el);
            list?.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });

            const feed = document.getElementById('home-feed');
            const slide = feed?.querySelector(`.post-feed-slide[data-post-id="${_homeCommentsPost.id}"]`);
            const countEl = slide?.querySelector('.home-comment-btn .post-action-count');
            if (countEl) {
                _homeCommentsPost.comments_count = (_homeCommentsPost.comments_count || 0) + 1;
                countEl.textContent = _homeCommentsPost.comments_count;
            }
        }
    } catch (e) {
        if (input) input.value = text;
    } finally {
        if (input) input.disabled = false;
        input?.focus();
    }
}

function escHtmlHome(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
