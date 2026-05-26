import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
import os

os.makedirs(r'G:\telegram-mini-app\diagrams', exist_ok=True)

def table_box(ax, x, y, name, fields, width=4.0, hc='#1565C0'):
    row_h = 0.40
    header_h = 0.50
    total_h = header_h + len(fields) * row_h
    rect_h = FancyBboxPatch((x, y + total_h - header_h), width, header_h,
                             boxstyle='square,pad=0', linewidth=1.2,
                             edgecolor='#222222', facecolor=hc)
    ax.add_patch(rect_h)
    ax.text(x + width/2, y + total_h - header_h/2, name, ha='center', va='center',
            fontsize=8.5, fontweight='bold', color='white', fontfamily='DejaVu Sans')
    for i, (fname, ftype, is_pk, is_fk) in enumerate(fields):
        yrow = y + total_h - header_h - (i+1)*row_h
        fc = '#f0f4ff' if i % 2 == 0 else '#ffffff'
        if is_pk: fc = '#fff9c4'
        if is_fk: fc = '#e8f5e9'
        rect_r = FancyBboxPatch((x, yrow), width, row_h,
                                 boxstyle='square,pad=0', linewidth=0.7,
                                 edgecolor='#aaaaaa', facecolor=fc)
        ax.add_patch(rect_r)
        marker = 'PK' if is_pk else ('FK' if is_fk else '  ')
        ax.text(x + 0.12, yrow + row_h/2, marker, ha='left', va='center',
                fontsize=6.0, color='#666666', fontweight='bold', fontfamily='DejaVu Sans')
        ax.text(x + 0.58, yrow + row_h/2, fname, ha='left', va='center',
                fontsize=7.5, color='#111111', fontfamily='DejaVu Sans')
        ax.text(x + width - 0.1, yrow + row_h/2, ftype, ha='right', va='center',
                fontsize=7.0, color='#555555', fontstyle='italic', fontfamily='DejaVu Sans')
    rect_b = FancyBboxPatch((x, y), width, total_h, boxstyle='square,pad=0',
                             linewidth=1.5, edgecolor='#333333', facecolor='none')
    ax.add_patch(rect_b)
    return total_h

def rel(ax, x1, y1, x2, y2):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle='->', color='#334488', lw=1.0,
                                connectionstyle='arc3,rad=0.0'))

fig, ax = plt.subplots(figsize=(20, 22))
ax.set_xlim(0, 20)
ax.set_ylim(0, 22)
ax.axis('off')
ax.set_facecolor('white')
fig.patch.set_facecolor('white')

ax.text(10, 21.6, 'Схема базы данных', ha='center', va='center',
        fontsize=14, fontweight='bold', fontfamily='DejaVu Sans')

W = 4.2

# Row 1: TelegramUser (center)
h0 = table_box(ax, 7.9, 17.8, 'TelegramUser', [
    ('id', 'BigInt', True, False),
    ('telegram_id', 'BigInt', False, False),
    ('username', 'Varchar(64)', False, False),
    ('first_name', 'Varchar(64)', False, False),
    ('last_name', 'Varchar(64)', False, False),
    ('avatar', 'ImageField', False, False),
    ('avatar_random_color', 'Varchar(7)', False, False),
    ('bio', 'Text', False, False),
    ('created_at', 'DateTime', False, False),
], W, '#1565C0')

# Row 1 companions: Follow, ClothingCategory
h1 = table_box(ax, 2.5, 18.8, 'Follow', [
    ('id', 'BigInt', True, False),
    ('follower', 'FK→TelegramUser', False, True),
    ('following', 'FK→TelegramUser', False, True),
    ('created_at', 'DateTime', False, False),
], W, '#6a1b9a')

h2 = table_box(ax, 13.4, 19.5, 'ClothingCategory', [
    ('id', 'BigInt', True, False),
    ('name', 'Varchar(64)', False, False),
    ('icon', 'Varchar(50)', False, False),
], W, '#00695c')

# Row 2: OutfitPost, ClothingItem
h3 = table_box(ax, 0.3, 13.0, 'OutfitPost', [
    ('id', 'BigInt', True, False),
    ('author', 'FK→TelegramUser', False, True),
    ('image', 'ImageField', False, False),
    ('title', 'Varchar(200)', False, False),
    ('description', 'Text', False, False),
    ('is_public', 'Boolean', False, False),
    ('likes_count', 'Int', False, False),
    ('created_at', 'DateTime', False, False),
], W, '#1565C0')

h4 = table_box(ax, 13.4, 14.0, 'ClothingItem', [
    ('id', 'BigInt', True, False),
    ('user', 'FK→TelegramUser', False, True),
    ('category', 'FK→Category', False, True),
    ('image', 'ImageField', False, False),
    ('name', 'Varchar(100)', False, False),
    ('created_at', 'DateTime', False, False),
], W, '#1565C0')

# Row 2.5: PostClothingItem, Hashtag, PostHashtag
h5 = table_box(ax, 5.5, 13.5, 'PostClothingItem', [
    ('id', 'BigInt', True, False),
    ('post', 'FK→OutfitPost', False, True),
    ('clothing_item', 'FK→ClothingItem', False, True),
    ('pos_x', 'Float', False, False),
    ('pos_y', 'Float', False, False),
    ('scale', 'Float', False, False),
    ('z_index', 'Int', False, False),
], W, '#4527a0')

h6 = table_box(ax, 10.5, 13.5, 'Hashtag', [
    ('id', 'BigInt', True, False),
    ('name', 'Varchar(50)', False, False),
], 3.0, '#00695c')

h7 = table_box(ax, 13.8, 10.5, 'PostHashtag', [
    ('id', 'BigInt', True, False),
    ('post', 'FK→OutfitPost', False, True),
    ('hashtag', 'FK→Hashtag', False, True),
], W, '#4527a0')

# Row 3: PostLike, PostComment, CommentLike
h8 = table_box(ax, 0.3, 9.0, 'PostLike', [
    ('id', 'BigInt', True, False),
    ('user', 'FK→TelegramUser', False, True),
    ('post', 'FK→OutfitPost', False, True),
    ('created_at', 'DateTime', False, False),
], 3.8, '#ad1457')

h9 = table_box(ax, 4.5, 8.5, 'PostComment', [
    ('id', 'BigInt', True, False),
    ('user', 'FK→TelegramUser', False, True),
    ('post', 'FK→OutfitPost', False, True),
    ('text', 'Text', False, False),
    ('created_at', 'DateTime', False, False),
    ('likes_count', 'Int', False, False),
], W, '#ad1457')

h10 = table_box(ax, 9.2, 8.5, 'CommentLike', [
    ('id', 'BigInt', True, False),
    ('user', 'FK→TelegramUser', False, True),
    ('comment', 'FK→PostComment', False, True),
    ('created_at', 'DateTime', False, False),
], W, '#ad1457')

# Row 4: Dialog, Message, Notification
h11 = table_box(ax, 0.3, 5.5, 'Dialog', [
    ('id', 'BigInt', True, False),
    ('user1', 'FK→TelegramUser', False, True),
    ('user2', 'FK→TelegramUser', False, True),
    ('created_at', 'DateTime', False, False),
], 3.8, '#e65100')

h12 = table_box(ax, 4.5, 4.5, 'Message', [
    ('id', 'BigInt', True, False),
    ('dialog', 'FK→Dialog', False, True),
    ('sender', 'FK→TelegramUser', False, True),
    ('text', 'Text', False, False),
    ('is_read', 'Boolean', False, False),
    ('is_edited', 'Boolean', False, False),
    ('created_at', 'DateTime', False, False),
], W, '#e65100')

h13 = table_box(ax, 9.2, 4.0, 'Notification', [
    ('id', 'BigInt', True, False),
    ('recipient', 'FK→TelegramUser', False, True),
    ('sender', 'FK→TelegramUser', False, True),
    ('notification_type', 'Varchar(20)', False, False),
    ('post', 'FK→OutfitPost', False, True),
    ('comment', 'FK→PostComment', False, True),
    ('is_read', 'Boolean', False, False),
    ('created_at', 'DateTime', False, False),
], W, '#e65100')

# Relations
# Follow -> TelegramUser
rel(ax, 6.7, 20.2, 7.9, 20.5)
# ClothingItem -> TelegramUser
rel(ax, 15.5, 17.6, 12.1, 19.5)
# ClothingItem -> Category
rel(ax, 15.5, 17.6, 15.5, 22.0)
# OutfitPost -> TelegramUser
rel(ax, 2.4, 20.6, 7.9, 19.5)
# PostClothingItem -> OutfitPost
rel(ax, 5.5, 16.0, 4.5, 20.6)
# PostClothingItem -> ClothingItem
rel(ax, 9.7, 16.0, 13.4, 16.5)
# Hashtag -> PostHashtag
rel(ax, 12.0, 14.5, 13.8, 12.5)
# PostHashtag -> OutfitPost
rel(ax, 13.8, 11.8, 4.5, 17.0)
# PostLike -> OutfitPost
rel(ax, 2.1, 12.7, 2.1, 21.0)
# PostComment -> OutfitPost
rel(ax, 6.6, 14.0, 4.5, 17.5)
# CommentLike -> PostComment
rel(ax, 9.2, 10.5, 8.7, 14.1)
# Dialog -> TelegramUser
rel(ax, 2.1, 9.2, 7.9, 18.5)
# Message -> Dialog
rel(ax, 4.5, 8.5, 4.1, 9.2)
# Notification -> TelegramUser
rel(ax, 11.3, 11.7, 10.0, 18.0)

plt.tight_layout(pad=0.3)
plt.savefig(r'G:\telegram-mini-app\diagrams\dbschema.png', dpi=150, bbox_inches='tight',
            facecolor='white')
plt.close()
print('dbschema.png saved')
