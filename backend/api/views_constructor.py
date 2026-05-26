import json
from django.db.models import Q
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny
from .models import (
    ClothingCategory, ClothingItem, OutfitPost, PostClothingItem,
    Hashtag, TelegramUser, PostLike, PostComment, Notification, CommentLike,
    Mannequin,
)


def get_current_user(request):
    tid = request.session.get('telegram_id')
    return TelegramUser.objects.filter(telegram_id=tid).first() if tid else None


# ═══════════════════════════════════════════════════════════════════════════════
#  CLOTHING CONSTRUCTOR API
# ═══════════════════════════════════════════════════════════════════════════════

@method_decorator(csrf_exempt, name='dispatch')
class MannequinView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        mannequins = Mannequin.objects.all()
        return Response({
            'mannequins': [
                {
                    'gender': m.gender,
                    'display_name': m.get_gender_display(),
                    'image_url': request.build_absolute_uri(m.image.url) if m.image else None,
                }
                for m in mannequins
            ]
        })


@method_decorator(csrf_exempt, name='dispatch')
class ClothingCategoriesView(APIView):
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
                    'icon_svg_url': c.icon_svg.url if c.icon_svg else None,
                }
                for c in categories
            ]
        })


@method_decorator(csrf_exempt, name='dispatch')
class ClothingItemsView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        category = request.GET.get('category')
        gender = request.GET.get('gender', 'male')
        style = request.GET.get('style')
        color = request.GET.get('color')

        qs = ClothingItem.objects.filter(category__name=category)
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
                for item in qs[:30]
            ]
        })


@method_decorator(csrf_exempt, name='dispatch')
class CreateOutfitView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        mannequin_type = request.data.get('mannequin_type', 'male')
        description = request.data.get('description', '').strip()
        final_image = request.FILES.get('final_image')

        # Support both JSON body and multipart form
        hashtags_raw = request.data.get('hashtags', '[]')
        items_raw = request.data.get('items', '[]')

        if isinstance(hashtags_raw, list):
            hashtags = hashtags_raw
        else:
            try:
                hashtags = json.loads(hashtags_raw)
            except (json.JSONDecodeError, TypeError):
                hashtags = []

        if isinstance(items_raw, list):
            items_data = items_raw
        else:
            try:
                items_data = json.loads(items_raw)
            except (json.JSONDecodeError, TypeError):
                items_data = []

        if not description:
            return Response({'error': 'Description required'}, status=400)

        if not items_data:
            return Response({'error': 'No items in outfit'}, status=400)

        post = OutfitPost.objects.create(
            user=user,
            mannequin_type=mannequin_type,
            description=description,
        )

        if final_image:
            post.final_image = final_image
            post.save(update_fields=['final_image'])

        for tag_name in hashtags:
            tag, _ = Hashtag.objects.get_or_create(tag=tag_name.lower())
            tag.usage_count += 1
            tag.save()
            post.hashtags.add(tag)

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

        return Response({'status': 'ok', 'post_id': post.id})


@method_decorator(csrf_exempt, name='dispatch')
class UserOutfitsView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request, telegram_id):
        try:
            user = TelegramUser.objects.get(telegram_id=telegram_id)
        except TelegramUser.DoesNotExist:
            return Response({'error': 'User not found'}, status=404)

        current_user = get_current_user(request)
        liked_post_ids = set()
        if current_user:
            liked_post_ids = set(
                PostLike.objects.filter(user=current_user)
                .values_list('post_id', flat=True)
            )

        posts = OutfitPost.objects.filter(user=user).select_related('user').prefetch_related(
            'hashtags', 'items__clothing__category'
        )

        return Response({
            'posts': [_serialize_post(p, liked_post_ids) for p in posts]
        })


@method_decorator(csrf_exempt, name='dispatch')
class PostLikeView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request, post_id):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        try:
            post = OutfitPost.objects.get(id=post_id)
        except OutfitPost.DoesNotExist:
            return Response({'error': 'Post not found'}, status=404)

        like, created = PostLike.objects.get_or_create(post=post, user=user)

        if not created:
            like.delete()
            post.likes_count = max(0, post.likes_count - 1)
            liked = False
        else:
            post.likes_count += 1
            liked = True

        post.save(update_fields=['likes_count'])

        # Notification
        if liked and post.user != user:
            Notification.objects.get_or_create(
                recipient=post.user, sender=user, notif_type='like', post=post,
            )
        elif not liked:
            Notification.objects.filter(
                recipient=post.user, sender=user, notif_type='like', post=post,
            ).delete()

        return Response({'liked': liked, 'likes_count': post.likes_count})


@method_decorator(csrf_exempt, name='dispatch')
class PostCommentsView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request, post_id):
        try:
            post = OutfitPost.objects.get(id=post_id)
        except OutfitPost.DoesNotExist:
            return Response({'error': 'Post not found'}, status=404)

        current_user = get_current_user(request)
        liked_comment_ids = set()
        if current_user:
            liked_comment_ids = set(
                CommentLike.objects
                .filter(user=current_user, comment__post=post)
                .values_list('comment_id', flat=True)
            )

        comments = post.comments.select_related('user').order_by('created_at')

        return Response({
            'comments': [
                {
                    'id': c.id,
                    'text': c.text,
                    'created_at': c.created_at.strftime('%Y-%m-%d %H:%M'),
                    'likes_count': c.likes_count,
                    'is_liked': c.id in liked_comment_ids,
                    'user': {
                        'telegram_id': c.user.telegram_id,
                        'username': c.user.username,
                        'first_name': c.user.first_name,
                        'avatar': c.user.avatar.url if c.user.avatar else None,
                        'avatar_color': c.user.avatar_random_color,
                    },
                }
                for c in comments
            ]
        })

    def post(self, request, post_id):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        try:
            post = OutfitPost.objects.get(id=post_id)
        except OutfitPost.DoesNotExist:
            return Response({'error': 'Post not found'}, status=404)

        text = request.data.get('text', '').strip()
        if not text:
            return Response({'error': 'Text required'}, status=400)

        comment = PostComment.objects.create(post=post, user=user, text=text)

        # Notification (don't notify yourself)
        if post.user != user:
            Notification.objects.create(
                recipient=post.user, sender=user,
                notif_type='comment', post=post, comment=comment,
            )

        return Response({
            'id': comment.id,
            'text': comment.text,
            'created_at': comment.created_at.strftime('%Y-%m-%d %H:%M'),
            'user': {
                'telegram_id': user.telegram_id,
                'username': user.username,
                'first_name': user.first_name,
                'avatar': user.avatar.url if user.avatar else None,
                'avatar_color': user.avatar_random_color,
            },
        })


@method_decorator(csrf_exempt, name='dispatch')
class CommentLikeView(APIView):
    """Лайк/анлайк комментария"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request, post_id, comment_id):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        try:
            comment = PostComment.objects.get(id=comment_id, post_id=post_id)
        except PostComment.DoesNotExist:
            return Response({'error': 'Comment not found'}, status=404)

        like, created = CommentLike.objects.get_or_create(comment=comment, user=user)

        if not created:
            like.delete()
            comment.likes_count = max(0, comment.likes_count - 1)
            liked = False
        else:
            comment.likes_count += 1
            liked = True

        comment.save(update_fields=['likes_count'])
        return Response({'liked': liked, 'likes_count': comment.likes_count})


@method_decorator(csrf_exempt, name='dispatch')
class SearchPostsView(APIView):
    """Поиск постов по хештегу или описанию"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        q = request.GET.get('q', '').strip().lstrip('#')
        if not q:
            return Response({'posts': []})

        current_user = get_current_user(request)
        liked_ids = set()
        if current_user:
            liked_ids = set(
                PostLike.objects.filter(user=current_user)
                .values_list('post_id', flat=True)
            )

        posts = OutfitPost.objects.filter(
            Q(hashtags__tag__icontains=q) | Q(description__icontains=q)
        ).distinct().select_related('user').prefetch_related(
            'hashtags', 'items__clothing__category'
        )[:30]

        return Response({
            'posts': [_serialize_post(p, liked_ids) for p in posts]
        })


def _serialize_post(p, liked_ids):
    outfit_items = []
    seen_clothing_ids = set()
    for pi in p.items.all():
        c = pi.clothing
        if c.id in seen_clothing_ids:
            continue
        seen_clothing_ids.add(c.id)
        outfit_items.append({
            'name': c.name,
            'image': c.image.url if c.image else None,
            'style': c.get_style_display() if c.style else '',
            'color': c.color,
            'category': c.category.get_name_display() if c.category_id else '',
            'item_description': c.item_description,
            'buy_link': c.buy_link or '',
        })

    return {
        'id': p.id,
        'final_image': p.final_image.url if p.final_image else None,
        'description': p.description,
        'likes_count': p.likes_count,
        'comments_count': p.comments.count(),
        'is_liked': p.id in liked_ids,
        'hashtags': [{'tag': t.tag} for t in p.hashtags.all()],
        'outfit_items': outfit_items,
        'user': {
            'telegram_id': p.user.telegram_id,
            'username': p.user.username,
            'first_name': p.user.first_name,
            'avatar': p.user.avatar.url if p.user.avatar else None,
            'avatar_color': p.user.avatar_random_color,
        },
    }


@method_decorator(csrf_exempt, name='dispatch')
class DeleteOutfitView(APIView):
    """Удаление поста владельцем"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def delete(self, request, post_id):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        try:
            post = OutfitPost.objects.get(id=post_id, user=user)
        except OutfitPost.DoesNotExist:
            return Response({'error': 'Not found or not yours'}, status=404)

        # Удаляем файл изображения
        if post.final_image:
            try:
                post.final_image.delete(save=False)
            except Exception:
                pass

        post.delete()  # каскадно удаляет items, likes, comments, m2m hashtags
        return Response({'status': 'ok'})


@method_decorator(csrf_exempt, name='dispatch')
class HomeFeedView(APIView):
    """Рекомендательная лента для главной страницы"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        # Light check: just count posts newer than since_id
        since_id = request.GET.get('since_id')
        if since_id:
            try:
                count = (
                    OutfitPost.objects
                    .filter(id__gt=int(since_id))
                    .exclude(user=user)
                    .count()
                )
            except (ValueError, TypeError):
                count = 0
            return Response({'new_count': count})

        import random

        following_ids = set(
            user.following.values_list('following__telegram_id', flat=True)
        )

        base_qs = (
            OutfitPost.objects
            .select_related('user')
            .prefetch_related('hashtags', 'items__clothing__category')
            .exclude(user=user)
        )

        if following_ids:
            following_posts = list(
                base_qs.filter(user__telegram_id__in=following_ids)
                .order_by('-created_at')[:20]
            )
            popular_posts = list(
                base_qs.exclude(user__telegram_id__in=following_ids)
                .order_by('-likes_count', '-created_at')[:20]
            )
            # Чередуем подписки и популярные
            feed = []
            fi, pi = 0, 0
            while fi < len(following_posts) or pi < len(popular_posts):
                if fi < len(following_posts):
                    feed.append(following_posts[fi]); fi += 1
                if pi < len(popular_posts):
                    feed.append(popular_posts[pi]); pi += 1
        else:
            feed = list(base_qs.order_by('-likes_count', '-created_at')[:40])

        # Лёгкое перемешивание нижней половины
        if len(feed) > 6:
            top = feed[:3]
            rest = feed[3:]
            random.shuffle(rest)
            feed = top + rest

        # Дедупликация
        seen = set()
        unique = []
        for p in feed:
            if p.id not in seen:
                seen.add(p.id)
                unique.append(p)

        liked_ids = set(
            PostLike.objects.filter(user=user)
            .values_list('post_id', flat=True)
        )

        return Response({
            'posts': [_serialize_post(p, liked_ids) for p in unique[:40]]
        })


@method_decorator(csrf_exempt, name='dispatch')
class NotificationListView(APIView):
    """Список уведомлений + пометить прочитанными"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)

        notifs = (
            Notification.objects
            .filter(recipient=user)
            .select_related('sender', 'post', 'comment')[:50]
        )
        unread_count = Notification.objects.filter(recipient=user, is_read=False).count()

        result = []
        for n in notifs:
            d = {
                'id': n.id,
                'type': n.notif_type,
                'is_read': n.is_read,
                'created_at': n.created_at.strftime('%d.%m %H:%M'),
                'sender': {
                    'telegram_id': n.sender.telegram_id,
                    'first_name': n.sender.first_name,
                    'username': n.sender.username,
                    'avatar': n.sender.avatar.url if n.sender.avatar else None,
                    'avatar_color': n.sender.avatar_random_color,
                },
            }
            if n.post:
                d['post'] = {
                    'id': n.post.id,
                    'image': n.post.final_image.url if n.post.final_image else None,
                }
            if n.comment:
                d['comment'] = {
                    'id': n.comment.id,
                    'text': n.comment.text[:80],
                }
            result.append(d)

        return Response({'unread_count': unread_count, 'notifications': result})

    def post(self, request):
        """Mark all as read"""
        user = get_current_user(request)
        if not user:
            return Response({'error': 'Not authorized'}, status=401)
        Notification.objects.filter(recipient=user, is_read=False).update(is_read=True)
        return Response({'status': 'ok'})


@method_decorator(csrf_exempt, name='dispatch')
class PostDetailView(APIView):
    """Детали одного поста по ID"""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request, post_id):
        current_user = get_current_user(request)
        liked_ids = set()
        if current_user:
            liked_ids = set(
                PostLike.objects.filter(user=current_user)
                .values_list('post_id', flat=True)
            )
        try:
            post = (
                OutfitPost.objects
                .select_related('user')
                .prefetch_related('hashtags', 'items__clothing__category')
                .get(id=post_id)
            )
        except OutfitPost.DoesNotExist:
            return Response({'error': 'Not found'}, status=404)
        return Response({'post': _serialize_post(post, liked_ids)})
