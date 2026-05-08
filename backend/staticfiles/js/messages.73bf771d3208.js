// ── Global chat state ──────────────────────────────────────────────────────────
let _chatSocket = null;
let _editingMessageId = null;
let _currentDialogId = null;
let _currentUserId = null;

function _stopChatPolling() {
    // Закрываем WebSocket соединение
    if (_chatSocket) {
        _chatSocket.close();
        _chatSocket = null;
    }
    _currentDialogId = null;
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MESSAGES PAGE — dialog list
// ═══════════════════════════════════════════════════════════════════════════════

function initMessagesPage() {
    const page = document.querySelector('.messages-page');
    if (!page) return;

    _stopChatPolling();
    loadDialogs();
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

    el.innerHTML = `
        <div class="dialog-avatar" style="background-color:${u.avatar_color}">${avatarHtml}</div>
        <div class="dialog-info">
            <div class="dialog-info-top">
                <div class="dialog-name">${escHtml(name)}</div>
                <div class="dialog-time">${d.last_message ? d.last_message.time : ''}</div>
                ${unreadHtml}
            </div>
            ${lastMsgHtml}
        </div>
    `;

    el.addEventListener('click', () => {
        const url = `/chat/${d.dialog_id}/`;
        loadPage(url);
        history.pushState({}, '', url);
    });

    return el;
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

    const dialogId  = parseInt(page.dataset.dialogId);
    const msgArea   = document.getElementById('chat-messages');
    const input     = document.getElementById('chat-input');
    const sendBtn   = document.getElementById('chat-send-btn');
    const backBtn   = document.getElementById('chat-back-btn');
    const editIndicator = document.getElementById('chat-edit-indicator');
    const editClose = document.getElementById('chat-edit-close');

    _currentDialogId = dialogId;
    
    // ✅ ИСПРАВЛЕНИЕ: Получаем ID текущего пользователя из data-атрибута
    _currentUserId = parseInt(page.dataset.currentUserId);
    
    console.log('Current User ID:', _currentUserId); // Для отладки

    // ── Context Menu Setup ────────────────────────────────────────────────────
    setupContextMenu(dialogId, input);

    // ── Back ──────────────────────────────────────────────────────────────────
    if (backBtn) {
        backBtn.addEventListener('click', () => {
            _stopChatPolling();
            cancelEdit();
            loadPage('/messages/');
            history.pushState({}, '', '/messages/');
        });
    }

    // ── Cancel Edit ───────────────────────────────────────────────────────────
    if (editClose) {
        editClose.addEventListener('click', cancelEdit);
    }

    // ── Load initial messages ─────────────────────────────────────────────────
    loadMessages(dialogId, msgArea, true);

    // ── WebSocket Connection ──────────────────────────────────────────────────
    connectWebSocket(dialogId, msgArea);

    // ── Send ──────────────────────────────────────────────────────────────────
    const doSend = async () => {
        const text = input.value.trim();
        if (!text) return;

        input.value = '';
        sendBtn.disabled = true;

        try {
            if (_editingMessageId) {
                // Edit via WebSocket
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
                // Send via WebSocket
                if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
                    _chatSocket.send(JSON.stringify({
                        type: 'chat_message',
                        text: text,
                        sender_id: _currentUserId
                    }));
                } else {
                    // Fallback to HTTP if WebSocket not connected
                    console.warn('WebSocket not connected, falling back to HTTP');
                    const resp = await fetch(`/api/dialogs/${dialogId}/messages/`, {
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
        
        // Typing indicator
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
    // Определяем протокол (ws или wss)
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/chat/${dialogId}/`;
    
    console.log('Connecting to WebSocket:', wsUrl);
    
    _chatSocket = new WebSocket(wsUrl);
    
    _chatSocket.onopen = (e) => {
        console.log('✅ WebSocket connected');
        showConnectionStatus('connected');
        
        // ✅ Помечаем все непрочитанные сообщения как прочитанные
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
                // Новое сообщение
                if (msgArea.querySelector('[style*="Начните"]')) {
                    msgArea.innerHTML = '';
                }
                
                const message = data.message;
                // Вычисляем is_mine если не пришло с бэкенда
                if (message.is_mine === undefined) {
                    message.is_mine = (message.sender_id === _currentUserId);
                }
                
                appendMessage(msgArea, message);
                scrollToBottom(msgArea);
                
                // ✅ Если это не мое сообщение - помечаем как прочитанное
                if (!message.is_mine && _chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
                    _chatSocket.send(JSON.stringify({
                        type: 'mark_as_read',
                        user_id: _currentUserId
                    }));
                }
                break;
                
            case 'message_edited':
                // Сообщение отредактировано
                updateMessageInDOM(data.message_id, data.text, data.edited);
                break;
                
            case 'message_deleted':
                // Сообщение удалено
                const wrap = document.querySelector(`.chat-bubble-wrap[data-id="${data.message_id}"]`);
                if (wrap) wrap.remove();
                break;
                
            case 'user_typing':
                // Показать индикатор печати
                // TODO: implement typing indicator UI
                break;
        }
    };
    
    _chatSocket.onerror = (error) => {
        console.error('❌ WebSocket error:', error);
        showConnectionStatus('error');
    };
    
    _chatSocket.onclose = (e) => {
        console.log('WebSocket closed:', e.code, e.reason);
        showConnectionStatus('disconnected');
        
        // Попытка переподключения через 3 секунды
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

function showConnectionStatus(status) {
    // Можно добавить визуальный индикатор статуса подключения
    const statusColors = {
        connected: '#4CAF50',
        disconnected: '#F44336',
        error: '#FF9800'
    };
    
    // Временно выводим в консоль
    console.log(`Connection status: ${status}`);
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
    // Send delete via WebSocket
    if (_chatSocket && _chatSocket.readyState === WebSocket.OPEN) {
        _chatSocket.send(JSON.stringify({
            type: 'message_delete',
            message_id: messageId,
            sender_id: _currentUserId
        }));
    } else {
        // Fallback to HTTP
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
    if (!initial) return; // WebSocket handles updates

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