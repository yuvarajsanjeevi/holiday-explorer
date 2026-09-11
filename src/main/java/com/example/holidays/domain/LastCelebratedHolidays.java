package com.example.holidays.domain;

import java.util.List;

/**
 * The answer to the last-celebrated query. Carries the country name so the web layer does not have
 * to look up a domain fact of its own, the same way the other two queries resolve it.
 */
public record LastCelebratedHolidays(CountryCode countryCode, String countryName, List<Holiday> holidays) {

    public LastCelebratedHolidays {
        holidays = List.copyOf(holidays);
    }
}
