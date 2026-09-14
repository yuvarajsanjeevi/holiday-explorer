package com.example.holidays.config;

import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.domain.Holiday;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

/**
 * Holidays barely change - the API itself serves them with {@code max-age=604800} - so caching is
 * the biggest win available on latency and on the load we put on a free service. Redis rather than
 * in-process so instances share it and a restart is not cold. The 24h TTL is a compromise: past
 * years never change, current ones get corrected now and then. Lettuce writes asynchronously, so a
 * value is not readable the moment after you write it.
 */
@EnableCaching
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private static final TypeFactory TYPES = TypeFactory.createDefaultInstance();

    private static final JavaType HOLIDAY_LIST = TYPES.constructCollectionType(List.class, Holiday.class);

    private static final JavaType COUNTRY_MAP =
            TYPES.constructMapType(Map.class, String.class, String.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Caching {} and {} in Redis for {}", ApplicationConstants.HOLIDAYS_CACHE,
                ApplicationConstants.COUNTRIES_CACHE, ApplicationConstants.CACHE_TTL);
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfiguration())
                .withInitialCacheConfigurations(Map.of(
                        ApplicationConstants.HOLIDAYS_CACHE, cacheOf(HOLIDAY_LIST),
                        ApplicationConstants.COUNTRIES_CACHE, cacheOf(COUNTRY_MAP)))
                .build();
    }

    /**
     * One serializer per cache, bound to that cache's value type. The generic one needs Jackson
     * default typing, which does not round-trip erased generics - entries wrote fine and then failed
     * to read back.
     */
    private static RedisCacheConfiguration cacheOf(JavaType valueType) {
        return baseConfiguration().serializeValuesWith(
                SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(valueType)));
    }

    private static RedisCacheConfiguration baseConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ApplicationConstants.CACHE_TTL)
                // A typed serializer cannot hold null, and caching a failure for 24h is not wanted anyway.
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()));
    }
}
