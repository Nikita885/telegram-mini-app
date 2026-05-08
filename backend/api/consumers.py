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
                
                # ✅ NEW: Уведомляем обоих участников об обновлении диалога
                dialog_update = await self.get_dialog_update(self.dialog_id, message)
                if dialog_update:
                    await self.channel_layer.group_send(
                        f'dialogs_{message["sender_id"]}',
                        {
                            'type': 'dialog_updated',
                            'dialog': dialog_update['sender_view']
                        }
                    )
                    await self.channel_layer.group_send(
                        f'dialogs_{dialog_update["other_user_id"]}',
                        {
                            'type': 'dialog_updated',
                            'dialog': dialog_update['other_view']
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
                
                # ✅ Обновляем диалог в списке (изменился текст последнего сообщения)
                dialog_update = await self.get_dialog_update_after_edit(self.dialog_id, data.get('message_id'), data.get('text', ''))
                if dialog_update:
                    for user_id, view in dialog_update.items():
                        await self.channel_layer.group_send(
                            f'dialogs_{user_id}',
                            {
                                'type': 'dialog_updated',
                                'dialog': view
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
                
                # ✅ Обновляем диалог в списке (может измениться последнее сообщение)
                dialog_update = await self.get_dialog_update_after_delete(self.dialog_id)
                if dialog_update:
                    for user_id, view in dialog_update.items():
                        await self.channel_layer.group_send(
                            f'dialogs_{user_id}',
                            {
                                'type': 'dialog_updated',
                                'dialog': view
                            }
                        )
        
        elif message_type == 'mark_as_read':
            # Помечаем сообщения как прочитанные
            updated = await self.mark_messages_as_read(
                dialog_id=self.dialog_id,
                user_id=data.get('user_id')
            )
            
            # ✅ Обновляем диалог в списке (сбросился счетчик непрочитанных)
            if updated > 0:
                dialog_update = await self.get_dialog_update_simple(self.dialog_id)
                if dialog_update:
                    for user_id, view in dialog_update.items():
                        await self.channel_layer.group_send(
                            f'dialogs_{user_id}',
                            {
                                'type': 'dialog_updated',
                                'dialog': view
                            }
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
    def get_dialog_update(self, dialog_id, message_data):
        """Получаем данные обновленного диалога для обоих участников"""
        try:
            dialog = Dialog.objects.select_related('user1', 'user2').get(id=dialog_id)
            sender = TelegramUser.objects.get(telegram_id=message_data['sender_id'])
            other_user = dialog.get_other_user(sender)
            
            # Счетчик непрочитанных для получателя
            unread_count = Message.objects.filter(
                dialog=dialog,
                is_read=False
            ).exclude(sender=other_user).count()
            
            def serialize_user(u):
                return {
                    'telegram_id': u.telegram_id,
                    'username': u.username or '',
                    'first_name': u.first_name or '',
                    'last_name': u.last_name or '',
                    'avatar_url': u.avatar.url if u.avatar else None,
                    'avatar_color': u.avatar_random_color or '#cccccc',
                }
            
            return {
                'sender_view': {
                    'dialog_id': dialog.id,
                    'other_user': serialize_user(other_user),
                    'last_message': {
                        'text': message_data['text'],
                        'time': message_data['time'],
                        'is_mine': True,
                    },
                    'unread_count': 0,
                    'pinned': dialog.pinned,
                },
                'other_view': {
                    'dialog_id': dialog.id,
                    'other_user': serialize_user(sender),
                    'last_message': {
                        'text': message_data['text'],
                        'time': message_data['time'],
                        'is_mine': False,
                    },
                    'unread_count': unread_count,
                    'pinned': dialog.pinned,
                },
                'other_user_id': other_user.telegram_id
            }
        except Exception as e:
            print(f"Error getting dialog update: {e}")
            return None
    
    @database_sync_to_async
    def get_dialog_update_after_edit(self, dialog_id, message_id, new_text):
        """Обновление диалога после редактирования сообщения"""
        try:
            dialog = Dialog.objects.select_related('user1', 'user2').get(id=dialog_id)
            last_msg = dialog.messages.order_by('-created_at').first()
            
            # Если отредактировали не последнее сообщение - не обновляем список
            if not last_msg or last_msg.id != message_id:
                return None
            
            return self._serialize_dialog_for_both(dialog, new_text)
        except Exception as e:
            print(f"Error in get_dialog_update_after_edit: {e}")
            return None
    
    @database_sync_to_async
    def get_dialog_update_after_delete(self, dialog_id):
        """Обновление диалога после удаления сообщения"""
        try:
            dialog = Dialog.objects.select_related('user1', 'user2').get(id=dialog_id)
            return self._serialize_dialog_for_both(dialog)
        except Exception as e:
            print(f"Error in get_dialog_update_after_delete: {e}")
            return None
    
    @database_sync_to_async
    def get_dialog_update_simple(self, dialog_id):
        """Простое обновление диалога (например, после прочтения)"""
        try:
            dialog = Dialog.objects.select_related('user1', 'user2').get(id=dialog_id)
            return self._serialize_dialog_for_both(dialog)
        except Exception as e:
            print(f"Error in get_dialog_update_simple: {e}")
            return None
    
    def _serialize_dialog_for_both(self, dialog, override_text=None):
        """Сериализует диалог для обоих участников"""
        last_msg = dialog.messages.order_by('-created_at').first()
        
        def serialize_user(u):
            return {
                'telegram_id': u.telegram_id,
                'username': u.username or '',
                'first_name': u.first_name or '',
                'last_name': u.last_name or '',
                'avatar_url': u.avatar.url if u.avatar else None,
                'avatar_color': u.avatar_random_color or '#cccccc',
            }
        
        result = {}
        
        for user in [dialog.user1, dialog.user2]:
            other = dialog.get_other_user(user)
            unread = Message.objects.filter(
                dialog=dialog,
                is_read=False
            ).exclude(sender=user).count()
            
            result[user.telegram_id] = {
                'dialog_id': dialog.id,
                'other_user': serialize_user(other),
                'last_message': {
                    'text': override_text or (last_msg.text if last_msg else ''),
                    'time': last_msg.created_at.strftime('%H:%M') if last_msg else '',
                    'is_mine': last_msg.sender == user if last_msg else False,
                } if last_msg else None,
                'unread_count': unread,
                'pinned': dialog.pinned,
            }
        
        return result
    
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
            return updated
        except Exception as e:
            print(f"Error marking messages as read: {e}")
            return 0


# ═══════════════════════════════════════════════════════════════════════════════
#  ✅ NEW: DialogListConsumer для real-time обновления списка диалогов
# ═══════════════════════════════════════════════════════════════════════════════

class DialogListConsumer(AsyncWebsocketConsumer):
    """
    WebSocket consumer для списка диалогов.
    Получает уведомления об обновлениях диалогов в реальном времени.
    """
    
    async def connect(self):
        user = await self.get_user()
        if not user:
            await self.close()
            return
        
        self.user_id = user.telegram_id
        self.room_group_name = f'dialogs_{self.user_id}'
        
        # Присоединяемся к личной группе для обновлений диалогов
        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name
        )
        
        await self.accept()
        
        await self.send(text_data=json.dumps({
            'type': 'connection_established',
            'message': 'Connected to dialog list'
        }))
    
    async def disconnect(self, close_code):
        if hasattr(self, 'room_group_name'):
            await self.channel_layer.group_discard(
                self.room_group_name,
                self.channel_name
            )
    
    async def dialog_updated(self, event):
        """Отправляем обновление диалога клиенту"""
        await self.send(text_data=json.dumps({
            'type': 'dialog_updated',
            'dialog': event['dialog']
        }))
    
    @database_sync_to_async
    def get_user(self):
        session = self.scope.get('session', {})
        telegram_id = session.get('telegram_id')
        if not telegram_id:
            return None
        return TelegramUser.objects.filter(telegram_id=telegram_id).first()