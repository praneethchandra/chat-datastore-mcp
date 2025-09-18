package com.example.chatdatastore.service;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.model.KVShadow;
import com.example.chatdatastore.repo.KVShadowRepo;
import com.example.chatdatastore.repo.OutboxRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CacheSyncVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(CacheSyncVerificationService.class);

    private final KvClient kvClient;
    private final KVShadowRepo kvShadowRepo;
    private final OutboxRepo outboxRepo;

    public CacheSyncVerificationService(KvClient kvClient, KVShadowRepo kvShadowRepo, OutboxRepo outboxRepo) {
        this.kvClient = kvClient;
        this.kvShadowRepo = kvShadowRepo;
        this.outboxRepo = outboxRepo;
    }

    /**
     * Comprehensive sync verification report
     */
    public Map<String, Object> verifyCacheSync() {
        Map<String, Object> report = new HashMap<>();
        
        // 1. Check for unprocessed outbox events
        long unprocessedEvents = outboxRepo.countByProcessedFalse();
        report.put("unprocessedOutboxEvents", unprocessedEvents);
        
        // 2. Compare cache vs shadow store
        Map<String, Object> syncStatus = compareCacheWithShadow();
        report.put("cacheVsShadowSync", syncStatus);
        
        // 3. Check for stale shadow entries
        Map<String, Object> staleCheck = checkForStaleEntries();
        report.put("staleEntriesCheck", staleCheck);
        
        // 4. Overall sync health
        boolean isHealthy = unprocessedEvents == 0 && 
                           (Boolean) syncStatus.get("allInSync") &&
                           ((List<?>) staleCheck.get("staleKeys")).isEmpty();
        report.put("overallSyncHealth", isHealthy ? "HEALTHY" : "ISSUES_DETECTED");
        report.put("timestamp", Instant.now());
        
        logger.info("Cache sync verification completed. Health: {}", report.get("overallSyncHealth"));
        return report;
    }

    /**
     * Compare Redis cache values with MongoDB shadow store
     */
    private Map<String, Object> compareCacheWithShadow() {
        List<KVShadow> allShadows = kvShadowRepo.findAll();
        List<String> shadowKeys = allShadows.stream().map(KVShadow::getKey).collect(Collectors.toList());
        
        if (shadowKeys.isEmpty()) {
            return Map.of("allInSync", true, "checkedKeys", 0, "mismatches", List.of());
        }
        
        Map<String, String> cacheValues = kvClient.mget(shadowKeys);
        List<Map<String, Object>> mismatches = new ArrayList<>();
        
        for (KVShadow shadow : allShadows) {
            String cacheValue = cacheValues.get(shadow.getKey());
            String shadowValue = shadow.getLastValue();
            
            // Handle null values
            if (!Objects.equals(cacheValue, shadowValue)) {
                Map<String, Object> mismatch = new HashMap<>();
                mismatch.put("key", shadow.getKey());
                mismatch.put("cacheValue", cacheValue);
                mismatch.put("shadowValue", shadowValue);
                mismatch.put("shadowHash", shadow.getValueHash());
                mismatch.put("lastWriteAt", shadow.getLastWriteAt());
                mismatches.add(mismatch);
            }
        }
        
        return Map.of(
            "allInSync", mismatches.isEmpty(),
            "checkedKeys", shadowKeys.size(),
            "mismatches", mismatches
        );
    }

    /**
     * Check for entries that might be stale (haven't been updated recently)
     */
    private Map<String, Object> checkForStaleEntries() {
        Instant staleThreshold = Instant.now().minus(Duration.ofHours(24)); // 24 hours ago
        List<KVShadow> staleEntries = kvShadowRepo.findAll().stream()
            .filter(shadow -> shadow.getLastWriteAt().isBefore(staleThreshold))
            .collect(Collectors.toList());
        
        List<String> staleKeys = staleEntries.stream()
            .map(KVShadow::getKey)
            .collect(Collectors.toList());
        
        return Map.of(
            "staleKeys", staleKeys,
            "staleCount", staleKeys.size(),
            "staleThreshold", staleThreshold
        );
    }

    /**
     * Verify a specific key's sync status
     */
    public Map<String, Object> verifyKeySync(String key) {
        Optional<String> cacheValue = kvClient.get(key);
        Optional<KVShadow> shadowOpt = kvShadowRepo.findById(key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("cacheValue", cacheValue.orElse(null));
        result.put("cacheExists", cacheValue.isPresent());
        
        if (shadowOpt.isPresent()) {
            KVShadow shadow = shadowOpt.get();
            result.put("shadowValue", shadow.getLastValue());
            result.put("shadowHash", shadow.getValueHash());
            result.put("lastWriteAt", shadow.getLastWriteAt());
            result.put("sessionId", shadow.getSessionId());
            result.put("interactionId", shadow.getInteractionId());
            result.put("inSync", Objects.equals(cacheValue.orElse(null), shadow.getLastValue()));
        } else {
            result.put("shadowExists", false);
            result.put("inSync", !cacheValue.isPresent()); // Both should be absent
        }
        
        return result;
    }

    /**
     * Get TTL information for cache entries
     */
    public Map<String, Object> getCacheTTLInfo(String keyPrefix, int limit) {
        List<String> keys = kvClient.scan(keyPrefix, limit);
        Map<String, Object> ttlInfo = new HashMap<>();
        
        for (String key : keys) {
            Optional<Duration> ttl = kvClient.ttl(key);
            ttlInfo.put(key, ttl.map(Duration::toSeconds).orElse(-1L));
        }
        
        return Map.of("ttlInfo", ttlInfo, "scannedKeys", keys.size());
    }

    /**
     * Scheduled health check (runs every 5 minutes)
     */
    @Scheduled(fixedDelay = 300000L, initialDelay = 60000L) // 5 minutes
    public void scheduledSyncHealthCheck() {
        try {
            Map<String, Object> report = verifyCacheSync();
            String health = (String) report.get("overallSyncHealth");
            
            if (!"HEALTHY".equals(health)) {
                logger.warn("Cache sync health check failed: {}", report);
                // You could add alerting here (email, Slack, etc.)
            } else {
                logger.debug("Cache sync health check passed");
            }
        } catch (Exception e) {
            logger.error("Error during scheduled sync health check", e);
        }
    }

    /**
     * Force sync repair for a specific key (emergency use)
     */
    public Map<String, Object> forceSyncRepair(String key) {
        try {
            Optional<String> cacheValue = kvClient.get(key);
            Optional<KVShadow> shadowOpt = kvShadowRepo.findById(key);
            
            if (cacheValue.isPresent() && shadowOpt.isEmpty()) {
                // Cache has value but shadow doesn't - create shadow entry
                String value = cacheValue.get();
                KVShadow shadow = KVShadow.builder()
                    .key(key)
                    .lastValue(value)
                    .valueHash(Integer.toHexString(value.hashCode()))
                    .lastWriteAt(Instant.now())
                    .metadata(Map.of("repaired", true))
                    .build();
                kvShadowRepo.save(shadow);
                return Map.of("action", "created_shadow", "key", key, "success", true);
                
            } else if (!cacheValue.isPresent() && shadowOpt.isPresent()) {
                // Shadow exists but cache doesn't - remove shadow
                kvShadowRepo.deleteById(key);
                return Map.of("action", "removed_shadow", "key", key, "success", true);
                
            } else if (cacheValue.isPresent() && shadowOpt.isPresent()) {
                // Both exist but values differ - update shadow to match cache
                KVShadow shadow = shadowOpt.get();
                String value = cacheValue.get();
                shadow.setLastValue(value);
                shadow.setValueHash(Integer.toHexString(value.hashCode()));
                shadow.setLastWriteAt(Instant.now());
                shadow.setMetadata(Map.of("repaired", true));
                kvShadowRepo.save(shadow);
                return Map.of("action", "updated_shadow", "key", key, "success", true);
            }
            
            return Map.of("action", "no_repair_needed", "key", key, "success", true);
            
        } catch (Exception e) {
            logger.error("Error during force sync repair for key: " + key, e);
            return Map.of("action", "repair_failed", "key", key, "success", false, "error", e.getMessage());
        }
    }
}
