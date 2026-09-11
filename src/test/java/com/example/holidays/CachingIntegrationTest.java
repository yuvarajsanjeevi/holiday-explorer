package com.example.holidays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.Holiday;
import com.example.holidays.service.CountryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Checks the caching is really in the request path, not just configured. It breaks silently when it
 * is wrong, so these assert the observable thing: no second HTTP call.
 *
 * Writes to Redis are asynchronous, hence the wait before the second read.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(RedisTestContainer.class)
@EnabledIf("com.example.holidays.RedisTestContainer#dockerAvailable")
class CachingIntegrationTest {

    private static final String BASE_URL = "https://date.nager.at/api/v3";
    private static final CountryCode NL = CountryCode.of("NL");

    @TestConfiguration
    static class StubbedUpstream {

        static MockRestServiceServer server;

        @Bean
        RestClient.Builder restClientBuilder() {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            return builder;
        }
    }

    @Autowired
    private NagerDateClient client;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CountryService countryService;

    @Test
    @DisplayName("a repeated (year, country) lookup is served from the cache, not the network")
    void repeatedLookupHitsTheCacheNotTheNetwork() throws IOException {
        Cache cache = cacheManager.getCache("holidays");
        assertThat(cache).isNotNull();
        cache.evict("2026:NL");

        MockRestServiceServer server = StubbedUpstream.server;
        server.reset();
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/public-holidays-2026-NL.json")
                                .getContentAsString(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON));

        List<Holiday> first = client.publicHolidays(2026, NL);
        await().atMost(Duration.ofSeconds(5)).until(() -> cache.get("2026:NL") != null);

        List<Holiday> second = client.publicHolidays(2026, NL);

        assertThat(second).isEqualTo(first).isNotEmpty();
        server.verify(); // fails if the second call reached the network
    }

    @Test
    @DisplayName("reading the country list is served from the cache")
    void countryReadsAreCached() throws IOException {
        MockRestServiceServer server = StubbedUpstream.server;
        server.reset();
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/AvailableCountries"))
                .andRespond(withSuccess(countriesFixture(), MediaType.APPLICATION_JSON));

        countryService.refresh(); // populates the cache
        countryService.getAll();
        countryService.getAll();

        server.verify(); // fails if a read reached the network
    }

    /**
     * The mirror of the one above. A refresh served from the cache it is meant to update would never
     * see a new country, and would look healthy while the API was down.
     */
    @Test
    @DisplayName("refreshing the country list really goes to the network, so it can actually refresh")
    void countryRefreshIsNotServedFromCache() throws IOException {
        MockRestServiceServer server = StubbedUpstream.server;
        server.reset();
        // Exactly two requests for two refreshes: no more, and crucially no fewer.
        server.expect(ExpectedCount.times(2), requestTo(BASE_URL + "/AvailableCountries"))
                .andRespond(withSuccess(countriesFixture(), MediaType.APPLICATION_JSON));

        countryService.refresh();
        countryService.refresh();

        server.verify();
    }

    private static String countriesFixture() throws IOException {
        return new ClassPathResource("fixtures/available-countries.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
