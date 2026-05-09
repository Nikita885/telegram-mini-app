import React, { useState, useEffect, useRef } from 'react';

/**
 * ЛЕНТА ОБРАЗОВ (FEED)
 * Infinite scroll с рекомендациями
 */

const OutfitFeed = () => {
  const [outfits, setOutfits] = useState([]);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [feedType, setFeedType] = useState('recommended'); // recommended / following / popular
  
  const observerTarget = useRef(null);
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  LOAD DATA
  // ══════════════════════════════════════════════════════════════════════════════
  
  useEffect(() => {
    loadFeed(1, true);
  }, [feedType]);
  
  useEffect(() => {
    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting && hasMore && !loading) {
          loadFeed(page + 1, false);
        }
      },
      { threshold: 0.5 }
    );
    
    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }
    
    return () => {
      if (observerTarget.current) {
        observer.unobserve(observerTarget.current);
      }
    };
  }, [page, hasMore, loading]);
  
  const loadFeed = async (pageNum, reset = false) => {
    if (loading) return;
    
    setLoading(true);
    
    try {
      const resp = await fetch(`/api/outfits/feed/?type=${feedType}&page=${pageNum}`);
      const data = await resp.json();
      
      if (reset) {
        setOutfits(data.outfits || []);
      } else {
        setOutfits(prev => [...prev, ...(data.outfits || [])]);
      }
      
      setPage(pageNum);
      setHasMore(data.has_more || false);
    } catch (e) {
      console.error('Load feed error:', e);
    } finally {
      setLoading(false);
    }
  };
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  ACTIONS
  // ══════════════════════════════════════════════════════════════════════════════
  
  const toggleLike = async (outfitId) => {
    try {
      const resp = await fetch(`/api/outfits/${outfitId}/like/`, {
        method: 'POST',
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
      const data = await resp.json();
      
      // Обновляем локальное состояние
      setOutfits(prev => prev.map(outfit => 
        outfit.id === outfitId
          ? {
              ...outfit,
              is_liked: data.status === 'liked',
              likes_count: data.likes_count
            }
          : outfit
      ));
    } catch (e) {
      console.error('Like error:', e);
    }
  };
  
  const openOutfit = (outfitId) => {
    // Переход на детальную страницу образа
    loadPage(`/outfit/${outfitId}/`);
    history.pushState({}, '', `/outfit/${outfitId}/`);
  };
  
  const openProfile = (telegramId) => {
    loadPage(`/user/${telegramId}/`);
    history.pushState({}, '', `/user/${telegramId}/`);
  };
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  RENDER
  // ══════════════════════════════════════════════════════════════════════════════
  
  return (
    <div className="outfit-feed">
      
      {/* Header */}
      <div className="feed-header">
        <h1 className="feed-title">Образы</h1>
        
        {/* Фильтры */}
        <div className="feed-filters">
          <button
            className={`feed-filter-btn ${feedType === 'recommended' ? 'active' : ''}`}
            onClick={() => setFeedType('recommended')}
          >
            Рекомендации
          </button>
          <button
            className={`feed-filter-btn ${feedType === 'following' ? 'active' : ''}`}
            onClick={() => setFeedType('following')}
          >
            Подписки
          </button>
          <button
            className={`feed-filter-btn ${feedType === 'popular' ? 'active' : ''}`}
            onClick={() => setFeedType('popular')}
          >
            Популярное
          </button>
        </div>
      </div>
      
      {/* Список образов */}
      <div className="outfit-list">
        {outfits.map(outfit => (
          <OutfitCard
            key={outfit.id}
            outfit={outfit}
            onLike={() => toggleLike(outfit.id)}
            onOpen={() => openOutfit(outfit.id)}
            onAuthorClick={() => openProfile(outfit.author.telegram_id)}
          />
        ))}
        
        {/* Индикатор загрузки */}
        {loading && (
          <div style={{ padding: '40px', textAlign: 'center' }}>
            <div className="search-spinner"></div>
          </div>
        )}
        
        {/* Пустое состояние */}
        {!loading && outfits.length === 0 && (
          <div className="dialogs-empty">
            <div className="empty-icon"><i className="ri-gallery-line"></i></div>
            <p>Нет образов</p>
            <p style={{ fontSize: '13px' }}>Создайте свой первый образ!</p>
          </div>
        )}
        
        {/* Наблюдатель для infinite scroll */}
        <div ref={observerTarget} style={{ height: '20px' }}></div>
      </div>
      
    </div>
  );
};


/**
 * КАРТОЧКА ОБРАЗА
 */
const OutfitCard = ({ outfit, onLike, onOpen, onAuthorClick }) => {
  const displayName = [outfit.author.first_name, outfit.author.last_name]
    .filter(Boolean).join(' ') || outfit.author.username || 'Пользователь';
  
  const avatarHtml = outfit.author.avatar_url
    ? <img src={outfit.author.avatar_url} alt="" />
    : <span>{(displayName[0] || '?').toUpperCase()}</span>;
  
  const timeAgo = getTimeAgo(outfit.created_at);
  
  return (
    <div className="outfit-card">
      
      {/* Автор */}
      <div className="outfit-card-header">
        <div
          className="outfit-author-avatar"
          style={{ backgroundColor: outfit.author.avatar_color }}
          onClick={onAuthorClick}
        >
          {avatarHtml}
        </div>
        <div className="outfit-author-info" onClick={onAuthorClick}>
          <div className="outfit-author-name">{displayName}</div>
          <div className="outfit-posted-time">{timeAgo}</div>
        </div>
      </div>
      
      {/* Изображение образа */}
      <div className="outfit-card-image" onClick={onOpen}>
        {outfit.preview_url && (
          <img src={outfit.preview_url} alt={outfit.title} />
        )}
      </div>
      
      {/* Действия */}
      <div className="outfit-card-actions">
        <button
          className={`outfit-action-btn ${outfit.is_liked ? 'liked' : ''}`}
          onClick={onLike}
        >
          <i className={outfit.is_liked ? 'ri-heart-fill' : 'ri-heart-line'}></i>
          {outfit.likes_count > 0 && <span>{outfit.likes_count}</span>}
        </button>
        
        <button className="outfit-action-btn" onClick={onOpen}>
          <i className="ri-chat-3-line"></i>
          {outfit.comments_count > 0 && <span>{outfit.comments_count}</span>}
        </button>
        
        <button className="outfit-action-btn">
          <i className="ri-share-line"></i>
        </button>
      </div>
      
      {/* Описание */}
      {outfit.title && (
        <div className="outfit-card-title">{outfit.title}</div>
      )}
      {outfit.description && (
        <div className="outfit-card-description">{outfit.description}</div>
      )}
      
      {/* Теги */}
      {outfit.tags && outfit.tags.length > 0 && (
        <div className="outfit-card-tags">
          {outfit.tags.map((tag, idx) => (
            <span key={idx} className="outfit-tag">
              {tag.color_hex && (
                <span
                  className="tag-color"
                  style={{ background: tag.color_hex }}
                ></span>
              )}
              {tag.name}
            </span>
          ))}
        </div>
      )}
      
    </div>
  );
};


/**
 * HELPERS
 */
const getTimeAgo = (timestamp) => {
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
};


export default OutfitFeed;