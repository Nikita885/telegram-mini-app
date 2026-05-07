// ── Global chat state ──────────────────────────────────────────────────────────
let _chatPollingInterval = null;
let _lastMessageId = 0;

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

    // ── Back ──────────────────────────────────────────────────────────────────
    if (backBtn) {
        backBtn.addEventListener('click', () => {
            _stopChatPolling();
            loadPage('/messages/');
            history.pushState({}, '', '/messages/');
        });
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
    });
}

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
    wrap.dataset.id = msg.id;
    wrap.innerHTML = `
        <div class="chat-bubble">${escHtml(msg.text)}</div>
        <div class="chat-bubble-time">${msg.time}</div>
    `;
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