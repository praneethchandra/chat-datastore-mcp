package com.example.chatdatastore.mcp;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.model.OutboxEvent;
import com.example.chatdatastore.repo.OutboxRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class KvTools {

    private final KvClient kvClient;
    private final OutboxRepo outboxRepo;

    public KvTools(KvClient kvClient, OutboxRepo outboxRepo) {
        this.kvClient = kvClient;
        this.outboxRepo = outboxRepo;
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
        Map<String,Object> payload = new HashMap<>();
        payload.put("key", key);
        payload.put("value", value);
        payload.put("ttlSec", ttlSec);
        if (sessionId != null) payload.put("sessionId", sessionId);
        if (interactionId != null) payload.put("interactionId", interactionId);
        OutboxEvent evt = OutboxEvent.builder()
                .type("KVMutated")
                .ts(Instant.now())
                .payload(payload)
                .processed(false)
                .build();
        outboxRepo.save(evt);
        return Map.of("ok", true);
    }

    @Tool(description = "Delete a key from the KV cache")
    public Map<String,Object> kv_del(String key) {
        kvClient.del(key);
        OutboxEvent evt = OutboxEvent.builder()
                .type("KVMutated")
                .ts(Instant.now())
                .payload(Map.of("key", key, "value", null))
                .processed(false)
                .build();
        outboxRepo.save(evt);
        return Map.of("ok", true);
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
