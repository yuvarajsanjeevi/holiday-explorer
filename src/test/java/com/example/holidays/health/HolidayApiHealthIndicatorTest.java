package com.example.holidays.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.holidays.exception.UpstreamUnavailableException;
import com.example.holidays.service.CountryService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class HolidayApiHealthIndicatorTest {

    private final CountryService countryService = Mockito.mock(CountryService.class);
    private final HolidayApiHealthIndicator indicator = new HolidayApiHealthIndicator(countryService);

    @Test
    @DisplayName("UP when a snapshot is held, reporting its size and age")
    void upWhenSnapshotHeld() {
        Instant refreshedAt = Instant.parse("2026-09-09T12:00:00Z");
        given(countryService.getAll()).willReturn(Map.of("NL", "Netherlands"));
        given(countryService.lastSuccessfulRefresh()).willReturn(Optional.of(refreshedAt));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("availableCountries", 1)
                .containsEntry("lastSuccessfulRefresh", refreshedAt.toString());
    }

    @Test
    @DisplayName("DOWN only when no data has ever been loaded")
    void downWhenNoDataAtAll() {
        given(countryService.getAll()).willReturn(Map.of());
        given(countryService.lastSuccessfulRefresh()).willReturn(Optional.empty());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("DOWN, with the cause attached, when the snapshot cannot be read at all")
    void downWhenTheCountryServiceFails() {
        given(countryService.getAll())
                .willThrow(new UpstreamUnavailableException("Could not reach the holiday API."));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("error", UpstreamUnavailableException.class.getName()
                        + ": Could not reach the holiday API.");
    }

    @Test
    @DisplayName("stays UP on a stale snapshot - a stale answer still answers")
    void upWhenSnapshotIsStale() {
        given(countryService.getAll()).willReturn(Map.of("NL", "Netherlands", "DE", "Germany"));
        given(countryService.lastSuccessfulRefresh())
                .willReturn(Optional.of(Instant.parse("2020-01-01T00:00:00Z")));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}
