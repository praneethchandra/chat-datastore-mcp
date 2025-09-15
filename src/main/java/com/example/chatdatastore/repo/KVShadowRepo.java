package com.example.chatdatastore.repo;

import com.example.chatdatastore.model.KVShadow;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KVShadowRepo extends MongoRepository<KVShadow, String> {}
