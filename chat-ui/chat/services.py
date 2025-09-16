import requests
import json
import logging
import time
from typing import Dict, Any, Optional, List
from django.conf import settings
from openai import OpenAI
from .models import ChatSession, ChatMessage, MCPOperation

logger = logging.getLogger('chat')

class MCPClient:
    """Client for interacting with the MCP server"""
    
    def __init__(self):
        self.base_url = settings.MCP_SERVER_URL
        self.session = requests.Session()
        
    def call_tool(self, session_id: str, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call an MCP tool"""
        url = f"{self.base_url}/mcp/message"
        params = {"sessionId": session_id}
        
        payload = {
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments
            }
        }
        
        start_time = time.time()
        try:
            response = self.session.post(url, params=params, json=payload, timeout=30)
            duration_ms = int((time.time() - start_time) * 1000)
            
            if response.status_code == 200:
                result = response.json()
                logger.info(f"MCP tool call successful: {tool_name} in {duration_ms}ms")
                return {
                    'success': True,
                    'data': result,
                    'duration_ms': duration_ms
                }
            else:
                logger.error(f"MCP tool call failed: {tool_name} - {response.status_code} - {response.text}")
                return {
                    'success': False,
                    'error': f"HTTP {response.status_code}: {response.text}",
                    'duration_ms': duration_ms
                }
                
        except requests.exceptions.RequestException as e:
            duration_ms = int((time.time() - start_time) * 1000)
            logger.error(f"MCP tool call exception: {tool_name} - {str(e)}")
            return {
                'success': False,
                'error': str(e),
                'duration_ms': duration_ms
            }
    
    def get_capabilities(self) -> Dict[str, Any]:
        """Get MCP server capabilities"""
        try:
            response = self.session.get(f"{self.base_url}/mcp/capabilities", timeout=10)
            if response.status_code == 200:
                return response.json()
            else:
                return {'error': f"HTTP {response.status_code}"}
        except requests.exceptions.RequestException as e:
            return {'error': str(e)}

class AIService:
    """Service for interacting with AI models (OpenAI/Ollama)"""
    
    def __init__(self):
        self.openai_client = None
        if settings.OPENAI_API_KEY:
            self.openai_client = OpenAI(api_key=settings.OPENAI_API_KEY)
        
        self.ollama_base_url = settings.OLLAMA_BASE_URL
        
    def generate_response(self, messages: List[Dict[str, str]], provider: str = 'openai', 
                         model: str = 'gpt-3.5-turbo', **kwargs) -> Dict[str, Any]:
        """Generate AI response"""
        
        if provider == 'openai' and self.openai_client:
            return self._generate_openai_response(messages, model, **kwargs)
        elif provider == 'ollama':
            return self._generate_ollama_response(messages, model, **kwargs)
        else:
            return {
                'success': False,
                'error': f"Provider {provider} not available or not configured"
            }
    
    def _generate_openai_response(self, messages: List[Dict[str, str]], model: str, **kwargs) -> Dict[str, Any]:
        """Generate response using OpenAI"""
        try:
            response = self.openai_client.chat.completions.create(
                model=model,
                messages=messages,
                max_tokens=kwargs.get('max_tokens', 1000),
                temperature=kwargs.get('temperature', 0.7)
            )
            
            return {
                'success': True,
                'content': response.choices[0].message.content,
                'model': model,
                'tokens_used': response.usage.total_tokens if response.usage else None
            }
            
        except Exception as e:
            logger.error(f"OpenAI API error: {str(e)}")
            return {
                'success': False,
                'error': str(e)
            }
    
    def _generate_ollama_response(self, messages: List[Dict[str, str]], model: str, **kwargs) -> Dict[str, Any]:
        """Generate response using Ollama"""
        try:
            url = f"{self.ollama_base_url}/api/chat"
            payload = {
                "model": model,
                "messages": messages,
                "stream": False
            }
            
            response = requests.post(url, json=payload, timeout=60)
            
            if response.status_code == 200:
                result = response.json()
                return {
                    'success': True,
                    'content': result['message']['content'],
                    'model': model,
                    'tokens_used': None  # Ollama doesn't provide token count
                }
            else:
                return {
                    'success': False,
                    'error': f"Ollama HTTP {response.status_code}: {response.text}"
                }
                
        except requests.exceptions.RequestException as e:
            logger.error(f"Ollama API error: {str(e)}")
            return {
                'success': False,
                'error': str(e)
            }

class ChatService:
    """Main chat service that orchestrates AI and MCP interactions"""
    
    def __init__(self):
        self.mcp_client = MCPClient()
        self.ai_service = AIService()
    
    def process_message(self, session: ChatSession, user_message: str, user_preferences) -> ChatMessage:
        """Process a user message and generate AI response"""
        
        # Create user message
        user_msg = ChatMessage.objects.create(
            session=session,
            role='user',
            content=user_message,
            status='completed'
        )
        
        # Create assistant message (initially pending)
        assistant_msg = ChatMessage.objects.create(
            session=session,
            role='assistant',
            content='',
            status='processing'
        )
        
        try:
            # Get conversation history
            messages = self._build_conversation_history(session)
            
            # Generate AI response
            ai_response = self.ai_service.generate_response(
                messages=messages,
                provider=user_preferences.preferred_ai_provider,
                model=self._get_model_for_provider(user_preferences),
                max_tokens=user_preferences.max_tokens,
                temperature=user_preferences.temperature
            )
            
            if ai_response['success']:
                assistant_msg.content = ai_response['content']
                assistant_msg.model_used = ai_response.get('model', '')
                assistant_msg.tokens_used = ai_response.get('tokens_used')
                assistant_msg.status = 'completed'
            else:
                assistant_msg.content = f"Error generating response: {ai_response['error']}"
                assistant_msg.error_message = ai_response['error']
                assistant_msg.status = 'error'
            
            assistant_msg.save()
            
            # Update session
            session.updated_at = assistant_msg.timestamp
            if not session.title:
                session.title = user_message[:50] + "..." if len(user_message) > 50 else user_message
            session.save()
            
            return assistant_msg
            
        except Exception as e:
            logger.error(f"Error processing message: {str(e)}")
            assistant_msg.content = f"An error occurred while processing your message: {str(e)}"
            assistant_msg.error_message = str(e)
            assistant_msg.status = 'error'
            assistant_msg.save()
            return assistant_msg
    
    def _build_conversation_history(self, session: ChatSession) -> List[Dict[str, str]]:
        """Build conversation history for AI context"""
        messages = []
        
        # Add system message
        messages.append({
            "role": "system",
            "content": "You are a helpful AI assistant with access to a chat datastore via MCP tools. You can store and retrieve information using KV operations and query document collections."
        })
        
        # Add recent conversation history (last 20 messages)
        recent_messages = session.messages.filter(
            status='completed'
        ).order_by('-timestamp')[:20]
        # Reverse to get chronological order
        recent_messages = list(reversed(recent_messages))
        
        for msg in recent_messages:
            messages.append({
                "role": msg.role,
                "content": msg.content
            })
        
        return messages
    
    def _get_model_for_provider(self, user_preferences) -> str:
        """Get the appropriate model based on provider"""
        if user_preferences.preferred_ai_provider == 'openai':
            return user_preferences.openai_model
        else:
            return user_preferences.ollama_model
    
    def call_mcp_tool(self, message: ChatMessage, tool_name: str, arguments: Dict[str, Any]) -> MCPOperation:
        """Call an MCP tool and record the operation"""
        
        operation = MCPOperation.objects.create(
            message=message,
            operation_type=tool_name,
            parameters=arguments,
            status='success'  # Will be updated based on result
        )
        
        try:
            # Use session ID from the chat session
            session_id = str(message.session.id)
            
            result = self.mcp_client.call_tool(session_id, tool_name, arguments)
            
            operation.response = result.get('data', {})
            operation.duration_ms = result.get('duration_ms')
            
            if result['success']:
                operation.status = 'success'
            else:
                operation.status = 'error'
                operation.error_details = result.get('error', 'Unknown error')
            
            operation.save()
            return operation
            
        except Exception as e:
            logger.error(f"Error calling MCP tool {tool_name}: {str(e)}")
            operation.status = 'error'
            operation.error_details = str(e)
            operation.save()
            return operation
