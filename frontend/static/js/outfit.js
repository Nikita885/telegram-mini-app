// ═══════════════════════════════════════════════════════════════════════════════
//  OUTFIT CREATOR
// ═══════════════════════════════════════════════════════════════════════════════

function initOutfitCreator() {
    const page = document.querySelector('.outfit-creator');
    if (!page) return;

    document.body.classList.add('hide-nav');

    const canvas = document.getElementById('outfit-canvas');
    const ctx = canvas ? canvas.getContext('2d') : null;
    const backBtn = document.getElementById('creator-back-btn');
    const saveBtn = document.getElementById('creator-save-btn');
    const clearBtn = document.getElementById('clear-btn');
    const categoryTabs = document.querySelectorAll('.category-tab');
    const clothingGrid = document.getElementById('clothing-grid');
    const selectedList = document.getElementById('selected-items-list');
    const tagSelector = document.getElementById('tag-selector');

    let activeCategory = 'tops';
    let selectedItems = [];
    let selectedTags = [];
    let clothingLibrary = [];
    let availableTags = {};

    // ── Back button ────────────────────────────────────────────────────────
    if (backBtn) {
        backBtn.addEventListener('click', () => {
            loadPage('/outfits/');
            history.pushState({}, '', '/outfits/');
        });
    }

    // ── Category tabs ──────────────────────────────────────────────────────
    categoryTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            categoryTabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            activeCategory = tab.dataset.category;
            loadClothingByCategory(activeCategory);
        });
    });

    // ── Clear button ───────────────────────────────────────────────────────
    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            selectedItems = [];
            renderCanvas();
            renderSelectedItems();
        });
    }

    // ── Save button ────────────────────────────────────────────────────────
    if (saveBtn) {
        saveBtn.addEventListener('click', saveOutfit);
    }

    // ── Load tags ──────────────────────────────────────────────────────────
    async function loadTags() {
        try {
            const resp = await fetch('/api/clothing/tags/');
            const data = await resp.json();
            availableTags = data.tags || {};
            renderTagFilters();
        } catch (e) {
            console.error('Load tags error:', e);
        }
    }

    function renderTagFilters() {
        if (!tagSelector) return;
        
        tagSelector.innerHTML = '';
        
        // Show color tags
        const colors = availableTags.color || [];
        colors.forEach(tag => {
            const chip = document.createElement('button');
            chip.className = 'tag-chip';
            chip.dataset.tagId = tag.id;
            chip.dataset.tagSlug = tag.slug;
            
            if (tag.color_hex) {
                chip.innerHTML = `
                    <span class="tag-color" style="background:${tag.color_hex}"></span>
                    ${tag.name}
                `;
            } else {
                chip.textContent = tag.name;
            }
            
            chip.addEventListener('click', () => {
                toggleTag(tag);
                chip.classList.toggle('active');
                loadClothingByCategory(activeCategory);
            });
            
            tagSelector.appendChild(chip);
        });
    }

    function toggleTag(tag) {
        const index = selectedTags.findIndex(t => t.id === tag.id);
        if (index > -1) {
            selectedTags.splice(index, 1);
        } else {
            selectedTags.push(tag);
        }
    }

    // ── Load clothing ──────────────────────────────────────────────────────
    async function loadClothingByCategory(category) {
        if (!clothingGrid) return;
        
        clothingGrid.innerHTML = '<div style="text-align:center;padding:40px;color:#999;">Загрузка...</div>';
        
        try {
            const tags = selectedTags.map(t => t.slug).join(',');
            const url = `/api/clothing/?category=${category}${tags ? '&tags=' + tags : ''}`;
            const resp = await fetch(url);
            const data = await resp.json();
            
            clothingLibrary = data.items || [];
            renderClothingGrid();
        } catch (e) {
            console.error('Load clothing error:', e);
            clothingGrid.innerHTML = '<div style="text-align:center;padding:40px;color:#999;">Ошибка загрузки</div>';
        }
    }

    function renderClothingGrid() {
        if (!clothingGrid) return;
        
        if (clothingLibrary.length === 0) {
            clothingGrid.innerHTML = '<div style="text-align:center;padding:40px;color:#999;">Нет одежды</div>';
            return;
        }
        
        clothingGrid.innerHTML = '';
        
        clothingLibrary.forEach(item => {
            const card = document.createElement('div');
            card.className = 'clothing-card';
            card.innerHTML = `
                <img src="${item.thumbnail || item.image_url}" alt="${escHtml(item.name)}">
                <div class="clothing-card-name">${escHtml(item.name)}</div>
            `;
            
            card.addEventListener('click', () => addClothingToOutfit(item));
            
            clothingGrid.appendChild(card);
        });
    }

    // ── Add/remove items ───────────────────────────────────────────────────
    function addClothingToOutfit(clothingItem) {
        const newItem = {
            ...clothingItem,
            position: {
                x: 100,
                y: getCategoryDefaultY(clothingItem.category.slug),
                scale: 1,
                rotation: 0,
                z_index: clothingItem.default_z_index
            }
        };
        
        selectedItems.push(newItem);
        renderCanvas();
        renderSelectedItems();
    }

    function removeItem(itemId) {
        selectedItems = selectedItems.filter(item => item.id !== itemId);
        renderCanvas();
        renderSelectedItems();
    }

    function renderSelectedItems() {
        if (!selectedList) return;
        
        selectedList.innerHTML = '';
        
        selectedItems.forEach(item => {
            const chip = document.createElement('div');
            chip.className = 'selected-item-chip';
            chip.innerHTML = `
                <img src="${item.thumbnail || item.image_url}" alt="">
                <span>${escHtml(item.name)}</span>
                <button>×</button>
            `;
            
            chip.querySelector('button').addEventListener('click', () => removeItem(item.id));
            
            selectedList.appendChild(chip);
        });
    }

    function getCategoryDefaultY(category) {
        const positions = {
            'tops': 120,
            'bottoms': 280,
            'shoes': 480,
            'accessories': 100
        };
        return positions[category] || 200;
    }

    // ── Canvas rendering ───────────────────────────────────────────────────
    function renderCanvas() {
        if (!ctx || !canvas) return;
        
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        // Draw mannequin
        drawMannequin();
        
        // Draw clothing items sorted by z-index
        const sorted = [...selectedItems].sort((a, b) => 
            (a.position.z_index || a.default_z_index) - (b.position.z_index || b.default_z_index)
        );
        
        sorted.forEach(item => {
            drawClothingItem(item);
        });
    }

    function drawMannequin() {
        ctx.save();
        ctx.globalAlpha = 0.1;
        ctx.fillStyle = '#000';
        
        // Head
        ctx.beginPath();
        ctx.arc(200, 80, 40, 0, Math.PI * 2);
        ctx.fill();
        
        // Body
        ctx.fillRect(160, 120, 80, 200);
        
        // Arms
        ctx.fillRect(120, 120, 40, 150);
        ctx.fillRect(240, 120, 40, 150);
        
        // Legs
        ctx.fillRect(160, 320, 35, 200);
        ctx.fillRect(205, 320, 35, 200);
        
        ctx.restore();
    }

    function drawClothingItem(item) {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.src = item.image_url;
        
        img.onload = () => {
            const pos = item.position;
            const x = pos.x || 0;
            const y = pos.y || 0;
            const scale = pos.scale || 1;
            const rotation = pos.rotation || 0;
            
            ctx.save();
            ctx.translate(x, y);
            ctx.rotate(rotation * Math.PI / 180);
            ctx.scale(scale, scale);
            ctx.drawImage(img, 0, 0, 200, 200);
            ctx.restore();
        };
    }

    // ── Save outfit ────────────────────────────────────────────────────────
    async function saveOutfit() {
        if (selectedItems.length === 0) {
            alert('Добавьте хотя бы один предмет одежды');
            return;
        }

        saveBtn.disabled = true;
        saveBtn.textContent = 'Сохранение...';

        try {
            // Get canvas screenshot
            const preview = canvas.toDataURL('image/png');
            
            // Extract tags from selected items
            const tagSet = new Set();
            selectedItems.forEach(item => {
                item.tags?.forEach(tag => tagSet.add(tag.id));
            });
            
            const outfitData = {
                title: 'Мой образ',
                description: '',
                items: selectedItems.map((item, idx) => ({
                    clothing_item_id: item.id,
                    position_data: item.position,
                    order: idx
                })),
                preview_image: preview,
                tags: Array.from(tagSet)
            };
            
            const resp = await fetch('/api/outfits/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                },
                body: JSON.stringify(outfitData)
            });
            
            const data = await resp.json();
            
            if (resp.ok && data.id) {
                // Navigate to outfit detail
                loadPage(`/outfit/${data.id}/`);
                history.pushState({}, '', `/outfit/${data.id}/`);
            } else {
                alert('Ошибка сохранения: ' + (data.error || 'Unknown error'));
            }
        } catch (e) {
            console.error('Save error:', e);
            alert('Не удалось сохранить образ');
        } finally {
            saveBtn.disabled = false;
            saveBtn.textContent = 'Сохранить';
        }
    }

    // ── Initialize ─────────────────────────────────────────────────────────
    loadTags();
    loadClothingByCategory(activeCategory);
}


// ═══════════════════════════════════════════════════════════════════════════════
//  OUTFIT FEED
// ═══════════════════════════════════════════════════════════════════════════════

function initOutfitFeed() {
    const page = document.querySelector('.outfit-feed');
    if (!page) return;

    const filterBtns = document.querySelectorAll('.feed-filter-btn');
    const outfitList = document.getElementById('outfit-list');

    let feedType = 'recommended';
    let currentPage = 1;
    let hasMore = true;
    let loading = false;

    // ── Filter buttons ─────────────────────────────────────────────────────
    filterBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            if (btn.classList.contains('active')) return;
            
            filterBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            feedType = btn.dataset.type;
            
            // Reset and reload
            currentPage = 1;
            hasMore = true;
            outfitList.innerHTML = '';
            loadFeed(true);
        });
    });

    // ── Load feed ──────────────────────────────────────────────────────────
    async function loadFeed(reset = false) {
        if (loading || (!reset && !hasMore)) return;
        
        loading = true;
        
        if (reset) {
            outfitList.innerHTML = '<div style="text-align:center;padding:40px;"><div class="search-spinner"></div></div>';
        }
        
        try {
            const resp = await fetch(`/api/outfits/feed/?type=${feedType}&page=${currentPage}`);
            const data = await resp.json();
            
            if (reset) {
                outfitList.innerHTML = '';
            }
            
            const outfits = data.outfits || [];
            
            if (outfits.length === 0 && reset) {
                outfitList.innerHTML = `
                    <div class="dialogs-empty">
                        <div class="empty-icon"><i class="ri-gallery-line"></i></div>
                        <p>Нет образов</p>
                        <p style="font-size:13px">Создайте свой первый образ!</p>
                    </div>`;
                return;
            }
            
            outfits.forEach(outfit => {
                outfitList.appendChild(createOutfitCard(outfit));
            });
            
            hasMore = data.has_more || false;
            currentPage++;
        } catch (e) {
            console.error('Load feed error:', e);
            if (reset) {
                outfitList.innerHTML = `
                    <div class="dialogs-empty">
                        <p>Ошибка загрузки</p>
                    </div>`;
            }
        } finally {
            loading = false;
        }
    }

    function createOutfitCard(outfit) {
        const card = document.createElement('div');
        card.className = 'outfit-card';
        
        const displayName = [outfit.author.first_name, outfit.author.last_name]
            .filter(Boolean).join(' ') || outfit.author.username || 'Пользователь';
        
        const avatarHtml = outfit.author.avatar_url
            ? `<img src="${outfit.author.avatar_url}" alt="">`
            : `<span>${(displayName[0] || '?').toUpperCase()}</span>`;
        
        const timeAgo = getTimeAgo(outfit.created_at);
        
        card.innerHTML = `
            <div class="outfit-card-header">
                <div class="outfit-author-avatar" style="background-color:${outfit.author.avatar_color}">
                    ${avatarHtml}
                </div>
                <div class="outfit-author-info">
                    <div class="outfit-author-name">${escHtml(displayName)}</div>
                    <div class="outfit-posted-time">${timeAgo}</div>
                </div>
            </div>
            
            <div class="outfit-card-image">
                ${outfit.preview_url ? `<img src="${outfit.preview_url}" alt="${escHtml(outfit.title)}">` : ''}
            </div>
            
            <div class="outfit-card-actions">
                <button class="outfit-action-btn ${outfit.is_liked ? 'liked' : ''}" data-action="like">
                    <i class="${outfit.is_liked ? 'ri-heart-fill' : 'ri-heart-line'}"></i>
                    ${outfit.likes_count > 0 ? `<span>${outfit.likes_count}</span>` : ''}
                </button>
                <button class="outfit-action-btn" data-action="comment">
                    <i class="ri-chat-3-line"></i>
                    ${outfit.comments_count > 0 ? `<span>${outfit.comments_count}</span>` : ''}
                </button>
                <button class="outfit-action-btn" data-action="share">
                    <i class="ri-share-line"></i>
                </button>
            </div>
            
            ${outfit.title ? `<div class="outfit-card-title">${escHtml(outfit.title)}</div>` : ''}
            ${outfit.description ? `<div class="outfit-card-description">${escHtml(outfit.description)}</div>` : ''}
            
            ${outfit.tags && outfit.tags.length > 0 ? `
                <div class="outfit-card-tags">
                    ${outfit.tags.map(tag => `
                        <span class="outfit-tag">
                            ${tag.color_hex ? `<span class="tag-color" style="background:${tag.color_hex}"></span>` : ''}
                            ${escHtml(tag.name)}
                        </span>
                    `).join('')}
                </div>
            ` : ''}
        `;
        
        // Click on image to view detail
        const image = card.querySelector('.outfit-card-image');
        if (image) {
            image.addEventListener('click', () => {
                loadPage(`/outfit/${outfit.id}/`);
                history.pushState({}, '', `/outfit/${outfit.id}/`);
            });
        }
        
        // Click on author to view profile
        const authorAvatar = card.querySelector('.outfit-author-avatar');
        const authorInfo = card.querySelector('.outfit-author-info');
        [authorAvatar, authorInfo].forEach(el => {
            if (el) {
                el.addEventListener('click', () => {
                    loadPage(`/user/${outfit.author.telegram_id}/`);
                    history.pushState({}, '', `/user/${outfit.author.telegram_id}/`);
                });
            }
        });
        
        // Action buttons
        const likeBtn = card.querySelector('[data-action="like"]');
        const commentBtn = card.querySelector('[data-action="comment"]');
        
        if (likeBtn) {
            likeBtn.addEventListener('click', () => toggleLike(outfit.id, likeBtn));
        }
        
        if (commentBtn) {
            commentBtn.addEventListener('click', () => {
                loadPage(`/outfit/${outfit.id}/`);
                history.pushState({}, '', `/outfit/${outfit.id}/`);
            });
        }
        
        return card;
    }

    async function toggleLike(outfitId, btn) {
        btn.disabled = true;
        
        try {
            const resp = await fetch(`/api/outfits/${outfitId}/like/`, {
                method: 'POST',
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
            });
            const data = await resp.json();
            
            const icon = btn.querySelector('i');
            const countSpan = btn.querySelector('span');
            
            if (data.status === 'liked') {
                btn.classList.add('liked');
                icon.className = 'ri-heart-fill';
            } else {
                btn.classList.remove('liked');
                icon.className = 'ri-heart-line';
            }
            
            if (data.likes_count > 0) {
                if (countSpan) {
                    countSpan.textContent = data.likes_count;
                } else {
                    btn.innerHTML += `<span>${data.likes_count}</span>`;
                }
            } else {
                if (countSpan) countSpan.remove();
            }
        } catch (e) {
            console.error('Like error:', e);
        } finally {
            btn.disabled = false;
        }
    }

    // ── Infinite scroll ────────────────────────────────────────────────────
    let observer = null;
    
    function setupInfiniteScroll() {
        const observerTarget = document.createElement('div');
        observerTarget.style.height = '20px';
        outfitList.appendChild(observerTarget);
        
        observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting && hasMore && !loading) {
                loadFeed(false);
            }
        }, { threshold: 0.5 });
        
        observer.observe(observerTarget);
    }

    // ── Initialize ─────────────────────────────────────────────────────────
    loadFeed(true);
    setupInfiniteScroll();
}


// ═══════════════════════════════════════════════════════════════════════════════
//  OUTFIT DETAIL
// ═══════════════════════════════════════════════════════════════════════════════

function initOutfitDetail() {
    const page = document.querySelector('.outfit-detail');
    if (!page) return;

    document.body.classList.add('hide-nav');

    const outfitId = parseInt(page.dataset.outfitId);
    const backBtn = document.getElementById('outfit-back-btn');
    const imageContainer = document.getElementById('outfit-image');
    const infoContainer = document.getElementById('outfit-info');

    // ── Back button ────────────────────────────────────────────────────────
    if (backBtn) {
        backBtn.addEventListener('click', () => {
            loadPage('/outfits/');
            history.pushState({}, '', '/outfits/');
        });
    }

    // ── Load outfit ────────────────────────────────────────────────────────
    async function loadOutfit() {
        try {
            const resp = await fetch(`/api/outfits/${outfitId}/`);
            const outfit = await resp.json();
            
            renderOutfit(outfit);
        } catch (e) {
            console.error('Load outfit error:', e);
            if (imageContainer) {
                imageContainer.innerHTML = '<div style="padding:40px;text-align:center;color:#999;">Ошибка загрузки</div>';
            }
        }
    }

    function renderOutfit(outfit) {
        // Image
        if (imageContainer) {
            imageContainer.innerHTML = outfit.preview_url
                ? `<img src="${outfit.preview_url}" alt="${escHtml(outfit.title)}">`
                : '<div style="padding:40px;color:#999;">Нет изображения</div>';
        }
        
        // Info
        if (infoContainer) {
            const displayName = [outfit.author.first_name, outfit.author.last_name]
                .filter(Boolean).join(' ') || outfit.author.username || 'Пользователь';
            
            infoContainer.innerHTML = `
                <div class="outfit-detail-stats">
                    <div class="outfit-stat">
                        <i class="ri-heart-line"></i>
                        <span>${outfit.likes_count}</span>
                    </div>
                    <div class="outfit-stat">
                        <i class="ri-chat-3-line"></i>
                        <span>${outfit.comments_count}</span>
                    </div>
                    <div class="outfit-stat">
                        <i class="ri-eye-line"></i>
                        <span>${outfit.views_count}</span>
                    </div>
                </div>
                
                ${outfit.title ? `<h2>${escHtml(outfit.title)}</h2>` : ''}
                ${outfit.description ? `<p>${escHtml(outfit.description)}</p>` : ''}
                
                <div style="margin-top:16px;">
                    <strong>Автор:</strong> ${escHtml(displayName)}
                </div>
            `;
        }
    }

    // ── Initialize ─────────────────────────────────────────────────────────
    loadOutfit();
}


// ═══════════════════════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

function getTimeAgo(timestamp) {
    const now = new Date();
    const posted = new Date(timestamp);
    const seconds = Math.floor((now - posted) / 1000);
    
    if (seconds < 60) return 'только что';
    if (seconds < 3600) return `${Math.floor(seconds / 60)} мин назад`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)} ч назад`;
    if (seconds < 604800) return `${Math.floor(seconds / 86400)} д назад`;
    
    return posted.toLocaleDateString('ru-RU', {
        day: 'numeric',
        month: 'short'
    });
}

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}


// ═══════════════════════════════════════════════════════════════════════════════
//  INITIALIZATION (called from base.js)
// ═══════════════════════════════════════════════════════════════════════════════

if (typeof window.initOutfitCreator === 'undefined') {
    window.initOutfitCreator = initOutfitCreator;
}
if (typeof window.initOutfitFeed === 'undefined') {
    window.initOutfitFeed = initOutfitFeed;
}
if (typeof window.initOutfitDetail === 'undefined') {
    window.initOutfitDetail = initOutfitDetail;
}