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
from django.core.files.storage import default_storage
from django.db.models import Q

from .models import TelegramUser, Follow
from .utils import verify_telegram_init_data
import random


def generate_random_color() -> str:
    return '#{:06x}'.format(random.randint(0, 0xFFFFFF))


# ── Authorize ─────────────────────────────────────────────────────────────────

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


# ── Search users ──────────────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class SearchUsersView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)

        query = request.GET.get('q', '').strip()

        # Strip leading @ so "@username" and "username" both work
        if query.startswith('@'):
            query = query[1:]

        if not query:
            return Response({'users': []})

        users = TelegramUser.objects.filter(
            Q(username__icontains=query) |
            Q(first_name__icontains=query) |
            Q(last_name__icontains=query)
        ).exclude(telegram_id=current_user.telegram_id)[:20]

        following_ids = set(
            current_user.following.values_list('following__telegram_id', flat=True)
        )

        result = []
        for u in users:
            result.append({
                'telegram_id': u.telegram_id,
                'username': u.username or '',
                'first_name': u.first_name or '',
                'last_name': u.last_name or '',
                'avatar_url': u.avatar.url if u.avatar else None,
                'avatar_color': u.avatar_random_color or '#cccccc',
                'is_following': u.telegram_id in following_ids,
            })

        return Response({'users': result})


# ── Follow toggle ─────────────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class FollowToggleView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)

        target_id = request.data.get('telegram_id')
        if not target_id:
            return Response({'error': 'telegram_id required'}, status=400)

        try:
            target = TelegramUser.objects.get(telegram_id=target_id)
        except TelegramUser.DoesNotExist:
            return Response({'error': 'User not found'}, status=404)

        if target.telegram_id == current_user.telegram_id:
            return Response({'error': 'Cannot follow yourself'}, status=400)

        follow_qs = Follow.objects.filter(follower=current_user, following=target)
        if follow_qs.exists():
            follow_qs.delete()
            return Response({'status': 'unfollowed'})
        else:
            Follow.objects.create(follower=current_user, following=target)
            return Response({'status': 'followed'})


# ── Avatar update ─────────────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class UpdateAvatarView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        user = get_current_user(request)
        if not user:
            return JsonResponse({'error': 'Not authorized'}, status=401)

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
        if user.avatar and user.avatar.name:
            if default_storage.exists(user.avatar.name):
                default_storage.delete(user.avatar.name)
            user.avatar = None
            user.save()

    def _update_color(self, request, user):
        color = request.data.get('color')
        if not color or not color.startswith('#'):
            return JsonResponse({'error': 'Invalid color'}, status=400)
        user.avatar_random_color = color
        user.save()
        return JsonResponse({'status': 'ok', 'color': color})

    def _update_from_telegram(self, user):
        self._delete_old_avatar(user)
        bot_token = settings.TG_BOT_TOKEN
        response = requests.get(
            f"https://api.telegram.org/bot{bot_token}/getUserProfilePhotos",
            params={'user_id': user.telegram_id, 'limit': 1}
        )
        data = response.json()
        if not data.get('ok') or not data['result']['total_count']:
            return JsonResponse({'error': 'No avatar found in Telegram'}, status=404)
        file_id = data['result']['photos'][0][-1]['file_id']
        file_resp = requests.get(
            f"https://api.telegram.org/bot{bot_token}/getFile",
            params={'file_id': file_id}
        ).json()
        if not file_resp.get('ok'):
            return JsonResponse({'error': 'Failed to get file info'}, status=500)
        file_path = file_resp['result']['file_path']
        img_resp = requests.get(
            f"https://api.telegram.org/file/bot{bot_token}/{file_path}", stream=True
        )
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
        file = request.FILES.get('avatar')
        if not file:
            return JsonResponse({'error': 'No file'}, status=400)
        try:
            self._delete_old_avatar(user)
            img = Image.open(file)
            min_side = min(img.size)
            left = (img.width - min_side) / 2
            top = (img.height - min_side) / 2
            img = img.crop((left, top, left + min_side, top + min_side))
            img = img.resize((200, 200), Image.Resampling.LANCZOS)
            buffer = BytesIO()
            img.save(buffer, format='WebP', quality=80)
            buffer.seek(0)
            user.avatar.save(f'avatar_{user.telegram_id}.webp', buffer, save=True)
            return JsonResponse({'status': 'ok', 'avatar_url': user.avatar.url})
        except Exception as e:
            print(e)
            return JsonResponse({'error': 'Invalid image'}, status=400)

    def _delete_avatar(self, user):
        try:
            self._delete_old_avatar(user)
            user.save()
            return JsonResponse({'status': 'ok', 'message': 'Avatar deleted successfully'})
        except Exception as e:
            print(f"Error deleting avatar: {e}")
            return JsonResponse({'error': 'Failed to delete avatar'}, status=500)


# ── Helpers ───────────────────────────────────────────────────────────────────

def get_current_user(request):
    telegram_id = request.session.get('telegram_id')
    return TelegramUser.objects.filter(telegram_id=telegram_id).first() if telegram_id else None


# ── Page views ────────────────────────────────────────────────────────────────

def home_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'home.html', {'user': user})


def search_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'search.html', {'user': user})


def messages_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    return render(request, 'messages.html', {'telegram_id': user.telegram_id})


def profile_view(request):
    user = get_current_user(request)
    if not user:
        return redirect('/authorize/')
    context = {
        'user': user,
        'following_count': user.following.count(),
        'followers_count': user.followers.count(),
        'posts_count': user.posts.count(),
    }
    return render(request, 'profile.html', context)


def user_profile_view(request, telegram_id):
    current_user = get_current_user(request)
    if not current_user:
        return redirect('/authorize/')

    # Own profile → redirect
    if current_user.telegram_id == telegram_id:
        return redirect('/profile/')

    try:
        profile_user = TelegramUser.objects.get(telegram_id=telegram_id)
    except TelegramUser.DoesNotExist:
        return redirect('/search/')

    is_following = Follow.objects.filter(
        follower=current_user, following=profile_user
    ).exists()

    context = {
        'user': current_user,
        'profile_user': profile_user,
        'following_count': profile_user.following.count(),
        'followers_count': profile_user.followers.count(),
        'posts_count': profile_user.posts.count(),
        'is_following': is_following,
    }
    return render(request, 'user_profile.html', context)


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
    context = {'user': user}
    if request.headers.get('X-Requested-With') == 'XMLHttpRequest':
        return render(request, 'avatar.html', context)
    return render(request, 'base.html', context)