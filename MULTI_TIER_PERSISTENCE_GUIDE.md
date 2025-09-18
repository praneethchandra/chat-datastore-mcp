# Multi-Tier Persistence Strategy Guide

## Overview

The Multi-Tier Persistence Strategy extends the hybrid synchronization approach to handle different data persistence requirements for Redis (critical/fast access data) vs MongoDB (comprehensive data storage). This approach optimizes performance by caching only essential data in Redis while storing complete information in MongoDB.

## Problem Statement

Different types of data have different access patterns and requirements:

1. **Critical Data**: Needs immediate access (user sessions, current state)
2. **Comprehensive Data**: Complete records with full context and metadata
3. **Evaluation Data**: Analytics and assessment information (MongoDB only)
4. **Historical Data**: Archival information that doesn't need caching

## Architecture

### Data Flow Strategy

```mermaid
flowchart TD
    A[Application Request] --> B[Multi-Tier Persistence Service]
    B --> C{Determine Strategy}
    C --> D[Redis Cache<br/>Critical Data]
    C --> E[MongoDB<br/>Complete Data]
    
    D --> F[Session status & context<br/>Latest interaction response<br/>User context<br/>TTL-based expiration]
    E --> G[Full session details<br/>Complete interaction history<br/>Evaluation data<br/>Metadata & analytics]
    
    D --> H[Hybrid Sync Service]
    H --> I[Shadow Store KVShadow]
    E --> J[Direct Persistence]
    J --> K[Permanent Storage]
    
    style D fill:#e1f5fe
    style E fill:#e8f5e8
    style H fill:#fff3e0
    style J fill:#ffebee
```

### Multi-Tier Architecture

```mermaid
graph TB
    subgraph "Application Layer"
        APP[Chat Application]
        MCP[MCP Tools]
    end
    
    subgraph "Service Layer"
        MTS[MultiTierPersistenceService]
        HSS[HybridSyncService]
    end
    
    subgraph "Cache Tier - Redis"
        RC[Redis Cache]
        subgraph "Cache Strategies"
            CS1[CRITICAL_ONLY<br/>1 hour TTL]
            CS2[WITH_CONTEXT<br/>30 min TTL]
            CS3[FULL_SESSION<br/>15 min TTL]
        end
    end
    
    subgraph "Persistent Tier - MongoDB"
        MC[MongoDB Collections]
        subgraph "Collections"
            SESS[sessions<br/>Complete session data]
            INT[interactions<br/>Full interaction history]
            EVAL[evaluations<br/>Assessment data]
            SHADOW[kvshadow<br/>Sync tracking]
        end
    end
    
    APP --> MTS
    MCP --> MTS
    MTS --> HSS
    MTS --> RC
    MTS --> MC
    HSS --> SHADOW
    
    RC --> CS1
    RC --> CS2
    RC --> CS3
    
    MC --> SESS
    MC --> INT
    MC --> EVAL
    MC --> SHADOW
    
    style MTS fill:#e1f5fe
    style HSS fill:#e8f5e8
    style RC fill:#fff3e0
    style MC fill:#ffebee
```

## Cache Strategies

### Session Cache Strategies

```mermaid
graph LR
    subgraph "Session Strategies"
        A[NONE<br/>No caching<br/>MongoDB only] --> B[CRITICAL_ONLY<br/>1 hour TTL<br/>Essential info only]
        B --> C[WITH_CONTEXT<br/>30 min TTL<br/>+ context & count]
        C --> D[FULL_SESSION<br/>15 min TTL<br/>Complete data]
    end
    
    subgraph "Use Cases"
        A1[Archival sessions<br/>Completed sessions]
        B1[Active sessions<br/>Fast status checks]
        C1[Frequent context access<br/>Active conversations]
        D1[Highly active sessions<br/>Full data access]
    end
    
    A -.-> A1
    B -.-> B1
    C -.-> C1
    D -.-> D1
    
    style A fill:#ffebee
    style B fill:#fff3e0
    style C fill:#e8f5e8
    style D fill:#e1f5fe
```

### Interaction Cache Strategies

```mermaid
graph LR
    subgraph "Interaction Strategies"
        A[NONE<br/>No caching<br/>MongoDB only] --> B[RESPONSE_ONLY<br/>30 min TTL<br/>Response + timestamp]
        B --> C[WITH_CONTEXT<br/>15 min TTL<br/>+ request & context]
        C --> D[FULL_INTERACTION<br/>5 min TTL<br/>Complete data]
    end
    
    subgraph "Use Cases"
        A1[Historical interactions<br/>Completed conversations]
        B1[Recent interactions<br/>Quick response retrieval]
        C1[Active conversations<br/>Context required]
        D1[Current interaction<br/>Being processed]
    end
    
    A -.-> A1
    B -.-> B1
    C -.-> C1
    D -.-> D1
    
    style A fill:#ffebee
    style B fill:#fff3e0
    style C fill:#e8f5e8
    style D fill:#e1f5fe
```

## Implementation Flow

### Session Persistence Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant MTS as MultiTierPersistenceService
    participant Mongo as MongoDB
    participant Redis as Redis Cache
    participant HSS as HybridSyncService
    
    App->>MTS: persistSession(session, strategy)
    
    par MongoDB Persistence
        MTS->>Mongo: save(session)
        Mongo-->>MTS: success
    and Redis Caching (if strategy != NONE)
        MTS->>MTS: buildCacheValue(session, strategy)
        MTS->>Redis: set(key, value, ttl)
        Redis-->>MTS: success
        MTS->>HSS: adaptiveSync(key, value, ttl)
        HSS-->>MTS: syncResult
    end
    
    MTS-->>App: PersistenceResult(success, syncResult)
```

### Evaluation Data Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant MTS as MultiTierPersistenceService
    participant Mongo as MongoDB
    
    App->>MTS: persistEvaluation(entityId, type, data)
    
    alt Session Evaluation
        MTS->>Mongo: findSession(entityId)
        Mongo-->>MTS: session
        MTS->>MTS: session.setEvaluationData(data)
        MTS->>Mongo: save(session)
        Mongo-->>MTS: success
    else Interaction Evaluation
        MTS->>Mongo: findInteraction(entityId)
        Mongo-->>MTS: interaction
        MTS->>MTS: interaction.setEvaluationData(data)
        MTS->>Mongo: save(interaction)
        Mongo-->>MTS: success
    end
    
    MTS-->>App: PersistenceResult(success)
    
    Note over MTS,Mongo: Evaluations are stored<br/>only in MongoDB
```

## MCP Tools

### Available Tools

#### Session Management
- `persist_session`: Create new session with caching strategy
- `update_session`: Update existing session with new data
- `get_session_cache_strategies`: List available caching strategies

#### Interaction Management
- `persist_interaction`: Store interaction with caching strategy
- `get_interaction_cache_strategies`: List available caching strategies

#### Evaluation Management
- `persist_evaluation`: Store evaluation data (MongoDB only)

#### Strategy Optimization
- `get_persistence_recommendations`: Get recommendations based on usage patterns
- `analyze_persistence_patterns`: Analyze current patterns and suggest optimizations

### Usage Examples

#### Persist High-Frequency Session
```bash
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "persist_session",
      "arguments": {
        "sessionId": "session-123",
        "userId": "user-456",
        "status": "active",
        "context": {"currentTopic": "product_inquiry"},
        "metadata": {"source": "web_chat"},
        "cacheStrategy": "WITH_CONTEXT"
      }
    }
  }'
```

#### Persist Interaction with Response Caching
```bash
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "persist_interaction",
      "arguments": {
        "interactionId": "int-789",
        "sessionId": "session-123",
        "role": "assistant",
        "request": "What are your product features?",
        "response": "Our product offers...",
        "context": {"intent": "product_info"},
        "processingTimeMs": 150,
        "cacheStrategy": "RESPONSE_ONLY"
      }
    }
  }'
```

#### Store Evaluation Data
```bash
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "persist_evaluation",
      "arguments": {
        "entityId": "session-123",
        "entityType": "session",
        "evaluationData": {
          "satisfaction_score": 4.5,
          "resolution_time": 300,
          "topics_covered": ["product_info", "pricing"],
          "sentiment": "positive"
        }
      }
    }
  }'
```

## Usage Patterns and Recommendations

### Pattern-Based Strategy Selection

```mermaid
graph TD
    A[Usage Pattern Analysis] --> B{Pattern Type}
    
    B -->|High Frequency| C[Session: WITH_CONTEXT<br/>Interaction: RESPONSE_ONLY]
    B -->|Real Time| D[Session: FULL_SESSION<br/>Interaction: FULL_INTERACTION]
    B -->|Analytical| E[Session: CRITICAL_ONLY<br/>Interaction: NONE]
    B -->|Archival| F[Session: NONE<br/>Interaction: NONE]
    
    C --> G[Balances performance<br/>with memory usage]
    D --> H[Maximum performance<br/>for immediate access]
    E --> I[Focus on MongoDB<br/>for complex queries]
    F --> J[No caching needed<br/>for historical data]
    
    style C fill:#e8f5e8
    style D fill:#e1f5fe
    style E fill:#fff3e0
    style F fill:#ffebee
```

## Performance Characteristics

### Memory Usage Comparison

```mermaid
graph LR
    subgraph "Redis Memory Usage"
        A[CRITICAL_ONLY<br/>~200 bytes]
        B[RESPONSE_ONLY<br/>~500 bytes]
        C[WITH_CONTEXT<br/>~800 bytes]
        D[FULL_SESSION<br/>~1.5KB]
        E[FULL_INTERACTION<br/>~2KB]
    end
    
    subgraph "Impact Level"
        A1[Low Impact]
        B1[Low Impact]
        C1[Medium Impact]
        D1[High Impact]
        E1[High Impact]
    end
    
    A --> A1
    B --> B1
    C --> C1
    D --> D1
    E --> E1
    
    style A fill:#e8f5e8
    style B fill:#e8f5e8
    style C fill:#fff3e0
    style D fill:#ffebee
    style E fill:#ffebee
```

### Access Pattern Performance

```mermaid
graph TD
    subgraph "Operation Types"
        A[Session Status Check]
        B[Full Session Retrieval]
        C[Interaction Response]
        D[Complex Query]
        E[Evaluation Storage]
    end
    
    subgraph "Latency"
        A1[1-2ms<br/>Redis Hit]
        B1[12-18ms<br/>Redis + MongoDB]
        C1[1-2ms<br/>Redis Hit]
        D1[50-100ms<br/>MongoDB Query]
        E1[15-25ms<br/>MongoDB Only]
    end
    
    A --> A1
    B --> B1
    C --> C1
    D --> D1
    E --> E1
    
    style A1 fill:#e8f5e8
    style B1 fill:#fff3e0
    style C1 fill:#e8f5e8
    style D1 fill:#ffebee
    style E1 fill:#fff3e0
```

## Configuration

### Application Properties
```yaml
app:
  persistence:
    default-session-strategy: CRITICAL_ONLY
    default-interaction-strategy: RESPONSE_ONLY
    evaluation-storage: mongodb-only
    cache-compression: true
  sync:
    async-timeout-ms: 2000
    max-async-threads: 10
```

### Environment-Specific Strategies

```mermaid
graph LR
    subgraph "Environment Strategies"
        A[Development<br/>FULL_SESSION<br/>WITH_CONTEXT]
        B[Production<br/>WITH_CONTEXT<br/>RESPONSE_ONLY]
        C[Analytics<br/>NONE<br/>NONE]
    end
    
    subgraph "Characteristics"
        A1[Full debugging<br/>Complete data access]
        B1[Balanced performance<br/>Optimized memory]
        C1[MongoDB focus<br/>Complex queries]
    end
    
    A --> A1
    B --> B1
    C --> C1
    
    style A fill:#e1f5fe
    style B fill:#e8f5e8
    style C fill:#fff3e0
```

## Migration Guide

### Migration Flow

```mermaid
flowchart TD
    A[Current Single-Tier System] --> B[Phase 1: Deploy Multi-Tier Service]
    B --> C[Phase 2: Migrate Sessions with Strategies]
    C --> D[Phase 3: Migrate Interactions with Caching]
    D --> E[Phase 4: Implement Evaluation Storage]
    E --> F[Phase 5: Optimize Based on Usage]
    
    B1[Alongside existing system<br/>No disruption]
    C1[Choose appropriate<br/>cache strategies]
    D1[Implement caching<br/>for interactions]
    E1[MongoDB-only storage<br/>for evaluations]
    F1[Monitor and adjust<br/>strategies]
    
    B -.-> B1
    C -.-> C1
    D -.-> D1
    E -.-> E1
    F -.-> F1
    
    style A fill:#ffebee
    style F fill:#e8f5e8
```

## Best Practices

### Strategy Selection Decision Tree

```mermaid
flowchart TD
    A[Data Access Analysis] --> B{Access Frequency}
    B -->|High| C{Data Size}
    B -->|Medium| D{Context Needed}
    B -->|Low| E[Use NONE Strategy]
    
    C -->|Small| F[Use CRITICAL_ONLY]
    C -->|Medium| G[Use WITH_CONTEXT]
    C -->|Large| H{Real-time Required}
    
    D -->|Yes| G
    D -->|No| F
    
    H -->|Yes| I[Use FULL Strategy]
    H -->|No| G
    
    style E fill:#ffebee
    style F fill:#fff3e0
    style G fill:#e8f5e8
    style I fill:#e1f5fe
```

## Troubleshooting

### Common Issues and Solutions

```mermaid
flowchart TD
    A[Performance Issues] --> B{Issue Type}
    
    B -->|High Memory Usage| C[Review cache strategies<br/>Reduce caching levels<br/>Implement shorter TTL]
    B -->|Poor Cache Hit Rates| D[Analyze access patterns<br/>Adjust strategies<br/>Increase TTL]
    B -->|Slow MongoDB Queries| E[Add appropriate indexes<br/>Optimize query patterns<br/>Consider read replicas]
    B -->|Sync Failures| F[Monitor hybrid sync health<br/>Check network connectivity<br/>Review error logs]
    
    style C fill:#ffebee
    style D fill:#fff3e0
    style E fill:#e8f5e8
    style F fill:#e1f5fe
```

## Future Enhancements

### Roadmap

```mermaid
timeline
    title Multi-Tier Persistence Roadmap
    
    section Phase 1
        Intelligent Caching : ML-based strategy selection
                           : Automatic optimization
    
    section Phase 2
        Dynamic TTL : Adaptive TTL based on access patterns
                   : Real-time adjustments
    
    section Phase 3
        Compression : Automatic data compression
                   : Memory optimization
    
    section Phase 4
        Tiered Storage : Additional storage tiers
                      : Cost optimization
    
    section Phase 5
        Real-time Analytics : Live monitoring
                           : Optimization recommendations
```

This multi-tier persistence strategy provides a comprehensive solution for handling different data access patterns while optimizing performance and resource usage. The hybrid synchronization ensures reliability during peak loads, while the configurable caching strategies allow for fine-tuned performance optimization based on specific use cases.
