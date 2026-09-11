package com.example.holidays;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;

/**
 * A throwaway Redis for the tests that need the real cache. A substitute store would not exercise
 * the serialization, which is where the cache bugs are.
 *
 * {@link #dockerAvailable()} lets them skip rather than fail without Docker.
 */
@TestConfiguration
public class RedisTestContainer {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer("redis:8-alpine");
    }
}
