package com.example.taskmanager;

import com.example.taskmanager.cache.TaskCache;
import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.TaskStatus;
import com.example.taskmanager.dto.TaskDtos.TaskResponse;
import com.example.taskmanager.health.RedisDependencyHealthIndicator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfrastructureResilienceTest {
    @Test
    void reportsRedisFailureAsDegraded() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenThrow(new IllegalStateException("offline"));

        var health = new RedisDependencyHealthIndicator(connectionFactory, true).health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails()).containsEntry("failOpen", true);
    }

    @Test
    void coalescesConcurrentCacheMissesForTheSameTask() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        TaskCache cache = new TaskCache(
                new StringRedisTemplate(connectionFactory),
                new ObjectMapper().findAndRegisterModules(),
                new SimpleMeterRegistry(),
                100,
                Duration.ofMinutes(1),
                false,
                Duration.ofMinutes(5));
        AtomicInteger loads = new AtomicInteger();
        TaskResponse expected = new TaskResponse(
                42L, 7L, "Hot task", null, TaskStatus.PENDING, Priority.MEDIUM,
                null, Instant.now(), Instant.now(), Set.of("cache"), Set.of(), 0);

        try (var executor = Executors.newFixedThreadPool(12)) {
            List<CompletableFuture<TaskResponse>> requests = IntStream.range(0, 24)
                    .mapToObj(ignored -> CompletableFuture.supplyAsync(
                            () -> cache.getOrLoad(42L, () -> {
                                loads.incrementAndGet();
                                try {
                                    Thread.sleep(20);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                                return expected;
                            }),
                            executor))
                    .toList();
            requests.forEach(CompletableFuture::join);
        }

        assertThat(loads).hasValue(1);
    }
}
