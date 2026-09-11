package com.example.holidays.client;

import static com.example.holidays.domain.TestHolidays.publicHoliday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.holidays.config.NagerDateProperties;
import com.example.holidays.config.RestClientConfig;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.Holiday;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamRejectedException;
import com.example.holidays.exception.UpstreamUnavailableException;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The HTTP edge: JSON binding, status-to-exception mapping, retries. Uses
 * {@link MockRestServiceServer} so there is no port to bind, but the real RestClient still runs.
 */
class RestNagerDateClientTest {

    private static final String BASE_URL = "https://date.nager.at/api/v3";
    private static final CountryCode NL = CountryCode.of("NL");
    private static final NagerDateProperties PROPERTIES =
            new NagerDateProperties(BASE_URL, 2, Duration.ofMillis(1), Duration.ofMillis(1), 3,
                    "0 0 */6 * * *", 50);

    private MockRestServiceServer server;
    private RestNagerDateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .configureMessageConverters(converters ->
                        converters.withJsonConverter(new JacksonJsonHttpMessageConverter()))
                .baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RetryTemplate retryTemplate = new RestClientConfig().nagerRetryTemplate(PROPERTIES);
        client = new RestNagerDateClient(builder.build(), retryTemplate);
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("fixtures/" + name).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("binds the upstream payload to the domain model, ignoring fields we do not use")
    void bindsHolidays() throws IOException {
        server.expect(requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture("public-holidays-2026-NL.json"), MediaType.APPLICATION_JSON));

        List<Holiday> holidays = client.publicHolidays(2026, NL);

        server.verify();
        assertThat(holidays).isNotEmpty();
        assertThat(holidays.getFirst().date())
                .as("the client sorts most recent first")
                .isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(holidays.getLast())
                .isEqualTo(publicHoliday(LocalDate.of(2026, 1, 1), "Nieuwjaarsdag", "New Year's Day"));
        assertThat(holidays).isSortedAccordingTo(Comparator.comparing(Holiday::date).reversed());
    }

    @Test
    @DisplayName("keeps every entry but tags which ones are public holidays")
    void tagsPublicHolidaysWithoutDroppingTheRest() throws IOException {
        server.expect(requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withSuccess(fixture("public-holidays-2026-NL.json"), MediaType.APPLICATION_JSON));

        List<Holiday> holidays = client.publicHolidays(2026, NL);

        // A real response. Goede Vrijdag and Bevrijdingsdag are School/Authorities: still
        // celebrated, so they stay in the list and carry the flag instead of being dropped.
        assertThat(holidays).hasSize(11);
        assertThat(holidays).filteredOn(Holiday::publicHoliday).hasSize(9);
        assertThat(holidays).filteredOn(holiday -> !holiday.publicHoliday())
                .extracting(Holiday::localName)
                .containsExactlyInAnyOrder("Goede Vrijdag", "Bevrijdingsdag");
    }

    @Test
    @DisplayName("an entry with no types is not tagged as a public holiday")
    void treatsMissingTypesAsNotPublic() {
        server.expect(requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withSuccess("""
                        [{"date":"2026-01-01","localName":"Untagged","name":"Untagged"},
                         {"date":"2026-01-02","localName":"Real","name":"Real","types":["Public"]}]""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.publicHolidays(2026, NL))
                .filteredOn(Holiday::publicHoliday)
                .extracting(Holiday::localName)
                .containsExactly("Real");
    }

    @Test
    @DisplayName("a type the API has not used before does not break binding")
    void toleratesAnUnknownType() {
        server.expect(requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withSuccess("""
                        [{"date":"2026-01-02","localName":"New kind","name":"New kind",
                          "types":["Seventh"]},
                         {"date":"2026-01-01","localName":"Real","name":"Real","types":["Public"]}]""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.publicHolidays(2026, NL))
                .filteredOn(Holiday::publicHoliday)
                .extracting(Holiday::localName)
                .containsExactly("Real");
    }

    @Test
    @DisplayName("binds the available-countries payload into a code-to-name map")
    void bindsCountries() throws IOException {
        server.expect(requestTo(BASE_URL + "/AvailableCountries"))
                .andRespond(withSuccess(fixture("available-countries.json"), MediaType.APPLICATION_JSON));

        assertThat(client.availableCountries()).containsEntry("NL", "Netherlands").containsEntry("DE", "Germany");
        server.verify();
    }

    @Test
    @DisplayName("maps 404 to UnknownCountryException without retrying")
    void mapsNotFound() {
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.publicHolidays(2026, NL))
                .isInstanceOf(UnknownCountryException.class)
                .hasMessageContaining("NL");
        server.verify(); // exactly one call: a 404 will not become a 200 on a second try
    }

    @Test
    @DisplayName("maps other 4xx to UpstreamRejectedException - in practice an unsupported year")
    void mapsBadRequest() {
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/PublicHolidays/1800/NL"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.publicHolidays(1800, NL))
                .isInstanceOf(UpstreamRejectedException.class)
                .hasMessageContaining("1800");
        server.verify();
    }

    @Test
    @DisplayName("retries a 5xx and succeeds on a later attempt")
    void retriesServerErrors() throws IOException {
        server.expect(ExpectedCount.times(2), requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withSuccess(fixture("public-holidays-2026-NL.json"), MediaType.APPLICATION_JSON));

        assertThat(client.publicHolidays(2026, NL)).isNotEmpty();
        server.verify(); // 1 initial attempt + 2 retries
    }

    @Test
    @DisplayName("gives up with UpstreamUnavailableException once the retries are exhausted")
    void failsAfterExhaustingRetries() {
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.publicHolidays(2026, NL))
                .isInstanceOf(UpstreamUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("keeps the last entry when the payload repeats a country code")
    void toleratesDuplicateCountryCodes() {
        server.expect(requestTo(BASE_URL + "/AvailableCountries"))
                .andRespond(withSuccess("""
                        [{"countryCode":"NL","name":"Holland"},{"countryCode":"NL","name":"Netherlands"}]""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.availableCountries())
                .as("a duplicate in someone else's payload must not become a 500")
                .containsExactly(java.util.Map.entry("NL", "Netherlands"));
        server.verify();
    }

    @Test
    @DisplayName("maps a 5xx on the country list to UpstreamUnavailableException once retries run out")
    void mapsServerErrorOnCountryList() {
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/AvailableCountries"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.availableCountries())
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("500");
        server.verify();
    }

    @Test
    @DisplayName("maps an unreachable host to UpstreamUnavailableException, naming the call")
    void mapsTransportFailure() {
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/PublicHolidays/2026/NL"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection refused");
                });

        assertThatThrownBy(() -> client.publicHolidays(2026, NL))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("public holidays for NL in 2026")
                .hasRootCauseInstanceOf(ResourceAccessException.class);
        server.verify(); // a transport failure is retried like a 5xx
    }
}
