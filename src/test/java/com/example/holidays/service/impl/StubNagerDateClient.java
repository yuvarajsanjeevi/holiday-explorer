package com.example.holidays.service.impl;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.Holiday;
import com.example.holidays.exception.UnknownCountryException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.shaded.com.google.common.collect.Lists;

/**
 * An in-memory {@link NagerDateClient}. Hand-written rather than mocked so it can count calls -
 * several optimisations here are only visible as calls <em>not</em> made.
 */
class StubNagerDateClient implements NagerDateClient {

    private final Map<String, List<Holiday>> holidaysByYearAndCountry = new HashMap<>();
    private final Map<String, String> countries = new LinkedHashMap<>();
    private final AtomicInteger holidayCalls = new AtomicInteger();

    StubNagerDateClient withCountry(String code, String name) {
        countries.put(code, name);
        return this;
    }

    /** Entries the API would tag School, Bank or Observance: celebrated, but not days off. */
    StubNagerDateClient withNonPublicHolidays(int year, String countryCode, String... monthDays) {
        return withHolidays(year, countryCode, false, true, monthDays);
    }

    /** Holidays that apply to some regions only, the way Swiss cantonal days do. */
    StubNagerDateClient withRegionalHolidays(int year, String countryCode, String... monthDays) {
        return withHolidays(year, countryCode, true, false, monthDays);
    }

    /** Registers holidays as {@code "MM-DD"} strings, or {@code "MM-DD|localName"} to set the name. */
    StubNagerDateClient withHolidays(int year, String countryCode, String... monthDays) {
        return withHolidays(year, countryCode, true, true, monthDays);
    }

    private StubNagerDateClient withHolidays(
            int year, String countryCode, boolean publicHoliday, boolean nationwide,
            String... monthDays) {
        countries.putIfAbsent(countryCode, countryCode);
        List<Holiday> holidays = new ArrayList<>(monthDays.length);
        for (String entry : monthDays) {
            String[] parts = entry.split("\\|", 2);
            LocalDate date = LocalDate.parse("%d-%s".formatted(year, parts[0]));
            String localName = parts.length > 1 ? parts[1] : "Holiday " + parts[0];
            holidays.add(new Holiday(date, localName, "EN " + localName, publicHoliday, nationwide));
        }
        // Match RestNagerDateClient's order. If this drifts, these tests pass against an order
        // the real client never produces.
        // Add rather than replace: a country can have both public holidays and observances.
        holidaysByYearAndCountry
                .computeIfAbsent(key(year, countryCode), ignored -> new ArrayList<>())
                .addAll(holidays);
        holidaysByYearAndCountry.get(key(year, countryCode))
                .sort(Comparator.comparing(Holiday::date).reversed());
        return this;
    }

    @Override
    public List<Holiday> publicHolidays(int year, CountryCode countryCode) {
        holidayCalls.incrementAndGet();
        if (!countries.containsKey(countryCode.value())) {
            throw new UnknownCountryException("Unknown country code '%s'.".formatted(countryCode));
        }
        return holidaysByYearAndCountry.getOrDefault(key(year, countryCode.value()), Lists.newArrayList());
    }

    @Override
    public Map<String, String> availableCountries() {
        return Map.copyOf(countries);
    }

    @Override
    public Map<String, String> refreshAvailableCountries() {
        return Map.copyOf(countries);
    }

    int holidayCalls() {
        return holidayCalls.get();
    }

    private static String key(int year, String countryCode) {
        return year + ":" + countryCode;
    }
}
