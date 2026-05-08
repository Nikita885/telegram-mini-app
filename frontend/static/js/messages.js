// ── Global chat state ──────────────────────────────────────────────────────────
let _chatPollingInterval = null;
let _lastMessageId = 0;
let _editingMessageId = null;

function _stopChatPolling() {
    if (_chatPollingInterval) {
        clearInterval(_chatPollingInterval);
        _chatPollingInterval = null;
    }
    _lastMessageId = 0;
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
//  CHAT PAGE
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

    // ── Polling every 2s ──────────────────────────────────────────────────────
    _stopChatPolling();
    _chatPollingInterval = setInterval(() => {
        loadMessages(dialogId, msgArea, false);
    }, 2000);

    // ── Send ──────────────────────────────────────────────────────────────────
    const doSend = async () => {
        const text = input.value.trim();
        if (!text) return;

        input.value = '';
        sendBtn.disabled = true;

        try {
            if (_editingMessageId) {
                // Edit message
                const resp = await fetch(`/api/dialogs/${dialogId}/messages/${_editingMessageId}/`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest',
                    },
                    body: JSON.stringify({ text }),
                });
                const data = await resp.json();
                if (data.status === 'ok') {
                    updateMessageInDOM(_editingMessageId, text, true);
                    cancelEdit();
                }
            } else {
                // Send new message
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
                    _lastMessageId = Math.max(_lastMessageId, msg.id);
                    scrollToBottom(msgArea);
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
    });
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

    // Hide menu
    function hideMenu() {
        menu.classList.remove('show');
        overlay.classList.remove('show');
        currentMessageId = null;
        currentMessageText = null;
        currentIsMine = false;
    }

    overlay.addEventListener('click', hideMenu);

    // Show menu
    function showMenu(msgId, msgText, isMine, event) {
        currentMessageId = msgId;
        currentMessageText = msgText;
        currentIsMine = isMine;

        // Show only edit/delete for own messages
        const editBtn = menu.querySelector('[data-action="edit"]');
        const deleteBtn = menu.querySelector('[data-action="delete"]');
        
        if (isMine) {
            editBtn.style.display = 'flex';
            deleteBtn.style.display = 'flex';
        } else {
            editBtn.style.display = 'none';
            deleteBtn.style.display = 'none';
        }

        // Сначала показываем меню чтобы получить его размеры
        menu.style.visibility = 'hidden';
        menu.classList.add('show');
        
        const menuWidth = menu.offsetWidth;
        const menuHeight = menu.offsetHeight;
        
        // Получаем координаты сообщения
        const bubble = event.target.closest('.chat-bubble');
        const rect = bubble ? bubble.getBoundingClientRect() : event.target.getBoundingClientRect();
        
        // Позиционируем меню ПОД сообщением, по правому краю
        let left = rect.right - menuWidth;
        let top = rect.bottom + 10;
        
        // Проверяем, не вылезает ли меню за левый край экрана
        if (left < 10) {
            left = 10;
        }
        
        // Проверяем, не вылезает ли меню за правый край экрана
        if (left + menuWidth > window.innerWidth - 10) {
            left = window.innerWidth - menuWidth - 10;
        }
        
        // Проверяем, не вылезает ли меню за нижний край экрана
        if (top + menuHeight > window.innerHeight - 10) {
            // Если не помещается снизу, показываем сверху
            top = rect.top - menuHeight - 10;
        }
        
        menu.style.left = left + 'px';
        menu.style.top = top + 'px';
        
        // Теперь делаем меню видимым
        menu.style.visibility = 'visible';
        overlay.classList.add('show');
    }

    // Menu actions
    menu.addEventListener('click', async (e) => {
        const button = e.target.closest('.message-menu-item');
        if (!button) return;

        const action = button.dataset.action;
        
        // ВАЖНО: Сохраняем значения ДО hideMenu(), потому что hideMenu очищает их!
        const messageId = currentMessageId;
        const messageText = currentMessageText;
        
        hideMenu();

        if (action === 'copy') {
            try {
                await navigator.clipboard.writeText(messageText);
            } catch (err) {
                // Fallback for older browsers
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

    // Long press detection
    document.addEventListener('pointerdown', (e) => {
        // Ищем wrap напрямую, чтобы работало при клике на любую часть сообщения
        const wrap = e.target.closest('.chat-bubble-wrap');
        if (!wrap) return;

        // Пробуем оба способа получить id
        const datasetId = wrap.dataset.id;
        const attrId = wrap.getAttribute('data-id');
        
        console.log('Long press detected:', {
            datasetId,
            attrId,
            classList: wrap.className
        });

        const msgId = parseInt(attrId || datasetId);
        
        if (!msgId || isNaN(msgId)) {
            console.error('Invalid message ID:', { datasetId, attrId, msgId });
            return;
        }

        const bubble = wrap.querySelector('.chat-bubble');
        if (!bubble) {
            console.error('Bubble not found in wrap');
            return;
        }

        const msgText = bubble.textContent.replace(/ \(изменено\)$/, '').trim();
        const isMine = wrap.classList.contains('mine');

        console.log('Will show menu for message:', { msgId, msgText: msgText.substring(0, 20), isMine });

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

    // Context menu (правый клик) для ПК
    document.addEventListener('contextmenu', (e) => {
        const wrap = e.target.closest('.chat-bubble-wrap');
        if (!wrap) return;

        e.preventDefault(); // Отменяем стандартное контекстное меню браузера

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
    // Используем селектор атрибута напрямую
    const wrap = document.querySelector(`.chat-bubble-wrap[data-id="${messageId}"]`);
    if (!wrap) {
        console.error('Message wrap not found for id:', messageId);
        return;
    }

    const bubble = wrap.querySelector('.chat-bubble');
    if (bubble) {
        bubble.textContent = newText;
        if (edited) {
            bubble.classList.add('edited');
        }
        console.log('Message updated:', messageId);
    }
}

// ── Delete Message ────────────────────────────────────────────────────────────

async function deleteMessage(dialogId, messageId) {
    console.log('Deleting message:', { dialogId, messageId });
    
    try {
        const resp = await fetch(`/api/dialogs/${dialogId}/messages/${messageId}/`, {
            method: 'DELETE',
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
        });
        const data = await resp.json();
        
        console.log('Delete response:', data);
        
        if (data.status === 'ok') {
            const wrap = document.querySelector(`.chat-bubble-wrap[data-id="${messageId}"]`);
            if (wrap) {
                wrap.remove();
                console.log('Message removed from DOM');
            } else {
                console.error('Could not find message wrap to remove:', messageId);
            }
        }
    } catch (e) {
        console.error('Delete error:', e);
    }
}

// ── Load Messages ─────────────────────────────────────────────────────────────

async function loadMessages(dialogId, msgArea, initial) {
    try {
        const url = initial
            ? `/api/dialogs/${dialogId}/messages/`
            : `/api/dialogs/${dialogId}/messages/?after=${_lastMessageId}`;

        const resp = await fetch(url, {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        const data = await resp.json();

        if (!data.messages) return;

        if (initial) {
            msgArea.innerHTML = '';
            if (data.messages.length === 0) {
                msgArea.innerHTML = `<div style="text-align:center;color:#ccc;padding:40px;font-size:14px">Начните переписку</div>`;
                return;
            }
        }

        let addedAny = false;
        data.messages.forEach(msg => {
            if (msg.id > _lastMessageId) {
                if (initial && msgArea.querySelector('[style*="Начните"]')) {
                    msgArea.innerHTML = '';
                }
                appendMessage(msgArea, msg);
                _lastMessageId = Math.max(_lastMessageId, msg.id);
                addedAny = true;
            }
        });

        if (initial || addedAny) {
            scrollToBottom(msgArea);
        }
    } catch (e) {
        if (initial) console.error('Load messages error:', e);
    }
}

function appendMessage(msgArea, msg) {
    const wrap = document.createElement('div');
    wrap.className = `chat-bubble-wrap ${msg.is_mine ? 'mine' : 'theirs'}`;
    
    // КРИТИЧЕСКИ ВАЖНО: устанавливаем data-id как строку
    wrap.setAttribute('data-id', String(msg.id));
    
    console.log('Creating message with id:', msg.id, 'data-id:', wrap.getAttribute('data-id'));
    
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

// ── Shared util ───────────────────────────────────────────────────────────────

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}