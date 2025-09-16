from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth.decorators import login_required
from django.contrib.auth import login
from django.contrib.auth.models import User
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
from django.utils.decorators import method_decorator
from django.views import View
from django.core.paginator import Paginator
import json
import logging

from .models import ChatSession, ChatMessage, UserPreferences, MCPOperation
from .services import ChatService, MCPClient

logger = logging.getLogger('chat')

def home(request):
    """Home page - redirect to chat if authenticated, otherwise show login"""
    if request.user.is_authenticated:
        return redirect('chat:chat_list')
    return render(request, 'chat/home.html')

@login_required
def chat_list(request):
    """List all chat sessions for the user"""
    sessions = ChatSession.objects.filter(
        user=request.user,
        is_active=True
    ).order_by('-updated_at')
    
    paginator = Paginator(sessions, 20)
    page_number = request.GET.get('page')
    page_obj = paginator.get_page(page_number)
    
    return render(request, 'chat/chat_list.html', {
        'page_obj': page_obj,
        'sessions': page_obj.object_list
    })

@login_required
def chat_session(request, session_id=None):
    """Chat session view"""
    if session_id:
        session = get_object_or_404(ChatSession, id=session_id, user=request.user)
    else:
        # Create new session
        session = ChatSession.objects.create(user=request.user)
        return redirect('chat:chat_session', session_id=session.id)
    
    messages = session.messages.order_by('timestamp')
    
    # Get or create user preferences
    preferences, created = UserPreferences.objects.get_or_create(user=request.user)
    
    return render(request, 'chat/chat_session.html', {
        'session': session,
        'messages': messages,
        'preferences': preferences
    })

@login_required
@require_http_methods(["POST"])
@csrf_exempt
def send_message(request, session_id):
    """Send a message in a chat session"""
    try:
        session = get_object_or_404(ChatSession, id=session_id, user=request.user)
        
        data = json.loads(request.body)
        user_message = data.get('message', '').strip()
        
        if not user_message:
            return JsonResponse({'error': 'Message cannot be empty'}, status=400)
        
        # Get user preferences
        preferences, created = UserPreferences.objects.get_or_create(user=request.user)
        
        # Process the message
        chat_service = ChatService()
        assistant_message = chat_service.process_message(session, user_message, preferences)
        
        # Return the response
        return JsonResponse({
            'success': True,
            'user_message': {
                'id': str(assistant_message.session.messages.filter(role='user').last().id),
                'content': user_message,
                'timestamp': assistant_message.session.messages.filter(role='user').last().timestamp.isoformat()
            },
            'assistant_message': {
                'id': str(assistant_message.id),
                'content': assistant_message.content,
                'timestamp': assistant_message.timestamp.isoformat(),
                'status': assistant_message.status,
                'model_used': assistant_message.model_used,
                'tokens_used': assistant_message.tokens_used
            }
        })
        
    except Exception as e:
        logger.error(f"Error sending message: {str(e)}")
        return JsonResponse({'error': str(e)}, status=500)

@login_required
def session_history(request, session_id):
    """Get session message history as JSON"""
    session = get_object_or_404(ChatSession, id=session_id, user=request.user)
    
    messages = session.messages.order_by('timestamp')
    
    message_data = []
    for msg in messages:
        message_data.append({
            'id': str(msg.id),
            'role': msg.role,
            'content': msg.content,
            'timestamp': msg.timestamp.isoformat(),
            'status': msg.status,
            'model_used': msg.model_used,
            'tokens_used': msg.tokens_used,
            'error_message': msg.error_message
        })
    
    return JsonResponse({
        'session_id': str(session.id),
        'title': session.get_title(),
        'messages': message_data
    })

@login_required
@require_http_methods(["POST"])
@csrf_exempt
def call_mcp_tool(request, session_id):
    """Call an MCP tool directly"""
    try:
        session = get_object_or_404(ChatSession, id=session_id, user=request.user)
        
        data = json.loads(request.body)
        tool_name = data.get('tool_name')
        arguments = data.get('arguments', {})
        
        if not tool_name:
            return JsonResponse({'error': 'Tool name is required'}, status=400)
        
        # Create a system message to track this operation
        system_message = ChatMessage.objects.create(
            session=session,
            role='system',
            content=f"MCP tool call: {tool_name}",
            status='processing'
        )
        
        # Call the MCP tool
        chat_service = ChatService()
        operation = chat_service.call_mcp_tool(system_message, tool_name, arguments)
        
        # Update system message
        system_message.content = f"MCP tool call: {tool_name} - {operation.status}"
        system_message.status = 'completed' if operation.status == 'success' else 'error'
        if operation.error_details:
            system_message.error_message = operation.error_details
        system_message.save()
        
        return JsonResponse({
            'success': operation.status == 'success',
            'operation_id': str(operation.id),
            'response': operation.response,
            'duration_ms': operation.duration_ms,
            'error': operation.error_details
        })
        
    except Exception as e:
        logger.error(f"Error calling MCP tool: {str(e)}")
        return JsonResponse({'error': str(e)}, status=500)

@login_required
def mcp_capabilities(request):
    """Get MCP server capabilities"""
    try:
        mcp_client = MCPClient()
        capabilities = mcp_client.get_capabilities()
        return JsonResponse(capabilities)
    except Exception as e:
        logger.error(f"Error getting MCP capabilities: {str(e)}")
        return JsonResponse({'error': str(e)}, status=500)

@login_required
def session_operations(request, session_id):
    """Get MCP operations for a session"""
    session = get_object_or_404(ChatSession, id=session_id, user=request.user)
    
    operations = MCPOperation.objects.filter(
        message__session=session
    ).order_by('-timestamp')
    
    operation_data = []
    for op in operations:
        operation_data.append({
            'id': str(op.id),
            'operation_type': op.operation_type,
            'parameters': op.parameters,
            'response': op.response,
            'status': op.status,
            'duration_ms': op.duration_ms,
            'timestamp': op.timestamp.isoformat(),
            'error_details': op.error_details
        })
    
    return JsonResponse({
        'session_id': str(session.id),
        'operations': operation_data
    })

@login_required
@require_http_methods(["POST"])
@csrf_exempt
def update_preferences(request):
    """Update user preferences"""
    try:
        data = json.loads(request.body)
        
        preferences, created = UserPreferences.objects.get_or_create(user=request.user)
        
        # Update preferences
        if 'preferred_ai_provider' in data:
            preferences.preferred_ai_provider = data['preferred_ai_provider']
        if 'openai_model' in data:
            preferences.openai_model = data['openai_model']
        if 'ollama_model' in data:
            preferences.ollama_model = data['ollama_model']
        if 'max_tokens' in data:
            preferences.max_tokens = data['max_tokens']
        if 'temperature' in data:
            preferences.temperature = data['temperature']
        if 'theme' in data:
            preferences.theme = data['theme']
        if 'show_timestamps' in data:
            preferences.show_timestamps = data['show_timestamps']
        if 'show_token_usage' in data:
            preferences.show_token_usage = data['show_token_usage']
        if 'show_mcp_operations' in data:
            preferences.show_mcp_operations = data['show_mcp_operations']
        
        preferences.save()
        
        return JsonResponse({'success': True})
        
    except Exception as e:
        logger.error(f"Error updating preferences: {str(e)}")
        return JsonResponse({'error': str(e)}, status=500)

@login_required
@require_http_methods(["POST"])
@csrf_exempt
def delete_session(request, session_id):
    """Delete a chat session"""
    try:
        session = get_object_or_404(ChatSession, id=session_id, user=request.user)
        session.is_active = False
        session.save()
        
        return JsonResponse({'success': True})
        
    except Exception as e:
        logger.error(f"Error deleting session: {str(e)}")
        return JsonResponse({'error': str(e)}, status=500)

def quick_login(request):
    """Quick login for demo purposes"""
    if request.method == 'POST':
        username = request.POST.get('username', 'demo')
        
        # Get or create user
        user, created = User.objects.get_or_create(
            username=username,
            defaults={'email': f'{username}@example.com'}
        )
        
        # Log in the user
        login(request, user)
        
        return redirect('chat:chat_list')
    
    return render(request, 'chat/quick_login.html')
