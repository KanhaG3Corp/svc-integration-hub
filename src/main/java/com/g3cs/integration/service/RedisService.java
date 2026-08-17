package com.g3cs.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisService(StringRedisTemplate stringRedisTemplate,
                        @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write Redis key " + key, e);
        }
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public boolean tryLock(String key, Duration ttl) {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, UUID.randomUUID().toString(), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
