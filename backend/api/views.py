from django.shortcuts import render
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from .models import TelegramUser
from .serializers import TelegramUserSerializer
import traceback

def home_view(request):
    return render(request, 'home.html')

def search_view(request):
    return render(request, 'search.html')

def messages_view(request):
    return render(request, 'messages.html')

def profile_view(request):
    user = TelegramUser.objects.first()
    return render(request, 'profile.html', {"user": user})

def create_view(request):
    return render(request, 'create.html')

def authorize_view(request):
    return render(request, 'authorize.html')

class TelegramUserView(APIView):
    """
    Сохраняет или обновляет данные пользователя Telegram.
    """
    authentication_classes = []
    permission_classes = []

    def post(self, request):
        telegram_id = request.data.get('telegram_id')
        if not telegram_id:
            return Response({'error': 'telegram_id is required'}, status=status.HTTP_400_BAD_REQUEST)

        user, created = TelegramUser.objects.update_or_create(
            telegram_id=telegram_id,
            defaults={
                'username': request.data.get('username'),
                'first_name': request.data.get('first_name'),
                'last_name': request.data.get('last_name'),
            }
        )
        serializer = TelegramUserSerializer(user)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def get(self, request, telegram_id=None):
        try:
            if telegram_id:
                user = TelegramUser.objects.get(telegram_id=telegram_id)
                serializer = TelegramUserSerializer(user)
                return Response(serializer.data)
            else:
                return Response({'error': 'telegram_id required'}, status=400)
        except TelegramUser.DoesNotExist:
            return Response({'error': 'User not found'}, status=404)
        except Exception as e:
            print("Ошибка в GET:", str(e))
            print(traceback.format_exc())
            return Response({'error': 'Server error', 'detail': str(e)}, status=500)
        
