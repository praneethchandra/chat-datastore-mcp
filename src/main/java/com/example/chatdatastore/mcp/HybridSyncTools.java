package com.example.chatdatastore.mcp;

import com.example.chatdatastore.service.HybridSyncService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HybridSyncTools {

    private final HybridSyncService hybridSyncService;

    public HybridSyncTools(HybridSyncService hybridSyncService) {
        this.hybridSyncService = hybridSyncService;
    }

    @Tool(description = "Get hybrid sync statistics including thread pool status and outbox events")
    public Map<String, Object> hybrid_sync_stats() {
        return hybridSyncService.getSyncStats();
    }

    @Tool(description = "Check if the system is currently under high load")
    public Map<String, Object> hybrid_sync_load_check() {
        boolean isHighLoad = hybridSyncService.isSystemUnderHighLoad();
        return Map.of(
            "isHighLoad", isHighLoad,
            "recommendation", isHighLoad ? 
                "System under high load - using event-based sync" : 
                "System load normal - using hybrid approach"
        );
    }

    @Tool(description = "Manually trigger hybrid sync for a specific key-value pair")
    public Map<String, Object> hybrid_sync_manual(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        HybridSyncService.SyncResult result = hybridSyncService.syncKvSet(key, value, ttlSec, sessionId, interactionId);
        return result.toMap();
    }

    @Tool(description = "Manually trigger hybrid sync delete for a specific key")
    public Map<String, Object> hybrid_sync_delete(String key) {
        HybridSyncService.SyncResult result = hybridSyncService.syncKvDelete(key);
        return result.toMap();
    }

    @Tool(description = "Use adaptive sync that automatically chooses strategy based on system load")
    public Map<String, Object> hybrid_sync_adaptive(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        HybridSyncService.SyncResult result = hybridSyncService.adaptiveSync(key, value, ttlSec, sessionId, interactionId);
        return result.toMap();
    }
}
