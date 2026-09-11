package com.example.holidays.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.config.NagerDateProperties;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.Holiday;
import com.example.holidays.exception.UpstreamUnavailableException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A country that passes validation and then fails while being fetched. Nothing else covers this:
 * every other test hits its error before the fetch starts, and a task throwing inside the executor
 * is where the exception type gets lost and a 404 turns into a 500.
 */
class ConcurrentFetchFailureTest {

    private static final NagerDateProperties PROPERTIES = new NagerDateProperties(
            "http://localhost", 0, Duration.ZERO, Duration.ZERO, 3, "0 0 */6 * * *", 50);

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private static final CountryCode NL = CountryCode.of("NL");
    private static final CountryCode DE = CountryCode.of("DE");

    private HolidayServiceImpl serviceThatFailsFetchingNl() {
        NagerDateClient client = Mockito.mock(NagerDateClient.class);
        given(client.availableCountries()).willReturn(Map.of("NL", "Netherlands", "DE", "Germany"));
        given(client.publicHolidays(2026, DE)).willReturn(List.of());
        given(client.publicHolidays(2026, NL))
                .willThrow(new UpstreamUnavailableException("The holiday API returned 503."));

        return new HolidayServiceImpl(client, new CountryServiceImpl(client), PROPERTIES);
    }

    @Test
    @DisplayName("a country failing during the concurrent fetch keeps its own exception type")
    void fanOutPreservesTheOriginalException() {
        HolidayServiceImpl service = serviceThatFailsFetchingNl();
        Set<CountryCode> countries = new LinkedHashSet<>(List.of(NL, DE));

        assertThatThrownBy(() -> service.weekdayHolidayCounts(2026, countries, TODAY))
                .as("must stay an UpstreamUnavailableException so the handler maps it to 502, not 500")
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    @DisplayName("the same holds for the two-country intersection")
    void sharedHolidaysPreservesTheOriginalException() {
        HolidayServiceImpl service = serviceThatFailsFetchingNl();

        assertThatThrownBy(() -> service.sharedHolidays(2026, NL, DE, TODAY))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    @DisplayName("a task failing with something that is not a RuntimeException still becomes a 502")
    void wrapsANonRuntimeFailure() {
        NagerDateClient client = Mockito.mock(NagerDateClient.class);
        given(client.availableCountries()).willReturn(Map.of("NL", "Netherlands"));
        given(client.publicHolidays(2026, NL)).willThrow(new StackOverflowError("blown stack in the parser"));

        HolidayServiceImpl service =
                new HolidayServiceImpl(client, new CountryServiceImpl(client), PROPERTIES);

        assertThatThrownBy(() -> service.weekdayHolidayCounts(2026, new LinkedHashSet<>(List.of(NL)), TODAY))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("Failed to retrieve holidays for 'NL'.")
                .hasCauseInstanceOf(StackOverflowError.class);
    }

    /**
     * Calls {@code resultOf} directly: the executor is already closed by the time results are read,
     * so there is no way to interrupt a waiting {@code get()} through the public API. Still worth
     * testing - swallowing the interrupt would leave the thread's flag cleared.
     */
    @Test
    @DisplayName("an interrupted read is reported as a 502 and leaves the thread interrupted")
    void restoresTheInterruptFlag() {
        Future<List<Holiday>> interrupting = new CompletableFuture<>() {
            @Override
            public List<Holiday> get() throws InterruptedException {
                throw new InterruptedException("interrupted while waiting");
            }
        };

        try {
            assertThatThrownBy(() -> HolidayServiceImpl.resultOf(NL, interrupting))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessageContaining("Interrupted while retrieving holidays for 'NL'.");
            assertThat(Thread.currentThread().isInterrupted())
                    .as("the interrupt must be handed on, not swallowed")
                    .isTrue();
        } finally {
            Thread.interrupted(); // clear the flag so it cannot leak into the next test on this thread
        }
    }
}
