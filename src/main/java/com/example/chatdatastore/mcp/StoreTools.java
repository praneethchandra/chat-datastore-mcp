package com.example.chatdatastore.mcp;

import com.example.chatdatastore.store.StoreClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StoreTools {

    private final StoreClient store;
    private final Set<String> allowedCollections;

    public StoreTools(StoreClient store,
                      @Value("${app.policies.allowedCollections:sessions,interactions,kvshadow,events}") List<String> allowed) {
        this.store = store;
        this.allowedCollections = Set.copyOf(allowed);
    }

    private void requireAllowed(String collection) {
        if (!allowedCollections.contains(collection)) {
            throw new IllegalArgumentException("Collection not allowed: " + collection);
        }
    }

    @Tool(description = "Find documents in a collection with filter/projection/sort/limit")
    public List<Map<String,Object>> store_find(String collection,
                                               Map<String,Object> filter,
                                               Map<String,Integer> projection,
                                               Map<String,Integer> sort,
                                               Integer limit) {
        requireAllowed(collection);
        return store.find(collection, filter, projection, sort, limit);
    }

    @Tool(description = "Run an aggregation pipeline (server-side validated)")
    public List<Map<String,Object>> store_aggregate(String collection,
                                                    List<Map<String,Object>> pipeline) {
        requireAllowed(collection);
        return store.aggregate(collection, pipeline);
    }

    @Tool(description = "Append an event into session event stream")
    public Map<String,Object> session_appendEvent(String sessionId, Map<String,Object> event) {
        store.appendEvent(sessionId, event);
        return Map.of("ok", true);
    }
}
