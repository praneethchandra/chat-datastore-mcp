from django.db import models
from django.contrib.auth.models import User
import uuid
import json

class ChatSession(models.Model):
    """Chat session model to track user conversations"""
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='chat_sessions')
    title = models.CharField(max_length=200, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    is_active = models.BooleanField(default=True)
    
    # MCP session tracking
    mcp_session_id = models.CharField(max_length=100, blank=True, null=True)
    
    # Metadata for OpenTelemetry and debugging
    metadata = models.JSONField(default=dict, blank=True)
    
    class Meta:
        ordering = ['-updated_at']
        
    def __str__(self):
        return f"Chat Session {self.id} - {self.user.username}"
    
    def get_title(self):
        """Generate title from first message if not set"""
        if self.title:
            return self.title
        first_message = self.messages.filter(role='user').first()
        if first_message:
            return first_message.content[:50] + "..." if len(first_message.content) > 50 else first_message.content
        return f"Chat {self.created_at.strftime('%Y-%m-%d %H:%M')}"

class ChatMessage(models.Model):
    """Individual chat message model"""
    ROLE_CHOICES = [
        ('user', 'User'),
        ('assistant', 'Assistant'),
        ('system', 'System'),
    ]
    
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('processing', 'Processing'),
        ('completed', 'Completed'),
        ('error', 'Error'),
    ]
    
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    session = models.ForeignKey(ChatSession, on_delete=models.CASCADE, related_name='messages')
    role = models.CharField(max_length=10, choices=ROLE_CHOICES)
    content = models.TextField()
    timestamp = models.DateTimeField(auto_now_add=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='completed')
    
    # AI model information
    model_used = models.CharField(max_length=100, blank=True)
    tokens_used = models.IntegerField(null=True, blank=True)
    
    # MCP operation tracking
    mcp_operations = models.JSONField(default=list, blank=True)
    
    # Error tracking
    error_message = models.TextField(blank=True)
    
    # OpenTelemetry trace information
    trace_id = models.CharField(max_length=100, blank=True)
    span_id = models.CharField(max_length=100, blank=True)
    
    class Meta:
        ordering = ['timestamp']
        
    def __str__(self):
        return f"{self.role}: {self.content[:50]}..."

class MCPOperation(models.Model):
    """Track MCP operations for debugging and telemetry"""
    OPERATION_TYPES = [
        ('kv_get', 'KV Get'),
        ('kv_set', 'KV Set'),
        ('kv_mget', 'KV Multi-Get'),
        ('kv_del', 'KV Delete'),
        ('kv_ttl', 'KV TTL'),
        ('kv_scan', 'KV Scan'),
        ('store_find', 'Store Find'),
        ('store_aggregate', 'Store Aggregate'),
        ('session_appendEvent', 'Session Append Event'),
        ('capabilities_list', 'Capabilities List'),
    ]
    
    STATUS_CHOICES = [
        ('success', 'Success'),
        ('error', 'Error'),
        ('timeout', 'Timeout'),
    ]
    
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    message = models.ForeignKey(ChatMessage, on_delete=models.CASCADE, related_name='operations')
    operation_type = models.CharField(max_length=50, choices=OPERATION_TYPES)
    parameters = models.JSONField(default=dict)
    response = models.JSONField(default=dict, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES)
    duration_ms = models.IntegerField(null=True, blank=True)
    timestamp = models.DateTimeField(auto_now_add=True)
    error_details = models.TextField(blank=True)
    
    class Meta:
        ordering = ['timestamp']
        
    def __str__(self):
        return f"{self.operation_type} - {self.status}"

class UserPreferences(models.Model):
    """User preferences for chat interface"""
    AI_PROVIDERS = [
        ('openai', 'OpenAI'),
        ('ollama', 'Ollama (Local)'),
    ]
    
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='chat_preferences')
    preferred_ai_provider = models.CharField(max_length=20, choices=AI_PROVIDERS, default='openai')
    openai_model = models.CharField(max_length=50, default='gpt-3.5-turbo')
    ollama_model = models.CharField(max_length=50, default='llama2')
    max_tokens = models.IntegerField(default=1000)
    temperature = models.FloatField(default=0.7)
    
    # UI preferences
    theme = models.CharField(max_length=20, default='light')
    show_timestamps = models.BooleanField(default=True)
    show_token_usage = models.BooleanField(default=False)
    show_mcp_operations = models.BooleanField(default=False)
    
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    
    def __str__(self):
        return f"Preferences for {self.user.username}"
