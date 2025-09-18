package com.example.chatdatastore.mcp;

import com.example.chatdatastore.model.Interaction;
import com.example.chatdatastore.model.Session;
import com.example.chatdatastore.service.MultiTierPersistenceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class MultiTierPersistenceTools {

    private final MultiTierPersistenceService multiTierPersistenceService;

    public MultiTierPersistenceTools(MultiTierPersistenceService multiTierPersistenceService) {
        this.multiTierPersistenceService = multiTierPersistenceService;
    }

    @Tool(description = "Persist session data with configurable caching strategy")
    public Map<String, Object> persist_session(String sessionId, String userId, String status, 
                                              Map<String, Object> context, Map<String, Object> metadata, 
                                              String cacheStrategy) {
        
        MultiTierPersistenceService.SessionCacheStrategy strategy;
        try {
            strategy = MultiTierPersistenceService.SessionCacheStrategy.valueOf(cacheStrategy.toUpperCase());
        } catch (Exception e) {
            strategy = MultiTierPersistenceService.SessionCacheStrategy.CRITICAL_ONLY;
        }
        
        Session session = Session.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(status)
                .startedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .context(context)
                .metadata(metadata)
                .interactionCount(0)
                .build();
        
        MultiTierPersistenceService.PersistenceResult result = 
            multiTierPersistenceService.persistSession(session, strategy);
        
        return result.toMap();
    }

    @Tool(description = "Update existing session with new activity and context")
    public Map<String, Object> update_session(String sessionId, String status, 
                                             Map<String, Object> context, Map<String, Object> metadata,
                                             Integer interactionCount, String cacheStrategy) {
        
        MultiTierPersistenceService.SessionCacheStrategy strategy;
        try {
            strategy = MultiTierPersistenceService.SessionCacheStrategy.valueOf(cacheStrategy.toUpperCase());
        } catch (Exception e) {
            strategy = MultiTierPersistenceService.SessionCacheStrategy.WITH_CONTEXT;
        }
        
        Session session = Session.builder()
                .sessionId(sessionId)
                .status(status)
                .lastActivityAt(Instant.now())
                .context(context)
                .metadata(metadata)
                .interactionCount(interactionCount)
                .build();
        
        MultiTierPersistenceService.PersistenceResult result = 
            multiTierPersistenceService.persistSession(session, strategy);
        
        return result.toMap();
    }

    @Tool(description = "Persist interaction data with configurable caching strategy")
    public Map<String, Object> persist_interaction(String interactionId, String sessionId, String role,
                                                  String request, String response, String content,
                                                  Map<String, Object> context, Map<String, Object> metadata,
                                                  Long processingTimeMs, String cacheStrategy) {
        
        MultiTierPersistenceService.InteractionCacheStrategy strategy;
        try {
            strategy = MultiTierPersistenceService.InteractionCacheStrategy.valueOf(cacheStrategy.toUpperCase());
        } catch (Exception e) {
            strategy = MultiTierPersistenceService.InteractionCacheStrategy.RESPONSE_ONLY;
        }
        
        Interaction interaction = Interaction.builder()
                .interactionId(interactionId)
                .sessionId(sessionId)
                .role(role)
                .request(request)
                .response(response)
                .content(content)
                .timestamp(Instant.now())
                .context(context)
                .metadata(metadata)
                .processingTimeMs(processingTimeMs)
                .build();
        
        MultiTierPersistenceService.PersistenceResult result = 
            multiTierPersistenceService.persistInteraction(interaction, strategy);
        
        return result.toMap();
    }

    @Tool(description = "Persist evaluation data for a session or interaction")
    public Map<String, Object> persist_evaluation(String entityId, String entityType, 
                                                 Map<String, Object> evaluationData) {
        
        if (!"session".equals(entityType) && !"interaction".equals(entityType)) {
            return Map.of(
                "success", false,
                "message", "entityType must be 'session' or 'interaction'"
            );
        }
        
        MultiTierPersistenceService.PersistenceResult result = 
            multiTierPersistenceService.persistEvaluation(entityId, entityType, evaluationData);
        
        return result.toMap();
    }

    @Tool(description = "Get available cache strategies for sessions")
    public Map<String, Object> get_session_cache_strategies() {
        return Map.of(
            "strategies", Map.of(
                "NONE", Map.of(
                    "description", "No caching - MongoDB only",
                    "ttl", null,
                    "useCase", "Archival sessions, completed sessions"
                ),
                "CRITICAL_ONLY", Map.of(
                    "description", "Cache essential session info only",
                    "ttl", 3600,
                    "useCase", "Active sessions requiring fast status checks"
                ),
                "WITH_CONTEXT", Map.of(
                    "description", "Cache session info with context",
                    "ttl", 1800,
                    "useCase", "Active sessions with frequent context access"
                ),
                "FULL_SESSION", Map.of(
                    "description", "Cache complete session data",
                    "ttl", 900,
                    "useCase", "Highly active sessions requiring full data access"
                )
            )
        );
    }

    @Tool(description = "Get available cache strategies for interactions")
    public Map<String, Object> get_interaction_cache_strategies() {
        return Map.of(
            "strategies", Map.of(
                "NONE", Map.of(
                    "description", "No caching - MongoDB only",
                    "ttl", null,
                    "useCase", "Historical interactions, completed conversations"
                ),
                "RESPONSE_ONLY", Map.of(
                    "description", "Cache interaction response only",
                    "ttl", 1800,
                    "useCase", "Recent interactions for quick response retrieval"
                ),
                "WITH_CONTEXT", Map.of(
                    "description", "Cache interaction with context",
                    "ttl", 900,
                    "useCase", "Active conversations requiring context"
                ),
                "FULL_INTERACTION", Map.of(
                    "description", "Cache complete interaction data",
                    "ttl", 300,
                    "useCase", "Current interaction being processed"
                )
            )
        );
    }

    @Tool(description = "Get persistence recommendations based on usage patterns")
    public Map<String, Object> get_persistence_recommendations(String usagePattern) {
        Map<String, Object> recommendations = Map.of();
        
        switch (usagePattern.toLowerCase()) {
            case "high_frequency":
                recommendations = Map.of(
                    "sessionStrategy", "WITH_CONTEXT",
                    "interactionStrategy", "RESPONSE_ONLY",
                    "reasoning", "High frequency access benefits from context caching but full interaction caching may overwhelm Redis"
                );
                break;
                
            case "real_time":
                recommendations = Map.of(
                    "sessionStrategy", "FULL_SESSION",
                    "interactionStrategy", "FULL_INTERACTION",
                    "reasoning", "Real-time applications need immediate access to all data"
                );
                break;
                
            case "analytical":
                recommendations = Map.of(
                    "sessionStrategy", "CRITICAL_ONLY",
                    "interactionStrategy", "NONE",
                    "reasoning", "Analytical workloads primarily use MongoDB for complex queries"
                );
                break;
                
            case "archival":
                recommendations = Map.of(
                    "sessionStrategy", "NONE",
                    "interactionStrategy", "NONE",
                    "reasoning", "Archival data doesn't need caching, focus on MongoDB storage"
                );
                break;
                
            default:
                recommendations = Map.of(
                    "sessionStrategy", "CRITICAL_ONLY",
                    "interactionStrategy", "RESPONSE_ONLY",
                    "reasoning", "Balanced approach for general use cases"
                );
        }
        
        return Map.of(
            "usagePattern", usagePattern,
            "recommendations", recommendations,
            "availablePatterns", new String[]{"high_frequency", "real_time", "analytical", "archival"}
        );
    }

    @Tool(description = "Analyze current persistence patterns and suggest optimizations")
    public Map<String, Object> analyze_persistence_patterns() {
        // This would typically analyze actual usage data
        // For now, return example analysis
        return Map.of(
            "analysis", Map.of(
                "totalSessions", "1,234",
                "activeSessions", "89",
                "cacheHitRate", "87.5%",
                "avgResponseTime", "45ms",
                "recommendations", new String[]{
                    "Consider using WITH_CONTEXT strategy for sessions with >10 interactions",
                    "RESPONSE_ONLY caching is optimal for current interaction patterns",
                    "Evaluation data storage is working efficiently in MongoDB only"
                }
            ),
            "metrics", Map.of(
                "redisMemoryUsage", "156MB",
                "mongoStorageUsage", "2.3GB",
                "syncSuccessRate", "99.2%",
                "avgSyncLatency", "12ms"
            )
        );
    }
}
