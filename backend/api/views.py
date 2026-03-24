from django.shortcuts import render
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from .models import TelegramUser
from .serializers import TelegramUserSerializer
import traceback

class AuthorizeView(APIView):
    def post(self, request):
        try:
            telegram_id = request.data.get('telegram_id')
            username = request.data.get('username')
            first_name = request.data.get('first_name')
            last_name = request.data.get('last_name')
            language_code = request.data.get('language_code')
            
            if not telegram_id:
                return Response(
                    {'error': 'telegram_id is required'},
                    status=status.HTTP_400_BAD_REQUEST
                )
            
            # Проверяем, существует ли пользователь
            user, created = TelegramUser.objects.get_or_create(
                telegram_id=telegram_id,
                defaults={
                    'username': username,
                    'first_name': first_name,
                    'last_name': last_name,
                    'language_code': language_code,
                }
            )
            
            return Response(
                {
                    'id': user.id,
                    'telegram_id': user.telegram_id,
                    'created': created
                },
                status=status.HTTP_200_OK
            )
        except Exception as e:
            traceback.print_exc()
            return Response(
                {'error': str(e)},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

def home_view(request, telegram_id):
    return render(request, 'home.html', {'telegram_id': telegram_id})

def search_view(request, telegram_id):
    return render(request, 'search.html', {'telegram_id': telegram_id})

def messages_view(request, telegram_id):
    return render(request, 'messages.html', {'telegram_id': telegram_id})

def profile_view(request, telegram_id):
    user = TelegramUser.objects.get(telegram_id=telegram_id)
    return render(request, 'profile.html', {'user': user, 'telegram_id': telegram_id})

def create_view(request, telegram_id):
    return render(request, 'create.html', {'telegram_id': telegram_id})

def authorize_view(request):
    return render(request, 'authorize.html')