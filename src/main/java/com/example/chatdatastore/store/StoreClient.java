package com.example.chatdatastore.store;

import java.util.List;
import java.util.Map;

public interface StoreClient {
    List<Map<String,Object>> find(String collection, Map<String,Object> filter, Map<String,Integer> projection, Map<String,Integer> sort, Integer limit);
    List<Map<String,Object>> aggregate(String collection, List<Map<String,Object>> pipeline);
    void appendEvent(String sessionId, Map<String,Object> event);
}
