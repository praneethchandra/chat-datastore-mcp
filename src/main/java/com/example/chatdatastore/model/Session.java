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
    private Map<String,Object> state;
}
