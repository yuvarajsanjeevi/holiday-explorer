package com.example.holidays.web.mapper;

import static com.example.holidays.domain.TestHolidays.publicHoliday;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.CountryHolidayCount;
import com.example.holidays.domain.Holiday;
import com.example.holidays.domain.LastCelebratedHolidays;
import com.example.holidays.domain.SharedHoliday;
import com.example.holidays.web.dto.LastHolidaysResponse;
import com.example.holidays.web.dto.SharedHolidayResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolidayMapperTest {

    private static final CountryCode NL = CountryCode.of("NL");
    private static final CountryCode DE = CountryCode.of("DE");

    private final HolidayMapper mapper = new HolidayMapper();

    private static SharedHoliday sharedOn(LocalDate date, CountryCode first, String firstName,
            CountryCode second, String secondName) {
        Map<CountryCode, Set<String>> names = new LinkedHashMap<>();
        names.put(first, new LinkedHashSet<>(List.of(firstName)));
        names.put(second, new LinkedHashSet<>(List.of(secondName)));
        return new SharedHoliday(date, names);
    }

    @Test
    @DisplayName("maps a holiday field for field")
    void mapsHoliday() {
        Holiday holiday = publicHoliday(LocalDate.of(2026, 4, 27), "Koningsdag", "King's Day");

        assertThat(mapper.toResponse(holiday))
                .satisfies(response -> {
                    assertThat(response.date()).isEqualTo(LocalDate.of(2026, 4, 27));
                    assertThat(response.localName()).isEqualTo("Koningsdag");
                    assertThat(response.name()).isEqualTo("King's Day");
                });
    }

    @Test
    @DisplayName("unwraps the country code to a plain string in the count response")
    void mapsCount() {
        CountryHolidayCount count = new CountryHolidayCount(NL, "Netherlands", 8);

        assertThat(mapper.toResponse(count))
                .satisfies(response -> {
                    assertThat(response.countryCode()).isEqualTo("NL");
                    assertThat(response.countryName()).isEqualTo("Netherlands");
                    assertThat(response.weekdayHolidayCount()).isEqualTo(8);
                });
    }

    @Test
    @DisplayName("echoes the country and the date the answer was computed against")
    void mapsLastHolidays() {
        LocalDate asOf = LocalDate.of(2026, 6, 1);
        List<Holiday> holidays = List.of(publicHoliday(LocalDate.of(2026, 5, 14), "Hemelvaartsdag", "Ascension Day"));

        LastHolidaysResponse response = mapper.toLastHolidaysResponse(
                new LastCelebratedHolidays(NL, "Netherlands", holidays), asOf);

        assertThat(response.countryCode()).isEqualTo("NL");
        assertThat(response.countryName()).isEqualTo("Netherlands");
        assertThat(response.asOf()).isEqualTo(asOf);
        assertThat(response.holidays()).singleElement()
                .extracting(com.example.holidays.web.dto.HolidayResponse::localName)
                .isEqualTo("Hemelvaartsdag");
    }

    @Test
    @DisplayName("keeps the caller's country order, because Jackson serialises keys in iteration order")
    void preservesCountryKeyOrder() {
        SharedHoliday nlFirst = sharedOn(LocalDate.of(2026, 12, 25), NL, "Eerste Kerstdag", DE, "Erster Weihnachtstag");
        SharedHoliday deFirst = sharedOn(LocalDate.of(2026, 12, 25), DE, "Erster Weihnachtstag", NL, "Eerste Kerstdag");

        assertThat(mapper.toResponse(nlFirst).localNames().keySet()).containsExactly("NL", "DE");
        assertThat(mapper.toResponse(deFirst).localNames().keySet()).containsExactly("DE", "NL");
    }

    @Test
    @DisplayName("carries every local name a country lists for a shared date")
    void keepsAllLocalNamesPerCountry() {
        Map<CountryCode, Set<String>> names = new LinkedHashMap<>();
        names.put(NL, new LinkedHashSet<>(List.of("Bevrijdingsdag", "Dodenherdenking")));
        names.put(DE, new LinkedHashSet<>(List.of("Maifeiertag")));

        SharedHolidayResponse response = mapper.toResponse(new SharedHoliday(LocalDate.of(2026, 5, 5), names));

        assertThat(response.localNames().get("NL")).containsExactly("Bevrijdingsdag", "Dodenherdenking");
        assertThat(response.localNames().get("DE")).containsExactly("Maifeiertag");
    }

    @Test
    @DisplayName("maps empty collections to empty lists rather than null")
    void mapsEmptyCollections() {
        assertThat(mapper.toHolidayResponses(List.of())).isEmpty();
        assertThat(mapper.toCountResponses(List.of())).isEmpty();
        assertThat(mapper.toSharedResponses(List.of())).isEmpty();
    }
}
