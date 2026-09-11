package com.example.holidays;

import static com.example.holidays.domain.TestHolidays.publicHoliday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.domain.Holiday;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Round-trips real values through a real Redis. Serialization bugs are invisible on the write path
 * and only show on a later read - an earlier CacheConfig wrote fine and then failed to read back.
 *
 * Writes are asynchronous, so each read polls briefly.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(RedisTestContainer.class)
@EnabledIf("com.example.holidays.RedisTestContainer#dockerAvailable")
class RedisCacheRoundTripTest {

    @MockitoBean
    private NagerDateClient nagerDateClient;

    @Autowired
    private CacheManager cacheManager;

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        assertThat(cache).as("cache '%s' must be configured", name).isNotNull();
        return cache;
    }

    /** Waits for an asynchronously written entry to become readable, then returns it. */
    private static <T> T readBack(Supplier<T> read) {
        await().atMost(Duration.ofSeconds(5)).until(() -> read.get() != null);
        return read.get();
    }

    @Test
    @DisplayName("a list of holidays survives a write-then-read through Redis")
    void holidayListRoundTrips() {
        Cache cache = cache("holidays");
        List<Holiday> holidays = List.of(
                publicHoliday(LocalDate.of(2026, 1, 1), "Nieuwjaarsdag", "New Year's Day"),
                publicHoliday(LocalDate.of(2026, 12, 25), "Eerste Kerstdag", "Christmas Day"));

        cache.put("2026:NL", holidays);

        assertThat(readBack(() -> cache.get("2026:NL", List.class))).isEqualTo(holidays);
    }

    @Test
    @DisplayName("holidays come back as the domain type, not as raw maps")
    void cachedHolidaysDeserialiseToTheDomainType() {
        Cache cache = cache("holidays");
        Holiday unityDay = publicHoliday(LocalDate.of(2026, 10, 3), "Tag der Deutschen Einheit", "German Unity Day");

        cache.put("2026:DE", List.of(unityDay));

        List<?> readBack = readBack(() -> cache.get("2026:DE", List.class));

        assertThat(readBack).singleElement().isInstanceOf(Holiday.class).isEqualTo(unityDay);
    }

    @Test
    @DisplayName("the country map survives a write-then-read through Redis")
    void countryMapRoundTrips() {
        Cache cache = cache("countries");
        Map<String, String> countries = Map.of("NL", "Netherlands", "DE", "Germany");

        cache.put("all", countries);

        assertThat(readBack(() -> cache.get("all", Map.class))).isEqualTo(countries);
    }

    @Test
    @DisplayName("a LocalDate survives the JSON round trip as a date, not a string or an array")
    void datesRoundTripAsDates() {
        Cache cache = cache("holidays");
        LocalDate leapDay = LocalDate.of(2028, 2, 29);

        cache.put("2028:XX", List.of(publicHoliday(leapDay, "Schrikkeldag", "Leap Day")));

        List<?> readBack = readBack(() -> cache.get("2028:XX", List.class));

        assertThat(readBack).singleElement().isEqualTo(publicHoliday(leapDay, "Schrikkeldag", "Leap Day"));
    }
}
