package com.example.holidays.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WeekendCalendarTest {

    private static final CountryCode NL = CountryCode.of("NL");
    private static final CountryCode EG = CountryCode.of("EG");

    @ParameterizedTest
    @DisplayName("most countries work Monday to Friday")
    @CsvSource({
            "2026-01-05, MONDAY,    true",
            "2026-01-08, THURSDAY,  true",
            "2026-01-09, FRIDAY,    true",
            "2026-01-10, SATURDAY,  false",
            "2026-01-11, SUNDAY,    false"})
    void defaultsToSaturdaySunday(LocalDate date, String expectedDay, boolean working) {
        assertThat(date.getDayOfWeek().name()).isEqualTo(expectedDay);
        assertThat(WeekendCalendar.isWorkingDay(date, NL)).isEqualTo(working);
    }

    @ParameterizedTest
    @DisplayName("Egypt works Sunday to Thursday, so Friday is off and Sunday is not")
    @CsvSource({
            "2026-01-08, THURSDAY,  true",
            "2026-01-09, FRIDAY,    false",
            "2026-01-10, SATURDAY,  false",
            "2026-01-11, SUNDAY,    true"})
    void egyptRestsFridayAndSaturday(LocalDate date, String expectedDay, boolean working) {
        assertThat(date.getDayOfWeek().name()).isEqualTo(expectedDay);
        assertThat(WeekendCalendar.isWorkingDay(date, EG)).isEqualTo(working);
    }

    @ParameterizedTest
    @DisplayName("every Friday-Saturday country is covered, published by the API or not")
    @ValueSource(strings = {"BD", "BH", "DJ", "DZ", "EG", "IQ", "LY", "SD", "SO", "SY", "YE",
            "IL", "JO", "KW", "MV", "OM", "PS", "QA", "SA"})
    void fridaySaturdayCountries(String code) {
        assertThat(WeekendCalendar.weekendIn(CountryCode.of(code)))
                .containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    }

    @Test
    @DisplayName("an unlisted country falls back to Saturday and Sunday")
    void unlistedCountryFallsBack() {
        assertThat(WeekendCalendar.weekendIn(CountryCode.of("ZZ")))
                .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("the lookup is case-insensitive, because CountryCode normalises")
    void lookupIsCaseInsensitive() {
        assertThat(WeekendCalendar.weekendIn(CountryCode.of("eg")))
                .containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    }
}
