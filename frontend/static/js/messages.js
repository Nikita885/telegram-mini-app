// ── Global chat state ──────────────────────────────────────────────────────────
let _chatSocket = null;
let _dialogsSocket = null;  // ✅ NEW: WebSocket для списка диалогов
let _editingMessageId = null;
let _currentDialogId = null;
let _currentUserId = null;

function _stopChatPolling() {
    // Закрываем WebSocket соединения
    if (_chatSocket) {
        _chatSocket.close();
        _chatSocket = null;
    }
    if (_dialogsSocket) {
        _dialogsSocket.close();
        _dialogsSocket = null;
    }
    _currentDialogId = null;
    
    // ✅ Сбрасываем флаг инициализации меню
    document.body.dataset.dialogMenuInitialized = 'false';
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MESSAGES PAGE — dialog list with real-time updates
// ═══════════════════════════════════════════════════════════════════════════════

function initMessagesPage() {
    const page = document.querySelector('.messages-page');
    if (!page) return;

    _stopChatPolling();
    loadDialogs();
    
    // ✅ NEW: Подключаем WebSocket для real-time обновлений
    connectDialogListWebSocket();
    
    // ✅ NEW: Настраиваем контекстное меню для диалогов
    setupDialogContextMenu();
}

async function loadDialogs() {
    const list = document.getElementById('dialogs-list');
    if (!list) return;

    try {
        const resp = await fetch('/api/dialogs/', {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();

        if (!data.dialogs || data.dialogs.length === 0) {
            list.innerHTML = `
                <div class="dialogs-empty">
                    <div class="empty-icon"><i class="ri-message-2-line"></i></div>
                    <p>Нет сообщений</p>
                    <p style="font-size:13px">Найдите человека в поиске и напишите ему</p>
                </div>`;
            return;
        }

        list.innerHTML = '';
        data.dialogs.forEach(d => list.appendChild(createDialogItem(d)));
    } catch (e) {
        console.error('Load dialogs error:', e);
        if (list) list.innerHTML = `<div class="dialogs-empty"><p>Ошибка загрузки</p></div>`;
    }
}

function createDialogItem(d) {
    const el = document.createElement('div');
    el.className = 'dialog-item';
    el.dataset.dialogId = d.dialog_id;
    
    // ✅ Добавляем timestamp для сортировки
    el.dataset.updatedAt = Date.now(); // Используем текущее время, так как данные свежие

    // ✅ Добавляем класс для закрепленных диалогов
    if (d.pinned) {
        el.classList.add('pinned');
    }

    const u = d.other_user;
    const name = [u.first_name, u.last_name].filter(Boolean).join(' ') || u.username || 'Пользователь';
    const avatarHtml = u.avatar_url
        ? `<img src="${u.avatar_url}" alt="">`
        : `<span>${(name[0] || '?').toUpperCase()}</span>`;

    const lastMsgHtml = d.last_message
        ? `<div class="dialog-last-msg${d.last_message.is_mine ? ' mine' : ''}">${escHtml(d.last_message.text)}</div>`
        : `<div class="dialog-last-msg" style="color:#ccc">Нет сообщений</div>`;

    const unreadHtml = d.unread_count > 0
        ? `<div class="dialog-unread">${d.unread_count}</div>`
        : '';
    
    // ✅ Иконка закрепленного чата
    const pinnedIcon = d.pinned
        ? `<i class="ri-pushpin-fill dialog-pin-icon"></i>`
        : '';

    el.innerHTML = `
        <div class="dialog-avatar" style="background-color:${u.avatar_color}">${avatarHtml}</div>
        <div class="dialog-info">
            <div class="dialog-info-top">
                <div class="dialog-name">${escHtml(name)}${pinnedIcon}</div>
                <div class="dialog-time">${d.last_message ? d.last_message.time : ''}</div>
                ${unreadHtml}
            </div>
            ${lastMsgHtml}
        </div>
    `;

    el.addEventListener('click', (e) => {
        // Не переходим в чат если кликнули по контекстному меню
        if (e.target.closest('.dialog-menu')) return;
        
        const url = `/chat/${d.dialog_id}/`;
        loadPage(url);
        history.pushState({}, '', url);
    });

    return el;
}

// ✅ NEW: WebSocket для real-time обновления списка диалогов
function connectDialogListWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/dialogs/`;
    
    console.log('Connecting to Dialogs WebSocket:', wsUrl);
    
    _dialogsSocket = new WebSocket(wsUrl);
    
    _dialogsSocket.onopen = (e) => {
        console.log('✅ Dialogs WebSocket connected');
    };
    
    _dialogsSocket.onmessage = (e) => {
        const data = JSON.parse(e.data);
        console.log('Dialogs WebSocket message:', data);
        
        if (data.type === 'dialog_updated') {
            updateDialogInList(data.dialog);
        }
    };
    
    _dialogsSocket.onerror = (error) => {
        console.error('❌ Dialogs WebSocket error:', error);
    };
    
    _dialogsSocket.onclose = (e) => {
        console.log('Dialogs WebSocket closed:', e.code);
        
        // Переподключение через 3 секунды если все еще на странице messages
        const page = document.querySelector('.messages-page');
        if (page) {
            setTimeout(() => {
                if (document.querySelector('.messages-page')) {
                    console.log('Reconnecting to dialogs...');
                    connectDialogListWebSocket();
                }
            }, 3000);
        }
    };
}

// ✅ NEW: Обновление диалога в списке
function updateDialogInList(dialogData) {
    const list = document.getElementById('dialogs-list');
    if (!list) return;
    
    // Ищем существующий элемент
    let existingItem = list.querySelector(`[data-dialog-id="${dialogData.dialog_id}"]`);
    
    // Создаем новый элемент
    const newItem = createDialogItem(dialogData);
    
    // ✅ Устанавливаем текущее время для правильной сортировки
    newItem.dataset.updatedAt = Date.now();
    
    if (existingItem) {
        // Заменяем существующий
        existingItem.replaceWith(newItem);
    } else {
        // Добавляем новый в начало
        if (list.firstChild) {
            list.insertBefore(newItem, list.firstChild);
        } else {
            list.appendChild(newItem);
        }
    }
    
    // ✅ Пересортировываем список (теперь с учетом времени!)
    sortDialogList(list);
}

// ✅ NEW: Сортировка списка диалогов
function sortDialogList(list) {
    const items = Array.from(list.querySelectorAll('.dialog-item'));
    
    items.sort((a, b) => {
        const aPinned = a.classList.contains('pinned');
        const bPinned = b.classList.contains('pinned');
        
        // Закрепленные всегда сверху
        if (aPinned && !bPinned) return -1;
        if (!aPinned && bPinned) return 1;
        
        // ✅ Внутри группы сортируем по времени (новые сверху)
        const aTime = parseInt(a.dataset.updatedAt) || 0;
        const bTime = parseInt(b.dataset.updatedAt) || 0;
        return bTime - aTime; // Новые первыми
    });
    
    // Перестраиваем DOM
    items.forEach(item => list.appendChild(item));
}

// ✅ NEW: Контекстное меню для диалогов
function setupDialogContextMenu() {
    // ✅ Проверяем, уже ли настроено меню
    if (document.body.dataset.dialogMenuInitialized === 'true') {
        return; // Уже настроено, не создаем повторно
    }
    document.body.dataset.dialogMenuInitialized = 'true';
    
    let longPressTimer = null;
    let currentDialogId = null;
    let currentIsPinned = false;

    // Создаем меню
    const menu = document.createElement('div');
    menu.className = 'dialog-menu';
    menu.innerHTML = `
        <button class="dialog-menu-item" data-action="pin">
            <i class="ri-pushpin-line"></i>
            <span class="pin-text">Закрепить</span>
        </button>
        <button class="dialog-menu-item delete" data-action="delete">
            <i class="ri-delete-bin-line"></i>
            <span>Удалить</span>
        </button>
    `;
    document.body.appendChild(menu);

    const overlay = document.createElement('div');
    overlay.className = 'dialog-menu-overlay';
    document.body.appendChild(overlay);

    function hideMenu() {
        menu.classList.remove('show');
        overlay.classList.remove('show');
        currentDialogId = null;
        currentIsPinned = false;
    }

    overlay.addEventListener('click', hideMenu);

    function showMenu(dialogId, isPinned, event) {
        currentDialogId = dialogId;
        currentIsPinned = isPinned;

        // Обновляем текст кнопки закрепления
        const pinText = menu.querySelector('.pin-text');
        const pinIcon = menu.querySelector('[data-action="pin"] i');
        if (isPinned) {
            pinText.textContent = 'Открепить';
            pinIcon.className = 'ri-unpin-line';
        } else {
            pinText.textContent = 'Закрепить';
            pinIcon.className = 'ri-pushpin-line';
        }

        menu.style.visibility = 'hidden';
        menu.classList.add('show');
        
        const menuWidth = menu.offsetWidth;
        const menuHeight = menu.offsetHeight;
        
        const item = event.target.closest('.dialog-item');
        const rect = item ? item.getBoundingClientRect() : event.target.getBoundingClientRect();
        
        let left = rect.right - menuWidth;
        let top = rect.bottom + 10;
        
        if (left < 10) left = 10;
        if (left + menuWidth > window.innerWidth - 10) {
            left = window.innerWidth - menuWidth - 10;
        }
        if (top + menuHeight > window.innerHeight - 10) {
            top = rect.top - menuHeight - 10;
        }
        
        menu.style.left = left + 'px';
        menu.style.top = top + 'px';
        menu.style.visibility = 'visible';
        overlay.classList.add('show');
    }

    menu.addEventListener('click', async (e) => {
        e.stopPropagation();
        
        const button = e.target.closest('.dialog-menu-item');
        if (!button) return;

        const action = button.dataset.action;
        const dialogId = currentDialogId;
        
        hideMenu();

        if (action === 'pin') {
            await togglePinDialog(dialogId);
        } else if (action === 'delete') {
            // Подтверждение удаления
            const confirmed = await tgConfirm('Удалить чат? Все сообщения будут удалены.');
            if (confirmed) {
                await deleteDialog(dialogId);
            }
        }
    });

    // Долгое нажатие
    document.addEventListener('pointerdown', (e) => {
        const item = e.target.closest('.dialog-item');
        if (!item) return;

        const dialogId = parseInt(item.dataset.dialogId);
        const isPinned = item.classList.contains('pinned');

        longPressTimer = setTimeout(() => {
            navigator.vibrate?.(50);
            showMenu(dialogId, isPinned, e);
        }, 500);
    });

    document.addEventListener('pointerup', () => {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    });

    document.addEventListener('pointermove', () => {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    });

    // Правая кнопка мыши
    document.addEventListener('contextmenu', (e) => {
        const item = e.target.closest('.dialog-item');
        if (!item) return;

        e.preventDefault();

        const dialogId = parseInt(item.dataset.dialogId);
        const isPinned = item.classList.contains('pinned');

        showMenu(dialogId, isPinned, e);
    });
}

// ✅ NEW: Закрепить/открепить диалог
async function togglePinDialog(dialogId) {
    try {
        const resp = await fetch(`/api/dialogs/${dialogId}/action/`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: JSON.stringify({ action: 'pin' }),
        });
        const data = await resp.json();

        if (data.status === 'ok') {
            // Обновляем элемент в списке
            const item = document.querySelector(`[data-dialog-id="${dialogId}"]`);
            if (item) {
                if (data.pinned) {
                    item.classList.add('pinned');
                    // Добавляем иконку
                    const nameEl = item.querySelector('.dialog-name');
                    if (nameEl && !nameEl.querySelector('.dialog-pin-icon')) {
                        nameEl.insertAdjacentHTML('beforeend', '<i class="ri-pushpin-fill dialog-pin-icon"></i>');
                    }
                } else {
                    item.classList.remove('pinned');
                    // Убираем иконку
                    const icon = item.querySelector('.dialog-pin-icon');
                    if (icon) icon.remove();
                }
                
                // Пересортировываем список
                const list = document.getElementById('dialogs-list');
                if (list) sortDialogList(list);
            }
        }
    } catch (e) {
        console.error('Pin dialog error:', e);
    }
}

// ✅ NEW: Удалить диалог
async function deleteDialog(dialogId) {
    try {
        const resp = await fetch(`/api/dialogs/${dialogId}/action/`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: JSON.stringify({ action: 'delete' }),
        });
        const data = await resp.json();

        if (data.status === 'ok') {
            // Удаляем элемент из списка
            const item = document.querySelector(`[data-dialog-id="${dialogId}"]`);
            if (item) {
                item.remove();
                
                // Проверяем, пустой ли список
                const list = document.getElementById('dialogs-list');
                if (list && !list.querySelector('.dialog-item')) {
                    list.innerHTML = `
                        <div class="dialogs-empty">
                            <div class="empty-icon"><i class="ri-message-2-line"></i></div>
                            <p>Нет сообщений</p>
                            <p style="font-size:13px">Найдите человека в поиске и напишите ему</p>
                        </div>`;
                }
            }
        }
    } catch (e) {
        console.error('Delete dialog error:', e);
    }
}

function tgConfirm(message) {
    return new Promise((resolve) => {
        if (window.Telegram?.WebApp?.showConfirm) {
            window.Telegram.WebApp.showConfirm(message, resolve);
        } else {
            resolve(window.confirm(message));
        }
    });
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CHAT PAGE - WebSocket Implementation
// ═══════════════════════════════════════════════════════════════════════════════

function initChatPage() {
    const page = document.querySelector('.chat-page');
    if (!page) {
        _stopChatPolling();
        return;
    }

    document.body.classList.add('hide-nav');

    const dialogId  = page.dataset.dialogId ? parseInt(page.dataset.dialogId) : null;
    const targetTelegramId = page.dataset.targetTelegramId ? parseInt(page.dataset.targetTelegramId) : null;
    
    const msgArea   = document.getElementById('chat-messages');
    const input     = document.getElementById('chat-input');
    const sendBtn   = document.getElementById('chat-send-btn');
    const backBtn   = document.getElementById('chat-back-btn');
    const editIndicator = document.getElementById('chat-edit-indicator');
    const editClose = document.getElementById('chat-edit-close');

    _currentDialogId = dialogId;
    _currentUserId = parseInt(page.dataset.currentUserId);
    
    console.log('Current User ID:', _currentUserId);
    console.log('Dialog ID:', dialogId, 'Target ID:', targetTelegramId);

    if (dialogId) {
        setupContextMenu(dialogId, input);
    }

    if (backBtn) {
        backBtn.addEventListener('click', () => {
            _stopChatPolling();
            cancelEdit();
            loadPage('/messages/');
            history.pushState({}, '', '/messages/');
        });
    }

    if (editClose) {
        editClose.addEventListener('click', cancelEdit);
    }

    if (dialogId) {
        loadMessages(dialogId, msgArea, true);
        connectWebSocket(dialogId, msgArea);
    } else {
        msgArea.innerHTML = '<div style="text-align:center;padding:40px;color:#888">Начните переписку</div>';
    }

    const doSend = async () => {
        const text = input.value.trim();
        if (!text) return;

        input.value = '';
        sendBtn.disabled = true;

        try {
            let actualDialogId = dialogId || _currentDialogId;
            
            if (!actualDialogId && targetTelegramId) {
                console.log('Creating new dialog...');
                
                const createResp = await fetch('/api/dialogs/create/', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest',
                    },
                    body: JSON.stringify({ target_telegram_id: targetTelegramId }),
                });
                
                const createData = await createResp.json();
                
                if (createData.dialog_id) {
                    actualDialogId = createData.dialog_id;
                    _currentDialogId = actualDialogId;
                    
                    const newUrl = `/chat/${actualDialogId}/`;
                    history.replaceState({}, '', newUrl);
                    page.dataset.dialogId = actualDialogId;
                    
                    connectWebSocket(actualDialogId, msgArea);
                    setupContextMenu(actualDialogId, input);
                    
                    console.log('Dialog created:', actualDialogId);
                    await new Promise(resolve => setTimeout(resolve, 500));
                } else {
                    throw new Error('Failed to create dialog');
                }
            }
            
            if (_editingMessageId) {
                if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
                    _chatSocket.send(JSON.stringify({
                        type: 'message_edit',
                        message_id: _editingMessageId,
                        text: text,
                        sender_id: _currentUserId
                    }));
                    cancelEdit();
                }
            } else {
                if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
                    if (msgArea.querySelector('[style*="Начните"]')) {
                        msgArea.innerHTML = '';
                    }
                    
                    _chatSocket.send(JSON.stringify({
                        type: 'chat_message',
                        text: text,
                        sender_id: _currentUserId
                    }));
                } else {
                    console.warn('WebSocket not connected, falling back to HTTP');
                    
                    if (msgArea.querySelector('[style*="Начните"]')) {
                        msgArea.innerHTML = '';
                    }
                    
                    const resp = await fetch(`/api/dialogs/${actualDialogId}/messages/`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-Requested-With': 'XMLHttpRequest',
                        },
                        body: JSON.stringify({ text }),
                    });
                    const msg = await resp.json();
                    if (msg.id) {
                        appendMessage(msgArea, msg);
                        scrollToBottom(msgArea);
                    }
                }
            }
        } catch (e) {
            console.error('Send error:', e);
            input.value = text;
        } finally {
            sendBtn.disabled = false;
            input.focus();
        }
    };

    sendBtn.addEventListener('click', doSend);
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            doSend();
        }
        if (e.key === 'Escape') {
            cancelEdit();
        }
        
        if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
            _chatSocket.send(JSON.stringify({
                type: 'typing',
                sender_id: _currentUserId
            }));
        }
    });
}

// ── WebSocket Connection ──────────────────────────────────────────────────────

function connectWebSocket(dialogId, msgArea) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/chat/${dialogId}/`;
    
    console.log('Connecting to WebSocket:', wsUrl);
    
    _chatSocket = new WebSocket(wsUrl);
    
    _chatSocket.onopen = (e) => {
        console.log('✅ WebSocket connected');
        
        if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
            _chatSocket.send(JSON.stringify({
                type: 'mark_as_read',
                user_id: _currentUserId
            }));
        }
    };
    
    _chatSocket.onmessage = (e) => {
        const data = JSON.parse(e.data);
        console.log('WebSocket message:', data);
        
        switch (data.type) {
            case 'connection_established':
                console.log('Connection confirmed:', data.message);
                break;
                
            case 'chat_message':
                if (msgArea.querySelector('[style*="Начните"]')) {
                    msgArea.innerHTML = '';
                }
                
                const message = data.message;
                if (message.is_mine === undefined) {
                    message.is_mine = (message.sender_id === _currentUserId);
                }
                
                appendMessage(msgArea, message);
                scrollToBottom(msgArea);
                
                if (!message.is_mine && _chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
                    _chatSocket.send(JSON.stringify({
                        type: 'mark_as_read',
                        user_id: _currentUserId
                    }));
                }
                break;
                
            case 'message_edited':
                updateMessageInDOM(data.message_id, data.text, data.edited);
                break;
                
            case 'message_deleted':
                const wrap = document.querySelector(`.chat-bubble-wrap[data-id="${data.message_id}"]`);
                if (wrap) wrap.remove();
                break;
                
            case 'user_typing':
                break;
        }
    };
    
    _chatSocket.onerror = (error) => {
        console.error('❌ WebSocket error:', error);
    };
    
    _chatSocket.onclose = (e) => {
        console.log('WebSocket closed:', e.code, e.reason);
        
        if (_currentDialogId === dialogId) {
            setTimeout(() => {
                if (_currentDialogId === dialogId) {
                    console.log('Attempting to reconnect...');
                    connectWebSocket(dialogId, msgArea);
                }
            }, 3000);
        }
    };
}

// ── Context Menu ──────────────────────────────────────────────────────────────

function setupContextMenu(dialogId, input) {
    let longPressTimer = null;
    let currentMessageId = null;
    let currentMessageText = null;
    let currentIsMine = false;

    const menu = document.createElement('div');
    menu.className = 'message-menu';
    menu.innerHTML = `
        <button class="message-menu-item" data-action="copy">
            <i class="ri-file-copy-line"></i>
            <span>Копировать</span>
        </button>
        <button class="message-menu-item" data-action="edit">
            <i class="ri-edit-line"></i>
            <span>Редактировать</span>
        </button>
        <button class="message-menu-item delete" data-action="delete">
            <i class="ri-delete-bin-line"></i>
            <span>Удалить</span>
        </button>
    `;
    document.body.appendChild(menu);

    const overlay = document.createElement('div');
    overlay.className = 'message-menu-overlay';
    document.body.appendChild(overlay);

    function hideMenu() {
        menu.classList.remove('show');
        overlay.classList.remove('show');
        currentMessageId = null;
        currentMessageText = null;
        currentIsMine = false;
    }

    overlay.addEventListener('click', hideMenu);

    function showMenu(msgId, msgText, isMine, event) {
        currentMessageId = msgId;
        currentMessageText = msgText;
        currentIsMine = isMine;

        const editBtn = menu.querySelector('[data-action="edit"]');
        const deleteBtn = menu.querySelector('[data-action="delete"]');
        
        if (isMine) {
            editBtn.style.display = 'flex';
            deleteBtn.style.display = 'flex';
        } else {
            editBtn.style.display = 'none';
            deleteBtn.style.display = 'none';
        }

        menu.style.visibility = 'hidden';
        menu.classList.add('show');
        
        const menuWidth = menu.offsetWidth;
        const menuHeight = menu.offsetHeight;
        
        const bubble = event.target.closest('.chat-bubble');
        const rect = bubble ? bubble.getBoundingClientRect() : event.target.getBoundingClientRect();
        
        let left = rect.right - menuWidth;
        let top = rect.bottom + 10;
        
        if (left < 10) left = 10;
        if (left + menuWidth > window.innerWidth - 10) {
            left = window.innerWidth - menuWidth - 10;
        }
        if (top + menuHeight > window.innerHeight - 10) {
            top = rect.top - menuHeight - 10;
        }
        
        menu.style.left = left + 'px';
        menu.style.top = top + 'px';
        menu.style.visibility = 'visible';
        overlay.classList.add('show');
    }

    menu.addEventListener('click', async (e) => {
        e.stopPropagation();
        
        const button = e.target.closest('.message-menu-item');
        if (!button) return;

        const action = button.dataset.action;
        const messageId = currentMessageId;
        const messageText = currentMessageText;
        
        hideMenu();

        if (action === 'copy') {
            try {
                await navigator.clipboard.writeText(messageText);
            } catch (err) {
                const textarea = document.createElement('textarea');
                textarea.value = messageText;
                textarea.style.position = 'fixed';
                textarea.style.opacity = '0';
                document.body.appendChild(textarea);
                textarea.select();
                document.execCommand('copy');
                document.body.removeChild(textarea);
            }
        } else if (action === 'edit') {
            startEdit(messageId, messageText, input);
        } else if (action === 'delete') {
            await deleteMessage(dialogId, messageId);
        }
    });

    document.addEventListener('pointerdown', (e) => {
        const wrap = e.target.closest('.chat-bubble-wrap');
        if (!wrap) return;

        const attrId = wrap.getAttribute('data-id');
        const msgId = parseInt(attrId);
        
        if (!msgId || isNaN(msgId)) return;

        const bubble = wrap.querySelector('.chat-bubble');
        if (!bubble) return;

        const msgText = bubble.textContent.replace(/ \(изменено\)$/, '').trim();
        const isMine = wrap.classList.contains('mine');

        longPressTimer = setTimeout(() => {
            navigator.vibrate?.(50);
            showMenu(msgId, msgText, isMine, e);
        }, 500);
    });

    document.addEventListener('pointerup', () => {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    });

    document.addEventListener('pointermove', () => {
        if (longPressTimer) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    });

    document.addEventListener('contextmenu', (e) => {
        const wrap = e.target.closest('.chat-bubble-wrap');
        if (!wrap) return;

        e.preventDefault();

        const attrId = wrap.getAttribute('data-id');
        const msgId = parseInt(attrId);
        
        if (!msgId || isNaN(msgId)) return;

        const bubble = wrap.querySelector('.chat-bubble');
        if (!bubble) return;

        const msgText = bubble.textContent.replace(/ \(изменено\)$/, '').trim();
        const isMine = wrap.classList.contains('mine');

        showMenu(msgId, msgText, isMine, e);
    });
}

// ── Edit Functions ────────────────────────────────────────────────────────────

function startEdit(messageId, text, input) {
    _editingMessageId = messageId;
    input.value = text;
    input.focus();

    const indicator = document.getElementById('chat-edit-indicator');
    const preview = document.getElementById('chat-edit-preview');
    
    if (indicator) indicator.classList.add('show');
    if (preview) preview.textContent = text;
}

function cancelEdit() {
    _editingMessageId = null;
    const input = document.getElementById('chat-input');
    const indicator = document.getElementById('chat-edit-indicator');
    
    if (input) input.value = '';
    if (indicator) indicator.classList.remove('show');
}

function updateMessageInDOM(messageId, newText, edited) {
    const wrap = document.querySelector(`.chat-bubble-wrap[data-id="${messageId}"]`);
    if (!wrap) return;

    const bubble = wrap.querySelector('.chat-bubble');
    if (bubble) {
        bubble.textContent = newText;
        if (edited) {
            bubble.classList.add('edited');
        }
    }
}

// ── Delete Message ────────────────────────────────────────────────────────────

async function deleteMessage(dialogId, messageId) {
    if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
        _chatSocket.send(JSON.stringify({
            type: 'message_delete',
            message_id: messageId,
            sender_id: _currentUserId
        }));
    } else {
        try {
            const resp = await fetch(`/api/dialogs/${dialogId}/messages/${messageId}/`, {
                method: 'DELETE',
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
            });
            const data = await resp.json();
            
            if (data.status === 'ok') {
                const wrap = document.querySelector(`.chat-bubble-wrap[data-id="${messageId}"]`);
                if (wrap) wrap.remove();
            }
        } catch (e) {
            console.error('Delete error:', e);
        }
    }
}

// ── Load Messages (initial load only) ────────────────────────────────────────

async function loadMessages(dialogId, msgArea, initial) {
    if (!initial) return;

    try {
        const resp = await fetch(`/api/dialogs/${dialogId}/messages/`, {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();

        if (!data.messages) return;

        msgArea.innerHTML = '';
        if (data.messages.length === 0) {
            msgArea.innerHTML = `<div style="text-align:center;color:#ccc;padding:40px;font-size:14px">Начните переписку</div>`;
            return;
        }

        data.messages.forEach(msg => appendMessage(msgArea, msg));
        scrollToBottom(msgArea);
    } catch (e) {
        console.error('Load messages error:', e);
    }
}

function appendMessage(msgArea, msg) {
    const wrap = document.createElement('div');
    wrap.className = `chat-bubble-wrap ${msg.is_mine ? 'mine' : 'theirs'}`;
    wrap.setAttribute('data-id', String(msg.id));
    
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble';
    if (msg.edited) bubble.classList.add('edited');
    bubble.textContent = msg.text;
    
    const time = document.createElement('div');
    time.className = 'chat-bubble-time';
    time.textContent = msg.time;
    
    wrap.appendChild(bubble);
    wrap.appendChild(time);
    msgArea.appendChild(wrap);
}

function scrollToBottom(el) {
    el.scrollTop = el.scrollHeight;
}

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}