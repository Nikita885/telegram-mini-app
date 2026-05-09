import React, { useState, useRef, useEffect } from 'react';

/**
 * КОНСТРУКТОР ОБРАЗОВ - React компонент
 * 
 * Архитектура:
 * - Canvas для визуализации манекена и одежды
 * - Боковая панель с категориями одежды
 * - Drag & drop или tap для добавления
 * - Слои (layering) для правильного наложения
 */

const OutfitCreator = ({ userId, onSave }) => {
  // ══════════════════════════════════════════════════════════════════════════════
  //  STATE
  // ══════════════════════════════════════════════════════════════════════════════
  
  const [activeCategory, setActiveCategory] = useState('tops');
  const [selectedItems, setSelectedItems] = useState([]); // Одежда на манекене
  const [clothingLibrary, setClothingLibrary] = useState([]);
  const [searchTags, setSearchTags] = useState([]);
  const [mannequin, setMannequin] = useState('female'); // female/male
  
  const canvasRef = useRef(null);
  const [canvasSize] = useState({ width: 400, height: 600 });
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  API CALLS
  // ══════════════════════════════════════════════════════════════════════════════
  
  useEffect(() => {
    loadClothingByCategory(activeCategory);
  }, [activeCategory, searchTags]);
  
  const loadClothingByCategory = async (category) => {
    const tags = searchTags.map(t => t.slug).join(',');
    const resp = await fetch(`/api/clothing/?category=${category}&tags=${tags}`);
    const data = await resp.json();
    setClothingLibrary(data.items || []);
  };
  
  const saveOutfit = async () => {
    // Screenshot canvas
    const canvas = canvasRef.current;
    const preview = canvas.toDataURL('image/png');
    
    const outfitData = {
      items: selectedItems.map((item, idx) => ({
        clothing_item_id: item.id,
        position_data: item.position,
        order: idx
      })),
      preview_image: preview,
      title: 'Мой образ', // можно добавить поле ввода
      tags: extractTags(selectedItems)
    };
    
    const resp = await fetch('/api/outfits/', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(outfitData)
    });
    
    if (resp.ok) {
      const outfit = await resp.json();
      onSave?.(outfit);
    }
  };
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  CANVAS RENDERING
  // ══════════════════════════════════════════════════════════════════════════════
  
  useEffect(() => {
    renderCanvas();
  }, [selectedItems, mannequin]);
  
  const renderCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // 1. Рисуем манекен (силуэт)
    drawMannequin(ctx);
    
    // 2. Рисуем одежду по слоям (z-index)
    const sorted = [...selectedItems].sort((a, b) => 
      (a.position.z_index || a.default_z_index) - (b.position.z_index || b.default_z_index)
    );
    
    sorted.forEach(item => {
      drawClothingItem(ctx, item);
    });
  };
  
  const drawMannequin = (ctx) => {
    // Простой силуэт человека
    ctx.save();
    ctx.globalAlpha = 0.1;
    ctx.fillStyle = '#000';
    
    // Голова
    ctx.beginPath();
    ctx.arc(200, 80, 40, 0, Math.PI * 2);
    ctx.fill();
    
    // Тело
    ctx.fillRect(160, 120, 80, 200);
    
    // Руки
    ctx.fillRect(120, 120, 40, 150);
    ctx.fillRect(240, 120, 40, 150);
    
    // Ноги
    ctx.fillRect(160, 320, 35, 200);
    ctx.fillRect(205, 320, 35, 200);
    
    ctx.restore();
  };
  
  const drawClothingItem = (ctx, item) => {
    const img = new Image();
    img.src = item.image_url;
    
    const pos = item.position;
    const x = pos.x || 0;
    const y = pos.y || 0;
    const scale = pos.scale || 1;
    const rotation = pos.rotation || 0;
    
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(rotation * Math.PI / 180);
    ctx.scale(scale, scale);
    ctx.drawImage(img, 0, 0);
    ctx.restore();
  };
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  INTERACTIONS
  // ══════════════════════════════════════════════════════════════════════════════
  
  const addClothingToOutfit = (clothingItem) => {
    // Добавляем одежду на манекен с позицией по умолчанию
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
    
    setSelectedItems(prev => [...prev, newItem]);
  };
  
  const getCategoryDefaultY = (category) => {
    const positions = {
      'tops': 120,
      'bottoms': 280,
      'shoes': 480,
      'accessories': 100
    };
    return positions[category] || 200;
  };
  
  const removeItem = (itemId) => {
    setSelectedItems(prev => prev.filter(item => item.id !== itemId));
  };
  
  const extractTags = (items) => {
    const tags = new Set();
    items.forEach(item => {
      item.tags?.forEach(tag => tags.add(tag.id));
    });
    return Array.from(tags);
  };
  
  // ══════════════════════════════════════════════════════════════════════════════
  //  RENDER
  // ══════════════════════════════════════════════════════════════════════════════
  
  return (
    <div className="outfit-creator">
      
      {/* Заголовок */}
      <div className="creator-header">
        <button className="back-btn" onClick={() => history.back()}>
          <i className="ri-arrow-left-line"></i>
        </button>
        <h1>Создать образ</h1>
        <button className="save-btn" onClick={saveOutfit}>
          Сохранить
        </button>
      </div>
      
      {/* Основная область */}
      <div className="creator-main">
        
        {/* Canvas с манекеном */}
        <div className="canvas-container">
          <canvas
            ref={canvasRef}
            width={canvasSize.width}
            height={canvasSize.height}
            className="outfit-canvas"
          />
          
          {/* Список добавленной одежды */}
          <div className="selected-items-list">
            {selectedItems.map((item, idx) => (
              <div key={idx} className="selected-item-chip">
                <img src={item.thumbnail || item.image_url} alt="" />
                <span>{item.name}</span>
                <button onClick={() => removeItem(item.id)}>×</button>
              </div>
            ))}
          </div>
        </div>
        
        {/* Библиотека одежды */}
        <div className="clothing-library">
          
          {/* Категории */}
          <div className="categories-tabs">
            {['tops', 'bottoms', 'shoes', 'accessories'].map(cat => (
              <button
                key={cat}
                className={`category-tab ${activeCategory === cat ? 'active' : ''}`}
                onClick={() => setActiveCategory(cat)}
              >
                <i className={`ri-${getCategoryIcon(cat)}`}></i>
                <span>{getCategoryName(cat)}</span>
              </button>
            ))}
          </div>
          
          {/* Поиск по тегам */}
          <div className="tag-filters">
            <TagSelector
              selectedTags={searchTags}
              onChange={setSearchTags}
            />
          </div>
          
          {/* Сетка одежды */}
          <div className="clothing-grid">
            {clothingLibrary.map(item => (
              <div
                key={item.id}
                className="clothing-card"
                onClick={() => addClothingToOutfit(item)}
              >
                <img src={item.thumbnail || item.image_url} alt={item.name} />
                <div className="clothing-card-name">{item.name}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
      
      {/* Нижняя панель инструментов */}
      <div className="creator-toolbar">
        <button onClick={() => setMannequin(m => m === 'female' ? 'male' : 'female')}>
          <i className="ri-user-line"></i>
          Сменить фигуру
        </button>
        <button onClick={() => setSelectedItems([])}>
          <i className="ri-delete-bin-line"></i>
          Очистить
        </button>
      </div>
      
    </div>
  );
};

// ══════════════════════════════════════════════════════════════════════════════
//  HELPERS
// ══════════════════════════════════════════════════════════════════════════════

const TagSelector = ({ selectedTags, onChange }) => {
  const [availableTags, setAvailableTags] = useState([]);
  
  useEffect(() => {
    fetch('/api/clothing/tags/')
      .then(r => r.json())
      .then(data => setAvailableTags(data.tags || []));
  }, []);
  
  const toggleTag = (tag) => {
    const exists = selectedTags.find(t => t.id === tag.id);
    if (exists) {
      onChange(selectedTags.filter(t => t.id !== tag.id));
    } else {
      onChange([...selectedTags, tag]);
    }
  };
  
  return (
    <div className="tag-selector">
      {availableTags.map(tag => (
        <button
          key={tag.id}
          className={`tag-chip ${selectedTags.find(t => t.id === tag.id) ? 'active' : ''}`}
          onClick={() => toggleTag(tag)}
        >
          {tag.color_hex && (
            <span className="tag-color" style={{ background: tag.color_hex }}></span>
          )}
          {tag.name}
        </button>
      ))}
    </div>
  );
};

const getCategoryIcon = (slug) => {
  const icons = {
    'tops': 't-shirt-line',
    'bottoms': 'folder-line',
    'shoes': 'footprint-line',
    'accessories': 'bear-smile-line'
  };
  return icons[slug] || 'shopping-bag-line';
};

const getCategoryName = (slug) => {
  const names = {
    'tops': 'Верх',
    'bottoms': 'Низ',
    'shoes': 'Обувь',
    'accessories': 'Аксессуары'
  };
  return names[slug] || slug;
};

export default OutfitCreator;