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
@Document("kvshadow")
public class KVShadow {
    @Id
    private String key;
    private String valueHash;
    private String lastValue;
    private Instant lastWriteAt;
    private String sessionId;
    private String interactionId;
    private Map<String,Object> metadata;
}
