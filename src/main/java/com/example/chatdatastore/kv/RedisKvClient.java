package com.example.chatdatastore.kv;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

@Component
public class RedisKvClient implements KvClient {

    private final StringRedisTemplate redis;

    @Autowired
    public RedisKvClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public Map<String, String> mget(List<String> keys) {
        List<String> values = redis.opsForValue().multiGet(new HashSet<>(keys));
        Map<String,String> result = new LinkedHashMap<>();
        int i = 0;
        for (String k : keys) {
            String v = (values != null && i < values.size()) ? values.get(i) : null;
            result.put(k, v);
            i++;
        }
        return result;
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            redis.opsForValue().set(key, value);
        } else {
            redis.opsForValue().set(key, value, ttl);
        }
    }

    @Override
    public void del(String key) {
        redis.delete(key);
    }

    @Override
    public Optional<Duration> ttl(String key) {
        Long seconds = redis.getExpire(key);
        if (seconds == null || seconds < 0) return Optional.empty();
        return Optional.of(Duration.ofSeconds(seconds));
    }

    @Override
    public List<String> scan(String prefix, int limit) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(prefix + "*")
                .count(Math.max(limit, 100)).build();
        try (RedisConnection conn = Objects.requireNonNull(redis.getConnectionFactory()).getConnection()) {
            List<String> keys = new ArrayList<>();
            try (var cursor = conn.scan(options)) {
                while (cursor.hasNext() && keys.size() < limit) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        }
    }
}
