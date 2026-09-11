package com.example.holidays.client;

import com.example.holidays.client.dto.HolidayType;
import com.example.holidays.client.dto.NagerCountryDto;
import com.example.holidays.client.dto.NagerHolidayDto;
import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.Holiday;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamRejectedException;
import com.example.holidays.exception.UpstreamUnavailableException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** {@link NagerDateClient} over HTTP. Callers go through the interface, so the caching proxy is always in the way. */
@Component
public class RestNagerDateClient implements NagerDateClient {

    private static final Logger log = LoggerFactory.getLogger(RestNagerDateClient.class);

    private static final ParameterizedTypeReference<List<NagerHolidayDto>> HOLIDAY_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<NagerCountryDto>> COUNTRY_LIST =
            new ParameterizedTypeReference<>() {};

    private static final Comparator<Holiday> MOST_RECENT_FIRST = Comparator.comparing(Holiday::date).reversed();

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    public RestNagerDateClient(RestClient nagerRestClient, RetryTemplate nagerRetryTemplate) {
        this.restClient = nagerRestClient;
        this.retryTemplate = nagerRetryTemplate;
    }

    @Override
    @Cacheable(
            cacheNames = ApplicationConstants.HOLIDAYS_CACHE,
            key = ApplicationConstants.HOLIDAYS_CACHE_KEY
    )
    public List<Holiday> publicHolidays(int year, CountryCode countryCode) {
        // Cached, so this body only runs on a miss - these lines are the miss log.
        log.debug("GET /PublicHolidays/{}/{}", year, countryCode);
        List<NagerHolidayDto> payload = call(
                "public holidays for %s in %d".formatted(countryCode, year),
                () -> restClient.get()
                        .uri("/PublicHolidays/{year}/{code}", year, countryCode.value())
                        .retrieve()
                        .onStatus(HttpStatus.NOT_FOUND::equals, (request, response) -> {
                            throw new UnknownCountryException(
                                    "Unknown country code '%s'.".formatted(countryCode));
                        })
                        .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                            throw new UpstreamRejectedException(
                                    "The holiday API rejected year %d for country '%s'."
                                            .formatted(year, countryCode));
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                            throw new UpstreamUnavailableException(
                                    "The holiday API returned %s.".formatted(response.getStatusCode()));
                        })
                        .body(HOLIDAY_LIST));

        List<Holiday> holidays = payload.stream()
                .map(dto -> new Holiday(dto.date(), dto.localName(), dto.name(),
                        isPublicHoliday(dto), Boolean.TRUE.equals(dto.global())))
                .sorted(MOST_RECENT_FIRST)
                .toList();
        log.debug("{} returned {} holidays for {}", countryCode, holidays.size(), year);
        return holidays;
    }

    /**
     * The endpoint returns a superset - school days, bank days and observances too, tagged in
     * {@code types}. We cache them all, since "celebrated in both countries" should include Dutch
     * Good Friday, and only the query that counts days off filters on this flag. A Bank-only day is
     * not a day off for most people: Denmark's Banklukkedag, Irish Good Friday.
     */
    private static boolean isPublicHoliday(NagerHolidayDto dto) {
        return dto.types() != null && dto.types().contains(HolidayType.PUBLIC);
    }

    @Override
    @Cacheable(ApplicationConstants.COUNTRIES_CACHE)
    public Map<String, String> availableCountries() {
        return fetchAvailableCountries();
    }

    /**
     * {@code @CachePut}, not {@code @Cacheable}: always runs and overwrites. Cached, it would read
     * back its own copy and never see a new country. Neither method takes arguments, so both land
     * on the same key.
     */
    @Override
    @CachePut(ApplicationConstants.COUNTRIES_CACHE)
    public Map<String, String> refreshAvailableCountries() {
        return fetchAvailableCountries();
    }

    private Map<String, String> fetchAvailableCountries() {
        log.debug("GET /AvailableCountries");
        List<NagerCountryDto> payload = call("available countries",
                () -> restClient.get()
                        .uri("/AvailableCountries")
                        .retrieve()
                        .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                            throw new UpstreamUnavailableException(
                                    "The holiday API returned %s.".formatted(response.getStatusCode()));
                        })
                        .body(COUNTRY_LIST));

        return payload.stream().collect(Collectors.toMap(
                NagerCountryDto::countryCode, NagerCountryDto::name, (first, second) -> second));
    }

    /**
     * {@link ResourceAccessException} becomes {@link UpstreamUnavailableException} so the retry
     * policy has one type to match on, and {@link RetryException} is unwrapped so callers only ever
     * see our own exceptions.
     */
    private <T> T call(String description, Supplier<T> operation) {
        try {
            return retryTemplate.execute(() -> {
                try {
                    return operation.get();
                } catch (ResourceAccessException e) {
                    log.debug("Could not reach the holiday API for {}; the retry policy decides next",
                            description, e);
                    throw new UpstreamUnavailableException(
                            "Could not reach the holiday API for %s.".formatted(description), e);
                }
            });
        } catch (RetryException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new UpstreamUnavailableException(
                    "Could not retrieve %s from the holiday API.".formatted(description), cause);
        }
    }
}
