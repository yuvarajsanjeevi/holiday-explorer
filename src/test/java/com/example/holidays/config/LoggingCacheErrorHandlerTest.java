package com.example.holidays.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

class LoggingCacheErrorHandlerTest {

    @Test
    @DisplayName("Without the handler, a cache outage fails the request")
    void cacheFailurePropagatesByDefault() {
        try (var context = new AnnotationConfigApplicationContext(WithoutHandler.class)) {
            CountingService service = context.getBean(CountingService.class);

            assertThatThrownBy(() -> service.lookup("NL"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Redis is down");
        }
    }

    @Test
    @DisplayName("With the handler, a cache outage falls through to the method every time")
    void cacheFailureFallsThroughWithHandler() {
        try (var context = new AnnotationConfigApplicationContext(WithHandler.class)) {
            CountingService service = context.getBean(CountingService.class);

            assertThat(service.lookup("NL")).isEqualTo("NL-1");
            assertThat(service.lookup("NL")).isEqualTo("NL-2");
        }
    }

    @Test
    @DisplayName("CacheConfig registers the logging error handler")
    void cacheConfigUsesLoggingHandler() {
        assertThat(new CacheConfig().errorHandler()).isInstanceOf(LoggingCacheErrorHandler.class);
    }

    @EnableCaching
    static class WithoutHandler {

        @Bean
        CacheManager failingCacheManager() {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(new FailingCache()));
            return manager;
        }

        @Bean
        CountingService countingService() {
            return new CountingService();
        }
    }

    @EnableCaching
    static class WithHandler extends WithoutHandler implements CachingConfigurer {

        @Override
        public CacheErrorHandler errorHandler() {
            return new LoggingCacheErrorHandler();
        }
    }

    static class FailingCache extends ConcurrentMapCache {

        FailingCache() {
            super("holidays");
        }

        @Override
        protected Object lookup(Object key) {
            throw new IllegalStateException("Redis is down");
        }

        @Override
        public void put(Object key, Object value) {
            throw new IllegalStateException("Redis is down");
        }
    }

    static class CountingService {

        private final AtomicInteger calls = new AtomicInteger();

        @Cacheable("holidays")
        public String lookup(String countryCode) {
            return countryCode + "-" + calls.incrementAndGet();
        }
    }
}
