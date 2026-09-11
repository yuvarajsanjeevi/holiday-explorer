package com.example.holidays.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * How the API classifies an entry. One holiday can carry several: US New Year's Day is Public and
 * Bank, Danish Banklukkedag is Bank, Optional and School.
 */
public enum HolidayType {

    /** An actual public holiday. The only one that counts as a day off. */
    PUBLIC,

    /** Banks close, but it is not a public holiday: Danish Banklukkedag, Irish Good Friday. */
    BANK,

    SCHOOL,

    AUTHORITIES,

    OPTIONAL,

    OBSERVANCE;

    /** Unknown values become {@code null}, so a seventh type upstream does not break every request. */
    @JsonCreator
    static HolidayType from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownToUs) {
            return null;
        }
    }
}
