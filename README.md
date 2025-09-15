# Chat Datastore MCP Server

## Application Overview

The Chat Datastore MCP Server is a Spring Boot application that implements the Model Context Protocol (MCP) to provide chat session management and key-value storage capabilities. It serves as a backend service for chat applications, offering persistent storage for sessions, interactions, and cached data with real-time event processing.

### Key Features
- **MCP Server Implementation**: Provides tools for chat session management and key-value operations
- **Multi-Storage Architecture**: Uses MongoDB for persistent storage and Redis for caching
- **Event-Driven Architecture**: Implements outbox pattern for reliable event processing
- **Observability**: Integrated with OpenTelemetry for monitoring and tracing
- **RESTful API**: WebFlux-based reactive endpoints with Server-Sent Events (SSE) support

## System Architecture

```mermaid
graph TB
    Client[Client Applications]
    
    subgraph "Chat Datastore MCP Server"
        subgraph "Presentation Layer"
            SSE[SSE Controller]
            MCP[MCP Controller]
            MSG[Message Controller]
        end
        
        subgraph "MCP Tools Layer"
            KvTools[KvTools<br/>- kv_get<br/>- kv_set<br/>- kv_mget<br/>- kv_scan<br/>- kv_del<br/>- kv_ttl]
            StoreTools[StoreTools<br/>- store_find<br/>- store_aggregate<br/>- session_appendEvent]
            CapTools[CapabilitiesTools<br/>- list_capabilities]
        end
        
        subgraph "Service Layer"
            OutboxProjector[OutboxProjector<br/>- Event Processing<br/>- Scheduled Tasks]
            KvClient[KvClient Interface]
            StoreClient[StoreClient Interface]
        end
        
        subgraph "Implementation Layer"
            RedisKvClient[RedisKvClient]
            MongoStoreClient[MongoStoreClient]
        end
        
        subgraph "Repository Layer"
            SessionRepo[SessionRepo]
            InteractionRepo[InteractionRepo]
            KVShadowRepo[KVShadowRepo]
            OutboxRepo[OutboxRepo]
        end
    end
    
    subgraph "Data Storage"
        Redis[(Redis<br/>Key-Value Cache<br/>TTL Support<br/>Scan Operations)]
        MongoDB[(MongoDB<br/>- sessions<br/>- interactions<br/>- kvshadow<br/>- events)]
    end
    
    subgraph "Observability Stack"
        OtelCollector[OpenTelemetry<br/>Collector]
        ClickHouse[(ClickHouse<br/>Metrics & Traces)]
    end
    
    %% Client connections
    Client -->|MCP Protocol<br/>SSE/WebFlux| SSE
    Client -->|MCP Protocol<br/>SSE/WebFlux| MCP
    Client -->|MCP Protocol<br/>SSE/WebFlux| MSG
    
    %% Controller to Tools
    SSE --> KvTools
    SSE --> StoreTools
    SSE --> CapTools
    MCP --> KvTools
    MCP --> StoreTools
    MCP --> CapTools
    MSG --> KvTools
    MSG --> StoreTools
    MSG --> CapTools
    
    %% Tools to Services
    KvTools --> KvClient
    KvTools --> OutboxRepo
    StoreTools --> StoreClient
    
    %% Service implementations
    KvClient --> RedisKvClient
    StoreClient --> MongoStoreClient
    
    %% Services to Storage
    RedisKvClient --> Redis
    MongoStoreClient --> MongoDB
    
    %% Repository connections
    SessionRepo --> MongoDB
    InteractionRepo --> MongoDB
    KVShadowRepo --> MongoDB
    OutboxRepo --> MongoDB
    
    %% Outbox pattern
    OutboxProjector --> OutboxRepo
    OutboxProjector --> KVShadowRepo
    
    %% Observability
    OutboxProjector -->|Metrics/Traces| OtelCollector
    RedisKvClient -->|Metrics/Traces| OtelCollector
    MongoStoreClient -->|Metrics/Traces| OtelCollector
    OtelCollector --> ClickHouse
    
    %% Styling
    classDef clientStyle fill:#e1f5fe
    classDef toolStyle fill:#f3e5f5
    classDef serviceStyle fill:#e8f5e8
    classDef repoStyle fill:#fff3e0
    classDef storageStyle fill:#ffebee
    classDef observabilityStyle fill:#f1f8e9
    
    class Client clientStyle
    class KvTools,StoreTools,CapTools toolStyle
    class OutboxProjector,KvClient,StoreClient,RedisKvClient,MongoStoreClient serviceStyle
    class SessionRepo,InteractionRepo,KVShadowRepo,OutboxRepo repoStyle
    class Redis,MongoDB storageStyle
    class OtelCollector,ClickHouse observabilityStyle
```

**Architecture Overview:**
- **Presentation Layer**: Handles HTTP/SSE requests and MCP protocol communication
- **MCP Tools Layer**: Implements MCP tools for KV operations, store queries, and capabilities
- **Service Layer**: Business logic and interfaces for data access
- **Implementation Layer**: Concrete implementations for Redis and MongoDB clients
- **Repository Layer**: Data access objects for different entity types
- **Data Storage**: Redis for caching, MongoDB for persistent storage
- **Observability**: OpenTelemetry integration with ClickHouse for metrics and tracing

## Design Patterns

### 1. **Repository Pattern**
- **Implementation**: `SessionRepo`, `InteractionRepo`, `KVShadowRepo`, `OutboxRepo`
- **Purpose**: Abstracts data access logic and provides a clean interface for data operations
- **Benefits**: Separation of concerns, testability, and maintainability

### 2. **Outbox Pattern**
- **Implementation**: `OutboxProjector` service with scheduled processing
- **Purpose**: Ensures reliable event processing and eventual consistency
- **Benefits**: Guarantees event delivery, handles failures gracefully

### 3. **Tool Pattern (MCP)**
- **Implementation**: `@Tool` annotations in service classes
- **Purpose**: Exposes business logic as callable tools through MCP protocol
- **Benefits**: Standardized interface, discoverability, type safety

### 4. **Builder Pattern**
- **Implementation**: Lombok `@Builder` on model classes
- **Purpose**: Provides fluent API for object construction
- **Benefits**: Immutability, readability, optional parameters

### 5. **Dependency Injection**
- **Implementation**: Spring's IoC container
- **Purpose**: Manages object lifecycle and dependencies
- **Benefits**: Loose coupling, testability, configuration flexibility

### 6. **Event Sourcing (Partial)**
- **Implementation**: Session event streams and outbox events
- **Purpose**: Maintains audit trail and enables event replay
- **Benefits**: Traceability, debugging, analytics

## Sequence Diagrams

### 1. KV Set Operation (Success Scenario)

```mermaid
sequenceDiagram
    participant Client
    participant KvTools
    participant KvClient
    participant Redis
    participant OutboxRepo
    participant MongoDB
    participant OutboxProjector
    participant KVShadowRepo

    Client->>KvTools: kv_set(key, value, ttl, sessionId, interactionId)
    KvTools->>KvClient: set(key, value, ttl)
    KvClient->>Redis: SET key value EX ttl
    Redis-->>KvClient: OK
    KvClient-->>KvTools: success
    
    KvTools->>OutboxRepo: save(OutboxEvent)
    OutboxRepo->>MongoDB: insert event
    MongoDB-->>OutboxRepo: success
    OutboxRepo-->>KvTools: saved
    KvTools-->>Client: {ok: true}
    
    Note over OutboxProjector: Scheduled every 2s
    OutboxProjector->>OutboxRepo: findTop50ByProcessedFalseOrderByTsAsc()
    OutboxRepo-->>OutboxProjector: [events]
    OutboxProjector->>KVShadowRepo: save(KVShadow)
    KVShadowRepo->>MongoDB: upsert shadow
    MongoDB-->>KVShadowRepo: success
    OutboxProjector->>OutboxRepo: markProcessed(event)
    OutboxRepo->>MongoDB: update processed=true
```

### 2. Store Find Operation (Success Scenario)

```mermaid
sequenceDiagram
    participant Client
    participant StoreTools
    participant StoreClient
    participant MongoDB

    Client->>StoreTools: store_find(collection, filter, projection, sort, limit)
    StoreTools->>StoreTools: requireAllowed(collection)
    alt Collection allowed
        StoreTools->>StoreClient: find(collection, filter, projection, sort, limit)
        StoreClient->>MongoDB: db.collection.find(filter).project(projection).sort(sort).limit(limit)
        MongoDB-->>StoreClient: [documents]
        StoreClient-->>StoreTools: [results]
        StoreTools-->>Client: [results]
    else Collection not allowed
        StoreTools-->>Client: IllegalArgumentException
    end
```

### 3. KV Operation Failure and Retry Scenario

```mermaid
sequenceDiagram
    participant Client
    participant KvTools
    participant KvClient
    participant Redis
    participant OutboxRepo
    participant MongoDB
    participant OutboxProjector

    Client->>KvTools: kv_set(key, value, ttl)
    KvTools->>KvClient: set(key, value, ttl)
    KvClient->>Redis: SET key value EX ttl
    Redis-->>KvClient: Connection Error
    KvClient-->>KvTools: Exception
    KvTools-->>Client: Error Response
    
    Note over Client: Client retries after delay
    Client->>KvTools: kv_set(key, value, ttl) [retry]
    KvTools->>KvClient: set(key, value, ttl)
    KvClient->>Redis: SET key value EX ttl
    Redis-->>KvClient: OK
    KvTools->>OutboxRepo: save(OutboxEvent)
    OutboxRepo->>MongoDB: insert event
    MongoDB-->>OutboxRepo: success
    OutboxRepo-->>KvTools: saved
    KvTools-->>Client: {ok: true}
    
    Note over OutboxProjector: Processing fails
    OutboxProjector->>OutboxRepo: findUnprocessedEvents()
    OutboxRepo-->>OutboxProjector: [events]
    OutboxProjector->>OutboxProjector: processEvent() [fails]
    Note over OutboxProjector: Event remains unprocessed
    
    Note over OutboxProjector: Next scheduled run
    OutboxProjector->>OutboxRepo: findUnprocessedEvents()
    OutboxRepo-->>OutboxProjector: [events]
    OutboxProjector->>OutboxProjector: processEvent() [succeeds]
    OutboxProjector->>OutboxRepo: markProcessed(event)
    OutboxRepo->>MongoDB: update processed=true
```

### 4. Session Event Append (Success Scenario)

```mermaid
sequenceDiagram
    participant Client
    participant StoreTools
    participant StoreClient
    participant MongoDB

    Client->>StoreTools: session_appendEvent(sessionId, event)
    StoreTools->>StoreClient: appendEvent(sessionId, event)
    StoreClient->>MongoDB: db.sessions.updateOne({_id: sessionId}, {$push: {events: event}})
    MongoDB-->>StoreClient: UpdateResult
    StoreClient-->>StoreTools: success
    StoreTools-->>Client: {ok: true}
```

## Local Environment Setup

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose
- Git

### Step 1: Clone the Repository
```bash
git clone <repository-url>
cd chat-datastore-mcp
```

### Step 2: Start Infrastructure Services
```bash
cd deploy
docker-compose up -d redis mongo clickhouse otel-collector
```

### Step 3: Verify Infrastructure
```bash
# Check Redis
docker exec -it redis redis-cli ping
# Should return: PONG

# Check MongoDB
docker exec -it mongo mongosh --eval "db.adminCommand('ping')"
# Should return: { ok: 1 }

# Check ClickHouse
curl http://localhost:8123/ping
# Should return: Ok.
```

### Step 4: Build and Run the Application

#### Option A: Run with Maven (Development)
```bash
# From project root
mvn clean compile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### Option B: Run with Docker
```bash
# Build and run everything
cd deploy
docker-compose up --build
```

### Step 5: Verify Application
```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check MCP SSE endpoint
curl http://localhost:8080/sse
```

## Testing the Application

### Manual Testing with curl

#### 1. Test KV Operations
```bash
# Set a key-value pair
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "kv_set",
      "arguments": {
        "key": "test-key",
        "value": "test-value",
        "ttlSec": 3600,
        "sessionId": "session-123"
      }
    }
  }'

# Get a key
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "kv_get",
      "arguments": {
        "key": "test-key"
      }
    }
  }'
```

#### 2. Test Store Operations
```bash
# Find sessions
curl -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "store_find",
      "arguments": {
        "collection": "sessions",
        "filter": {},
        "limit": 10
      }
    }
  }'
```

### Integration Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=KvToolsTest

# Run with specific profile
mvn test -Dspring.profiles.active=test
```

## Postman Collection

### Import Collection
Create a new Postman collection with the following requests:

#### Collection: Chat Datastore MCP API

**Base URL**: `http://localhost:8080`

#### 1. Health Check
- **Method**: GET
- **URL**: `{{baseUrl}}/actuator/health`
- **Description**: Check application health status

#### 2. MCP Capabilities
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "postman-client",
      "version": "1.0.0"
    }
  }
}
```

#### 3. List Tools
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/list",
  "params": {}
}
```

#### 4. KV Set
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "kv_set",
    "arguments": {
      "key": "user:{{$randomUUID}}",
      "value": "{{$randomFirstName}}",
      "ttlSec": 3600,
      "sessionId": "session-{{$randomUUID}}",
      "interactionId": "interaction-{{$randomUUID}}"
    }
  }
}
```

#### 5. KV Get
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "kv_get",
    "arguments": {
      "key": "user:test-key"
    }
  }
}
```

#### 6. KV Multiple Get
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "kv_mget",
    "arguments": {
      "keys": ["key1", "key2", "key3"]
    }
  }
}
```

#### 7. KV Scan
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "kv_scan",
    "arguments": {
      "prefix": "user:",
      "limit": 50
    }
  }
}
```

#### 8. Store Find Sessions
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "store_find",
    "arguments": {
      "collection": "sessions",
      "filter": {},
      "projection": {"sessionId": 1, "userId": 1, "startedAt": 1},
      "sort": {"startedAt": -1},
      "limit": 10
    }
  }
}
```

#### 9. Store Aggregate
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "store_aggregate",
    "arguments": {
      "collection": "sessions",
      "pipeline": [
        {"$group": {"_id": "$userId", "sessionCount": {"$sum": 1}}},
        {"$sort": {"sessionCount": -1}},
        {"$limit": 10}
      ]
    }
  }
}
```

#### 10. Session Append Event
- **Method**: POST
- **URL**: `{{baseUrl}}/mcp/message`
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "session_appendEvent",
    "arguments": {
      "sessionId": "session-123",
      "event": {
        "type": "message",
        "timestamp": "{{$isoTimestamp}}",
        "data": {
          "content": "Hello, world!",
          "sender": "user"
        }
      }
    }
  }
}
```

### Environment Variables
Create a Postman environment with:
- `baseUrl`: `http://localhost:8080`

### Usage Instructions

1. **Import Collection**: Copy the above requests into a new Postman collection
2. **Set Environment**: Create and select the environment with `baseUrl`
3. **Initialize MCP**: Run the "MCP Capabilities" request first
4. **List Available Tools**: Run "List Tools" to see all available MCP tools
5. **Test KV Operations**: Use KV Set/Get/Scan requests to test caching
6. **Test Store Operations**: Use Store Find/Aggregate to test MongoDB queries
7. **Monitor Events**: Check the outbox events processing by querying the events collection

### Testing Workflow

1. **Health Check** → Verify application is running
2. **Initialize MCP** → Establish MCP protocol connection
3. **List Tools** → Confirm all tools are available
4. **KV Operations** → Test Redis caching functionality
5. **Store Operations** → Test MongoDB query capabilities
6. **Event Processing** → Verify outbox pattern is working

The application provides comprehensive chat session management with reliable event processing, making it suitable for production chat applications requiring persistent storage and caching capabilities.
