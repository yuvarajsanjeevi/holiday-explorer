package com.example.holidays.web;

import static com.example.holidays.domain.TestHolidays.publicHoliday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.CountryHolidayCount;
import com.example.holidays.domain.Holiday;
import com.example.holidays.domain.LastCelebratedHolidays;
import com.example.holidays.domain.SharedHoliday;
import com.example.holidays.domain.SharedHolidays;
import com.example.holidays.exception.InvalidRequestException;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamRejectedException;
import com.example.holidays.exception.UpstreamUnavailableException;
import com.example.holidays.service.HolidayService;
import com.example.holidays.web.mapper.HolidayMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Routing, JSON shape and the exception-to-status mapping. The service is mocked, so this covers
 * only the web layer - the queries are in {@code HolidayServiceImplTest}.
 */
@WebMvcTest(HolidayController.class)
@Import(HolidayMapper.class)
class HolidayControllerTest {

    private static final CountryCode NL = CountryCode.of("NL");
    private static final CountryCode DE = CountryCode.of("DE");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private HolidayService service;

    @Test
    @DisplayName("returns the last celebrated holidays with the date the answer was computed for")
    void returnsLastCelebrated() {
        given(service.lastCelebratedHolidays(eq(NL), eq(3), any()))
                .willReturn(new LastCelebratedHolidays(NL, "Netherlands", List.of(
                        publicHoliday(LocalDate.of(2026, 5, 25), "Tweede Pinksterdag", "Whit Monday"),
                        publicHoliday(LocalDate.of(2026, 5, 14), "Hemelvaartsdag", "Ascension Day"))));

        assertThat(mvc.get().uri("/api/v1/countries/NL/holidays/last"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.countryCode").isEqualTo("NL");
                    assertThat(json).extractingPath("$.countryName").isEqualTo("Netherlands");
                    assertThat(json).extractingPath("$.asOf").isEqualTo(LocalDate.now().toString());
                    assertThat(json).extractingPath("$.holidays[0].localName").isEqualTo("Tweede Pinksterdag");
                    assertThat(json).extractingPath("$.holidays").asArray().hasSize(2);
                });
    }

    @Test
    @DisplayName("normalises a lower-case country code before delegating")
    void normalisesPathVariable() {
        given(service.lastCelebratedHolidays(eq(NL), eq(3), any()))
                .willReturn(new LastCelebratedHolidays(NL, "Netherlands", List.of()));

        assertThat(mvc.get().uri("/api/v1/countries/nl/holidays/last")).hasStatusOk();
    }

    @Test
    @DisplayName("preserves the service's descending ordering in the response")
    void returnsWeekdayCountsInOrder() {
        Set<CountryCode> requested = new LinkedHashSet<>(List.of(NL, DE));
        given(service.weekdayHolidayCounts(eq(2026), eq(requested), any())).willReturn(List.of(
                new CountryHolidayCount(DE, "Germany", 10),
                new CountryHolidayCount(NL, "Netherlands", 8)));

        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?year=2026&countryCodes=NL,DE"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$[0].countryCode").isEqualTo("DE");
                    assertThat(json).extractingPath("$[0].weekdayHolidayCount").isEqualTo(10);
                    assertThat(json).extractingPath("$[1].countryCode").isEqualTo("NL");
                });
    }

    @Test
    @DisplayName("renders shared holidays with local names keyed by country code")
    void returnsSharedHolidays() {
        Map<CountryCode, Set<String>> names = new LinkedHashMap<>();
        names.put(NL, new LinkedHashSet<>(List.of("Eerste Kerstdag")));
        names.put(DE, new LinkedHashSet<>(List.of("Erster Weihnachtstag")));
        Map<CountryCode, String> countries = new LinkedHashMap<>();
        countries.put(NL, "Netherlands");
        countries.put(DE, "Germany");
        given(service.sharedHolidays(eq(2026), eq(NL), eq(DE), any()))
                .willReturn(new SharedHolidays(2026, countries,
                        List.of(new SharedHoliday(LocalDate.of(2026, 12, 25), names))));

        assertThat(mvc.get().uri("/api/v1/holidays/shared?year=2026&first=NL&second=DE"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.year").isEqualTo(2026);
                    assertThat(json).extractingPath("$.countries[0].countryCode").isEqualTo("NL");
                    assertThat(json).extractingPath("$.countries[0].countryName").isEqualTo("Netherlands");
                    assertThat(json).extractingPath("$.countries[1].countryName").isEqualTo("Germany");
                    assertThat(json).extractingPath("$.commonHolidays[0].date").isEqualTo("2026-12-25");
                    assertThat(json).extractingPath("$.commonHolidays[0].localNames.NL[0]").isEqualTo("Eerste Kerstdag");
                    assertThat(json).extractingPath("$.commonHolidays[0].localNames.DE[0]").isEqualTo("Erster Weihnachtstag");
                });
    }

    @Test
    @DisplayName("renders a malformed country code as a 400")
    void malformedCountryCodeIsBadRequest() {
        assertThat(mvc.get().uri("/api/v1/countries/TOOLONG/holidays/last"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.title").isEqualTo("Invalid request"));
    }

    @Test
    @DisplayName("renders an unknown country as 404")
    void unknownCountryIsNotFound() {
        willThrow(new UnknownCountryException("Unknown country code(s): ZZ."))
                .given(service).lastCelebratedHolidays(any(), anyInt(), any());

        assertThat(mvc.get().uri("/api/v1/countries/ZZ/holidays/last"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.detail")
                        .isEqualTo("Unknown country code(s): ZZ."));
    }

    @Test
    @DisplayName("renders an invalid request from the service as 400")
    void invalidRequestIsBadRequest() {
        willThrow(new InvalidRequestException("Year 1800 is outside the supported range 1976-2076."))
                .given(service).weekdayHolidayCounts(anyInt(), any(), any());

        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?year=1800&countryCodes=NL"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("renders an upstream rejection as 400 - the caller asked for something upstream refuses")
    void upstreamRejectionIsBadRequest() {
        willThrow(new UpstreamRejectedException("The holiday API rejected year 2026 for country 'NL'."))
                .given(service).weekdayHolidayCounts(anyInt(), any(), any());

        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?year=2026&countryCodes=NL"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.title").isEqualTo("Invalid request");
                    assertThat(json).extractingPath("$.detail")
                            .isEqualTo("The holiday API rejected year 2026 for country 'NL'.");
                });
    }

    @Test
    @DisplayName("renders an upstream outage as 502, not 500")
    void upstreamOutageIsBadGateway() {
        willThrow(new UpstreamUnavailableException("The holiday API returned 503."))
                .given(service).weekdayHolidayCounts(anyInt(), any(), any());

        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?year=2026&countryCodes=NL"))
                .hasStatus(HttpStatus.BAD_GATEWAY)
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.title")
                        .isEqualTo("Holiday API unavailable"));
    }

    @Test
    @DisplayName("renders an unanticipated failure as 500, telling the caller nothing about the cause")
    void unexpectedFailureIsInternalError() {
        willThrow(new IllegalStateException("NullPointer at com.example.internals.Secret line 42"))
                .given(service).weekdayHolidayCounts(anyInt(), any(), any());

        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?year=2026&countryCodes=NL"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.title").isEqualTo("Internal error");
                    assertThat(json).extractingPath("$.detail")
                            .as("internal detail must stay in the log, not leak to the caller")
                            .isEqualTo("The request could not be completed.");
                    assertThat(json).extractingPath("$.type")
                            .isEqualTo("https://example.com/holiday-explorer/errors/internal-error");
                });
    }

    @Test
    @DisplayName("renders a missing required parameter as 400")
    void missingParameterIsBadRequest() {
        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?countryCodes=NL"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.detail")
                        .isEqualTo("Required query parameter 'year' is missing."));
    }

    @Test
    @DisplayName("a mistyped path is 404, not a 500 from the catch-all")
    void unknownPathIsNotFound() {
        assertThat(mvc.get().uri("/api/v1/nope"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.title").isEqualTo("Not found"));
    }

    @Test
    @DisplayName("the wrong verb on a real path is 405, not a 500")
    void wrongMethodIsNotAllowed() {
        assertThat(mvc.post().uri("/api/v1/holidays/shared"))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.title").isEqualTo("Method not allowed"));
    }

    @Test
    @DisplayName("renders a non-numeric year as 400")
    void nonNumericYearIsBadRequest() {
        assertThat(mvc.get().uri("/api/v1/holidays/weekday-counts?year=abc&countryCodes=NL"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
