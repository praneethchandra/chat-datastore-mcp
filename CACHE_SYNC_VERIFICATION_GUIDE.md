# Cache and Data Store Synchronization Verification Guide

## Overview

Your application implements a sophisticated cache synchronization pattern using:
- **Redis** as the primary cache layer
- **MongoDB** as the persistent data store
- **Outbox Pattern** for eventual consistency
- **Shadow Store (KVShadow)** for tracking cache state

## How Synchronization Works

### 1. Write Path
```
KV Write → Redis Cache + Outbox Event → OutboxProjector → KVShadow (MongoDB)
```

1. When data is written via `KvTools.kv_set()`:
   - Value is immediately stored in Redis cache
   - An `OutboxEvent` is created with type "KVMutated"
   - Event is marked as `processed: false`

2. `OutboxProjector` runs every 2 seconds:
   - Processes unprocessed outbox events
   - Creates/updates `KVShadow` entries in MongoDB
   - Marks events as `processed: true`

### 2. Read Path
- Reads come directly from Redis cache via `KvTools.kv_get()`
- MongoDB shadow store is used for verification and recovery

## Verification Methods

### 1. Comprehensive Health Check
```java
// Via MCP tool
verify_cache_sync()

// Direct service call
cacheSyncVerificationService.verifyCacheSync()
```

**Returns:**
- `unprocessedOutboxEvents`: Count of pending sync operations
- `cacheVsShadowSync`: Comparison results between Redis and MongoDB
- `staleEntriesCheck`: Keys that haven't been updated recently
- `overallSyncHealth`: "HEALTHY" or "ISSUES_DETECTED"

### 2. Individual Key Verification
```java
// Via MCP tool
verify_key_sync("your-key")

// Direct service call
cacheSyncVerificationService.verifyKeySync("your-key")
```

**Returns:**
- Cache value vs shadow value comparison
- Sync status for the specific key
- Metadata (sessionId, interactionId, lastWriteAt)

### 3. TTL Information
```java
// Via MCP tool
get_cache_ttl_info("prefix:", 100)

// Direct service call
cacheSyncVerificationService.getCacheTTLInfo("prefix:", 100)
```

**Returns:**
- TTL values for all keys matching the prefix
- Helps identify expiring cache entries

## Monitoring and Alerting

### 1. Automated Health Checks
- Runs every 5 minutes via `@Scheduled` annotation
- Logs warnings when sync issues are detected
- Can be extended with alerting (email, Slack, etc.)

### 2. Key Metrics to Monitor

#### Sync Health Indicators
- **Unprocessed Outbox Events**: Should be 0 or very low
- **Cache vs Shadow Mismatches**: Should be empty
- **Stale Entries**: Keys older than 24 hours (configurable)

#### Performance Metrics
- OutboxProjector processing time
- Number of events processed per cycle
- Cache hit/miss ratios

### 3. Log Monitoring
Watch for these log patterns:
```
INFO  - Cache sync verification completed. Health: HEALTHY
WARN  - Cache sync health check failed: {...}
ERROR - Error during scheduled sync health check
```

## Troubleshooting Common Issues

### 1. Unprocessed Outbox Events
**Symptoms:** `unprocessedOutboxEvents > 0`

**Causes:**
- OutboxProjector service is down
- Database connectivity issues
- Processing errors in OutboxProjector

**Solutions:**
- Check OutboxProjector logs
- Verify MongoDB connectivity
- Restart the application if needed

### 2. Cache vs Shadow Mismatches
**Symptoms:** `allInSync: false` with mismatches listed

**Causes:**
- Race conditions during high write volume
- Redis cache eviction due to memory pressure
- Manual cache manipulation outside the application

**Solutions:**
- Use `force_sync_repair(key)` for critical keys
- Investigate cache eviction policies
- Review application logs for errors

### 3. Stale Entries
**Symptoms:** High count of `staleKeys`

**Causes:**
- Keys with very long TTLs
- Abandoned cache entries
- Application not cleaning up properly

**Solutions:**
- Review TTL policies
- Implement cache cleanup procedures
- Consider adjusting stale threshold

## Emergency Procedures

### 1. Force Sync Repair
```java
// Via MCP tool - use with caution
force_sync_repair("problematic-key")
```

**Actions performed:**
- If cache exists but shadow doesn't: Creates shadow entry
- If shadow exists but cache doesn't: Removes shadow entry  
- If both exist but differ: Updates shadow to match cache

### 2. Manual Verification Queries

#### Check Unprocessed Events
```javascript
// MongoDB query
db.outbox.find({processed: false}).count()
```

#### Compare Cache vs Shadow
```bash
# Redis CLI
redis-cli get "your-key"

# MongoDB query
db.kvshadow.findOne({_id: "your-key"})
```

## Best Practices

### 1. Regular Monitoring
- Set up automated alerts for sync health failures
- Monitor unprocessed event counts
- Track cache hit ratios

### 2. Capacity Planning
- Monitor Redis memory usage
- Plan for OutboxProjector processing capacity
- Consider MongoDB storage growth

### 3. Testing
- Test sync behavior under high load
- Verify recovery after Redis restarts
- Test network partition scenarios

### 4. Operational Procedures
- Include sync verification in deployment checks
- Document escalation procedures for sync failures
- Regular backup of MongoDB shadow store

## Configuration Recommendations

### 1. OutboxProjector Tuning
```yaml
# Current: 2 second intervals, 50 events per batch
# Consider adjusting based on load:
outbox:
  processing-interval: 2000ms
  batch-size: 50
```

### 2. Redis Configuration
```yaml
# Ensure appropriate eviction policy
redis:
  maxmemory-policy: allkeys-lru
  # Monitor memory usage
```

### 3. MongoDB Indexing
```javascript
// Recommended indexes for performance
db.outbox.createIndex({processed: 1, ts: 1})
db.kvshadow.createIndex({lastWriteAt: 1})
```

## Summary

Your cache synchronization is well-architected with:
- ✅ Eventual consistency via outbox pattern
- ✅ Shadow store for verification
- ✅ Automated health monitoring
- ✅ Emergency repair capabilities
- ✅ Comprehensive verification tools

The new verification service provides multiple ways to confirm sync status and detect issues early. Regular monitoring of the health check results will ensure your cache and data store remain synchronized.
