// ═══════════════════════════════════════════════════════════════════════════════
//  OUTFIT POSTS GRID
// ═══════════════════════════════════════════════════════════════════════════════

var currentPosts = [];
var currentPostIndex = 0;

// ── Multi-select deletion state ────────────────────────────────────────────────
var _selectionMode = false;
var _selectedPostIds = new Set();

function initProfilePosts() {
    const postsGrid = document.getElementById('posts-grid');
    if (!postsGrid) return;
    const telegramId = postsGrid.dataset.userId;
    if (!telegramId) return;
    loadUserPosts(telegramId);
}

async function loadUserPosts(telegramId) {
    const postsGrid = document.getElementById('posts-grid');
    if (!postsGrid) return;

    // Exit selection mode when reloading
    _exitSelectionMode();

    try {
        const resp = await fetch(`/api/outfit/user/${telegramId}/`);
        const data = await resp.json();

        if (!data.posts || data.posts.length === 0) {
            postsGrid.innerHTML = `
                <div class="posts-empty">
                    <i class="ri-t-shirt-line"></i>
                    <p>Пока нет постов</p>
                </div>`;
            return;
        }

        postsGrid.innerHTML = '';
        data.posts.forEach((post, index) => {
            postsGrid.appendChild(createPostThumbnail(post, index));
        });
    } catch (e) {
        console.error('Load posts error:', e);
    }
}

function createPostThumbnail(post, index) {
    const div = document.createElement('div');
    div.className = 'post-thumbnail';
    div.dataset.postId = post.id;
    div.innerHTML = `<img src="${post.final_image || '/static/images/mannequin_male.png'}" alt="" draggable="false">`;

    const grid = document.getElementById('posts-grid');
    const isOwn = grid && String(grid.dataset.userId) === String(window.__currentUserId);

    // Click: open viewer OR toggle selection
    let _lpWasTriggered = false;
    div.addEventListener('click', () => {
        if (_lpWasTriggered) { _lpWasTriggered = false; return; }
        if (_selectionMode && isOwn) {
            _togglePostSelection(post.id, div);
        } else if (!_selectionMode) {
            openPostViewer(index);
        }
    });

    if (isOwn) {
        let lpTimer = null;

        // ── Touch (mobile) ──
        div.addEventListener('touchstart', () => {
            _lpWasTriggered = false;
            lpTimer = setTimeout(() => {
                _lpWasTriggered = true;
                if (navigator.vibrate) navigator.vibrate(40);
                _enterSelectionMode(post.id, div);
            }, 550);
        }, { passive: true });

        ['touchend', 'touchmove', 'touchcancel'].forEach(ev => {
            div.addEventListener(ev, () => clearTimeout(lpTimer), { passive: true });
        });

        // ── Mouse (desktop) ──
        div.addEventListener('mousedown', (e) => {
            if (e.button !== 0) return; // left button only
            _lpWasTriggered = false;
            lpTimer = setTimeout(() => {
                _lpWasTriggered = true;
                if (!_selectionMode) _enterSelectionMode(post.id, div);
                else _togglePostSelection(post.id, div);
            }, 550);
        });

        ['mouseup', 'mouseleave'].forEach(ev => {
            div.addEventListener(ev, () => clearTimeout(lpTimer));
        });

        div.addEventListener('contextmenu', e => {
            e.preventDefault();
            clearTimeout(lpTimer);
            if (!_selectionMode) _enterSelectionMode(post.id, div);
            else _togglePostSelection(post.id, div);
        });
    }

    return div;
}

// ── Multi-select helpers ───────────────────────────────────────────────────────

function _enterSelectionMode(postId, thumbEl) {
    _selectionMode = true;
    _selectedPostIds.clear();
    // Select the first post immediately
    _selectedPostIds.add(postId);
    thumbEl.classList.add('post-thumbnail--selected');
    _ensureDeleteBar();
    _updateDeleteBarCount();
}

function _togglePostSelection(postId, thumbEl) {
    if (_selectedPostIds.has(postId)) {
        _selectedPostIds.delete(postId);
        thumbEl.classList.remove('post-thumbnail--selected');
    } else {
        _selectedPostIds.add(postId);
        thumbEl.classList.add('post-thumbnail--selected');
    }
    _updateDeleteBarCount();
}

function _exitSelectionMode() {
    _selectionMode = false;
    _selectedPostIds.clear();
    document.querySelectorAll('.post-thumbnail--selected').forEach(el => {
        el.classList.remove('post-thumbnail--selected');
    });
    document.getElementById('post-delete-bar')?.classList.remove('show');
}

function _ensureDeleteBar() {
    let bar = document.getElementById('post-delete-bar');
    if (bar) { bar.classList.add('show'); return bar; }

    bar = document.createElement('div');
    bar.id = 'post-delete-bar';
    bar.className = 'post-delete-bar';
    bar.innerHTML = `
        <button class="delete-bar-cancel" id="delete-bar-cancel">Отмена</button>
        <button class="delete-bar-confirm" id="delete-bar-confirm">
            <i class="ri-delete-bin-line"></i> <span id="delete-bar-label">Удалить</span>
        </button>
    `;
    document.body.appendChild(bar);

    document.getElementById('delete-bar-cancel').addEventListener('click', _exitSelectionMode);
    document.getElementById('delete-bar-confirm').addEventListener('click', _confirmDeleteSelected);

    bar.classList.add('show');
    return bar;
}

function _updateDeleteBarCount() {
    const count = _selectedPostIds.size;
    const label = document.getElementById('delete-bar-label');
    if (label) {
        // Russian plural: 1 пост, 2-4 поста, 5+ постов
        const word = count === 1 ? 'пост' : count >= 2 && count <= 4 ? 'поста' : 'постов';
        label.textContent = `Удалить ${count} ${word}`;
    }
    if (count === 0) _exitSelectionMode();
}

async function _confirmDeleteSelected() {
    const count = _selectedPostIds.size;
    if (count === 0) return;

    const word = count === 1 ? 'пост' : count >= 2 && count <= 4 ? 'поста' : 'постов';
    const confirmed = confirm(`Вы точно хотите удалить ${count} ${word}? Это действие нельзя отменить.`);
    if (!confirmed) return;

    const idsToDelete = [..._selectedPostIds];
    _exitSelectionMode();

    for (const postId of idsToDelete) {
        const thumb = document.querySelector(`.post-thumbnail[data-post-id="${postId}"]`);
        thumb?.remove();
        await deletePost(postId);
    }

    // Show empty state if grid is now empty
    const grid = document.getElementById('posts-grid');
    if (grid && !grid.querySelector('.post-thumbnail')) {
        grid.innerHTML = `
            <div class="posts-empty">
                <i class="ri-t-shirt-line"></i>
                <p>Пока нет постов</p>
            </div>`;
    }
}

async function deletePost(postId) {
    try {
        await fetch(`/api/outfit/${postId}/delete/`, {
            method: 'DELETE',
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
    } catch (e) {
        console.error('Delete post error:', e);
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  POST VIEWER — TikTok-стиль (CSS scroll snap)
// ═══════════════════════════════════════════════════════════════════════════════

function openPostViewer(startIndex) {
    const postsGrid = document.getElementById('posts-grid');
    if (!postsGrid) return;
    const telegramId = postsGrid.dataset.userId;

    fetch(`/api/outfit/user/${telegramId}/`)
        .then(r => r.json())
        .then(data => {
            currentPosts = data.posts || [];
            currentPostIndex = startIndex;
            showPostViewer();
        })
        .catch(e => console.error('Open viewer error:', e));
}

// Callable from search results / notifications
function openPostViewerWithPosts(posts, startIndex) {
    currentPosts = posts;
    currentPostIndex = startIndex;
    showPostViewer();
}

function showPostViewer() {
    let viewer = document.getElementById('post-viewer');
    if (!viewer) {
        viewer = buildViewerShell();
        document.body.appendChild(viewer);
    }

    buildFeed();
    viewer.classList.add('show');
    document.body.classList.add('hide-nav');

    requestAnimationFrame(() => {
        const feed = document.getElementById('post-feed');
        const slides = feed?.querySelectorAll('.post-feed-slide');
        if (slides?.[currentPostIndex]) {
            slides[currentPostIndex].scrollIntoView({ behavior: 'instant', block: 'start' });
        }
    });
}

function closePostViewer() {
    const viewer = document.getElementById('post-viewer');
    if (viewer) viewer.classList.remove('show');
    // Only restore nav if we're not inside a chat (chat manages hide-nav itself)
    if (!document.querySelector('.chat-page')) {
        document.body.classList.remove('hide-nav');
    }
    closeCommentsPanel();
}

// ── Build viewer shell (created once) ─────────────────────────────────────────
function buildViewerShell() {
    const viewer = document.createElement('div');
    viewer.id = 'post-viewer';
    viewer.className = 'post-viewer';

    viewer.innerHTML = `
        <button class="post-viewer-close" id="post-viewer-close">
            <i class="ri-close-line"></i>
        </button>
        <div class="post-feed" id="post-feed"></div>
        <div class="comments-panel" id="comments-panel">
            <div class="comments-header">
                <span>Комментарии</span>
                <button class="comments-header-close" id="comments-close">
                    <i class="ri-close-line"></i>
                </button>
            </div>
            <div class="comments-list" id="comments-list"></div>
            <div class="comments-input-row">
                <input type="text" id="comment-input" placeholder="Добавить комментарий..." autocomplete="off">
                <button class="comment-send-btn" id="comment-send">
                    <i class="ri-send-plane-2-line"></i>
                </button>
            </div>
        </div>
    `;

    viewer.querySelector('#post-viewer-close').addEventListener('click', closePostViewer);
    viewer.querySelector('#comments-close').addEventListener('click', closeCommentsPanel);
    viewer.querySelector('#comment-send').addEventListener('click', submitComment);
    viewer.querySelector('#comment-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') submitComment();
    });

    return viewer;
}

// ── Build feed slides ──────────────────────────────────────────────────────────
function buildFeed() {
    const feed = document.getElementById('post-feed');
    if (!feed) return;

    feed.innerHTML = '';

    const observer = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (entry.isIntersecting && entry.intersectionRatio >= 0.6) {
                currentPostIndex = parseInt(entry.target.dataset.index);
            }
        });
    }, { root: feed, threshold: 0.6 });

    currentPosts.forEach((post, index) => {
        const slide = createPostSlide(post, index);
        observer.observe(slide);
        feed.appendChild(slide);
    });
}

function createPostSlide(post, index) {
    const slide = document.createElement('div');
    slide.className = 'post-feed-slide';
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
        .map(t => `<span class="post-hashtag">#${escHtml(t.tag)}</span>`)
        .join('');

    const isOwn = post.user?.telegram_id && String(post.user.telegram_id) === String(window.__currentUserId);

    // Build carousel item pages
    const itemPagesHtml = outfitItems.map(item => {
        const itemImg = item.image ? `<img src="${item.image}" alt="" draggable="false">` : `<div class="carousel-item-placeholder"><i class="ri-t-shirt-line"></i></div>`;
        const buyHtml = item.buy_link
            ? `<a class="carousel-item-buy" href="${item.buy_link}" target="_blank" rel="noopener"><i class="ri-shopping-bag-line"></i> Где купить</a>`
            : '';
        const details = [
            item.category ? `<span class="carousel-item-tag">${escHtml(item.category)}</span>` : '',
            item.style ? `<span class="carousel-item-tag">${escHtml(item.style)}</span>` : '',
            item.color ? `<span class="carousel-item-tag">${escHtml(item.color)}</span>` : '',
        ].filter(Boolean).join('');
        return `
        <div class="carousel-page carousel-item-page">
            <div class="carousel-item-img">${itemImg}</div>
            <div class="carousel-item-info">
                <div class="carousel-item-name">${escHtml(item.name)}</div>
                ${details ? `<div class="carousel-item-tags">${details}</div>` : ''}
                ${item.item_description ? `<div class="carousel-item-desc">${escHtml(item.item_description)}</div>` : ''}
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
        <div class="post-carousel" id="carousel-${post.id}">
            <div class="carousel-page carousel-outfit-page">
                <img src="${imageUrl}" alt="" draggable="false">
            </div>
            ${itemPagesHtml}
        </div>
        ${dotsHtml}
        <div class="post-slide-actions">
            <button class="post-action-btn slide-profile-btn">
                <div class="post-action-avatar" style="background:${post.user?.avatar_color || '#999'}">
                    ${avatarContent}
                </div>
            </button>
            <button class="post-action-btn slide-like-btn ${isLiked ? 'liked' : ''}">
                <div class="post-action-icon">
                    <i class="${isLiked ? 'ri-heart-fill' : 'ri-heart-line'}"></i>
                </div>
                <span class="post-action-count">${post.likes_count || 0}</span>
            </button>
            <button class="post-action-btn slide-comment-btn">
                <div class="post-action-icon">
                    <i class="ri-chat-3-line"></i>
                </div>
                <span class="post-action-count">${post.comments_count || 0}</span>
            </button>
            <button class="post-action-btn slide-share-btn">
                <div class="post-action-icon">
                    <i class="ri-send-plane-line"></i>
                </div>
            </button>
            ${isOwn ? `
            <button class="post-action-btn slide-delete-btn">
                <div class="post-action-icon">
                    <i class="ri-delete-bin-line"></i>
                </div>
            </button>` : ''}
        </div>
        <div class="post-slide-info">
            ${post.description ? `<div class="post-slide-description">${escHtml(post.description)}</div>` : ''}
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

    // ── Double-tap to like on any carousel page ──
    let lastTapTime = 0;
    slide.querySelectorAll('.carousel-page').forEach(page => {
        page.addEventListener('click', e => {
            if (carousel && carousel.dataset.dragging === 'true') {
                carousel.dataset.dragging = 'false';
                return;
            }
            const now = Date.now();
            if (now - lastTapTime < 350) {
                toggleSlideLike(slide, post);
                showHeartPop(e.clientX, e.clientY, slide);
            }
            lastTapTime = now;
        });
    });

    // Profile button
    slide.querySelector('.slide-profile-btn').addEventListener('click', () => {
        if (post.user?.telegram_id) {
            closePostViewer();
            loadPage(`/user/${post.user.telegram_id}/`);
            history.pushState({}, '', `/user/${post.user.telegram_id}/`);
        }
    });

    // Like button
    slide.querySelector('.slide-like-btn').addEventListener('click', () => {
        toggleSlideLike(slide, post);
    });

    // Comment button
    slide.querySelector('.slide-comment-btn').addEventListener('click', () => {
        currentPostIndex = index;
        openCommentsPanel();
    });

    // Share button
    slide.querySelector('.slide-share-btn').addEventListener('click', () => {
        openShareModal(post);
    });

    // Delete button (only for own posts)
    slide.querySelector('.slide-delete-btn')?.addEventListener('click', async () => {
        if (!confirm('Удалить этот пост?')) return;
        await deletePost(post.id);
        currentPosts.splice(index, 1);
        slide.remove();

        // Also remove the thumbnail from the profile grid (if it's visible behind the viewer)
        const thumb = document.querySelector(`.post-thumbnail[data-post-id="${post.id}"]`);
        if (thumb) {
            thumb.remove();
            const grid = document.getElementById('posts-grid');
            if (grid && !grid.querySelector('.post-thumbnail')) {
                grid.innerHTML = `
                    <div class="posts-empty">
                        <i class="ri-t-shirt-line"></i>
                        <p>Пока нет постов</p>
                    </div>`;
            }
        }

        if (currentPosts.length === 0) closePostViewer();
    });

    return slide;
}

// ── Heart pop animation on double-tap ─────────────────────────────────────────
function showHeartPop(clientX, clientY, parent) {
    const rect = parent.getBoundingClientRect();
    const heart = document.createElement('div');
    heart.className = 'heart-pop';
    heart.textContent = '❤️';
    heart.style.left = (clientX - rect.left) + 'px';
    heart.style.top = (clientY - rect.top) + 'px';
    parent.appendChild(heart);
    setTimeout(() => heart.remove(), 900);
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LIKES
// ═══════════════════════════════════════════════════════════════════════════════

async function toggleSlideLike(slide, post) {
    const btn = slide.querySelector('.slide-like-btn');
    const icon = btn?.querySelector('i');
    const countEl = btn?.querySelector('.post-action-count');

    const wasLiked = btn?.classList.contains('liked');
    const prevCount = parseInt(countEl?.textContent || '0');

    if (wasLiked) {
        btn?.classList.remove('liked');
        if (icon) icon.className = 'ri-heart-line';
        if (countEl) countEl.textContent = Math.max(0, prevCount - 1);
    } else {
        btn?.classList.add('liked');
        if (icon) icon.className = 'ri-heart-fill';
        if (countEl) countEl.textContent = prevCount + 1;
    }

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
        if (countEl) countEl.textContent = prevCount;
        console.error('Like error:', e);
    }
}

async function toggleCommentLike(btn, postId, commentId) {
    const icon = btn.querySelector('i');
    const countEl = btn.querySelector('.comment-like-count');
    const wasLiked = btn.classList.contains('liked');
    const prevCount = parseInt(countEl?.textContent || '0');
    const newCount = wasLiked ? Math.max(0, prevCount - 1) : prevCount + 1;

    // Optimistic
    btn.classList.toggle('liked', !wasLiked);
    if (icon) icon.className = !wasLiked ? 'ri-heart-fill' : 'ri-heart-line';
    if (countEl) {
        countEl.textContent = newCount;
        countEl.style.display = newCount > 0 ? '' : 'none';
    }

    try {
        const resp = await fetch(`/api/outfit/${postId}/comments/${commentId}/like/`, {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
        const data = await resp.json();
        if (data.liked !== undefined) {
            btn.classList.toggle('liked', data.liked);
            if (icon) icon.className = data.liked ? 'ri-heart-fill' : 'ri-heart-line';
            if (countEl) {
                countEl.textContent = data.likes_count;
                countEl.style.display = data.likes_count > 0 ? '' : 'none';
            }
        }
    } catch (e) {
        // Revert
        btn.classList.toggle('liked', wasLiked);
        if (icon) icon.className = wasLiked ? 'ri-heart-fill' : 'ri-heart-line';
        if (countEl) {
            countEl.textContent = prevCount;
            countEl.style.display = prevCount > 0 ? '' : 'none';
        }
        console.error('Comment like error:', e);
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  COMMENTS
// ═══════════════════════════════════════════════════════════════════════════════

var _openCommentHighlight = null;

async function openCommentsPanel(highlightCommentId) {
    _openCommentHighlight = highlightCommentId || null;
    const panel = document.getElementById('comments-panel');
    if (!panel) return;
    panel.classList.add('open');
    await loadComments();
}

function closeCommentsPanel() {
    const panel = document.getElementById('comments-panel');
    if (panel) panel.classList.remove('open');
    _openCommentHighlight = null;
}

async function loadComments() {
    const post = currentPosts[currentPostIndex];
    if (!post) return;
    const list = document.getElementById('comments-list');
    if (!list) return;

    list.innerHTML = '<div class="comments-empty">Загрузка...</div>';

    try {
        const resp = await fetch(`/api/outfit/${post.id}/comments/`);
        const data = await resp.json();

        if (!data.comments?.length) {
            list.innerHTML = '<div class="comments-empty">Комментариев пока нет</div>';
            return;
        }
        list.innerHTML = '';
        data.comments.forEach(c => {
            const el = createCommentItem(c);
            if (_openCommentHighlight && String(c.id) === String(_openCommentHighlight)) {
                el.classList.add('comment-highlighted');
            }
            list.appendChild(el);
        });
        list.scrollTop = list.scrollHeight;

        const hi = list.querySelector('.comment-highlighted');
        if (hi) setTimeout(() => hi.scrollIntoView({ behavior: 'smooth', block: 'center' }), 150);

    } catch (e) {
        list.innerHTML = '<div class="comments-empty">Ошибка загрузки</div>';
    }
}

function createCommentItem(comment, postIdOverride, onAvatarClick) {
    const div = document.createElement('div');
    div.className = 'comment-item';
    div.dataset.commentId = comment.id;
    const u = comment.user;
    const letter = (u.first_name || u.username || '?')[0].toUpperCase();
    const avatarHtml = u.avatar ? `<img src="${u.avatar}" alt="">` : letter;
    const likesCount = comment.likes_count || 0;
    const isLiked = comment.is_liked || false;
    div.innerHTML = `
        <div class="comment-avatar" style="background-color:${u.avatar_color || '#999'}">${avatarHtml}</div>
        <div class="comment-body">
            <div class="comment-author">${escHtml(u.first_name || u.username || 'Пользователь')}</div>
            <div class="comment-text">${escHtml(comment.text)}</div>
            <div class="comment-time">${comment.created_at || ''}</div>
        </div>
        <button class="comment-like-btn${isLiked ? ' liked' : ''}" data-comment-id="${comment.id}">
            <i class="${isLiked ? 'ri-heart-fill' : 'ri-heart-line'}"></i>
            <span class="comment-like-count"${likesCount === 0 ? ' style="display:none"' : ''}>${likesCount}</span>
        </button>`;

    // Avatar click → profile
    if (u.telegram_id) {
        const avatarEl = div.querySelector('.comment-avatar');
        avatarEl.style.cursor = 'pointer';
        avatarEl.addEventListener('click', () => {
            if (onAvatarClick) {
                onAvatarClick();
            } else {
                closePostViewer();
            }
            const url = `/user/${u.telegram_id}/`;
            loadPage(url);
            history.pushState({}, '', url);
        });
    }

    // Comment like button
    const likeBtn = div.querySelector('.comment-like-btn');
    likeBtn.addEventListener('click', () => {
        const postId = postIdOverride || currentPosts[currentPostIndex]?.id;
        if (postId) toggleCommentLike(likeBtn, postId, comment.id);
    });

    return div;
}

async function submitComment() {
    const post = currentPosts[currentPostIndex];
    if (!post) return;
    const input = document.getElementById('comment-input');
    const text = input?.value.trim();
    if (!text) return;

    input.value = '';
    input.disabled = true;

    try {
        const resp = await fetch(`/api/outfit/${post.id}/comments/`, {
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
            const list = document.getElementById('comments-list');
            list?.querySelector('.comments-empty')?.remove();
            list?.appendChild(createCommentItem(comment));
            list?.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });

            const feed = document.getElementById('post-feed');
            const slide = feed?.querySelector(`.post-feed-slide[data-post-id="${post.id}"]`);
            const countEl = slide?.querySelector('.slide-comment-btn .post-action-count');
            if (countEl) {
                post.comments_count = (post.comments_count || 0) + 1;
                countEl.textContent = post.comments_count;
            }
        }
    } catch (e) {
        if (input) input.value = text;
        console.error('Submit comment error:', e);
    } finally {
        if (input) input.disabled = false;
        input?.focus();
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SHARE POST TO CHAT
// ═══════════════════════════════════════════════════════════════════════════════

function openShareModal(post) {
    let modal = document.getElementById('share-post-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'share-post-modal';
        modal.className = 'share-modal';
        modal.innerHTML = `
            <div class="share-modal-backdrop"></div>
            <div class="share-modal-sheet">
                <div class="share-modal-header">
                    <span>Отправить другу</span>
                    <button class="share-modal-close"><i class="ri-close-line"></i></button>
                </div>
                <div class="share-modal-list" id="share-modal-list"></div>
            </div>
        `;
        modal.querySelector('.share-modal-backdrop').addEventListener('click', closeShareModal);
        modal.querySelector('.share-modal-close').addEventListener('click', closeShareModal);
        document.body.appendChild(modal);
    }

    modal._sharePost = post;
    modal.classList.add('open');
    loadShareDialogs(modal);
}

function closeShareModal() {
    document.getElementById('share-post-modal')?.classList.remove('open');
}

async function loadShareDialogs(modal) {
    const list = modal.querySelector('#share-modal-list');
    if (!list) return;
    list.innerHTML = '<div class="share-loading">Загрузка...</div>';

    try {
        const resp = await fetch('/api/dialogs/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();

        if (!data.dialogs?.length) {
            list.innerHTML = '<div class="share-loading">Нет диалогов</div>';
            return;
        }

        list.innerHTML = '';
        data.dialogs.forEach(d => {
            const u = d.other_user;
            const name = [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username || 'Пользователь';
            const avatarHtml = u.avatar_url
                ? `<img src="${u.avatar_url}" alt="">`
                : (name[0] || '?').toUpperCase();

            const item = document.createElement('div');
            item.className = 'share-dialog-item';
            item.innerHTML = `
                <div class="share-dialog-avatar" style="background:${u.avatar_color || '#999'}">${avatarHtml}</div>
                <div class="share-dialog-name">${escHtml(name)}</div>
                <button class="share-send-btn">Отправить</button>
            `;
            const btn = item.querySelector('.share-send-btn');
            btn.addEventListener('click', async () => {
                btn.textContent = '✓';
                btn.disabled = true;
                await sendPostToDialog(d.dialog_id, modal._sharePost);
                setTimeout(closeShareModal, 700);
            });
            list.appendChild(item);
        });
    } catch (e) {
        list.innerHTML = '<div class="share-loading">Ошибка загрузки</div>';
    }
}

async function sendPostToDialog(dialogId, post) {
    const postData = {
        id: post.id,
        img: post.final_image || null,
        author: post.user?.first_name || post.user?.username || '',
        desc: (post.description || '').slice(0, 80),
    };
    try {
        await fetch(`/api/dialogs/${dialogId}/messages/`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: JSON.stringify({ text: `[post:${JSON.stringify(postData)}]` }),
        });
    } catch (e) {
        console.error('Send post error:', e);
    }
}

// ── Carousel drag-scroll for desktop mouse ────────────────────────────────────
function setupCarouselDragScroll(carousel) {
    let isDown = false;
    let startX, scrollLeft;

    carousel.addEventListener('mousedown', e => {
        isDown = true;
        carousel.dataset.dragging = 'false';
        startX = e.pageX;
        scrollLeft = carousel.scrollLeft;
        e.preventDefault();
    });

    carousel.addEventListener('mouseleave', () => { isDown = false; });

    carousel.addEventListener('mouseup', () => { isDown = false; });

    carousel.addEventListener('mousemove', e => {
        if (!isDown) return;
        const walk = e.pageX - startX;
        if (Math.abs(walk) > 4) {
            carousel.dataset.dragging = 'true';
            e.preventDefault();
            carousel.scrollLeft = scrollLeft - walk;
        }
    });
}

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
