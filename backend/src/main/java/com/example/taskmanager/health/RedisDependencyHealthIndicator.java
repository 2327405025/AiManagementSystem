package com.example.taskmanager.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisDependency")
public class RedisDependencyHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;
    private final boolean enabled;

    public RedisDependencyHealthIndicator(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.redis-enabled:false}") boolean enabled) {
        this.connectionFactory = connectionFactory;
        this.enabled = enabled;
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        try (var connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return Health.up().withDetail("response", pong).build();
        } catch (RuntimeException exception) {
            return Health.status("DEGRADED")
                    .withDetail("failOpen", true)
                    .withDetail("error", exception.getClass().getSimpleName())
                    .build();
        }
    }
}
