import json
import requests
from django.shortcuts import render, redirect, get_object_or_404
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
from django.utils import timezone

from .models import TelegramUser, Follow, Dialog, Message
from .utils import verify_telegram_init_data
import random


def generate_random_color():
    return '#{:06x}'.format(random.randint(0, 0xFFFFFF))


def serialize_user(u):
    return {
        'telegram_id': u.telegram_id,
        'username': u.username or '',
        'first_name': u.first_name or '',
        'last_name': u.last_name or '',
        'avatar_url': u.avatar.url if u.avatar else None,
        'avatar_color': u.avatar_random_color or '#cccccc',
    }


def get_or_create_dialog(user_a, user_b):
    u1, u2 = (user_a, user_b) if user_a.telegram_id < user_b.telegram_id else (user_b, user_a)
    dialog, _ = Dialog.objects.get_or_create(user1=u1, user2=u2)
    return dialog


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


# ── Search ────────────────────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class SearchUsersView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        query = request.GET.get('q', '').strip()
        if query.startswith('@'):
            query = query[1:]
        if not query:
            return Response({'users': []})
        users = TelegramUser.objects.filter(
            Q(username__icontains=query) | Q(first_name__icontains=query) | Q(last_name__icontains=query)
        ).exclude(telegram_id=current_user.telegram_id)[:20]
        following_ids = set(current_user.following.values_list('following__telegram_id', flat=True))
        result = []
        for u in users:
            d = serialize_user(u)
            d['is_following'] = u.telegram_id in following_ids
            result.append(d)
        return Response({'users': result})


# ── Follow ────────────────────────────────────────────────────────────────────

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
        qs = Follow.objects.filter(follower=current_user, following=target)
        if qs.exists():
            qs.delete()
            return Response({'status': 'unfollowed'})
        Follow.objects.create(follower=current_user, following=target)
        return Response({'status': 'followed'})


# ── Connections (Following/Followers/Friends) ─────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class ConnectionsView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request, connection_type):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)

        if connection_type == 'following':
            users = TelegramUser.objects.filter(
                followers__follower=current_user
            ).distinct()
        elif connection_type == 'followers':
            users = TelegramUser.objects.filter(
                following__following=current_user
            ).distinct()
        elif connection_type == 'friends':
            following_ids = set(current_user.following.values_list('following__telegram_id', flat=True))
            followers_ids = set(current_user.followers.values_list('follower__telegram_id', flat=True))
            mutual_ids = following_ids & followers_ids
            users = TelegramUser.objects.filter(telegram_id__in=mutual_ids)
        else:
            return Response({'error': 'Invalid connection type'}, status=400)

        current_following_ids = set(current_user.following.values_list('following__telegram_id', flat=True))

        result = []
        for u in users:
            d = serialize_user(u)
            d['is_following'] = u.telegram_id in current_following_ids
            result.append(d)

        return Response({'users': result})


# ── Dialogs list ──────────────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class DialogListView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)

        dialogs = Dialog.objects.filter(
            Q(user1=current_user) | Q(user2=current_user)
        ).select_related('user1', 'user2').order_by('-updated_at')

        result = []
        for d in dialogs:
            other = d.get_other_user(current_user)
            last_msg = d.messages.order_by('-created_at').first()
            unread = d.messages.filter(is_read=False).exclude(sender=current_user).count()
            result.append({
                'dialog_id': d.id,
                'other_user': serialize_user(other),
                'last_message': {
                    'text': last_msg.text,
                    'time': last_msg.created_at.strftime('%H:%M'),
                    'is_mine': last_msg.sender == current_user,
                } if last_msg else None,
                'unread_count': unread,
            })
        return Response({'dialogs': result})


# ── Start dialog (DEPRECATED - kept for backward compatibility) ───────────────

@method_decorator(csrf_exempt, name='dispatch')
class StartDialogView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        target_id = request.data.get('telegram_id')
        if not target_id:
            return Response({'error': 'telegram_id required'}, status=400)
        if int(target_id) == current_user.telegram_id:
            return Response({'error': 'Cannot message yourself'}, status=400)
        try:
            target = TelegramUser.objects.get(telegram_id=target_id)
        except TelegramUser.DoesNotExist:
            return Response({'error': 'User not found'}, status=404)
        dialog = get_or_create_dialog(current_user, target)
        return Response({'dialog_id': dialog.id})


# ── ✅ NEW: Create dialog API ─────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class CreateDialogView(APIView):
    """
    API endpoint для создания нового диалога.
    Вызывается из JavaScript при отправке первого сообщения.
    """
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        
        target_telegram_id = request.data.get('target_telegram_id')
        if not target_telegram_id:
            return Response({'error': 'target_telegram_id required'}, status=400)
        
        if int(target_telegram_id) == current_user.telegram_id:
            return Response({'error': 'Cannot message yourself'}, status=400)
        
        try:
            target_user = TelegramUser.objects.get(telegram_id=target_telegram_id)
        except TelegramUser.DoesNotExist:
            return Response({'error': 'User not found'}, status=404)
        
        # Проверяем, может уже есть диалог
        dialog = Dialog.objects.filter(
            Q(user1=current_user, user2=target_user) |
            Q(user1=target_user, user2=current_user)
        ).first()
        
        # Если нет - создаем
        if not dialog:
            u1, u2 = (current_user, target_user) if current_user.telegram_id < target_user.telegram_id else (target_user, current_user)
            dialog = Dialog.objects.create(user1=u1, user2=u2)
        
        return Response({
            'dialog_id': dialog.id,
            'created': True
        })


# ── Dialog messages ───────────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class DialogMessagesView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def _get_dialog(self, request, dialog_id):
        current_user = get_current_user(request)
        if not current_user:
            return None, None, Response({'error': 'Not authorized'}, status=401)
        try:
            dialog = Dialog.objects.get(
                Q(user1=current_user) | Q(user2=current_user),
                id=dialog_id
            )
        except Dialog.DoesNotExist:
            return None, None, Response({'error': 'Not found'}, status=404)
        return current_user, dialog, None

    def get(self, request, dialog_id):
        current_user, dialog, err = self._get_dialog(request, dialog_id)
        if err:
            return err

        after_id = request.GET.get('after')
        qs = dialog.messages.select_related('sender').order_by('created_at')
        if after_id:
            qs = qs.filter(id__gt=int(after_id))

        # mark incoming as read
        qs.filter(is_read=False).exclude(sender=current_user).update(is_read=True)

        return Response({
            'messages': [
                {
                    'id': m.id,
                    'text': m.text,
                    'time': m.created_at.strftime('%H:%M'),
                    'is_mine': m.sender == current_user,
                    'edited': m.edited,
                }
                for m in qs
            ]
        })

    def post(self, request, dialog_id):
        current_user, dialog, err = self._get_dialog(request, dialog_id)
        if err:
            return err

        text = request.data.get('text', '').strip()
        if not text:
            return Response({'error': 'Empty message'}, status=400)

        msg = Message.objects.create(dialog=dialog, sender=current_user, text=text)
        Dialog.objects.filter(id=dialog.id).update(updated_at=timezone.now())

        return Response({
            'id': msg.id,
            'text': msg.text,
            'time': msg.created_at.strftime('%H:%M'),
            'is_mine': True,
            'edited': False,
        }, status=201)


# ── Edit/Delete Message ───────────────────────────────────────────────────────

@method_decorator(csrf_exempt, name='dispatch')
class MessageDetailView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def _get_message(self, request, dialog_id, message_id):
        current_user = get_current_user(request)
        if not current_user:
            return None, None, Response({'error': 'Not authorized'}, status=401)
        
        try:
            dialog = Dialog.objects.get(
                Q(user1=current_user) | Q(user2=current_user),
                id=dialog_id
            )
            message = Message.objects.get(id=message_id, dialog=dialog)
            
            if message.sender != current_user:
                return None, None, Response({'error': 'Permission denied'}, status=403)
                
            return message, dialog, None
        except (Dialog.DoesNotExist, Message.DoesNotExist):
            return None, None, Response({'error': 'Not found'}, status=404)

    def put(self, request, dialog_id, message_id):
        """Edit message"""
        message, dialog, err = self._get_message(request, dialog_id, message_id)
        if err:
            return err

        new_text = request.data.get('text', '').strip()
        if not new_text:
            return Response({'error': 'Empty message'}, status=400)

        message.text = new_text
        message.edited = True
        message.save()

        return Response({'status': 'ok'})

    def delete(self, request, dialog_id, message_id):
        """Delete message"""
        message, dialog, err = self._get_message(request, dialog_id, message_id)
        if err:
            return err

        message.delete()
        return Response({'status': 'ok'})


# ── Avatar ────────────────────────────────────────────────────────────────────

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
        r = requests.get(f"https://api.telegram.org/bot{bot_token}/getUserProfilePhotos",
                         params={'user_id': user.telegram_id, 'limit': 1})
        data = r.json()
        if not data.get('ok') or not data['result']['total_count']:
            return JsonResponse({'error': 'No avatar found in Telegram'}, status=404)
        file_id = data['result']['photos'][0][-1]['file_id']
        fr = requests.get(f"https://api.telegram.org/bot{bot_token}/getFile",
                          params={'file_id': file_id}).json()
        if not fr.get('ok'):
            return JsonResponse({'error': 'Failed to get file info'}, status=500)
        img_resp = requests.get(f"https://api.telegram.org/file/bot{bot_token}/{fr['result']['file_path']}", stream=True)
        if img_resp.status_code != 200:
            return JsonResponse({'error': 'Failed to download image'}, status=500)
        img = Image.open(img_resp.raw)
        img.thumbnail((200, 200), Image.Resampling.LANCZOS)
        buf = BytesIO()
        img.save(buf, format='WebP', quality=80)
        buf.seek(0)
        user.avatar.save(f'avatar_{user.telegram_id}.webp', buf, save=True)
        return JsonResponse({'status': 'ok', 'avatar_url': user.avatar.url})

    def _upload_avatar(self, request, user):
        file = request.FILES.get('avatar')
        if not file:
            return JsonResponse({'error': 'No file'}, status=400)
        try:
            self._delete_old_avatar(user)
            img = Image.open(file)
            s = min(img.size)
            l = (img.width - s) / 2
            t = (img.height - s) / 2
            img = img.crop((l, t, l + s, t + s)).resize((200, 200), Image.Resampling.LANCZOS)
            buf = BytesIO()
            img.save(buf, format='WebP', quality=80)
            buf.seek(0)
            user.avatar.save(f'avatar_{user.telegram_id}.webp', buf, save=True)
            return JsonResponse({'status': 'ok', 'avatar_url': user.avatar.url})
        except Exception as e:
            print(e)
            return JsonResponse({'error': 'Invalid image'}, status=400)

    def _delete_avatar(self, user):
        try:
            self._delete_old_avatar(user)
            return JsonResponse({'status': 'ok'})
        except Exception as e:
            print(e)
            return JsonResponse({'error': 'Failed to delete avatar'}, status=500)


# ── Helpers ───────────────────────────────────────────────────────────────────

def get_current_user(request):
    tid = request.session.get('telegram_id')
    return TelegramUser.objects.filter(telegram_id=tid).first() if tid else None


# ── Page views ────────────────────────────────────────────────────────────────

def home_view(request):
    u = get_current_user(request)
    return render(request, 'home.html', {'user': u}) if u else redirect('/authorize/')

def search_view(request):
    u = get_current_user(request)
    return render(request, 'search.html', {'user': u}) if u else redirect('/authorize/')

def messages_view(request):
    u = get_current_user(request)
    return render(request, 'messages.html', {'user': u}) if u else redirect('/authorize/')


# ── ✅ NEW: Chat with user (no dialog yet) ────────────────────────────────────

def chat_with_user_view(request, telegram_id):
    """
    Показывает страницу чата с пользователем БЕЗ создания диалога.
    Диалог будет создан при отправке первого сообщения.
    """
    current_user = get_current_user(request)
    if not current_user:
        return redirect('/authorize/')
    
    target_user = get_object_or_404(TelegramUser, telegram_id=telegram_id)
    
    # Проверяем, может уже есть диалог?
    existing_dialog = Dialog.objects.filter(
        Q(user1=current_user, user2=target_user) |
        Q(user1=target_user, user2=current_user)
    ).first()
    
    context = {
        'user': current_user,
        'target_user': target_user,
        'dialog_id': existing_dialog.id if existing_dialog else None,
        'target_telegram_id': telegram_id,
    }
    
    return render(request, 'chat.html', context)


# ── Existing chat view ────────────────────────────────────────────────────────

def chat_view(request, dialog_id):
    current_user = get_current_user(request)
    if not current_user:
        return redirect('/authorize/')
    try:
        dialog = Dialog.objects.select_related('user1', 'user2').get(
            Q(user1=current_user) | Q(user2=current_user), id=dialog_id
        )
    except Dialog.DoesNotExist:
        return redirect('/messages/')
    
    other = dialog.get_other_user(current_user)
    
    return render(request, 'chat.html', {
        'user': current_user,
        'dialog_id': dialog.id,
        'target_user': other,
        'target_telegram_id': other.telegram_id,
    })


def profile_view(request):
    u = get_current_user(request)
    if not u:
        return redirect('/authorize/')
    return render(request, 'profile.html', {
        'user': u,
        'following_count': u.following.count(),
        'followers_count': u.followers.count(),
        'posts_count': u.posts.count(),
    })

def user_profile_view(request, telegram_id):
    current_user = get_current_user(request)
    if not current_user:
        return redirect('/authorize/')
    if current_user.telegram_id == telegram_id:
        return redirect('/profile/')
    try:
        profile_user = TelegramUser.objects.get(telegram_id=telegram_id)
    except TelegramUser.DoesNotExist:
        return redirect('/search/')
    is_following = Follow.objects.filter(follower=current_user, following=profile_user).exists()
    return render(request, 'user_profile.html', {
        'user': current_user,
        'profile_user': profile_user,
        'following_count': profile_user.following.count(),
        'followers_count': profile_user.followers.count(),
        'posts_count': profile_user.posts.count(),
        'is_following': is_following,
    })

def connections_view(request, connection_type):
    u = get_current_user(request)
    if not u:
        return redirect('/authorize/')
    
    if request.headers.get('X-Requested-With') != 'XMLHttpRequest':
        return redirect('/profile/')
    
    titles = {
        'following': 'Подписки',
        'followers': 'Подписчики',
        'friends': 'Друзья'
    }
    
    return render(request, 'connections.html', {
        'user': u,
        'active_tab': connection_type,
        'page_title': titles.get(connection_type, 'Связи'),
    })

def create_view(request):
    u = get_current_user(request)
    return render(request, 'create.html', {'telegram_id': u.telegram_id}) if u else redirect('/authorize/')

def authorize_view(request):
    return render(request, 'authorize.html')

def avatar_view(request):
    u = get_current_user(request)
    if not u:
        return redirect('/authorize/')
    
    if request.headers.get('X-Requested-With') != 'XMLHttpRequest':
        return redirect('/profile/')
    
    ctx = {'user': u}
    return render(request, 'avatar.html', ctx)

@method_decorator(csrf_exempt, name='dispatch')
class DialogActionView(APIView):
    """
    API для действий с диалогом: закрепить/удалить
    """
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request, dialog_id):
        current_user = get_current_user(request)
        if not current_user:
            return Response({'error': 'Not authorized'}, status=401)
        
        try:
            dialog = Dialog.objects.get(
                Q(user1=current_user) | Q(user2=current_user),
                id=dialog_id
            )
        except Dialog.DoesNotExist:
            return Response({'error': 'Dialog not found'}, status=404)
        
        action = request.data.get('action')
        
        if action == 'pin':
            # Переключаем состояние закрепления
            dialog.pinned = not dialog.pinned
            dialog.save()
            return Response({
                'status': 'ok',
                'pinned': dialog.pinned
            })
        
        elif action == 'delete':
            # Полное удаление диалога и всех сообщений (cascade)
            dialog.delete()
            return Response({'status': 'ok'})
        
        return Response({'error': 'Invalid action'}, status=400)