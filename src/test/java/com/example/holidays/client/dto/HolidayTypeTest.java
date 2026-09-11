package com.example.holidays.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HolidayTypeTest {

    @ParameterizedTest
    @DisplayName("every type the API actually sends maps to a constant")
    @CsvSource({
            "Public,      PUBLIC",
            "Bank,        BANK",
            "School,      SCHOOL",
            "Authorities, AUTHORITIES",
            "Optional,    OPTIONAL",
            "Observance,  OBSERVANCE"})
    void mapsEveryKnownType(String wire, HolidayType expected) {
        assertThat(HolidayType.from(wire)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("an unknown type is null, not an exception")
    @ValueSource(strings = {"Seventh", "", "public holiday"})
    void unknownTypeIsNull(String wire) {
        // The API adding a type should not break every request.
        assertThat(HolidayType.from(wire)).isNull();
    }

    @Test
    @DisplayName("null in, null out")
    void nullIsNull() {
        assertThat(HolidayType.from(null)).isNull();
    }

    @Test
    @DisplayName("the six values are all there, so nothing silently went missing")
    void hasAllSixValues() {
        assertThat(HolidayType.values()).containsExactlyInAnyOrder(
                HolidayType.PUBLIC, HolidayType.BANK, HolidayType.SCHOOL,
                HolidayType.AUTHORITIES, HolidayType.OPTIONAL, HolidayType.OBSERVANCE);
    }
}
