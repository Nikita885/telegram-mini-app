// ═══════════════════════════════════════════════════════════════════════════════
//  CLOTHING CONSTRUCTOR
// ═══════════════════════════════════════════════════════════════════════════════

function initConstructorPage() {
    const page = document.querySelector('.constructor-page');
    if (!page) return;

    document.body.classList.add('hide-nav');

    const state = {
        gender: 'male',
        selectedCategory: null,
        clothingObjects: [], // {id, clothingId, element, x, y, scale, rotation, zIndex}
        selectedObject: null,
        history: [],
        historyIndex: -1,
        categoryFilters: {
            style: null,
            color: null
        }
    };

    let objectIdCounter = 0;

    // ── Elements ──────────────────────────────────────────────────────────
    const closeBtn = document.getElementById('constructor-close');
    const genderToggle = document.getElementById('gender-toggle');
    const undoBtn = document.getElementById('undo-btn');
    const redoBtn = document.getElementById('redo-btn');
    const saveBtn = document.getElementById('save-btn');
    const clearBtn = document.getElementById('clear-btn');
    
    const canvas = document.getElementById('scene-canvas');
    const mannequinImg = document.getElementById('mannequin-img');
    
    const categoriesContainer = document.querySelector('.categories-container');
    const categoriesScroll = document.querySelector('.categories-scroll');
    const gallery = document.getElementById('clothing-gallery');
    const galleryGrid = document.getElementById('gallery-grid');

    // ── Close ────────────────────────────────────────────────────────────
    closeBtn?.addEventListener('click', () => {
        const hasChanges = state.clothingObjects.length > 0;
        if (hasChanges) {
            const confirmed = confirm('Все изменения будут потеряны. Закрыть?');
            if (!confirmed) return;
        }
        
        loadPage('/home/');
        history.pushState({}, '', '/home/');
    });

    // ── Gender Toggle ────────────────────────────────────────────────────
    genderToggle?.addEventListener('change', (e) => {
        const newGender = e.target.checked ? 'female' : 'male';
        if (state.clothingObjects.length > 0) {
            const confirmed = confirm('При смене пола все объекты будут удалены. Продолжить?');
            if (!confirmed) {
                e.target.checked = state.gender === 'female';
                return;
            }
        }
        
        state.gender = newGender;
        clearAllObjects();
        updateMannequin();
        closeGallery();
    });

    function updateMannequin() {
        if (!mannequinImg) return;
        mannequinImg.src = `/static/images/mannequin_${state.gender}.png`;
    }

    // ── History (Undo/Redo) ──────────────────────────────────────────────
    function saveHistory() {
        // Удаляем всё после текущей позиции
        state.history = state.history.slice(0, state.historyIndex + 1);
        
        // Сохраняем текущее состояние
        state.history.push(JSON.parse(JSON.stringify(state.clothingObjects)));
        state.historyIndex++;
        
        // Ограничиваем историю 50 шагами
        if (state.history.length > 50) {
            state.history.shift();
            state.historyIndex--;
        }
        
        updateHistoryButtons();
    }

    function undo() {
        if (state.historyIndex <= 0) return;
        
        state.historyIndex--;
        restoreState(state.history[state.historyIndex]);
        updateHistoryButtons();
    }

    function redo() {
        if (state.historyIndex >= state.history.length - 1) return;
        
        state.historyIndex++;
        restoreState(state.history[state.historyIndex]);
        updateHistoryButtons();
    }

    function restoreState(savedObjects) {
        // Удаляем все текущие объекты
        state.clothingObjects.forEach(obj => {
            if (obj.element && obj.element.parentNode) {
                obj.element.parentNode.removeChild(obj.element);
            }
        });
        
        state.clothingObjects = [];
        state.selectedObject = null;
        
        // Восстанавливаем объекты
        savedObjects.forEach(objData => {
            addClothingFromData(objData);
        });
    }

    function updateHistoryButtons() {
        if (undoBtn) undoBtn.disabled = state.historyIndex <= 0;
        if (redoBtn) redoBtn.disabled = state.historyIndex >= state.history.length - 1;
    }

    undoBtn?.addEventListener('click', undo);
    redoBtn?.addEventListener('click', redo);

    // ── Clear All ────────────────────────────────────────────────────────
    clearBtn?.addEventListener('click', () => {
        if (state.clothingObjects.length === 0) return;
        
        const confirmed = confirm('Удалить все объекты?');
        if (!confirmed) return;
        
        clearAllObjects();
        saveHistory();
    });

    function clearAllObjects() {
        state.clothingObjects.forEach(obj => {
            if (obj.element && obj.element.parentNode) {
                obj.element.parentNode.removeChild(obj.element);
            }
        });
        
        state.clothingObjects = [];
        state.selectedObject = null;
    }

    // ── Categories ───────────────────────────────────────────────────────
    async function loadCategories() {
        try {
            const resp = await fetch('/api/clothing/categories/');
            const data = await resp.json();
            
            if (!data.categories) return;
            
            categoriesScroll.innerHTML = '';
            data.categories.forEach(cat => {
                const btn = createCategoryButton(cat);
                categoriesScroll.appendChild(btn);
            });
            
            // ✅ Добавляем drag-scroll для ПК
            setupDragScroll(categoriesScroll);
        } catch (e) {
            console.error('Load categories error:', e);
        }
    }

    // ✅ Drag-scroll для categories на ПК
    function setupDragScroll(element) {
        let isDown = false;
        let isDragging = false;
        let startX;
        let scrollLeft;
        let startScrollLeft;

        element.addEventListener('mousedown', (e) => {
            // ✅ Если клик на кнопку - полностью игнорируем
            if (e.target.closest('.category-btn')) {
                return;
            }
            
            isDown = true;
            isDragging = false;
            element.style.cursor = 'grabbing';
            startX = e.pageX;
            scrollLeft = element.scrollLeft;
            startScrollLeft = element.scrollLeft;
        });

        element.addEventListener('mouseleave', () => {
            isDown = false;
            isDragging = false;
            element.style.cursor = 'grab';
        });

        element.addEventListener('mouseup', () => {
            isDown = false;
            isDragging = false;
            element.style.cursor = 'grab';
        });

        element.addEventListener('mousemove', (e) => {
            if (!isDown) return;
            
            const x = e.pageX;
            const walk = (x - startX) * 2;
            
            // ✅ Если начали двигаться - это drag
            if (Math.abs(walk) > 3) {
                isDragging = true;
                e.preventDefault();
                element.scrollLeft = scrollLeft - walk;
            }
        });
        
        // ✅ Отключаем клик если был drag
        element.addEventListener('click', (e) => {
            if (isDragging) {
                e.preventDefault();
                e.stopPropagation();
            }
        }, true);
    }

    function createCategoryButton(category) {
        const btn = document.createElement('button');
        btn.className = 'category-btn';
        btn.dataset.category = category.name;
        btn.innerHTML = `<i class="${category.icon_class}"></i>`;
        
        // ✅ Обработчик клика - работает и на ПК и на мобильном
        btn.addEventListener('click', (e) => {
            e.stopPropagation(); // Останавливаем всплытие к scroll
            
            if (state.selectedCategory === category.name) {
                closeGallery();
            } else {
                selectCategory(category.name);
            }
        });
        
        return btn;
    }

    function selectCategory(categoryName) {
        state.selectedCategory = categoryName;
        state.categoryFilters = { style: null, color: null };
        
        // Обновляем UI кнопок
        document.querySelectorAll('.category-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.category === categoryName);
        });
        
        openGallery();
        loadClothingItems();
    }

    function openGallery() {
        categoriesContainer?.classList.add('open');
    }

    function closeGallery() {
        categoriesContainer?.classList.remove('open');
        state.selectedCategory = null;
        document.querySelectorAll('.category-btn').forEach(btn => {
            btn.classList.remove('active');
        });
    }

    // ── Clothing Items Gallery ──────────────────────────────────────────
    async function loadClothingItems() {
        if (!state.selectedCategory) return;
        
        try {
            const params = new URLSearchParams({
                category: state.selectedCategory,
                gender: state.gender
            });
            
            if (state.categoryFilters.style) params.append('style', state.categoryFilters.style);
            if (state.categoryFilters.color) params.append('color', state.categoryFilters.color);
            
            const resp = await fetch(`/api/clothing/items/?${params}`);
            const data = await resp.json();
            
            if (!data.items || data.items.length === 0) {
                galleryGrid.innerHTML = '<div class="gallery-empty">Нет подходящих вещей</div>';
                return;
            }
            
            galleryGrid.innerHTML = '';
            data.items.forEach(item => {
                const card = createClothingCard(item);
                galleryGrid.appendChild(card);
            });
        } catch (e) {
            console.error('Load clothing error:', e);
        }
    }

    function createClothingCard(item) {
        const card = document.createElement('div');
        card.className = 'clothing-item-card';
        card.innerHTML = `<img src="${item.image}" alt="${item.name}">`;
        
        card.addEventListener('click', () => {
            addClothingToCanvas(item);
            closeGallery();
        });
        
        return card;
    }

    // ── Add Clothing to Canvas ──────────────────────────────────────────
    function addClothingToCanvas(clothingItem) {
        const obj = {
            id: objectIdCounter++,
            clothingId: clothingItem.id,
            imageUrl: clothingItem.image,
            x: canvas.offsetWidth / 2,
            y: canvas.offsetHeight / 2,
            scale: 1.0,
            rotation: 0,
            zIndex: state.clothingObjects.length,
            element: null,
            width: 150,  // Начальное значение
            height: 200
        };
        
        const el = document.createElement('div');
        el.className = 'clothing-layer';
        el.dataset.id = obj.id;
        el.style.position = 'absolute';
        el.style.zIndex = obj.zIndex;
        
        const img = document.createElement('img');
        img.src = obj.imageUrl;
        img.draggable = false;
        
        // ✅ Подгоняем размер под реальное изображение
        img.onload = () => {
            const naturalRatio = img.naturalWidth / img.naturalHeight;
            const maxSize = 200;
            
            if (naturalRatio > 1) {
                // Широкое изображение
                obj.width = maxSize;
                obj.height = maxSize / naturalRatio;
            } else {
                // Высокое изображение
                obj.height = maxSize;
                obj.width = maxSize * naturalRatio;
            }
            
            el.style.width = obj.width + 'px';
            el.style.height = obj.height + 'px';
            updateObjectTransform(obj);
        };
        
        el.appendChild(img);
        
        // ✅ Добавляем resize handles
        ['tl', 'tr', 'bl', 'br'].forEach(corner => {
            const handle = document.createElement('div');
            handle.className = `resize-handle resize-${corner}`;
            handle.dataset.corner = corner;
            el.appendChild(handle);
        });
        
        canvas.appendChild(el);
        obj.element = el;
        
        // Устанавливаем начальные размеры
        el.style.width = obj.width + 'px';
        el.style.height = obj.height + 'px';
        
        updateObjectTransform(obj);
        state.clothingObjects.push(obj);
        
        selectObject(obj);
        saveHistory();
        
        makeObjectDraggable(obj);
    }

    function addClothingFromData(objData) {
        const obj = { ...objData, element: null };
        
        if (!obj.width) obj.width = 150;
        if (!obj.height) obj.height = 200;
        
        const el = document.createElement('div');
        el.className = 'clothing-layer';
        el.dataset.id = obj.id;
        el.style.position = 'absolute';
        el.style.width = obj.width + 'px';
        el.style.height = obj.height + 'px';
        el.style.zIndex = obj.zIndex;
        
        const img = document.createElement('img');
        img.src = obj.imageUrl;
        img.draggable = false;
        el.appendChild(img);
        
        // ✅ Добавляем resize handles
        ['tl', 'tr', 'bl', 'br'].forEach(corner => {
            const handle = document.createElement('div');
            handle.className = `resize-handle resize-${corner}`;
            handle.dataset.corner = corner;
            el.appendChild(handle);
        });
        
        canvas.appendChild(el);
        obj.element = el;
        
        updateObjectTransform(obj);
        state.clothingObjects.push(obj);
        
        makeObjectDraggable(obj);
    }

    function updateObjectTransform(obj) {
        if (!obj.element) return;
        
        // ✅ Используем реальные размеры объекта
        const centerX = obj.x - (obj.width / 2);
        const centerY = obj.y - (obj.height / 2);
        
        obj.element.style.transform = `
            translate(${centerX}px, ${centerY}px)
            scale(${obj.scale})
            rotate(${obj.rotation}deg)
        `;
    }

    function selectObject(obj) {
        state.selectedObject = obj;
        
        document.querySelectorAll('.clothing-layer').forEach(el => {
            el.classList.remove('selected');
        });
        
        if (obj && obj.element) {
            obj.element.classList.add('selected');
        }
    }

    // ── Drag & Drop (Touch + Mouse) ────────────────────────────────────
    function makeObjectDraggable(obj) {
        const el = obj.element;
        if (!el) return;
        
        let isDragging = false;
        let isResizing = false;
        let resizeCorner = null;
        let startObjX, startObjY; // ✅ Начальная позиция объекта
        let startClientX, startClientY;
        let initialScale = obj.scale;
        let initialRotation = obj.rotation;
        let initialDistance = 0;
        let initialAngle = 0;
        
        // Touch/Mouse events
        el.addEventListener('touchstart', onStart, { passive: false });
        el.addEventListener('mousedown', onStart);
        
        function onStart(e) {
            e.preventDefault();
            e.stopPropagation();
            
            selectObject(obj);
            
            // Проверяем, кликнули ли на resize handle
            const handle = e.target.closest('.resize-handle');
            
            if (handle) {
                // ── Resize ──
                isResizing = true;
                resizeCorner = handle.dataset.corner;
                
                const clientX = e.touches ? e.touches[0].clientX : e.clientX;
                const clientY = e.touches ? e.touches[0].clientY : e.clientY;
                
                startClientX = clientX;
                startClientY = clientY;
                initialScale = obj.scale;
                
                document.addEventListener('touchmove', onResizeMove, { passive: false });
                document.addEventListener('mousemove', onResizeMove);
                document.addEventListener('touchend', onEnd);
                document.addEventListener('mouseup', onEnd);
                
            } else if (e.touches && e.touches.length === 2) {
                // ── Two fingers - scale & rotate ──
                const touch1 = e.touches[0];
                const touch2 = e.touches[1];
                
                initialDistance = getDistance(touch1, touch2);
                initialAngle = getAngle(touch1, touch2);
                initialScale = obj.scale;
                initialRotation = obj.rotation;
                
                document.addEventListener('touchmove', onTwoFingerMove, { passive: false });
                document.addEventListener('touchend', onEnd);
            } else {
                // ── One finger/mouse - drag ──
                const clientX = e.touches ? e.touches[0].clientX : e.clientX;
                const clientY = e.touches ? e.touches[0].clientY : e.clientY;
                
                // ✅ Запоминаем начальную позицию объекта
                startObjX = obj.x;
                startObjY = obj.y;
                
                // ✅ Запоминаем начальные координаты клика
                startClientX = clientX;
                startClientY = clientY;
                
                isDragging = true;
                
                document.addEventListener('touchmove', onMove, { passive: false });
                document.addEventListener('mousemove', onMove);
                document.addEventListener('touchend', onEnd);
                document.addEventListener('mouseup', onEnd);
            }
        }
        
        function onMove(e) {
            if (!isDragging) return;
            e.preventDefault();
            
            const rect = canvas.getBoundingClientRect();
            const clientX = e.touches ? e.touches[0].clientX : e.clientX;
            const clientY = e.touches ? e.touches[0].clientY : e.clientY;
            
            // ✅ Вычисляем дельту от начального клика
            const deltaX = clientX - startClientX;
            const deltaY = clientY - startClientY;
            
            // ✅ Применяем к начальной позиции объекта
            obj.x = startObjX + deltaX;
            obj.y = startObjY + deltaY;
            
            updateObjectTransform(obj);
        }
        
        function onResizeMove(e) {
            if (!isResizing) return;
            e.preventDefault();
            
            const clientX = e.touches ? e.touches[0].clientX : e.clientX;
            const clientY = e.touches ? e.touches[0].clientY : e.clientY;
            
            // Вычисляем изменение расстояния
            const deltaX = clientX - startClientX;
            const deltaY = clientY - startClientY;
            const distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            
            // Определяем направление (от центра или к центру)
            let direction = 1;
            if (resizeCorner === 'tl') {
                direction = (deltaX < 0 || deltaY < 0) ? -1 : 1;
            } else if (resizeCorner === 'tr') {
                direction = (deltaX > 0 || deltaY < 0) ? 1 : -1;
            } else if (resizeCorner === 'bl') {
                direction = (deltaX < 0 || deltaY > 0) ? 1 : -1;
            } else if (resizeCorner === 'br') {
                direction = (deltaX > 0 || deltaY > 0) ? 1 : -1;
            }
            
            // Применяем изменение scale
            const scaleDelta = (distance / 100) * direction;
            obj.scale = Math.max(0.3, Math.min(3, initialScale + scaleDelta));
            
            updateObjectTransform(obj);
        }
        
        function onTwoFingerMove(e) {
            e.preventDefault();
            
            if (e.touches.length !== 2) return;
            
            const touch1 = e.touches[0];
            const touch2 = e.touches[1];
            
            // Scale
            const currentDistance = getDistance(touch1, touch2);
            const scaleChange = currentDistance / initialDistance;
            obj.scale = Math.max(0.3, Math.min(3, initialScale * scaleChange));
            
            // Rotate
            const currentAngle = getAngle(touch1, touch2);
            const angleDiff = currentAngle - initialAngle;
            obj.rotation = initialRotation + angleDiff;
            
            updateObjectTransform(obj);
        }
        
        function onEnd() {
            isDragging = false;
            isResizing = false;
            resizeCorner = null;
            
            document.removeEventListener('touchmove', onMove);
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('touchmove', onResizeMove);
            document.removeEventListener('mousemove', onResizeMove);
            document.removeEventListener('touchmove', onTwoFingerMove);
            document.removeEventListener('touchend', onEnd);
            document.removeEventListener('mouseup', onEnd);
            
            saveHistory();
        }
    }

    function getDistance(touch1, touch2) {
        const dx = touch2.clientX - touch1.clientX;
        const dy = touch2.clientY - touch1.clientY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    function getAngle(touch1, touch2) {
        const dx = touch2.clientX - touch1.clientX;
        const dy = touch2.clientY - touch1.clientY;
        return Math.atan2(dy, dx) * (180 / Math.PI);
    }

    // ── Save & Publish ──────────────────────────────────────────────────
    saveBtn?.addEventListener('click', () => {
        if (state.clothingObjects.length === 0) {
            alert('Добавьте хотя бы одну вещь');
            return;
        }
        
        openPublishScreen();
    });

    function openPublishScreen() {
        const publishScreen = document.getElementById('publish-screen');
        if (!publishScreen) return;
        
        // Генерируем превью
        generatePreview();
        
        publishScreen.classList.add('show');
    }

    function closePublishScreen() {
        const publishScreen = document.getElementById('publish-screen');
        if (!publishScreen) return;
        
        publishScreen.classList.remove('show');
    }

    document.getElementById('publish-back')?.addEventListener('click', closePublishScreen);

    async function generatePreview() {
        const previewImg = document.getElementById('publish-preview-img');
        if (!previewImg) return;
        
        try {
            // ✅ Создаём canvas для рендера
            const renderCanvas = document.createElement('canvas');
            const ctx = renderCanvas.getContext('2d');
            
            // Размеры canvas
            const rect = canvas.getBoundingClientRect();
            renderCanvas.width = canvas.offsetWidth;
            renderCanvas.height = canvas.offsetHeight;
            
            // Белый фон
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, renderCanvas.width, renderCanvas.height);
            
            // Рисуем mannequin
            const mannequinImgEl = document.getElementById('mannequin-img');
            if (mannequinImgEl && mannequinImgEl.complete) {
                const mw = mannequinImgEl.naturalWidth;
                const mh = mannequinImgEl.naturalHeight;
                const scale = Math.min(
                    (renderCanvas.width * 0.9) / mw,
                    (renderCanvas.height * 0.9) / mh
                );
                const w = mw * scale;
                const h = mh * scale;
                const x = (renderCanvas.width - w) / 2;
                const y = (renderCanvas.height - h) / 2;
                
                ctx.drawImage(mannequinImgEl, x, y, w, h);
            }
            
            // Рисуем все clothing objects (сортируем по zIndex)
            const sortedObjects = [...state.clothingObjects].sort((a, b) => a.zIndex - b.zIndex);
            
            for (const obj of sortedObjects) {
                const img = obj.element?.querySelector('img');
                if (!img || !img.complete) continue;
                
                ctx.save();
                
                // Применяем трансформации
                ctx.translate(obj.x, obj.y);
                ctx.rotate(obj.rotation * Math.PI / 180);
                ctx.scale(obj.scale, obj.scale);
                
                // Рисуем с центром в (0, 0)
                ctx.drawImage(img, -obj.width / 2, -obj.height / 2, obj.width, obj.height);
                
                ctx.restore();
            }
            
            // Конвертируем в dataURL
            const dataUrl = renderCanvas.toDataURL('image/png');
            previewImg.src = dataUrl;
            
        } catch (e) {
            console.error('Generate preview error:', e);
            // Fallback - показываем mannequin
            previewImg.src = mannequinImg.src;
        }
    }

    document.getElementById('publish-submit')?.addEventListener('click', async () => {
        const description = document.getElementById('publish-description')?.value.trim() || '';
        const hashtagsInput = document.getElementById('publish-hashtags')?.value.trim() || '';
        
        if (!description) {
            alert('Добавьте описание');
            return;
        }
        
        const hashtags = hashtagsInput
            .split(/[\s,]+/)
            .filter(t => t.startsWith('#'))
            .map(t => t.substring(1));
        
        try {
            // Собираем данные
            const postData = {
                mannequin_type: state.gender,
                description: description,
                hashtags: hashtags,
                items: state.clothingObjects.map(obj => ({
                    clothing_id: obj.clothingId,
                    position_x: obj.x,
                    position_y: obj.y,
                    scale: obj.scale,
                    rotation: obj.rotation,
                    z_index: obj.zIndex
                }))
            };
            
            const resp = await fetch('/api/outfit/create/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: JSON.stringify(postData)
            });
            
            const result = await resp.json();
            
            if (result.status === 'ok') {
                loadPage('/profile/');
                history.pushState({}, '', '/profile/');
            } else {
                alert('Ошибка: ' + result.error);
            }
        } catch (e) {
            console.error('Publish error:', e);
            alert('Ошибка при публикации');
        }
    });

    // ── Filters ──────────────────────────────────────────────────────────
    document.querySelectorAll('.filter-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            const filterType = chip.dataset.filter;
            const value = chip.dataset.value;
            
            // Toggle active
            const isActive = chip.classList.contains('active');
            
            // Remove active from same group
            document.querySelectorAll(`.filter-chip[data-filter="${filterType}"]`).forEach(c => {
                c.classList.remove('active');
            });
            
            if (!isActive) {
                chip.classList.add('active');
                state.categoryFilters[filterType] = value;
            } else {
                state.categoryFilters[filterType] = null;
            }
            
            loadClothingItems();
        });
    });

    // ── Click outside to close gallery ────────────────────────────────────
    document.addEventListener('click', (e) => {
        if (!categoriesContainer) return;
        if (!categoriesContainer.classList.contains('open')) return;
        
        // Если кликнули на categories-container или внутри него - игнорируем
        if (e.target.closest('.categories-container')) return;
        
        // Если кликнули на action buttons - игнорируем
        if (e.target.closest('.constructor-actions')) return;
        
        // Закрываем
        closeGallery();
    });

    // ── Click on canvas to deselect ───────────────────────────────────────
    canvas?.addEventListener('click', (e) => {
        // Если кликнули на clothing-layer - не снимаем выделение
        if (e.target.closest('.clothing-layer')) return;
        
        selectObject(null);
    });

    // ── Initialization ──────────────────────────────────────────────────
    updateMannequin();
    loadCategories();
    updateHistoryButtons();
    saveHistory(); // Начальное состояние
}

// Helper: escape HTML
function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}