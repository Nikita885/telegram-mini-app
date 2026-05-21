# Добавить в api/views.py

from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny
from .models import ClothingCategory, ClothingItem, OutfitPost, PostClothingItem, Hashtag

# ═══════════════════════════════════════════════════════════════════════════════
#  CLOTHING CONSTRUCTOR API
# ═══════════════════════════════════════════════════════════════════════════════

@method_decorator(csrf_exempt, name='dispatch')
class ClothingCategoriesView(APIView):
    """Список категорий одежды"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        categories = ClothingCategory.objects.all()
        return Response({
            'categories': [
                {
                    'name': c.name,
                    'display_name': c.get_name_display(),
                    'icon_class': c.icon_class,
                }
                for c in categories
            ]
        })


@method_decorator(csrf_exempt, name='dispatch')
class ClothingItemsView(APIView):
    """Список предметов одежды с фильтрами"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        category = request.GET.get('category')
        gender = request.GET.get('gender', 'male')
        style = request.GET.get('style')
        color = request.GET.get('color')

        qs = ClothingItem.objects.filter(category__name=category)
        
        # Фильтр по полу (включая unisex)
        qs = qs.filter(gender__in=[gender, 'unisex'])
        
        if style:
            qs = qs.filter(style=style)
        if color:
            qs = qs.filter(color__icontains=color)

        return Response({
            'items': [
                {
                    'id': item.id,
                    'name': item.name,
                    'image': item.image.url if item.image else None,
                    'color': item.color,
                    'style': item.style,
                }
                for item in qs[:30]  # лимит 30
            ]
        })


@method_decorator(csrf_exempt, name='dispatch')
class CreateOutfitView(APIView):
    """Создание поста с образом"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        mannequin_type = request.data.get('mannequin_type', 'male')
        description = request.data.get('description', '').strip()
        hashtags = request.data.get('hashtags', [])
        items_data = request.data.get('items', [])

        if not description:
            return Response({'error': 'Description required'}, status=400)

        if not items_data:
            return Response({'error': 'No items in outfit'}, status=400)

        # Создаем пост
        post = OutfitPost.objects.create(
            user=user,
            mannequin_type=mannequin_type,
            description=description
        )

        # Добавляем хештеги
        for tag_name in hashtags:
            tag, created = Hashtag.objects.get_or_create(tag=tag_name.lower())
            tag.usage_count += 1
            tag.save()
            post.hashtags.add(tag)

        # Добавляем предметы одежды
        for item_data in items_data:
            try:
                clothing = ClothingItem.objects.get(id=item_data['clothing_id'])
                PostClothingItem.objects.create(
                    post=post,
                    clothing=clothing,
                    position_x=item_data.get('position_x', 0),
                    position_y=item_data.get('position_y', 0),
                    scale=item_data.get('scale', 1.0),
                    rotation=item_data.get('rotation', 0),
                    z_index=item_data.get('z_index', 0),
                )
            except ClothingItem.DoesNotExist:
                continue

        return Response({
            'status': 'ok',
            'post_id': post.id
        })


@method_decorator(csrf_exempt, name='dispatch')
class UserOutfitsView(APIView):
    """Список постов пользователя"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request, telegram_id):
        try:
            user = TelegramUser.objects.get(telegram_id=telegram_id)
        except TelegramUser.DoesNotExist:
            return Response({'error': 'User not found'}, status=404)

        posts = OutfitPost.objects.filter(user=user)

        return Response({
            'posts': [
                {
                    'id': p.id,
                    'final_image': p.final_image.url if p.final_image else None,
                    'description': p.description,
                    'likes_count': p.likes_count,
                    'created_at': p.created_at.strftime('%Y-%m-%d'),
                    'hashtags': [{'tag': tag.tag} for tag in p.hashtags.all()],
                }
                for p in posts
            ]
        })