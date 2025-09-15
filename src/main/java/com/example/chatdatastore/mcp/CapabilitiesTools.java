package com.example.chatdatastore.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CapabilitiesTools {

    @Tool(description = "List available tool names and counts for introspection")
    public Map<String,Object> capabilities_list() {
        // Return static capabilities info to avoid circular dependency
        return Map.of(
                "server", Map.of("name", "chat-datastore", "version", "0.1.0"),
                "capabilities", Map.of(
                    "tools", true,
                    "resources", false,
                    "prompts", false,
                    "completion", false
                )
        );
    }
}
