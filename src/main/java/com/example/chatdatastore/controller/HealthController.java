package com.example.chatdatastore.controller;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.store.StoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final KvClient kvClient;
    private final StoreClient storeClient;

    public HealthController(KvClient kvClient, StoreClient storeClient) {
        this.kvClient = kvClient;
        this.storeClient = storeClient;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "chat-datastore-mcp");
        health.put("version", "1.0.0");
        
        // Test Redis connection
        try {
            kvClient.get("health-check");
            health.put("redis", "UP");
        } catch (Exception e) {
            health.put("redis", "DOWN");
            health.put("redisError", e.getMessage());
        }
        
        // Test MongoDB connection
        try {
            storeClient.find("sessions", Map.of(), null, null, 1);
            health.put("mongodb", "UP");
        } catch (Exception e) {
            health.put("mongodb", "DOWN");
            health.put("mongodbError", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, Object>> actuatorHealth() {
        return health();
    }
}
