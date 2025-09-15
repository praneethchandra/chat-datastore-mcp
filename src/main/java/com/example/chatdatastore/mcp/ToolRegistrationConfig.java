package com.example.chatdatastore.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

@Configuration
public class ToolRegistrationConfig {

    private final KvTools kvTools;
    private final StoreTools storeTools;
    private final CapabilitiesTools capTools;

    public ToolRegistrationConfig(KvTools kvTools, StoreTools storeTools, CapabilitiesTools capTools) {
        this.kvTools = kvTools;
        this.storeTools = storeTools;
        this.capTools = capTools;
    }

    @Bean
    public ToolCallbackProvider toolCallbacks() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(kvTools, storeTools, capTools)
                .build();
    }
}
