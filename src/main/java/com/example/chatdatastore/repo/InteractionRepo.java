package com.example.chatdatastore.repo;

import com.example.chatdatastore.model.Interaction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InteractionRepo extends MongoRepository<Interaction, String> {}
