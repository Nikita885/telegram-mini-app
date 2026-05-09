"""
API Views для конструктора образов
Добавить в backend/api/views.py или создать отдельный файл outfit_views.py
"""

from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from django.db.models import Q, Count, Prefetch
from django.core.files.base import ContentFile
from PIL import Image
from io import BytesIO
import base64
import json

from .models import (
    ClothingItem, ClothingCategory, ClothingTag,
    Outfit, OutfitItem, OutfitLike, OutfitComment, OutfitView
)


# ══════════════════════════════════════════════════════════════════════════════
#  БИБЛИОТЕКА ОДЕЖДЫ
# ══════════════════════════════════════════════════════════════════════════════

@method_decorator(csrf_exempt, name='dispatch')
class ClothingLibraryView(APIView):
    """Получить одежду по категории и тегам"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def get(self, request):
        category_slug = request.GET.get('category')
        tags = request.GET.get('tags', '').split(',') if request.GET.get('tags') else []
        search = request.GET.get('search', '')
        
        items = ClothingItem.objects.filter(is_active=True)
        
        # Фильтр по категории
        if category_slug:
            items = items.filter(category__slug=category_slug)
        
        # Фильтр по тегам
        if tags:
            for tag_slug in tags:
                items = items.filter(tags__slug=tag_slug)
        
        # Поиск
        if search:
            items = items.filter(Q(name__icontains=search))
        
        items = items.select_related('category').prefetch_related('tags')[:50]
        
        return Response({
            'items': [
                {
                    'id': item.id,
                    'name': item.name,
                    'image_url': item.image.url if item.image else None,
                    'thumbnail': item.thumbnail.url if item.thumbnail else None,
                    'category': {
                        'id': item.category.id,
                        'name': item.category.name,
                        'slug': item.category.slug,
                    },
                    'tags': [
                        {
                            'id': tag.id,
                            'name': tag.name,
                            'slug': tag.slug,
                            'type': tag.tag_type,
                            'color_hex': tag.color_hex,
                        }
                        for tag in item.tags.all()
                    ],
                    'default_z_index': item.default_z_index,
                }
                for item in items
            ]
        })


@method_decorator(csrf_exempt, name='dispatch')
class ClothingTagsView(APIView):
    """Получить все доступные теги"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def get(self, request):
        tag_type = request.GET.get('type')
        
        tags = ClothingTag.objects.all()
        
        if tag_type:
            tags = tags.filter(tag_type=tag_type)
        
        # Группируем по типам
        grouped_tags = {}
        for tag in tags:
            if tag.tag_type not in grouped_tags:
                grouped_tags[tag.tag_type] = []
            
            grouped_tags[tag.tag_type].append({
                'id': tag.id,
                'name': tag.name,
                'slug': tag.slug,
                'color_hex': tag.color_hex,
            })
        
        return Response({'tags': grouped_tags})


# ══════════════════════════════════════════════════════════════════════════════
#  СОЗДАНИЕ И УПРАВЛЕНИЕ ОБРАЗАМИ
# ══════════════════════════════════════════════════════════════════════════════

@method_decorator(csrf_exempt, name='dispatch')
class OutfitCreateView(APIView):
    """Создать новый образ"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def post(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        
        title = request.data.get('title', '')
        description = request.data.get('description', '')
        items = request.data.get('items', [])  # [{clothing_item_id, position_data, order}]
        preview_base64 = request.data.get('preview_image', '')
        tags = request.data.get('tags', [])
        
        # Создаем outfit
        outfit = Outfit.objects.create(
            user=current_user,
            title=title,
            description=description,
        )
        
        # Сохраняем превью (screenshot с canvas)
        if preview_base64:
            try:
                # Декодируем base64
                format, imgstr = preview_base64.split(';base64,')
                ext = format.split('/')[-1]
                data = ContentFile(base64.b64decode(imgstr), name=f'outfit_{outfit.id}.{ext}')
                outfit.preview_image = data
                outfit.save()
            except Exception as e:
                print(f"Error saving preview: {e}")
        
        # Добавляем предметы одежды
        for item_data in items:
            OutfitItem.objects.create(
                outfit=outfit,
                clothing_item_id=item_data['clothing_item_id'],
                position_data=item_data.get('position_data', {}),
                order=item_data.get('order', 0),
            )
        
        # Добавляем теги
        if tags:
            outfit.tags.set(tags)
        
        return Response({
            'id': outfit.id,
            'title': outfit.title,
            'preview_url': outfit.preview_image.url if outfit.preview_image else None,
        }, status=201)


@method_decorator(csrf_exempt, name='dispatch')
class OutfitDetailView(APIView):
    """Детальная информация об образе"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def get(self, request, outfit_id):
        current_user = get_current_user(request)
        
        try:
            outfit = Outfit.objects.select_related('user').prefetch_related(
                'items__clothing_item__category',
                'items__clothing_item__tags',
                'tags',
                'likes',
                'comments__user'
            ).get(id=outfit_id)
        except Outfit.DoesNotExist:
            return Response({'error': 'Not found'}, status=404)
        
        # Увеличиваем счетчик просмотров
        outfit.views_count += 1
        outfit.save(update_fields=['views_count'])
        
        # Записываем просмотр
        if current_user:
            OutfitView.objects.get_or_create(
                outfit=outfit,
                user=current_user,
                defaults={'duration_seconds': 0}
            )
        
        # Проверяем, лайкнул ли текущий пользователь
        is_liked = False
        if current_user:
            is_liked = outfit.likes.filter(user=current_user).exists()
        
        return Response({
            'id': outfit.id,
            'title': outfit.title,
            'description': outfit.description,
            'preview_url': outfit.preview_image.url if outfit.preview_image else None,
            'author': {
                'telegram_id': outfit.user.telegram_id,
                'username': outfit.user.username,
                'first_name': outfit.user.first_name,
                'last_name': outfit.user.last_name,
                'avatar_url': outfit.user.avatar.url if outfit.user.avatar else None,
                'avatar_color': outfit.user.avatar_random_color,
            },
            'items': [
                {
                    'id': item.id,
                    'clothing_item': {
                        'id': item.clothing_item.id,
                        'name': item.clothing_item.name,
                        'image_url': item.clothing_item.image.url if item.clothing_item.image else None,
                        'category': item.clothing_item.category.name,
                    },
                    'position_data': item.position_data,
                }
                for item in outfit.items.all()
            ],
            'tags': [
                {
                    'id': tag.id,
                    'name': tag.name,
                    'type': tag.tag_type,
                    'color_hex': tag.color_hex,
                }
                for tag in outfit.tags.all()
            ],
            'likes_count': outfit.likes_count,
            'comments_count': outfit.comments_count,
            'views_count': outfit.views_count,
            'is_liked': is_liked,
            'created_at': outfit.created_at.isoformat(),
        })


# ══════════════════════════════════════════════════════════════════════════════
#  ЛЕНТА ОБРАЗОВ (FEED)
# ══════════════════════════════════════════════════════════════════════════════

@method_decorator(csrf_exempt, name='dispatch')
class OutfitFeedView(APIView):
    """Лента рекомендаций образов"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def get(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        
        feed_type = request.GET.get('type', 'recommended')  # recommended / following / popular
        page = int(request.GET.get('page', 1))
        page_size = 20
        
        outfits = Outfit.objects.filter(is_public=True).select_related('user').prefetch_related('tags')
        
        if feed_type == 'following':
            # Образы от людей, на которых подписан
            following_ids = current_user.following.values_list('following_id', flat=True)
            outfits = outfits.filter(user_id__in=following_ids)
        
        elif feed_type == 'popular':
            # Популярные за последнюю неделю
            from datetime import timedelta
            from django.utils import timezone
            week_ago = timezone.now() - timedelta(days=7)
            outfits = outfits.filter(created_at__gte=week_ago).order_by('-likes_count', '-views_count')
        
        else:  # recommended
            # Простой алгоритм рекомендаций:
            # 1. Находим теги из образов, которые пользователь лайкнул
            # 2. Показываем образы с похожими тегами
            # 3. Исключаем уже просмотренные
            
            liked_outfits = OutfitLike.objects.filter(user=current_user).values_list('outfit_id', flat=True)
            liked_tags = ClothingTag.objects.filter(outfits__id__in=liked_outfits).distinct()
            
            if liked_tags.exists():
                outfits = outfits.filter(tags__in=liked_tags).distinct()
            
            # Исключаем просмотренные
            viewed_ids = OutfitView.objects.filter(user=current_user).values_list('outfit_id', flat=True)
            outfits = outfits.exclude(id__in=viewed_ids)
        
        # Пагинация
        start = (page - 1) * page_size
        end = start + page_size
        outfits = outfits[start:end]
        
        # Сериализация
        result = []
        for outfit in outfits:
            is_liked = OutfitLike.objects.filter(outfit=outfit, user=current_user).exists()
            
            result.append({
                'id': outfit.id,
                'title': outfit.title,
                'description': outfit.description,
                'preview_url': outfit.preview_image.url if outfit.preview_image else None,
                'author': {
                    'telegram_id': outfit.user.telegram_id,
                    'username': outfit.user.username,
                    'first_name': outfit.user.first_name,
                    'last_name': outfit.user.last_name,
                    'avatar_url': outfit.user.avatar.url if outfit.user.avatar else None,
                    'avatar_color': outfit.user.avatar_random_color,
                },
                'tags': [{'name': tag.name, 'color_hex': tag.color_hex} for tag in outfit.tags.all()[:5]],
                'likes_count': outfit.likes_count,
                'comments_count': outfit.comments_count,
                'is_liked': is_liked,
                'created_at': outfit.created_at.isoformat(),
            })
        
        return Response({
            'outfits': result,
            'page': page,
            'has_more': len(result) == page_size,
        })


# ══════════════════════════════════════════════════════════════════════════════
#  ЛАЙКИ И КОММЕНТАРИИ
# ══════════════════════════════════════════════════════════════════════════════

@method_decorator(csrf_exempt, name='dispatch')
class OutfitLikeToggleView(APIView):
    """Лайк / дизлайк образа"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def post(self, request, outfit_id):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        
        try:
            outfit = Outfit.objects.get(id=outfit_id)
        except Outfit.DoesNotExist:
            return Response({'error': 'Not found'}, status=404)
        
        like, created = OutfitLike.objects.get_or_create(
            outfit=outfit,
            user=current_user
        )
        
        if not created:
            # Уже лайкали - удаляем лайк
            like.delete()
            outfit.likes_count = max(0, outfit.likes_count - 1)
            outfit.save(update_fields=['likes_count'])
            return Response({'status': 'unliked', 'likes_count': outfit.likes_count})
        else:
            # Новый лайк
            outfit.likes_count += 1
            outfit.save(update_fields=['likes_count'])
            return Response({'status': 'liked', 'likes_count': outfit.likes_count})


@method_decorator(csrf_exempt, name='dispatch')
class OutfitCommentView(APIView):
    """Комментарии к образу"""
    authentication_classes = []
    permission_classes = [AllowAny]
    
    def get(self, request, outfit_id):
        """Получить комментарии"""
        try:
            outfit = Outfit.objects.get(id=outfit_id)
        except Outfit.DoesNotExist:
            return Response({'error': 'Not found'}, status=404)
        
        comments = outfit.comments.filter(parent__isnull=True).select_related('user').prefetch_related('replies__user')
        
        return Response({
            'comments': [
                {
                    'id': comment.id,
                    'text': comment.text,
                    'author': {
                        'telegram_id': comment.user.telegram_id,
                        'username': comment.user.username,
                        'first_name': comment.user.first_name,
                        'avatar_url': comment.user.avatar.url if comment.user.avatar else None,
                        'avatar_color': comment.user.avatar_random_color,
                    },
                    'created_at': comment.created_at.isoformat(),
                    'replies': [
                        {
                            'id': reply.id,
                            'text': reply.text,
                            'author': {
                                'telegram_id': reply.user.telegram_id,
                                'username': reply.user.username,
                                'first_name': reply.user.first_name,
                            },
                            'created_at': reply.created_at.isoformat(),
                        }
                        for reply in comment.replies.all()
                    ]
                }
                for comment in comments
            ]
        })
    
    def post(self, request, outfit_id):
        """Добавить комментарий"""
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        
        try:
            outfit = Outfit.objects.get(id=outfit_id)
        except Outfit.DoesNotExist:
            return Response({'error': 'Not found'}, status=404)
        
        text = request.data.get('text', '').strip()
        parent_id = request.data.get('parent_id')
        
        if not text:
            return Response({'error': 'Empty comment'}, status=400)
        
        comment = OutfitComment.objects.create(
            outfit=outfit,
            user=current_user,
            text=text,
            parent_id=parent_id if parent_id else None,
        )
        
        # Обновляем счетчик
        outfit.comments_count += 1
        outfit.save(update_fields=['comments_count'])
        
        return Response({
            'id': comment.id,
            'text': comment.text,
            'created_at': comment.created_at.isoformat(),
        }, status=201)


# ══════════════════════════════════════════════════════════════════════════════
#  HELPER
# ══════════════════════════════════════════════════════════════════════════════

def get_current_user(request):
    """Получить текущего пользователя из сессии"""
    from .models import TelegramUser
    tid = request.session.get('telegram_id')
    return TelegramUser.objects.filter(telegram_id=tid).first() if tid else None