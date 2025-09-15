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
@Document("outbox")
public class OutboxEvent {
    @Id
    private String id;
    private String type; // KVMutated
    private Instant ts;
    private Map<String,Object> payload;
    private boolean processed;
}
