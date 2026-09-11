package com.example.holidays.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the shared-holidays query answers. The country names sit here rather than on each
 * date: they are the same for the whole result, so repeating them per date would be noise.
 */
public record SharedHolidays(int year, Map<CountryCode, String> countryNames,
        List<SharedHoliday> commonHolidays) {

    /** Views, not copies, and the countries keep the order they were asked in. */
    public SharedHolidays {
        countryNames = Collections.unmodifiableMap(new LinkedHashMap<>(countryNames));
        commonHolidays = List.copyOf(commonHolidays);
    }
}
