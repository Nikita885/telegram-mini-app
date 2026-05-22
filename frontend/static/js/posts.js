// ═══════════════════════════════════════════════════════════════════════════════
//  OUTFIT POSTS IN PROFILE
// ═══════════════════════════════════════════════════════════════════════════════

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
            const thumbnail = createPostThumbnail(post, index);
            postsGrid.appendChild(thumbnail);
        });

    } catch (e) {
        console.error('Load posts error:', e);
    }
}

function createPostThumbnail(post, index) {
    const div = document.createElement('div');
    div.className = 'post-thumbnail';
    
    // TODO: Заменить на final_image когда будет скриншот
    const imageUrl = post.final_image || '/static/images/mannequin_male.png';
    
    div.innerHTML = `<img src="${imageUrl}" alt="">`;
    
    div.addEventListener('click', () => {
        openPostViewer(post, index);
    });
    
    return div;
}

// ═══════════════════════════════════════════════════════════════════════════════
//  POST VIEWER (FULLSCREEN)
// ═══════════════════════════════════════════════════════════════════════════════

let currentPosts = [];
let currentPostIndex = 0;
let viewerTouchStartY = 0;

function openPostViewer(post, index) {
    // Загружаем все посты для свайпа
    const postsGrid = document.getElementById('posts-grid');
    if (!postsGrid) return;

    const telegramId = postsGrid.dataset.userId;
    
    fetch(`/api/outfit/user/${telegramId}/`)
        .then(r => r.json())
        .then(data => {
            currentPosts = data.posts || [];
            currentPostIndex = index;
            showPostViewer();
        });
}

function showPostViewer() {
    let viewer = document.getElementById('post-viewer');
    
    if (!viewer) {
        viewer = createPostViewer();
        document.body.appendChild(viewer);
    }
    
    updatePostViewerContent();
    viewer.classList.add('show');
    document.body.classList.add('hide-nav');
}

function closePostViewer() {
    const viewer = document.getElementById('post-viewer');
    if (viewer) {
        viewer.classList.remove('show');
        document.body.classList.remove('hide-nav');
    }
}

function createPostViewer() {
    const viewer = document.createElement('div');
    viewer.id = 'post-viewer';
    viewer.className = 'post-viewer';
    
    viewer.innerHTML = `
        <button class="post-viewer-close" id="post-viewer-close">
            <i class="ri-close-line"></i>
        </button>
        
        <div class="post-viewer-image" id="post-viewer-image">
            <img id="post-viewer-img" src="" alt="">
        </div>
        
        <div class="post-viewer-actions">
            <button class="post-action-btn" id="post-action-profile">
                <div class="post-action-avatar" id="post-author-avatar"></div>
            </button>
            
            <button class="post-action-btn" id="post-action-like">
                <div class="post-action-icon">
                    <i class="ri-heart-line"></i>
                </div>
                <span class="post-action-count" id="post-like-count">0</span>
            </button>
            
            <button class="post-action-btn" id="post-action-comment">
                <div class="post-action-icon">
                    <i class="ri-chat-3-line"></i>
                </div>
                <span class="post-action-count">0</span>
            </button>
        </div>
        
        <div class="post-viewer-description">
            <div class="post-description-text collapsed" id="post-description-text"></div>
            <button class="post-description-more" id="post-description-more">Ещё</button>
            <div class="post-hashtags" id="post-hashtags"></div>
        </div>
        
        <div class="post-swipe-hint">Свайп вверх для следующего</div>
    `;
    
    // Event listeners
    const closeBtn = viewer.querySelector('#post-viewer-close');
    closeBtn?.addEventListener('click', closePostViewer);
    
    const profileBtn = viewer.querySelector('#post-action-profile');
    profileBtn?.addEventListener('click', () => {
        const post = currentPosts[currentPostIndex];
        if (!post) return;
        
        closePostViewer();
        // TODO: Navigate to user profile
    });
    
    const likeBtn = viewer.querySelector('#post-action-like');
    likeBtn?.addEventListener('click', togglePostLike);
    
    const commentBtn = viewer.querySelector('#post-action-comment');
    commentBtn?.addEventListener('click', () => {
        // TODO: Open comments
        alert('Комментарии скоро будут доступны');
    });
    
    const moreBtn = viewer.querySelector('#post-description-more');
    moreBtn?.addEventListener('click', () => {
        const textEl = viewer.querySelector('#post-description-text');
        const isCollapsed = textEl?.classList.contains('collapsed');
        
        if (isCollapsed) {
            textEl?.classList.remove('collapsed');
            textEl?.classList.add('expanded');
            if (moreBtn) moreBtn.textContent = 'Скрыть';
        } else {
            textEl?.classList.add('collapsed');
            textEl?.classList.remove('expanded');
            if (moreBtn) moreBtn.textContent = 'Ещё';
        }
    });
    
    // Swipe to next
    const imageContainer = viewer.querySelector('#post-viewer-image');
    imageContainer?.addEventListener('touchstart', (e) => {
        viewerTouchStartY = e.touches[0].clientY;
    });
    
    imageContainer?.addEventListener('touchend', (e) => {
        const touchEndY = e.changedTouches[0].clientY;
        const deltaY = viewerTouchStartY - touchEndY;
        
        // Swipe up
        if (deltaY > 50) {
            nextPost();
        }
    });
    
    return viewer;
}

function updatePostViewerContent() {
    const post = currentPosts[currentPostIndex];
    if (!post) return;
    
    const viewer = document.getElementById('post-viewer');
    if (!viewer) return;
    
    // Image
    const img = viewer.querySelector('#post-viewer-img');
    if (img) {
        img.src = post.final_image || '/static/images/mannequin_male.png';
    }
    
    // Author avatar (TODO: load user data)
    const avatar = viewer.querySelector('#post-author-avatar');
    if (avatar) {
        avatar.style.background = '#666';
        avatar.textContent = '?';
    }
    
    // Likes
    const likeCount = viewer.querySelector('#post-like-count');
    if (likeCount) {
        likeCount.textContent = post.likes_count || 0;
    }
    
    // Description
    const descText = viewer.querySelector('#post-description-text');
    if (descText) {
        descText.textContent = post.description || '';
        descText.classList.add('collapsed');
        descText.classList.remove('expanded');
    }
    
    const moreBtn = viewer.querySelector('#post-description-more');
    if (moreBtn) {
        moreBtn.textContent = 'Ещё';
        moreBtn.style.display = post.description.length > 80 ? 'block' : 'none';
    }
    
    // Hashtags
    const hashtagsContainer = viewer.querySelector('#post-hashtags');
    if (hashtagsContainer) {
        hashtagsContainer.innerHTML = '';
        post.hashtags?.forEach(tag => {
            const span = document.createElement('span');
            span.className = 'post-hashtag';
            span.textContent = '#' + tag.tag;
            hashtagsContainer.appendChild(span);
        });
    }
}

function nextPost() {
    if (currentPostIndex < currentPosts.length - 1) {
        currentPostIndex++;
        updatePostViewerContent();
    } else {
        // Нет следующего поста - закрываем
        closePostViewer();
    }
}

function togglePostLike() {
    // TODO: API call to like/unlike
    const likeBtn = document.getElementById('post-action-like');
    const icon = likeBtn?.querySelector('i');
    const count = document.getElementById('post-like-count');
    
    const isLiked = likeBtn?.classList.contains('liked');
    
    if (isLiked) {
        likeBtn?.classList.remove('liked');
        if (icon) icon.className = 'ri-heart-line';
        if (count) count.textContent = parseInt(count.textContent) - 1;
    } else {
        likeBtn?.classList.add('liked');
        if (icon) icon.className = 'ri-heart-fill';
        if (count) count.textContent = parseInt(count.textContent) + 1;
    }
}