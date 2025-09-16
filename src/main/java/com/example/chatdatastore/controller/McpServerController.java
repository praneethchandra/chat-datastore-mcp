package com.example.chatdatastore.controller;

import com.example.chatdatastore.mcp.KvTools;
import com.example.chatdatastore.mcp.StoreTools;
import com.example.chatdatastore.mcp.CapabilitiesTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class McpServerController {

    private final KvTools kvTools;
    private final StoreTools storeTools;
    private final CapabilitiesTools capabilitiesTools;
    private final ObjectMapper objectMapper;
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sseConnections = new ConcurrentHashMap<>();

    public McpServerController(KvTools kvTools, StoreTools storeTools, CapabilitiesTools capabilitiesTools, ObjectMapper objectMapper) {
        this.kvTools = kvTools;
        this.storeTools = storeTools;
        this.capabilitiesTools = capabilitiesTools;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sse(@RequestParam(required = false) String sessionId) {
        String connectionId = sessionId != null ? sessionId : "default";
        
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        sseConnections.put(connectionId, sink);
        
        // Send initial connection event
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("connected")
                .data("{\"status\":\"connected\",\"server\":\"chat-datastore\",\"version\":\"1.0.0\"}")
                .build());
        
        return sink.asFlux()
                .doOnCancel(() -> sseConnections.remove(connectionId))
                .doOnTerminate(() -> sseConnections.remove(connectionId))
                .onErrorResume(throwable -> {
                    sseConnections.remove(connectionId);
                    return Flux.empty();
                });
    }

    @PostMapping(value = "/mcp/message", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> handleMcpMessage(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            try {
                String method = (String) request.get("method");
                if (method == null) {
                    return createErrorResponse("Missing method field", -32600);
                }

                switch (method) {
                    case "initialize":
                        return handleInitialize(request);
                    case "tools/list":
                        return handleToolsList();
                    case "tools/call":
                        return handleToolCall(request);
                    default:
                        return createErrorResponse("Method not found: " + method, -32601);
                }
            } catch (Exception e) {
                return createErrorResponse("Internal error: " + e.getMessage(), -32603);
            }
        });
    }

    private Map<String, Object> handleInitialize(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false),
                        "resources", Map.of(),
                        "prompts", Map.of(),
                        "completion", Map.of()
                ),
                "serverInfo", Map.of(
                        "name", "chat-datastore",
                        "version", "1.0.0"
                )
        );
    }

    private Map<String, Object> handleToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // Add KV tools
        tools.add(createToolInfo("kv_get", "Get value for a key from the KV cache", 
                Map.of("key", Map.of("type", "string", "description", "The key to retrieve"))));
        tools.add(createToolInfo("kv_set", "Set a key to a value with optional ttlSec and linkage to a session/interaction",
                Map.of(
                        "key", Map.of("type", "string", "description", "The key to set"),
                        "value", Map.of("type", "string", "description", "The value to set"),
                        "ttlSec", Map.of("type", "integer", "description", "TTL in seconds (optional)"),
                        "sessionId", Map.of("type", "string", "description", "Session ID (optional)"),
                        "interactionId", Map.of("type", "string", "description", "Interaction ID (optional)")
                )));
        tools.add(createToolInfo("kv_mget", "Get multiple keys",
                Map.of("keys", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of keys to retrieve"))));
        tools.add(createToolInfo("kv_del", "Delete a key from the KV cache",
                Map.of("key", Map.of("type", "string", "description", "The key to delete"))));
        tools.add(createToolInfo("kv_ttl", "Get TTL for a key in seconds, if any",
                Map.of("key", Map.of("type", "string", "description", "The key to check TTL for"))));
        tools.add(createToolInfo("kv_scan", "Scan keys by prefix (limit enforced)",
                Map.of(
                        "prefix", Map.of("type", "string", "description", "Prefix to scan for"),
                        "limit", Map.of("type", "integer", "description", "Maximum number of keys to return (optional)")
                )));
        
        // Add Store tools
        tools.add(createToolInfo("store_find", "Find documents in a collection with filter/projection/sort/limit",
                Map.of(
                        "collection", Map.of("type", "string", "description", "Collection name"),
                        "filter", Map.of("type", "object", "description", "MongoDB filter (optional)"),
                        "projection", Map.of("type", "object", "description", "Field projection (optional)"),
                        "sort", Map.of("type", "object", "description", "Sort specification (optional)"),
                        "limit", Map.of("type", "integer", "description", "Maximum number of documents (optional)")
                )));
        tools.add(createToolInfo("store_aggregate", "Run an aggregation pipeline (server-side validated)",
                Map.of(
                        "collection", Map.of("type", "string", "description", "Collection name"),
                        "pipeline", Map.of("type", "array", "description", "Aggregation pipeline stages")
                )));
        tools.add(createToolInfo("session_appendEvent", "Append an event into session event stream",
                Map.of(
                        "sessionId", Map.of("type", "string", "description", "Session ID"),
                        "event", Map.of("type", "object", "description", "Event data to append")
                )));
        
        // Add Capabilities tool
        tools.add(createToolInfo("capabilities_list", "List available tool names and counts for introspection", Map.of()));
        
        return Map.of("tools", tools);
    }
    
    private Map<String, Object> createToolInfo(String name, String description, Map<String, Object> properties) {
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", properties,
                        "required", properties.keySet().stream()
                                .filter(key -> !key.equals("ttlSec") && !key.equals("sessionId") && !key.equals("interactionId") 
                                        && !key.equals("filter") && !key.equals("projection") && !key.equals("sort") && !key.equals("limit"))
                                .toList()
                )
        );
    }

    private Map<String, Object> handleToolCall(Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            String toolName = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            if (toolName == null) {
                return createErrorResponse("Missing tool name", -32602);
            }

            Object result = callTool(toolName, arguments != null ? arguments : Map.of());
            
            return Map.of(
                    "content", Map.of(
                            "type", "text",
                            "text", objectMapper.writeValueAsString(result)
                    ),
                    "isError", false
            );
        } catch (Exception e) {
            return createErrorResponse("Tool execution failed: " + e.getMessage(), -32603);
        }
    }
    
    private Object callTool(String toolName, Map<String, Object> arguments) throws Exception {
        switch (toolName) {
            // KV Tools
            case "kv_get":
                return kvTools.kv_get((String) arguments.get("key"));
            case "kv_set":
                return kvTools.kv_set(
                        (String) arguments.get("key"),
                        (String) arguments.get("value"),
                        (Integer) arguments.get("ttlSec"),
                        (String) arguments.get("sessionId"),
                        (String) arguments.get("interactionId")
                );
            case "kv_mget":
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>) arguments.get("keys");
                return kvTools.kv_mget(keys);
            case "kv_del":
                return kvTools.kv_del((String) arguments.get("key"));
            case "kv_ttl":
                return kvTools.kv_ttl((String) arguments.get("key"));
            case "kv_scan":
                return kvTools.kv_scan(
                        (String) arguments.get("prefix"),
                        (Integer) arguments.get("limit")
                );
            
            // Store Tools
            case "store_find":
                @SuppressWarnings("unchecked")
                Map<String, Object> filter = (Map<String, Object>) arguments.get("filter");
                @SuppressWarnings("unchecked")
                Map<String, Integer> projection = (Map<String, Integer>) arguments.get("projection");
                @SuppressWarnings("unchecked")
                Map<String, Integer> sort = (Map<String, Integer>) arguments.get("sort");
                return storeTools.store_find(
                        (String) arguments.get("collection"),
                        filter,
                        projection,
                        sort,
                        (Integer) arguments.get("limit")
                );
            case "store_aggregate":
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pipeline = (List<Map<String, Object>>) arguments.get("pipeline");
                return storeTools.store_aggregate(
                        (String) arguments.get("collection"),
                        pipeline
                );
            case "session_appendEvent":
                @SuppressWarnings("unchecked")
                Map<String, Object> event = (Map<String, Object>) arguments.get("event");
                return storeTools.session_appendEvent(
                        (String) arguments.get("sessionId"),
                        event
                );
            
            // Capabilities Tools
            case "capabilities_list":
                return capabilitiesTools.capabilities_list();
            
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }

    private Map<String, Object> createErrorResponse(String message, int code) {
        return Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message
                ),
                "isError", true
        );
    }

    @GetMapping("/mcp/capabilities")
    public Map<String, Object> getCapabilities() {
        Map<String, Object> toolsList = handleToolsList();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsList.get("tools");
        
        return Map.of(
                "server", Map.of(
                        "name", "chat-datastore",
                        "version", "1.0.0"
                ),
                "capabilities", Map.of(
                        "tools", true,
                        "resources", false,
                        "prompts", false,
                        "completion", false
                ),
                "tools", tools.stream()
                        .map(tool -> Map.of(
                                "name", tool.get("name"),
                                "description", tool.get("description")
                        ))
                        .toList()
        );
    }
}
