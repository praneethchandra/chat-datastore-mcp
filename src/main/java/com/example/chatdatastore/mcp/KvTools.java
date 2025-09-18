package com.example.chatdatastore.mcp;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.model.OutboxEvent;
import com.example.chatdatastore.repo.OutboxRepo;
import com.example.chatdatastore.service.HybridSyncService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class KvTools {

    private final KvClient kvClient;
    private final OutboxRepo outboxRepo;
    private final HybridSyncService hybridSyncService;

    public KvTools(KvClient kvClient, OutboxRepo outboxRepo, HybridSyncService hybridSyncService) {
        this.kvClient = kvClient;
        this.outboxRepo = outboxRepo;
        this.hybridSyncService = hybridSyncService;
    }

    @Tool(description = "Get value for a key from the KV cache")
    public Map<String,Object> kv_get(String key) {
        return Map.of("key", key, "value", kvClient.get(key).orElse(null));
    }

    @Tool(description = "Get multiple keys")
    public Map<String,String> kv_mget(List<String> keys) {
        return kvClient.mget(keys);
    }

    @Tool(description = "Set a key to a value with optional ttlSec and linkage to a session/interaction")
    public Map<String,Object> kv_set(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        Duration ttl = (ttlSec == null || ttlSec <= 0) ? null : Duration.ofSeconds(ttlSec);
        kvClient.set(key, value, ttl);
        
        // Use hybrid sync service for synchronization
        HybridSyncService.SyncResult syncResult = hybridSyncService.adaptiveSync(key, value, ttlSec, sessionId, interactionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("syncResult", syncResult.toMap());
        return result;
    }

    @Tool(description = "Delete a key from the KV cache")
    public Map<String,Object> kv_del(String key) {
        kvClient.del(key);
        
        // Use hybrid sync service for delete synchronization
        HybridSyncService.SyncResult syncResult = hybridSyncService.syncKvDelete(key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("syncResult", syncResult.toMap());
        return result;
    }

    @Tool(description = "Get TTL for a key in seconds, if any")
    public Map<String,Object> kv_ttl(String key) {
        return Map.of("key", key, "ttlSec", kvClient.ttl(key).map(Duration::toSeconds).orElse(null));
    }

    @Tool(description = "Scan keys by prefix (limit enforced)")
    public Map<String,Object> kv_scan(String prefix, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 100 : Math.min(limit, 1000);
        List<String> keys = kvClient.scan(prefix, lim);
        return Map.of("keys", keys);
    }
}
