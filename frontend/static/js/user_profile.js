function initUserProfilePage() {
    const btn = document.querySelector('.profile-follow-btn');
    if (!btn) return;

    btn.addEventListener('click', async () => {
        const telegramId = parseInt(btn.dataset.id);
        const isFollowing = btn.dataset.following === 'true';

        btn.disabled = true;

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
                btn.dataset.following = 'true';
                btn.textContent = 'Отписаться';
                btn.classList.replace('follow', 'unfollow');

                // Increment followers count display
                _adjustStatValue('.stat:nth-child(2) .stat-value', +1);
            } else if (data.status === 'unfollowed') {
                btn.dataset.following = 'false';
                btn.textContent = 'Подписаться';
                btn.classList.replace('unfollow', 'follow');

                // Decrement followers count display
                _adjustStatValue('.stat:nth-child(2) .stat-value', -1);
            }
        } catch (e) {
            console.error('Follow error:', e);
        } finally {
            btn.disabled = false;
        }
    });
}

function _adjustStatValue(selector, delta) {
    const el = document.querySelector(selector);
    if (!el) return;
    const current = parseInt(el.textContent) || 0;
    el.textContent = Math.max(0, current + delta);
}