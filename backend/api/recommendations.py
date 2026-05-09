# backend/api/recommendations.py
from django.db.models import Count, Q, F
from datetime import timedelta
from django.utils import timezone

class OutfitRecommender:
    """Алгоритм рекомендаций образов"""
    
    def __init__(self, user):
        self.user = user
    
    def get_recommendations(self, limit=20):
        """Персонализированные рекомендации"""
        
        # 1. Собираем данные о предпочтениях пользователя
        user_preferences = self._get_user_preferences()
        
        # 2. Находим похожих пользователей (collaborative filtering)
        similar_users = self._find_similar_users()
        
        # 3. Получаем кандидатов для рекомендаций
        candidates = self._get_candidates(user_preferences, similar_users)
        
        # 4. Ранжируем по релевантности
        ranked = self._rank_outfits(candidates, user_preferences)
        
        return ranked[:limit]
    
    def _get_user_preferences(self):
        """Анализ предпочтений пользователя"""
        # Анализируем лайкнутые образы
        liked_outfits = OutfitLike.objects.filter(user=self.user).values_list('outfit_id', flat=True)
        
        # Извлекаем популярные теги
        tag_counts = ClothingTag.objects.filter(
            outfits__id__in=liked_outfits
        ).annotate(
            count=Count('outfits')
        ).order_by('-count')[:10]
        
        # Извлекаем авторов, на которых подписан
        following = self.user.following.values_list('following_id', flat=True)
        
        return {
            'liked_tags': [tag.id for tag in tag_counts],
            'following': list(following),
            'liked_outfits': list(liked_outfits),
        }
    
    def _find_similar_users(self):
        """Находим пользователей с похожими вкусами"""
        # Пользователи, которые лайкали те же образы
        my_likes = OutfitLike.objects.filter(user=self.user).values_list('outfit_id', flat=True)
        
        similar_users = OutfitLike.objects.filter(
            outfit_id__in=my_likes
        ).exclude(
            user=self.user
        ).values('user_id').annotate(
            common_likes=Count('outfit_id')
        ).filter(
            common_likes__gte=3  # минимум 3 общих лайка
        ).order_by('-common_likes')[:50]
        
        return [u['user_id'] for u in similar_users]
    
    def _get_candidates(self, preferences, similar_users):
        """Получаем кандидатов для рекомендаций"""
        from api.models import Outfit
        
        # Исключаем уже просмотренные
        viewed_ids = OutfitView.objects.filter(user=self.user).values_list('outfit_id', flat=True)
        
        candidates = Outfit.objects.filter(
            is_public=True
        ).exclude(
            id__in=viewed_ids
        ).exclude(
            user=self.user
        )
        
        # Фильтруем по тегам или авторам
        candidates = candidates.filter(
            Q(tags__id__in=preferences['liked_tags']) |
            Q(user_id__in=similar_users) |
            Q(user_id__in=preferences['following'])
        ).distinct()
        
        # Сортируем по популярности (свежие + популярные)
        week_ago = timezone.now() - timedelta(days=7)
        candidates = candidates.annotate(
            score=F('likes_count') + F('views_count') / 10
        ).filter(
            created_at__gte=week_ago
        ).order_by('-score', '-created_at')
        
        return candidates[:100]
    
    def _rank_outfits(self, outfits, preferences):
        """Ранжируем образы по релевантности"""
        scored = []
        
        for outfit in outfits:
            score = 0
            
            # Бонус за совпадение тегов
            matching_tags = outfit.tags.filter(id__in=preferences['liked_tags']).count()
            score += matching_tags * 10
            
            # Бонус за подписку на автора
            if outfit.user_id in preferences['following']:
                score += 50
            
            # Бонус за популярность
            score += outfit.likes_count * 2
            score += outfit.views_count * 0.5
            
            # Штраф за старые образы
            days_old = (timezone.now() - outfit.created_at).days
            score -= days_old * 0.5
            
            scored.append((outfit, score))
        
        # Сортируем по score
        scored.sort(key=lambda x: x[1], reverse=True)
        
        return [outfit for outfit, _ in scored]


# Использование в API
def get_recommended_outfits(user, limit=20):
    recommender = OutfitRecommender(user)
    return recommender.get_recommendations(limit)