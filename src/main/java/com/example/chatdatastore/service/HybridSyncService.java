package com.example.chatdatastore.service;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.model.KVShadow;
import com.example.chatdatastore.model.OutboxEvent;
import com.example.chatdatastore.repo.KVShadowRepo;
import com.example.chatdatastore.repo.OutboxRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Service
public class HybridSyncService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSyncService.class);

    private final KvClient kvClient;
    private final KVShadowRepo kvShadowRepo;
    private final OutboxRepo outboxRepo;
    private final ExecutorService asyncExecutor;
    
    @Value("${app.sync.async-timeout-ms:2000}")
    private long asyncTimeoutMs;
    
    @Value("${app.sync.max-async-threads:10}")
    private int maxAsyncThreads;

    public HybridSyncService(KvClient kvClient, KVShadowRepo kvShadowRepo, OutboxRepo outboxRepo) {
        this.kvClient = kvClient;
        this.kvShadowRepo = kvShadowRepo;
        this.outboxRepo = outboxRepo;
        // Initialize with default values, will be updated by @Value injection
        this.asyncExecutor = createExecutorService();
    }
    
    private ExecutorService createExecutorService() {
        int threadCount = maxAsyncThreads > 0 ? maxAsyncThreads : 10; // Default to 10 if not set
        return Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "hybrid-sync-thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Hybrid sync for KV set operation
     * 1. Try async thread synchronization first
     * 2. Fall back to event-based sync if async fails
     */
    public SyncResult syncKvSet(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        logger.debug("Starting hybrid sync for key: {}", key);
        
        // First attempt: Async thread synchronization
        CompletableFuture<Boolean> asyncSync = CompletableFuture.supplyAsync(() -> {
            try {
                return performDirectSync(key, value, ttlSec, sessionId, interactionId);
            } catch (Exception e) {
                logger.warn("Async sync failed for key {}: {}", key, e.getMessage());
                return false;
            }
        }, asyncExecutor);

        try {
            // Wait for async sync with timeout
            Boolean asyncResult = asyncSync.get(asyncTimeoutMs, TimeUnit.MILLISECONDS);
            if (asyncResult) {
                logger.debug("Async sync succeeded for key: {}", key);
                return SyncResult.success("ASYNC_THREAD", "Direct synchronization completed successfully");
            }
        } catch (TimeoutException e) {
            logger.warn("Async sync timed out for key: {} after {}ms", key, asyncTimeoutMs);
            asyncSync.cancel(true);
        } catch (Exception e) {
            logger.warn("Async sync failed for key: {}", key, e);
        }

        // Fallback: Event-based synchronization
        logger.info("Falling back to event-based sync for key: {}", key);
        return performEventBasedSync(key, value, ttlSec, sessionId, interactionId);
    }

    /**
     * Hybrid sync for KV delete operation
     */
    public SyncResult syncKvDelete(String key) {
        logger.debug("Starting hybrid sync delete for key: {}", key);
        
        // First attempt: Async thread synchronization
        CompletableFuture<Boolean> asyncSync = CompletableFuture.supplyAsync(() -> {
            try {
                return performDirectDeleteSync(key);
            } catch (Exception e) {
                logger.warn("Async delete sync failed for key {}: {}", key, e.getMessage());
                return false;
            }
        }, asyncExecutor);

        try {
            Boolean asyncResult = asyncSync.get(asyncTimeoutMs, TimeUnit.MILLISECONDS);
            if (asyncResult) {
                logger.debug("Async delete sync succeeded for key: {}", key);
                return SyncResult.success("ASYNC_THREAD", "Direct delete synchronization completed successfully");
            }
        } catch (TimeoutException e) {
            logger.warn("Async delete sync timed out for key: {} after {}ms", key, asyncTimeoutMs);
            asyncSync.cancel(true);
        } catch (Exception e) {
            logger.warn("Async delete sync failed for key: {}", key, e);
        }

        // Fallback: Event-based synchronization
        logger.info("Falling back to event-based delete sync for key: {}", key);
        return performEventBasedDeleteSync(key);
    }

    /**
     * Direct synchronization - updates shadow store immediately
     */
    private boolean performDirectSync(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        try {
            String valueHash = Integer.toHexString(Objects.toString(value, "").hashCode());
            
            KVShadow shadow = KVShadow.builder()
                    .key(key)
                    .lastValue(value)
                    .valueHash(valueHash)
                    .lastWriteAt(Instant.now())
                    .sessionId(sessionId)
                    .interactionId(interactionId)
                    .metadata(Map.of(
                        "ttlSec", ttlSec,
                        "syncMethod", "ASYNC_THREAD",
                        "syncTimestamp", Instant.now().toString()
                    ))
                    .build();
            
            kvShadowRepo.save(shadow);
            logger.debug("Direct sync completed for key: {}", key);
            return true;
        } catch (Exception e) {
            logger.error("Direct sync failed for key: {}", key, e);
            return false;
        }
    }

    /**
     * Direct delete synchronization - removes from shadow store immediately
     */
    private boolean performDirectDeleteSync(String key) {
        try {
            kvShadowRepo.deleteById(key);
            logger.debug("Direct delete sync completed for key: {}", key);
            return true;
        } catch (Exception e) {
            logger.error("Direct delete sync failed for key: {}", key, e);
            return false;
        }
    }

    /**
     * Event-based synchronization - creates outbox event for later processing
     */
    private SyncResult performEventBasedSync(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        try {
            Map<String, Object> payload = Map.of(
                "key", key,
                "value", value,
                "ttlSec", ttlSec != null ? ttlSec : 0,
                "sessionId", sessionId != null ? sessionId : "",
                "interactionId", interactionId != null ? interactionId : "",
                "fallbackReason", "ASYNC_TIMEOUT_OR_FAILURE"
            );
            
            OutboxEvent event = OutboxEvent.builder()
                    .type("KVMutated")
                    .ts(Instant.now())
                    .payload(payload)
                    .processed(false)
                    .build();
            
            outboxRepo.save(event);
            logger.debug("Event-based sync queued for key: {}", key);
            return SyncResult.success("EVENT_BASED", "Synchronization queued via outbox pattern");
        } catch (Exception e) {
            logger.error("Event-based sync failed for key: {}", key, e);
            return SyncResult.failure("BOTH_FAILED", "Both async and event-based sync failed: " + e.getMessage());
        }
    }

    /**
     * Event-based delete synchronization
     */
    private SyncResult performEventBasedDeleteSync(String key) {
        try {
            Map<String, Object> payload = Map.of(
                "key", key,
                "value", (Object) null,
                "fallbackReason", "ASYNC_TIMEOUT_OR_FAILURE"
            );
            
            OutboxEvent event = OutboxEvent.builder()
                    .type("KVMutated")
                    .ts(Instant.now())
                    .payload(payload)
                    .processed(false)
                    .build();
            
            outboxRepo.save(event);
            logger.debug("Event-based delete sync queued for key: {}", key);
            return SyncResult.success("EVENT_BASED", "Delete synchronization queued via outbox pattern");
        } catch (Exception e) {
            logger.error("Event-based delete sync failed for key: {}", key, e);
            return SyncResult.failure("BOTH_FAILED", "Both async and event-based delete sync failed: " + e.getMessage());
        }
    }

    /**
     * Get sync statistics
     */
    public Map<String, Object> getSyncStats() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) asyncExecutor;
        return Map.of(
            "asyncThreads", Map.of(
                "activeCount", executor.getActiveCount(),
                "poolSize", executor.getPoolSize(),
                "maxPoolSize", executor.getMaximumPoolSize(),
                "queueSize", executor.getQueue().size(),
                "completedTasks", executor.getCompletedTaskCount()
            ),
            "configuration", Map.of(
                "asyncTimeoutMs", asyncTimeoutMs,
                "maxAsyncThreads", maxAsyncThreads
            ),
            "outboxEvents", Map.of(
                "unprocessed", outboxRepo.countByProcessedFalse(),
                "total", outboxRepo.count()
            )
        );
    }

    /**
     * Check if system is under high load (for adaptive behavior)
     */
    public boolean isSystemUnderHighLoad() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) asyncExecutor;
        long unprocessedEvents = outboxRepo.countByProcessedFalse();
        
        // Consider system under high load if:
        // 1. Thread pool is saturated (active threads >= 80% of max)
        // 2. Queue has pending tasks
        // 3. Too many unprocessed outbox events (> 100)
        boolean threadPoolSaturated = executor.getActiveCount() >= (executor.getMaximumPoolSize() * 0.8);
        boolean queueBacklog = executor.getQueue().size() > 0;
        boolean eventBacklog = unprocessedEvents > 100;
        
        return threadPoolSaturated || queueBacklog || eventBacklog;
    }

    /**
     * Adaptive sync that chooses strategy based on system load
     */
    public SyncResult adaptiveSync(String key, String value, Integer ttlSec, String sessionId, String interactionId) {
        if (isSystemUnderHighLoad()) {
            logger.info("System under high load, using event-based sync directly for key: {}", key);
            return performEventBasedSync(key, value, ttlSec, sessionId, interactionId);
        } else {
            return syncKvSet(key, value, ttlSec, sessionId, interactionId);
        }
    }

    /**
     * Result class for sync operations
     */
    public static class SyncResult {
        private final boolean success;
        private final String method;
        private final String message;
        private final Instant timestamp;

        private SyncResult(boolean success, String method, String message) {
            this.success = success;
            this.method = method;
            this.message = message;
            this.timestamp = Instant.now();
        }

        public static SyncResult success(String method, String message) {
            return new SyncResult(true, method, message);
        }

        public static SyncResult failure(String method, String message) {
            return new SyncResult(false, method, message);
        }

        public boolean isSuccess() { return success; }
        public String getMethod() { return method; }
        public String getMessage() { return message; }
        public Instant getTimestamp() { return timestamp; }

        public Map<String, Object> toMap() {
            return Map.of(
                "success", success,
                "method", method,
                "message", message,
                "timestamp", timestamp
            );
        }
    }
}
