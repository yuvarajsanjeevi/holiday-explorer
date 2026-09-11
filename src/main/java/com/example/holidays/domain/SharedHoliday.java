package com.example.holidays.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A date both countries celebrate. Names stay per country because the countries disagree - 25
 * December is Eerste Kerstdag in NL and Erster Weihnachtstag in DE - and a set per country, because
 * one country can list two holidays on the same date. Keyed in request order.
 */
public record SharedHoliday(LocalDate date, Map<CountryCode, Set<String>> localNames) {

    /** Views, not copies: without this whoever built the map could still change it afterwards. */
    public SharedHoliday {
        Map<CountryCode, Set<String>> sealed = new LinkedHashMap<>();
        localNames.forEach((country, names) ->
                sealed.put(country, Collections.unmodifiableSet(names)));
        localNames = Collections.unmodifiableMap(sealed);
    }
}
