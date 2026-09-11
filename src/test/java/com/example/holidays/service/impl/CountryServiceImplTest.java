package com.example.holidays.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CountryServiceImplTest {

    private static final Map<String, String> COUNTRIES = Map.of("NL", "Netherlands", "DE", "Germany");

    private NagerDateClient client;
    private CountryServiceImpl countryService;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(NagerDateClient.class);
        countryService = new CountryServiceImpl(client);
    }

    @Test
    @DisplayName("the scheduled refresh fetches upstream rather than reading the cache it updates")
    void refreshBypassesTheCache() {
        given(client.refreshAvailableCountries()).willReturn(COUNTRIES);

        countryService.refresh();

        verify(client).refreshAvailableCountries();
        verify(client, never()).availableCountries();
    }

    @Test
    @DisplayName("reads go through the cached lookup, not a fresh fetch")
    void readsUseTheCachedLookup() {
        given(client.availableCountries()).willReturn(COUNTRIES);

        assertThat(countryService.getAll()).isEqualTo(COUNTRIES);

        verify(client).availableCountries();
        verify(client, never()).refreshAvailableCountries();
    }

    @Test
    @DisplayName("records when the list was last actually fetched from upstream")
    void recordsRefreshTime() {
        given(client.refreshAvailableCountries()).willReturn(COUNTRIES);
        Instant before = Instant.now();

        countryService.refresh();

        assertThat(countryService.lastSuccessfulRefresh())
                .hasValueSatisfying(at -> assertThat(at).isBetween(before, Instant.now()));
    }

    @Test
    @DisplayName("startup survives the upstream API being down")
    void startupSurvivesUpstreamOutage() {
        given(client.refreshAvailableCountries()).willThrow(new UpstreamUnavailableException("down"));

        countryService.loadOnStartup(); // must not throw: an outage must not stop the app booting

        assertThat(countryService.lastSuccessfulRefresh()).isEmpty();
    }

    @Test
    @DisplayName("a failed refresh leaves the cached list to keep serving")
    void failedRefreshStillServesTheCachedList() {
        given(client.refreshAvailableCountries()).willThrow(new UpstreamUnavailableException("down"));
        given(client.availableCountries()).willReturn(COUNTRIES);

        countryService.refresh();

        assertThat(countryService.getAll())
                .as("a stale answer beats no answer")
                .isEqualTo(COUNTRIES);
    }

    @Test
    @DisplayName("names every unknown code in a single failure")
    void reportsGetAllUnknownCodes() {
        given(client.availableCountries()).willReturn(COUNTRIES);
        List<CountryCode> requested =
                List.of(CountryCode.of("NL"), CountryCode.of("ZZ"), CountryCode.of("QQ"));

        assertThatThrownBy(() -> countryService.validateCountryCodes(requested))
                .isInstanceOf(UnknownCountryException.class)
                .hasMessageContaining("ZZ")
                .hasMessageContaining("QQ");
    }

    @Test
    @DisplayName("accepts codes that are published upstream")
    void acceptsKnownCodes() {
        given(client.availableCountries()).willReturn(COUNTRIES);

        countryService.validateCountryCodes(List.of(CountryCode.of("NL"), CountryCode.of("DE")));
    }
}
