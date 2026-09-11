package com.example.holidays.domain;

import java.time.LocalDate;

/**
 * A holiday, reduced to the fields we report on. {@code publicHoliday} means an actual day off
 * rather than a school day, bank day or observance; {@code nationwide} means the whole country
 * rather than a region of it.
 */
public record Holiday(
        LocalDate date, String localName, String name, boolean publicHoliday, boolean nationwide) {
}
