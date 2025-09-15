package com.example.chatdatastore.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("interactions")
public class Interaction {
    @Id
    private String interactionId;
    private String sessionId;
    private String role;
    private String content;
    private Instant ts;
    private Map<String,Object> toolCalls;
}
