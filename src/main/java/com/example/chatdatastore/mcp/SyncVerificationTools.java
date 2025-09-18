package com.example.chatdatastore.mcp;

import com.example.chatdatastore.service.CacheSyncVerificationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SyncVerificationTools {

    private final CacheSyncVerificationService syncVerificationService;

    public SyncVerificationTools(CacheSyncVerificationService syncVerificationService) {
        this.syncVerificationService = syncVerificationService;
    }

    @Tool(description = "Verify overall cache and data store synchronization health")
    public Map<String, Object> verify_cache_sync() {
        return syncVerificationService.verifyCacheSync();
    }

    @Tool(description = "Verify synchronization status for a specific key")
    public Map<String, Object> verify_key_sync(String key) {
        return syncVerificationService.verifyKeySync(key);
    }

    @Tool(description = "Get TTL information for cache entries with a given prefix")
    public Map<String, Object> get_cache_ttl_info(String keyPrefix, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 100 : Math.min(limit, 1000);
        return syncVerificationService.getCacheTTLInfo(keyPrefix, lim);
    }

    @Tool(description = "Force repair synchronization for a specific key (emergency use only)")
    public Map<String, Object> force_sync_repair(String key) {
        return syncVerificationService.forceSyncRepair(key);
    }
}
