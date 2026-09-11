package com.example.holidays.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.example.holidays.config.NagerDateProperties;
import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.CountryHolidayCount;
import com.example.holidays.domain.Holiday;
import com.example.holidays.domain.SharedHoliday;
import com.example.holidays.exception.InvalidRequestException;
import com.example.holidays.exception.UnknownCountryException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The three queries. The date is pinned and the client is an in-memory stub, so nothing here touches
 * the network or starts a Spring context.
 */
class HolidayServiceImplTest {

    /** Every test that does not care about the date uses this one. */
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private static final CountryCode NL = CountryCode.of("NL");
    private static final CountryCode DE = CountryCode.of("DE");
    private static final CountryCode US = CountryCode.of("US");

    private static final NagerDateProperties PROPERTIES = new NagerDateProperties(
            "http://localhost", 0, Duration.ZERO, Duration.ZERO, 3, "0 0 */6 * * *", 50);

    private static HolidayServiceImpl serviceWith(StubNagerDateClient client) {
        CountryServiceImpl countryService = new CountryServiceImpl(client);
        // @PostConstruct does not run here, so warm the list the way startup would.
        countryService.loadOnStartup();
        return new HolidayServiceImpl(client, countryService, PROPERTIES);
    }

    private static Set<CountryCode> codes(CountryCode... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Nested
    @DisplayName("last celebrated holidays")
    class LastCelebrated {

        @Test
        @DisplayName("returns the three most recent past holidays, most recent first")
        void returnsThreeMostRecent() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01", "04-03", "04-06", "04-27", "05-14", "12-25");

            List<Holiday> result = serviceWith(client).lastCelebratedHolidays(NL, 3, TODAY).holidays();

            assertThat(result).extracting(Holiday::date).containsExactly(
                    LocalDate.of(2026, 5, 14), LocalDate.of(2026, 4, 27), LocalDate.of(2026, 4, 6));
        }

        @Test
        @DisplayName("the same holiday listed once per region counts once")
        void countsARegionallyRepeatedHolidayOnce() {
            // Switzerland lists Maria Himmelfahrt once per canton. Dates must be before TODAY.
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "CH", "05-15|Maria Himmelfahrt", "05-01|Bundesfeier")
                    .withRegionalHolidays(2026, "CH", "05-15|Maria Himmelfahrt");

            List<Holiday> result = serviceWith(client)
                    .lastCelebratedHolidays(CountryCode.of("CH"), 2, TODAY).holidays();

            assertThat(result).extracting(Holiday::localName)
                    .containsExactly("Maria Himmelfahrt", "Bundesfeier");
        }

        @Test
        @DisplayName("excludes a holiday falling today - it has not been celebrated yet")
        void excludesToday() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "04-27", "05-14", "06-01");

            List<Holiday> result = serviceWith(client).lastCelebratedHolidays(NL, 3, TODAY).holidays();

            assertThat(result).extracting(Holiday::date)
                    .doesNotContain(LocalDate.of(2026, 6, 1))
                    .containsExactly(LocalDate.of(2026, 5, 14), LocalDate.of(2026, 4, 27));
        }

        @Test
        @DisplayName("walks back into the previous year when the current one has too few")
        void rollsOverIntoPreviousYear() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01")
                    .withHolidays(2025, "NL", "12-25", "12-26");

            List<Holiday> result = serviceWith(client).lastCelebratedHolidays(NL, 3, LocalDate.parse("2026-01-02")).holidays();

            assertThat(result).extracting(Holiday::date).containsExactly(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 26), LocalDate.of(2025, 12, 25));
        }

        @Test
        @DisplayName("stops after one upstream call once the current year already satisfies the limit")
        void doesNotFetchEarlierYearsUnnecessarily() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01", "04-03", "04-06")
                    .withHolidays(2025, "NL", "12-25");

            serviceWith(client).lastCelebratedHolidays(NL, 3, TODAY);

            assertThat(client.holidayCalls())
                    .as("no earlier year can beat a full result set")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("gives up at the lookback limit and returns whatever it found")
        void stopsAtLookbackLimit() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withCountry("NL", "Netherlands")
                    .withHolidays(2023, "NL", "12-25");

            List<Holiday> result = serviceWith(client).lastCelebratedHolidays(NL, 3, TODAY).holidays();

            assertThat(result).isEmpty();
            assertThat(client.holidayCalls()).isEqualTo(3); // 2026, 2025, 2024 - then stop
        }

        @Test
        @DisplayName("returns fewer than requested rather than failing")
        void returnsFewerWhenNotEnoughExist() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");

            assertThat(serviceWith(client).lastCelebratedHolidays(NL, 3, TODAY).holidays()).hasSize(1);
        }

        @Test
        @DisplayName("rejects a non-positive limit")
        void rejectsNonPositiveLimit() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");
            HolidayServiceImpl service = serviceWith(client);

            assertThatThrownBy(() -> service.lastCelebratedHolidays(NL, 0, TODAY))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("limit must be between 1 and 100");
        }

        @Test
        @DisplayName("rejects a limit above the ceiling, so the cap is not just the lookback window")
        void rejectsLimitAboveCeiling() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");
            HolidayServiceImpl service = serviceWith(client);

            assertThatThrownBy(() -> service.lastCelebratedHolidays(
                    NL, ApplicationConstants.MAX_HOLIDAY_LIMIT + 1, TODAY))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("limit must be between 1 and 100");
        }

        @Test
        @DisplayName("accepts the ceiling itself")
        void acceptsTheCeiling() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");

            assertThat(serviceWith(client).lastCelebratedHolidays(
                    NL, ApplicationConstants.MAX_HOLIDAY_LIMIT, TODAY).holidays()).hasSize(1);
        }

        @Test
        @DisplayName("rejects a country the upstream API does not publish")
        void rejectsUnknownCountry() {
            StubNagerDateClient client = new StubNagerDateClient().withCountry("NL", "Netherlands");
            HolidayServiceImpl service = serviceWith(client);
            CountryCode unknown = CountryCode.of("ZZ");

            assertThatThrownBy(() -> service.lastCelebratedHolidays(unknown, 3, TODAY))
                    .isInstanceOf(UnknownCountryException.class)
                    .hasMessageContaining("ZZ");
        }
    }

    @Nested
    @DisplayName("weekday holiday counts")
    class WeekdayCounts {

        @Test
        @DisplayName("counts only Monday-to-Friday holidays and sorts descending")
        void countsWeekdaysAndSortsDescending() {
            // 2026-01-01 Thu, 01-03 Sat, 01-04 Sun, 01-05 Mon, 01-06 Tue
            StubNagerDateClient client = new StubNagerDateClient()
                    .withCountry("NL", "Netherlands").withCountry("DE", "Germany").withCountry("US", "USA")
                    .withHolidays(2026, "NL", "01-01", "01-03", "01-04")
                    .withHolidays(2026, "DE", "01-01", "01-05", "01-06")
                    .withHolidays(2026, "US", "01-03", "01-04");

            List<CountryHolidayCount> result =
                    serviceWith(client).weekdayHolidayCounts(2026, codes(NL, DE, US), TODAY);

            assertThat(result).extracting(c -> c.countryCode().value(), CountryHolidayCount::count)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("DE", 3L),
                            org.assertj.core.groups.Tuple.tuple("NL", 1L),
                            org.assertj.core.groups.Tuple.tuple("US", 0L));
        }

        @Test
        @DisplayName("counts only public holidays, not school days or observances")
        void countsOnlyPublicHolidays() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withCountry("NL", "Netherlands")
                    .withHolidays(2026, "NL", "01-01", "01-05")
                    .withNonPublicHolidays(2026, "NL", "01-06|Observance only");

            List<CountryHolidayCount> result =
                    serviceWith(client).weekdayHolidayCounts(2026, codes(NL), TODAY);

            assertThat(result).singleElement()
                    .extracting(CountryHolidayCount::count)
                    .as("the observance is celebrated but is not a day off")
                    .isEqualTo(2L);
        }

        @Test
        @DisplayName("counts only nationwide holidays, not regional ones")
        void countsOnlyNationwideHolidays() {
            // Without this, Switzerland reports 21 against the Netherlands' 9.
            StubNagerDateClient client = new StubNagerDateClient()
                    .withCountry("NL", "Netherlands")
                    .withHolidays(2026, "NL", "01-01", "01-05")
                    .withRegionalHolidays(2026, "NL", "01-06|One region only");

            List<CountryHolidayCount> result =
                    serviceWith(client).weekdayHolidayCounts(2026, codes(NL), TODAY);

            assertThat(result).singleElement()
                    .extracting(CountryHolidayCount::count)
                    .as("a holiday for one region is not a day off for the country")
                    .isEqualTo(2L);
        }

        @Test
        @DisplayName("breaks ties on country code so the order is deterministic")
        void breaksTiesByCountryCode() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withCountry("NL", "Netherlands").withCountry("DE", "Germany")
                    .withHolidays(2026, "NL", "01-01")
                    .withHolidays(2026, "DE", "01-01");

            List<CountryHolidayCount> result =
                    serviceWith(client).weekdayHolidayCounts(2026, codes(NL, DE), TODAY);

            assertThat(result).extracting(c -> c.countryCode().value()).containsExactly("DE", "NL");
        }

        @Test
        @DisplayName("resolves the English country name alongside the count")
        void includesCountryName() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withCountry("NL", "Netherlands").withHolidays(2026, "NL", "01-01");

            List<CountryHolidayCount> result =
                    serviceWith(client).weekdayHolidayCounts(2026, codes(NL), TODAY);

            assertThat(result).singleElement()
                    .extracting(CountryHolidayCount::countryName).isEqualTo("Netherlands");
        }

        @Test
        @DisplayName("names every unknown code in one failure instead of failing on the first")
        void reportsAllUnknownCodesAtOnce() {
            StubNagerDateClient client = new StubNagerDateClient().withCountry("NL", "Netherlands");
            HolidayServiceImpl service = serviceWith(client);
            Set<CountryCode> requested = codes(NL, CountryCode.of("ZZ"), CountryCode.of("QQ"));

            assertThatThrownBy(() -> service.weekdayHolidayCounts(2026, requested, TODAY))
                    .isInstanceOf(UnknownCountryException.class)
                    .hasMessageContaining("ZZ")
                    .hasMessageContaining("QQ");
        }

        @Test
        @DisplayName("rejects a year outside the upstream API's rolling window")
        void rejectsUnsupportedYear() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");
            HolidayServiceImpl service = serviceWith(client);
            Set<CountryCode> requested = codes(NL);

            assertThatThrownBy(() -> service.weekdayHolidayCounts(1800, requested, TODAY))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("1976-2076");
        }

        @Test
        @DisplayName("rejects a year past the far end of the window too, without calling upstream")
        void rejectsYearAboveTheWindow() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");
            HolidayServiceImpl service = serviceWith(client);
            Set<CountryCode> requested = codes(NL);

            assertThatThrownBy(() -> service.weekdayHolidayCounts(2077, requested, TODAY))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("2077")
                    .hasMessageContaining("1976-2076");
            assertThat(client.holidayCalls())
                    .as("the year is rejected before the network round trip")
                    .isZero();
        }

        @Test
        @DisplayName("accepts both edges of the window")
        void acceptsTheWindowEdges() {
            StubNagerDateClient client = new StubNagerDateClient().withCountry("NL", "Netherlands");
            HolidayServiceImpl service = serviceWith(client);
            Set<CountryCode> requested = codes(NL);

            assertThat(service.weekdayHolidayCounts(1976, requested, TODAY)).hasSize(1);
            assertThat(service.weekdayHolidayCounts(2076, requested, TODAY)).hasSize(1);
        }

        @Test
        @DisplayName("refuses an unreasonable number of countries in one request")
        void rejectsTooManyCountries() {
            StubNagerDateClient client = new StubNagerDateClient().withCountry("NL", "Netherlands");
            HolidayServiceImpl service = serviceWith(client);
            Set<CountryCode> tooMany = new LinkedHashSet<>();
            for (char first = 'A'; first <= 'Z'; first++) {
                for (char second = 'A'; second <= 'Z'; second++) {
                    tooMany.add(CountryCode.of("" + first + second));
                }
            }

            assertThatThrownBy(() -> service.weekdayHolidayCounts(2026, tooMany, TODAY))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("At most %d".formatted(ApplicationConstants.MAX_COUNTRIES_PER_REQUEST));
        }

        @Test
        @DisplayName("an empty request returns nothing, including with debug logging on")
        void acceptsAnEmptyRequest() {
            StubNagerDateClient client = new StubNagerDateClient().withCountry("NL", "Netherlands");
            HolidayServiceImpl service = serviceWith(client);

            // The result is only logged when debug is on, so at the default level this passes
            // whatever that line does. Turn it on and the line actually runs.
            ch.qos.logback.classic.Logger serviceLog =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HolidayServiceImpl.class);
            Level original = serviceLog.getLevel();
            serviceLog.setLevel(Level.DEBUG);
            try {
                assertThat(service.weekdayHolidayCounts(2026, Set.of(), TODAY)).isEmpty();
            } finally {
                serviceLog.setLevel(original);
            }
        }
    }

    @Nested
    @DisplayName("shared holidays")
    class Shared {

        @Test
        @DisplayName("returns only dates present in both countries, ascending, with both local names")
        void returnsIntersectionWithBothNames() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01|Nieuwjaarsdag", "04-27|Koningsdag", "12-25|Eerste Kerstdag")
                    .withHolidays(2026, "DE", "12-25|Erster Weihnachtstag", "10-03|Tag der Deutschen Einheit",
                            "01-01|Neujahr");

            List<SharedHoliday> result = serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays();

            assertThat(result).extracting(SharedHoliday::date)
                    .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 25));
            assertThat(result.getLast().localNames().get(NL)).containsExactly("Eerste Kerstdag");
            assertThat(result.getLast().localNames().get(DE)).containsExactly("Erster Weihnachtstag");
        }

        @Test
        @DisplayName("reports the caller's first country first, whichever side was indexed")
        void preservesRequestedCountryOrder() {
            // DE has fewer, so it is the side that gets scanned.
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01|Nieuwjaarsdag", "04-27|Koningsdag", "05-05|Bevrijdingsdag")
                    .withHolidays(2026, "DE", "01-01|Neujahr");

            List<SharedHoliday> result = serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays();

            assertThat(result).singleElement()
                    .satisfies(shared -> assertThat(shared.localNames().keySet())
                            .containsExactly(NL, DE));
        }

        @Test
        @DisplayName("reports the caller's first country first when it is the smaller side too")
        void preservesRequestedCountryOrderWhenFirstIsSmaller() {
            // Mirror of the test above: the scan runs over whichever side has fewer dates.
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01|Nieuwjaarsdag")
                    .withHolidays(2026, "DE", "01-01|Neujahr", "10-03|Tag der Deutschen Einheit",
                            "12-25|Erster Weihnachtstag");

            List<SharedHoliday> result = serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays();

            assertThat(result).singleElement().satisfies(shared -> {
                assertThat(shared.localNames().keySet()).containsExactly(NL, DE);
                assertThat(shared.localNames().get(NL)).containsExactly("Nieuwjaarsdag");
                assertThat(shared.localNames().get(DE)).containsExactly("Neujahr");
            });
        }

        @Test
        @DisplayName("emits a shared date once even when a country lists two holidays on it")
        void deduplicatesDatesWithMultipleHolidays() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "05-05|Bevrijdingsdag", "05-05|Dodenherdenking")
                    .withHolidays(2026, "DE", "05-05|Maifeiertag");

            List<SharedHoliday> result = serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().localNames().get(NL))
                    .containsExactly("Bevrijdingsdag", "Dodenherdenking");
        }

        @Test
        @DisplayName("the returned shared holiday cannot be mutated through the caller's map")
        void sharedHolidayIsImmutable() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "01-01|Nieuwjaarsdag")
                    .withHolidays(2026, "DE", "01-01|Neujahr");

            SharedHoliday shared =
                    serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays().getFirst();

            assertThatThrownBy(() -> shared.localNames().put(NL, new LinkedHashSet<>()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> shared.localNames().get(NL).add("smuggled"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("keeps a date both countries celebrate even when only one calls it a public holiday")
        void keepsNonPublicCelebrations() {
            // Good Friday 2026: Public in Germany, School/Authorities in the Netherlands.
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "DE", "04-03|Karfreitag")
                    .withNonPublicHolidays(2026, "NL", "04-03|Goede Vrijdag");

            List<SharedHoliday> result = serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays();

            assertThat(result).singleElement()
                    .satisfies(shared -> assertThat(shared.date()).isEqualTo(LocalDate.of(2026, 4, 3)));
        }

        @Test
        @DisplayName("keeps a regional holiday, because it is still celebrated")
        void keepsRegionalCelebrations() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withRegionalHolidays(2026, "NL", "03-01|Regional NL")
                    .withRegionalHolidays(2026, "DE", "03-01|Regional DE");

            List<SharedHoliday> result = serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays();

            assertThat(result).singleElement()
                    .satisfies(shared -> assertThat(shared.date()).isEqualTo(LocalDate.of(2026, 3, 1)));
        }

        @Test
        @DisplayName("returns an empty list when nothing overlaps")
        void returnsEmptyWhenNoOverlap() {
            StubNagerDateClient client = new StubNagerDateClient()
                    .withHolidays(2026, "NL", "04-27")
                    .withHolidays(2026, "DE", "10-03");

            assertThat(serviceWith(client).sharedHolidays(2026, NL, DE, TODAY).commonHolidays()).isEmpty();
        }

        @Test
        @DisplayName("rejects comparing a country with itself")
        void rejectsIdenticalCountries() {
            StubNagerDateClient client = new StubNagerDateClient().withHolidays(2026, "NL", "01-01");
            HolidayServiceImpl service = serviceWith(client);

            assertThatThrownBy(() -> service.sharedHolidays(2026, NL, CountryCode.of("nl"), TODAY))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("must differ");
        }
    }
}
