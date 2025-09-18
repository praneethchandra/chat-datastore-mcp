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
@Document("sessions")
public class Session {
    @Id
    private String sessionId;
    private String userId;
    private Instant startedAt;
    private Instant lastActivityAt;
    private String status;
    private Map<String, Object> context;
    private Map<String, Object> metadata;
    private Integer interactionCount;
    private Map<String, Object> state;
    
    // Evaluation fields
    private Map<String, Object> evaluationData;
    private Instant evaluatedAt;
}
