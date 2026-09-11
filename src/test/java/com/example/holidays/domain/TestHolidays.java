package com.example.holidays.domain;

import java.time.LocalDate;

/**
 * Builders for the shapes tests keep needing. In test scope on purpose - the production record has
 * one constructor and does not need to know what a test finds convenient.
 */
public final class TestHolidays {

    private TestHolidays() {
    }

    /** What most tests mean by "a holiday". */
    public static Holiday publicHoliday(LocalDate date, String localName, String name) {
        return new Holiday(date, localName, name, true, true);
    }
}
