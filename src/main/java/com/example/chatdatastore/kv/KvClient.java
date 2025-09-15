package com.example.chatdatastore.kv;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KvClient {
    Optional<String> get(String key);
    Map<String, String> mget(List<String> keys);
    void set(String key, String value, Duration ttl);
    void del(String key);
    Optional<Duration> ttl(String key);
    List<String> scan(String prefix, int limit);
}
