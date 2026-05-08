import json
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.db.models import Q
from api.models import Dialog, Message, TelegramUser


class ChatConsumer(AsyncWebsocketConsumer):
    """
    WebSocket consumer для чата.
    Каждый пользователь подключается к своим диалогам.
    """
    
    async def connect(self):
        # Получаем dialog_id из URL
        self.dialog_id = self.scope['url_route']['kwargs']['dialog_id']
        self.room_group_name = f'chat_{self.dialog_id}'
        
        # Проверяем, что пользователь имеет доступ к диалогу
        user = await self.get_user()
        if not user:
            await self.close()
            return
            
        has_access = await self.check_dialog_access(user, self.dialog_id)
        if not has_access:
            await self.close()
            return
        
        # Присоединяемся к группе комнаты
        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name
        )
        
        await self.accept()
        
        # Отправляем подтверждение подключения
        await self.send(text_data=json.dumps({
            'type': 'connection_established',
            'message': 'Connected to chat'
        }))
    
    async def disconnect(self, close_code):
        # Покидаем группу комнаты
        await self.channel_layer.group_discard(
            self.room_group_name,
            self.channel_name
        )
    
    async def receive(self, text_data):
        """
        Обработка входящих сообщений от клиента
        """
        data = json.loads(text_data)
        message_type = data.get('type')
        
        if message_type == 'chat_message':
            # Сохраняем сообщение в БД
            message = await self.save_message(
                dialog_id=self.dialog_id,
                text=data.get('text', ''),
                sender_id=data.get('sender_id')
            )
            
            if message:
                # Отправляем сообщение всем в группе
                await self.channel_layer.group_send(
                    self.room_group_name,
                    {
                        'type': 'chat_message',
                        'message': message
                    }
                )
        
        elif message_type == 'message_edit':
            # Редактирование сообщения
            success = await self.edit_message(
                message_id=data.get('message_id'),
                new_text=data.get('text', ''),
                sender_id=data.get('sender_id')
            )
            
            if success:
                await self.channel_layer.group_send(
                    self.room_group_name,
                    {
                        'type': 'message_edited',
                        'message_id': data.get('message_id'),
                        'text': data.get('text', ''),
                        'edited': True
                    }
                )
        
        elif message_type == 'message_delete':
            # Удаление сообщения
            success = await self.delete_message(
                message_id=data.get('message_id'),
                sender_id=data.get('sender_id')
            )
            
            if success:
                await self.channel_layer.group_send(
                    self.room_group_name,
                    {
                        'type': 'message_deleted',
                        'message_id': data.get('message_id')
                    }
                )
        
        elif message_type == 'mark_as_read':
            # Помечаем сообщения как прочитанные
            await self.mark_messages_as_read(
                dialog_id=self.dialog_id,
                user_id=data.get('user_id')
            )
        
        elif message_type == 'typing':
            # Индикатор печати
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'user_typing',
                    'sender_id': data.get('sender_id')
                }
            )
    
    # Обработчики событий от group_send
    
    async def chat_message(self, event):
        """Отправка нового сообщения клиенту"""
        message = event['message']
        
        # Вычисляем is_mine для текущего клиента
        user = await self.get_user()
        if user:
            message['is_mine'] = (message['sender_id'] == user.telegram_id)
        
        await self.send(text_data=json.dumps({
            'type': 'chat_message',
            'message': message
        }))
    
    async def message_edited(self, event):
        """Уведомление об редактировании сообщения"""
        await self.send(text_data=json.dumps({
            'type': 'message_edited',
            'message_id': event['message_id'],
            'text': event['text'],
            'edited': event['edited']
        }))
    
    async def message_deleted(self, event):
        """Уведомление об удалении сообщения"""
        await self.send(text_data=json.dumps({
            'type': 'message_deleted',
            'message_id': event['message_id']
        }))
    
    async def user_typing(self, event):
        """Индикатор печати"""
        await self.send(text_data=json.dumps({
            'type': 'user_typing',
            'sender_id': event['sender_id']
        }))
    
    # Database operations
    
    @database_sync_to_async
    def get_user(self):
        """Получаем текущего пользователя из сессии"""
        session = self.scope.get('session', {})
        telegram_id = session.get('telegram_id')
        if not telegram_id:
            return None
        return TelegramUser.objects.filter(telegram_id=telegram_id).first()
    
    @database_sync_to_async
    def check_dialog_access(self, user, dialog_id):
        """Проверяем, имеет ли пользователь доступ к диалогу"""
        try:
            Dialog.objects.get(
                Q(user1=user) | Q(user2=user),
                id=dialog_id
            )
            return True
        except Dialog.DoesNotExist:
            return False
    
    @database_sync_to_async
    def save_message(self, dialog_id, text, sender_id):
        """Сохраняем сообщение в БД"""
        try:
            dialog = Dialog.objects.get(id=dialog_id)
            sender = TelegramUser.objects.get(telegram_id=sender_id)
            
            message = Message.objects.create(
                dialog=dialog,
                sender=sender,
                text=text
            )
            
            # Обновляем updated_at диалога
            dialog.save()  # auto_now=True обновит updated_at
            
            return {
                'id': message.id,
                'text': message.text,
                'time': message.created_at.strftime('%H:%M'),
                'sender_id': sender.telegram_id,
                'edited': False
            }
        except Exception as e:
            print(f"Error saving message: {e}")
            return None
    
    @database_sync_to_async
    def edit_message(self, message_id, new_text, sender_id):
        """Редактируем сообщение"""
        try:
            message = Message.objects.get(id=message_id)
            # Проверяем, что редактирует отправитель
            if message.sender.telegram_id != sender_id:
                return False
            
            message.text = new_text
            message.edited = True
            message.save()
            return True
        except Exception as e:
            print(f"Error editing message: {e}")
            return False
    
    @database_sync_to_async
    def delete_message(self, message_id, sender_id):
        """Удаляем сообщение"""
        try:
            message = Message.objects.get(id=message_id)
            # Проверяем, что удаляет отправитель
            if message.sender.telegram_id != sender_id:
                return False
            
            message.delete()
            return True
        except Exception as e:
            print(f"Error deleting message: {e}")
            return False
    
    @database_sync_to_async
    def mark_messages_as_read(self, dialog_id, user_id):
        """Помечаем непрочитанные сообщения как прочитанные"""
        try:
            dialog = Dialog.objects.get(id=dialog_id)
            reader = TelegramUser.objects.get(telegram_id=user_id)
            
            # Помечаем все непрочитанные сообщения (не от текущего пользователя) как прочитанные
            updated = Message.objects.filter(
                dialog=dialog,
                is_read=False
            ).exclude(sender=reader).update(is_read=True)
            
            print(f"Marked {updated} messages as read for user {user_id}")
            return True
        except Exception as e:
            print(f"Error marking messages as read: {e}")
            return False