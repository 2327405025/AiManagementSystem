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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Two-level cache for immutable task DTOs.
 *
 * <p>Caffeine provides per-process request coalescing and Redis provides a
 * shared L2. Every Redis operation is fail-open because cache availability
 * must never determine task availability.</p>
 */
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

    public TaskResponse getOrLoad(Long id, Supplier<TaskResponse> loader) {
        // Caffeine invokes the mapping function once per key, preventing a
        // burst of identical misses from stampeding PostgreSQL.
        return local.get(id, key -> loadFromRedis(key).orElseGet(() -> {
            TaskResponse loaded = loader.get();
            writeToRedis(loaded);
            return loaded;
        }));
    }

    private Optional<TaskResponse> loadFromRedis(Long id) {
        if (!redisEnabled) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().get(key(id));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(value, TaskResponse.class));
        } catch (Exception ignored) {
            // Cache is an optimization: Redis failure must not fail the request.
            return Optional.empty();
        }
    }

    private void writeToRedis(TaskResponse task) {
        if (!redisEnabled) {
            return;
        }
        try {
            // Jitter prevents a large batch of entries from expiring together.
            Duration jitteredTtl = redisTtl.plusSeconds(ThreadLocalRandom.current().nextLong(61));
            redis.opsForValue().set(key(task.id()), mapper.writeValueAsString(task), jitteredTtl);
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
