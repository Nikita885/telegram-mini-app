function initUserProfilePage() {
    const followBtn = document.querySelector('.profile-follow-btn');
    const msgBtn    = document.querySelector('.profile-msg-btn');

    if (!followBtn && !msgBtn) return;

    // ── Follow / Unfollow ─────────────────────────────────────────────────────
    if (followBtn) {
        followBtn.addEventListener('click', async () => {
            const telegramId = parseInt(followBtn.dataset.id);
            followBtn.disabled = true;

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
                    followBtn.dataset.following = 'true';
                    followBtn.textContent = 'Отписаться';
                    followBtn.classList.replace('follow', 'unfollow');
                    _adjustStat(1, +1);
                } else if (data.status === 'unfollowed') {
                    followBtn.dataset.following = 'false';
                    followBtn.textContent = 'Подписаться';
                    followBtn.classList.replace('unfollow', 'follow');
                    _adjustStat(1, -1);
                }
            } catch (e) {
                console.error('Follow error:', e);
            } finally {
                followBtn.disabled = false;
            }
        });
    }

    // ── ✅ Message - NEW: Navigate without creating dialog ────────────────────
    if (msgBtn) {
        msgBtn.addEventListener('click', () => {
            const telegramId = parseInt(msgBtn.dataset.id);
            
            // ✅ Переходим на чат БЕЗ создания диалога
            const url = `/chat/with/${telegramId}/`;
            loadPage(url);
            history.pushState({}, '', url);
        });
    }
}

function _adjustStat(index, delta) {
    const el = document.querySelector(`.stat:nth-child(${index + 1}) .stat-value`);
    if (!el) return;
    el.textContent = Math.max(0, (parseInt(el.textContent) || 0) + delta);
}