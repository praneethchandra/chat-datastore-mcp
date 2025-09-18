package com.example.chatdatastore.repo;

import com.example.chatdatastore.model.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OutboxRepo extends MongoRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop50ByProcessedFalseOrderByTsAsc();
    long countByProcessedFalse();
}
