package com.example.chatdatastore.store;

import java.util.*;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class MongoStoreClient implements StoreClient {

    private final MongoTemplate mongo;

    @Autowired
    public MongoStoreClient(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<Map<String, Object>> find(String collection, Map<String, Object> filter, Map<String, Integer> projection, Map<String, Integer> sort, Integer limit) {
        Document f = new Document(filter == null ? Map.of() : filter);
        Document p = new Document(projection == null ? Map.of() : projection);
        Query q = new BasicQuery(f, p);
        if (sort != null && !sort.isEmpty()) {
            List<Sort.Order> orders = sort.entrySet().stream()
                .map(e -> new Sort.Order(e.getValue() != null && e.getValue() < 0 ? Sort.Direction.DESC : Sort.Direction.ASC, e.getKey()))
                .collect(Collectors.toList());
            q.with(Sort.by(orders));
        }
        if (limit != null && limit > 0) q.limit(limit);
        List<Document> docs = mongo.find(q, Document.class, collection);
        return docs.stream().map(d -> (Map<String,Object>) d).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> aggregate(String collection, List<Map<String, Object>> pipeline) {
        List<AggregationOperation> ops = new ArrayList<>();
        if (pipeline != null) {
            for (Map<String,Object> stage : pipeline) {
                // assume each stage is like {"$match": {...}} etc.
                Document doc = new Document(stage);
                ops.add(context -> doc);
            }
        }
        Aggregation agg = Aggregation.newAggregation(ops);
        var results = mongo.aggregate(agg, collection, Document.class);
        List<Map<String,Object>> out = new ArrayList<>();
        results.forEach(d -> out.add(d));
        return out;
    }

    @Override
    public void appendEvent(String sessionId, Map<String, Object> event) {
        Document e = new Document(event);
        e.put("sessionId", sessionId);
        e.put("ts", new Date());
        mongo.insert(e, "events");
    }
}
