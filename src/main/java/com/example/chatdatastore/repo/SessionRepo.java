package com.example.chatdatastore.repo;

import com.example.chatdatastore.model.Session;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionRepo extends MongoRepository<Session, String> {}
