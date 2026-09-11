package com.example.holidays;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.service.HolidayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test: the context starts and the caches exist. The upstream client is mocked so the build
 * never depends on a third party being up. {@link CachingIntegrationTest} covers the caching itself.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(RedisTestContainer.class)
@EnabledIf("com.example.holidays.RedisTestContainer#dockerAvailable")
class HolidayExplorerApplicationTests {

    @MockitoBean
    private NagerDateClient nagerDateClient;

    @Autowired
    private HolidayService service;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("the context starts and wires the query service")
    void contextLoads() {
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("the holiday and country caches are configured")
    void cachesAreConfigured() {
        assertThat(cacheManager.getCacheNames()).contains("holidays", "countries");
    }
}
