package com.example.taskmanager.cache;

import com.example.taskmanager.dto.TaskDtos.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class TaskCache {
    private static final String KEY_PREFIX = "task:v1:";
    private final Cache<Long, TaskResponse> local;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean redisEnabled;
    private final Duration redisTtl;

    public TaskCache(StringRedisTemplate redis,
                     ObjectMapper mapper,
                     MeterRegistry registry,
                     @Value("${app.cache.local-max-size:10000}") long maxSize,
                     @Value("${app.cache.local-ttl:60s}") Duration localTtl,
                     @Value("${app.cache.redis-enabled:false}") boolean redisEnabled,
                     @Value("${app.cache.redis-ttl:5m}") Duration redisTtl) {
        this.redis = redis;
        this.mapper = mapper;
        this.redisEnabled = redisEnabled;
        this.redisTtl = redisTtl;
        this.local = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(localTtl)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(registry, local, "task.cache.l1");
    }

    public Optional<TaskResponse> get(Long id) {
        TaskResponse localValue = local.getIfPresent(id);
        if (localValue != null) {
            return Optional.of(localValue);
        }
        if (!redisEnabled) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().get(key(id));
            if (value == null) {
                return Optional.empty();
            }
            TaskResponse response = mapper.readValue(value, TaskResponse.class);
            local.put(id, response);
            return Optional.of(response);
        } catch (Exception ignored) {
            // Cache is an optimization: Redis failure must not fail the request.
            return Optional.empty();
        }
    }

    public void put(TaskResponse task) {
        local.put(task.id(), task);
        if (!redisEnabled) {
            return;
        }
        try {
            redis.opsForValue().set(key(task.id()), mapper.writeValueAsString(task), redisTtl);
        } catch (Exception ignored) {
            // PostgreSQL remains the source of truth.
        }
    }

    public void evict(Long id) {
        local.invalidate(id);
        if (!redisEnabled) {
            return;
        }
        try {
            redis.delete(key(id));
        } catch (Exception ignored) {
            // Short TTL bounds stale data if Redis is temporarily unavailable.
        }
    }

    private String key(Long id) {
        return KEY_PREFIX + id;
    }
}
