import json
import os
import requests
from django.shortcuts import render, redirect
from django.views.decorators.csrf import csrf_exempt
from django.utils.decorators import method_decorator
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny
from django.conf import settings
from django.http import JsonResponse
from PIL import Image
from io import BytesIO
from django.views.decorators.cache import never_cache
from django.core.files.storage import default_storage
from django.core.files.base import ContentFile

from .models import TelegramUser
from .utils import verify_telegram_init_data
import random

def generate_random_color() -> str:
    """Генерирует случайный HEX-цвет (например, #a1b2c3)."""
    return '#{:06x}'.format(random.randint(0, 0xFFFFFF))

@method_decorator(csrf_exempt, name='dispatch')
class AuthorizeView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        try:
            init_data = request.data.get('initData')
            if not init_data:
                return Response({'error': 'initData required'}, status=400)

            data = verify_telegram_init_data(init_data, settings.TG_BOT_TOKEN)
            if not data:
                return Response({'error': 'Invalid Telegram data'}, status=403)

            user_raw = data.get('user')
            if not user_raw:
                return Response({'error': 'No user data'}, status=400)

            user_data = json.loads(user_raw) if isinstance(user_raw, str) else user_raw

            telegram_id = user_data.get('id')
            
            user, created = TelegramUser.objects.get_or_create(
                telegram_id=telegram_id,
                defaults={
                    'username': user_data.get('username'),
                    'first_name': user_data.get('first_name'),
                    'last_name': user_data.get('last_name'),
                    'language_code': user_data.get('language_code'),
                    'avatar_random_color': generate_random_color(),
                }
            )

            if not created:
                user.username = user_data.get('username')
                user.first_name = user_data.get('first_name')
                user.last_name = user_data.get('last_name')
                user.language_code = user_data.get('language_code')
                user.save()

            request.session['telegram_id'] = telegram_id
            request.session.save()

            return Response({'status': 'ok'}, status=200)

        except Exception as e:
            print("CRITICAL ERROR:", str(e))
            return Response({'error': 'Server error'}, status=500)
        
@method_decorator(csrf_exempt, name='dispatch')
class UpdateAvatarView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        user = get_current_user(request)
        if not user:
            return JsonResponse({'error': 'Not authorized'}, status=401)

        # Определяем тип операции
        update_type = request.GET.get('type') or request.data.get('type')
        
        if update_type == 'color':
            return self._update_color(request, user)
        elif update_type == 'telegram':
            return self._update_from_telegram(user)
        elif update_type == 'upload':
            return self._upload_avatar(request, user)
        elif update_type == 'delete':
            return self._delete_avatar(user)
        else:
            return JsonResponse({'error': 'Invalid update type'}, status=400)

    def _delete_old_avatar(self, user):
        """Удаляет старый файл аватара и сбрасывает поле"""
        if user.avatar and user.avatar.name:
            if default_storage.exists(user.avatar.name):
                default_storage.delete(user.avatar.name)
                print(f"Deleted old avatar: {user.avatar.name}")
            user.avatar = None
            user.save()   # ← БАГ: раньше этого не было — путь оставался в БД

    def _update_color(self, request, user):
        """Обновление цвета аватара"""
        color = request.data.get('color')
        
        if not color or not color.startswith('#'):
            return JsonResponse({'error': 'Invalid color'}, status=400)
        
        user.avatar_random_color = color
        user.save()
        
        return JsonResponse({'status': 'ok', 'color': color})

    def _update_from_telegram(self, user):
        """Загрузка аватара из Telegram"""
        # Удаляем старый аватар
        self._delete_old_avatar(user)
        
        bot_token = settings.TG_BOT_TOKEN
        
        # Получаем фото профиля
        url = f"https://api.telegram.org/bot{bot_token}/getUserProfilePhotos"
        params = {'user_id': user.telegram_id, 'limit': 1}
        response = requests.get(url, params=params)
        data = response.json()
        
        if not data.get('ok') or not data['result']['total_count']:
            return JsonResponse({'error': 'No avatar found in Telegram'}, status=404)
        
        # Получаем самое большое фото
        photos = data['result']['photos'][0]
        largest = photos[-1]
        file_id = largest['file_id']
        
        # Получаем путь к файлу
        file_url = f"https://api.telegram.org/bot{bot_token}/getFile"
        file_params = {'file_id': file_id}
        file_resp = requests.get(file_url, params=file_params)
        file_data = file_resp.json()
        
        if not file_data.get('ok'):
            return JsonResponse({'error': 'Failed to get file info'}, status=500)
        
        file_path = file_data['result']['file_path']
        download_url = f"https://api.telegram.org/file/bot{bot_token}/{file_path}"
        
        # Скачиваем изображение
        img_resp = requests.get(download_url, stream=True)
        if img_resp.status_code != 200:
            return JsonResponse({'error': 'Failed to download image'}, status=500)
        
        img = Image.open(img_resp.raw)
        img.thumbnail((200, 200), Image.Resampling.LANCZOS)
        buffer = BytesIO()
        img.save(buffer, format='WebP', quality=80)
        buffer.seek(0)
        
        user.avatar.save(f'avatar_{user.telegram_id}.webp', buffer, save=True)
        
        return JsonResponse({'status': 'ok', 'avatar_url': user.avatar.url})

    def _upload_avatar(self, request, user):
        """Загрузка аватара из галереи"""
        file = request.FILES.get('avatar')
        if not file:
            return JsonResponse({'error': 'No file'}, status=400)
        
        try:
            # Удаляем старый аватар
            self._delete_old_avatar(user)
            
            img = Image.open(file)
            
            # Обрезаем квадрат по центру
            min_side = min(img.size)
            left = (img.width - min_side) / 2
            top = (img.height - min_side) / 2
            right = (img.width + min_side) / 2
            bottom = (img.height + min_side) / 2
            
            img = img.crop((left, top, right, bottom))
            img = img.resize((200, 200), Image.Resampling.LANCZOS)
            
            buffer = BytesIO()
            img.save(buffer, format='WebP', quality=80)
            buffer.seek(0)
            
            user.avatar.save(f'avatar_{user.telegram_id}.webp', buffer, save=True)
            
            return JsonResponse({
                'status': 'ok',
                'avatar_url': user.avatar.url
            })
            
        except Exception as e:
            print(e)
            return JsonResponse({'error': 'Invalid image'}, status=400)
    
    def _delete_avatar(self, user):
        """Полное удаление аватара"""
        try:
            # Удаляем файл
            self._delete_old_avatar(user)
            # Сохраняем изменения
            user.save()
            
            return JsonResponse({
                'status': 'ok',
                'message': 'Avatar deleted successfully'
            })
        except Exception as e:
            print(f"Error deleting avatar: {e}")
            return JsonResponse({'error': 'Failed to delete avatar'}, status=500)
        
def get_current_user(request):
    telegram_id = request.session.get('telegram_id')
    return TelegramUser.objects.filter(telegram_id=telegram_id).first() if telegram_id else None

def home_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'home.html', {'user': user})

def search_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'search.html', {'telegram_id': user.telegram_id})

def messages_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'messages.html', {'telegram_id': user.telegram_id})

def profile_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    
    following_count = user.following.count()
    followers_count = user.followers.count()
    posts_count = user.posts.count()

    context = {
        'user': user,
        'following_count': following_count,
        'followers_count': followers_count,
        'posts_count': posts_count,
    }
    return render(request, 'profile.html', context)

def create_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'create.html', {'telegram_id': user.telegram_id})

def authorize_view(request):
    return render(request, 'authorize.html')

def avatar_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')

    context = {
        'user': user
    }

    if request.headers.get('X-Requested-With') == 'XMLHttpRequest':
        return render(request, 'avatar.html', context)

    return render(request, 'base.html', context)