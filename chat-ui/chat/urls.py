from django.urls import path
from . import views

app_name = 'chat'

urlpatterns = [
    # Main pages
    path('', views.home, name='home'),
    path('login/', views.quick_login, name='quick_login'),
    path('sessions/', views.chat_list, name='chat_list'),
    path('chat/', views.chat_session, name='new_chat'),
    path('chat/<uuid:session_id>/', views.chat_session, name='chat_session'),
    
    # API endpoints
    path('api/sessions/<uuid:session_id>/send/', views.send_message, name='send_message'),
    path('api/sessions/<uuid:session_id>/history/', views.session_history, name='session_history'),
    path('api/sessions/<uuid:session_id>/operations/', views.session_operations, name='session_operations'),
    path('api/sessions/<uuid:session_id>/mcp-tool/', views.call_mcp_tool, name='call_mcp_tool'),
    path('api/sessions/<uuid:session_id>/delete/', views.delete_session, name='delete_session'),
    
    # Settings and capabilities
    path('api/preferences/', views.update_preferences, name='update_preferences'),
    path('api/mcp/capabilities/', views.mcp_capabilities, name='mcp_capabilities'),
]
