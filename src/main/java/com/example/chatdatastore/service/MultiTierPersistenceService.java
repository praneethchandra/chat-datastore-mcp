package com.example.chatdatastore.service;

import com.example.chatdatastore.kv.KvClient;
import com.example.chatdatastore.model.Interaction;
import com.example.chatdatastore.model.Session;
import com.example.chatdatastore.repo.InteractionRepo;
import com.example.chatdatastore.repo.SessionRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class MultiTierPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(MultiTierPersistenceService.class);

    private final KvClient kvClient;
    private final SessionRepo sessionRepo;
    private final InteractionRepo interactionRepo;
    private final HybridSyncService hybridSyncService;

    public MultiTierPersistenceService(KvClient kvClient, SessionRepo sessionRepo, 
                                     InteractionRepo interactionRepo, HybridSyncService hybridSyncService) {
        this.kvClient = kvClient;
        this.sessionRepo = sessionRepo;
        this.interactionRepo = interactionRepo;
        this.hybridSyncService = hybridSyncService;
    }

    /**
     * Persist session data with multi-tier strategy:
     * - Redis: Critical session info (current state, user context)
     * - MongoDB: Complete session details, history, metadata
     */
    public PersistenceResult persistSession(Session session, SessionCacheStrategy cacheStrategy) {
        logger.debug("Persisting session {} with cache strategy: {}", session.getSessionId(), cacheStrategy);
        
        CompletableFuture<Void> mongoFuture = CompletableFuture.runAsync(() -> {
            try {
                sessionRepo.save(session);
                logger.debug("Session {} saved to MongoDB", session.getSessionId());
            } catch (Exception e) {
                logger.error("Failed to save session {} to MongoDB", session.getSessionId(), e);
                throw new RuntimeException("MongoDB persistence failed", e);
            }
        });

        CompletableFuture<HybridSyncService.SyncResult> redisFuture = null;
        
        if (cacheStrategy != SessionCacheStrategy.NONE) {
            redisFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String cacheKey = "session:" + session.getSessionId();
                    String cacheValue = buildSessionCacheValue(session, cacheStrategy);
                    Integer ttl = cacheStrategy.getTtlSeconds();
                    
                    // Store in Redis
                    Duration ttlDuration = ttl != null ? Duration.ofSeconds(ttl) : null;
                    kvClient.set(cacheKey, cacheValue, ttlDuration);
                    
                    // Use hybrid sync for shadow store
                    return hybridSyncService.adaptiveSync(cacheKey, cacheValue, ttl, 
                                                        session.getSessionId(), null);
                } catch (Exception e) {
                    logger.error("Failed to cache session {}", session.getSessionId(), e);
                    throw new RuntimeException("Redis caching failed", e);
                }
            });
        }

        try {
            // Wait for MongoDB persistence (required)
            mongoFuture.get();
            
            HybridSyncService.SyncResult syncResult = null;
            if (redisFuture != null) {
                syncResult = redisFuture.get();
            }
            
            return PersistenceResult.success("Session persisted successfully", syncResult);
            
        } catch (Exception e) {
            logger.error("Session persistence failed for {}", session.getSessionId(), e);
            return PersistenceResult.failure("Session persistence failed: " + e.getMessage());
        }
    }

    /**
     * Persist interaction data with multi-tier strategy:
     * - Redis: Critical interaction info (latest response, context)
     * - MongoDB: Complete interaction details, evaluations, metadata
     */
    public PersistenceResult persistInteraction(Interaction interaction, InteractionCacheStrategy cacheStrategy) {
        logger.debug("Persisting interaction {} with cache strategy: {}", interaction.getInteractionId(), cacheStrategy);
        
        CompletableFuture<Void> mongoFuture = CompletableFuture.runAsync(() -> {
            try {
                interactionRepo.save(interaction);
                logger.debug("Interaction {} saved to MongoDB", interaction.getInteractionId());
            } catch (Exception e) {
                logger.error("Failed to save interaction {} to MongoDB", interaction.getInteractionId(), e);
                throw new RuntimeException("MongoDB persistence failed", e);
            }
        });

        CompletableFuture<HybridSyncService.SyncResult> redisFuture = null;
        
        if (cacheStrategy != InteractionCacheStrategy.NONE) {
            redisFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String cacheKey = "interaction:" + interaction.getInteractionId();
                    String cacheValue = buildInteractionCacheValue(interaction, cacheStrategy);
                    Integer ttl = cacheStrategy.getTtlSeconds();
                    
                    // Store in Redis
                    Duration ttlDuration = ttl != null ? Duration.ofSeconds(ttl) : null;
                    kvClient.set(cacheKey, cacheValue, ttlDuration);
                    
                    // Use hybrid sync for shadow store
                    return hybridSyncService.adaptiveSync(cacheKey, cacheValue, ttl, 
                                                        interaction.getSessionId(), interaction.getInteractionId());
                } catch (Exception e) {
                    logger.error("Failed to cache interaction {}", interaction.getInteractionId(), e);
                    throw new RuntimeException("Redis caching failed", e);
                }
            });
        }

        try {
            // Wait for MongoDB persistence (required)
            mongoFuture.get();
            
            HybridSyncService.SyncResult syncResult = null;
            if (redisFuture != null) {
                syncResult = redisFuture.get();
            }
            
            return PersistenceResult.success("Interaction persisted successfully", syncResult);
            
        } catch (Exception e) {
            logger.error("Interaction persistence failed for {}", interaction.getInteractionId(), e);
            return PersistenceResult.failure("Interaction persistence failed: " + e.getMessage());
        }
    }

    /**
     * Persist evaluation data (MongoDB only, no caching needed)
     */
    public PersistenceResult persistEvaluation(String entityId, String entityType, Map<String, Object> evaluation) {
        logger.debug("Persisting evaluation for {} {}", entityType, entityId);
        
        try {
            if ("session".equals(entityType)) {
                Session session = sessionRepo.findById(entityId).orElse(null);
                if (session != null) {
                    session.setEvaluationData(evaluation);
                    session.setEvaluatedAt(Instant.now());
                    sessionRepo.save(session);
                }
            } else if ("interaction".equals(entityType)) {
                Interaction interaction = interactionRepo.findById(entityId).orElse(null);
                if (interaction != null) {
                    interaction.setEvaluationData(evaluation);
                    interaction.setEvaluatedAt(Instant.now());
                    interactionRepo.save(interaction);
                }
            }
            
            return PersistenceResult.success("Evaluation persisted successfully", null);
            
        } catch (Exception e) {
            logger.error("Evaluation persistence failed for {} {}", entityType, entityId, e);
            return PersistenceResult.failure("Evaluation persistence failed: " + e.getMessage());
        }
    }

    /**
     * Build cache value for session based on strategy
     */
    private String buildSessionCacheValue(Session session, SessionCacheStrategy strategy) {
        Map<String, Object> cacheData = Map.of();
        
        switch (strategy) {
            case CRITICAL_ONLY:
                cacheData = Map.of(
                    "sessionId", session.getSessionId(),
                    "userId", session.getUserId(),
                    "status", session.getStatus(),
                    "lastActivity", session.getLastActivityAt()
                );
                break;
                
            case WITH_CONTEXT:
                cacheData = Map.of(
                    "sessionId", session.getSessionId(),
                    "userId", session.getUserId(),
                    "status", session.getStatus(),
                    "lastActivity", session.getLastActivityAt(),
                    "context", session.getContext(),
                    "interactionCount", session.getInteractionCount()
                );
                break;
                
            case FULL_SESSION:
                cacheData = Map.of(
                    "sessionId", session.getSessionId(),
                    "userId", session.getUserId(),
                    "status", session.getStatus(),
                    "startedAt", session.getStartedAt(),
                    "lastActivity", session.getLastActivityAt(),
                    "context", session.getContext(),
                    "metadata", session.getMetadata(),
                    "interactionCount", session.getInteractionCount()
                );
                break;
        }
        
        return cacheData.toString(); // In production, use JSON serialization
    }

    /**
     * Build cache value for interaction based on strategy
     */
    private String buildInteractionCacheValue(Interaction interaction, InteractionCacheStrategy strategy) {
        Map<String, Object> cacheData = Map.of();
        
        switch (strategy) {
            case RESPONSE_ONLY:
                cacheData = Map.of(
                    "interactionId", interaction.getInteractionId(),
                    "response", interaction.getResponse(),
                    "timestamp", interaction.getTimestamp()
                );
                break;
                
            case WITH_CONTEXT:
                cacheData = Map.of(
                    "interactionId", interaction.getInteractionId(),
                    "sessionId", interaction.getSessionId(),
                    "request", interaction.getRequest(),
                    "response", interaction.getResponse(),
                    "timestamp", interaction.getTimestamp(),
                    "context", interaction.getContext()
                );
                break;
                
            case FULL_INTERACTION:
                cacheData = Map.of(
                    "interactionId", interaction.getInteractionId(),
                    "sessionId", interaction.getSessionId(),
                    "request", interaction.getRequest(),
                    "response", interaction.getResponse(),
                    "timestamp", interaction.getTimestamp(),
                    "context", interaction.getContext(),
                    "metadata", interaction.getMetadata(),
                    "processingTime", interaction.getProcessingTimeMs()
                );
                break;
        }
        
        return cacheData.toString(); // In production, use JSON serialization
    }

    /**
     * Cache strategies for sessions
     */
    public enum SessionCacheStrategy {
        NONE(null),
        CRITICAL_ONLY(3600),      // 1 hour - just essential info
        WITH_CONTEXT(1800),       // 30 minutes - includes context
        FULL_SESSION(900);        // 15 minutes - complete session data
        
        private final Integer ttlSeconds;
        
        SessionCacheStrategy(Integer ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
        
        public Integer getTtlSeconds() {
            return ttlSeconds;
        }
    }

    /**
     * Cache strategies for interactions
     */
    public enum InteractionCacheStrategy {
        NONE(null),
        RESPONSE_ONLY(1800),      // 30 minutes - just the response
        WITH_CONTEXT(900),        // 15 minutes - includes context
        FULL_INTERACTION(300);    // 5 minutes - complete interaction data
        
        private final Integer ttlSeconds;
        
        InteractionCacheStrategy(Integer ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
        
        public Integer getTtlSeconds() {
            return ttlSeconds;
        }
    }

    /**
     * Result class for persistence operations
     */
    public static class PersistenceResult {
        private final boolean success;
        private final String message;
        private final HybridSyncService.SyncResult syncResult;
        private final Instant timestamp;

        private PersistenceResult(boolean success, String message, HybridSyncService.SyncResult syncResult) {
            this.success = success;
            this.message = message;
            this.syncResult = syncResult;
            this.timestamp = Instant.now();
        }

        public static PersistenceResult success(String message, HybridSyncService.SyncResult syncResult) {
            return new PersistenceResult(true, message, syncResult);
        }

        public static PersistenceResult failure(String message) {
            return new PersistenceResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public HybridSyncService.SyncResult getSyncResult() { return syncResult; }
        public Instant getTimestamp() { return timestamp; }

        public Map<String, Object> toMap() {
            Map<String, Object> result = Map.of(
                "success", success,
                "message", message,
                "timestamp", timestamp
            );
            
            if (syncResult != null) {
                result.put("syncResult", syncResult.toMap());
            }
            
            return result;
        }
    }
}
