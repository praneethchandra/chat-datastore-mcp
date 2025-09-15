package com.example.chatdatastore.service;

import com.example.chatdatastore.model.KVShadow;
import com.example.chatdatastore.model.OutboxEvent;
import com.example.chatdatastore.repo.KVShadowRepo;
import com.example.chatdatastore.repo.OutboxRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OutboxProjector {

    private final OutboxRepo outboxRepo;
    private final KVShadowRepo kvShadowRepo;

    public OutboxProjector(OutboxRepo outboxRepo, KVShadowRepo kvShadowRepo) {
        this.outboxRepo = outboxRepo;
        this.kvShadowRepo = kvShadowRepo;
    }

    @Scheduled(fixedDelay = 2000L, initialDelay = 5000L)
    public void run() {
        List<OutboxEvent> events = outboxRepo.findTop50ByProcessedFalseOrderByTsAsc();
        for (OutboxEvent e : events) {
            try {
                if (!Objects.equals("KVMutated", e.getType())) {
                    e.setProcessed(true);
                    outboxRepo.save(e);
                    continue;
                }
                Map<String,Object> p = e.getPayload();
                String key = (String) p.get("key");
                String value = (String) p.get("value");
                String valueHash = Integer.toHexString(Objects.toString(value, "").hashCode());
                String sessionId = (String) p.getOrDefault("sessionId", null);
                String interactionId = (String) p.getOrDefault("interactionId", null);

                KVShadow shadow = KVShadow.builder()
                        .key(key)
                        .lastValue(value)
                        .valueHash(valueHash)
                        .lastWriteAt(Instant.now())
                        .sessionId(sessionId)
                        .interactionId(interactionId)
                        .metadata(Map.of("ttlSec", p.getOrDefault("ttlSec", null)))
                        .build();
                kvShadowRepo.save(shadow);

                e.setProcessed(true);
                outboxRepo.save(e);
            } catch (Exception ex) {
                // leave as unprocessed to retry
            }
        }
    }
}
