package com.example.holidays.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Which days each country counts as its weekend - the holiday API does not tell us. Egypt rests
 * Friday and Saturday, so a flat Sat/Sun rule gave 10 working-day holidays for 2024 where Cairo
 * counts 15. Correct as of 2026; these do change, Saudi Arabia moved in 2013 and the UAE in 2022.
 */
public final class WeekendCalendar {

    private static final Set<DayOfWeek> SATURDAY_SUNDAY = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private static final Set<DayOfWeek> FRIDAY_SATURDAY = Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    // Only the countries that differ; everything else falls back to Sat/Sun. Afghanistan, Iran,
    // Nepal and Brunei are left out on purpose - their weeks fit neither shape and a wrong entry
    // would be worse than the fallback.
    private static final Map<String, Set<DayOfWeek>> BY_COUNTRY = Map.ofEntries(
            Map.entry("BD", FRIDAY_SATURDAY),   // Bangladesh
            Map.entry("BH", FRIDAY_SATURDAY),   // Bahrain
            Map.entry("DJ", FRIDAY_SATURDAY),   // Djibouti
            Map.entry("DZ", FRIDAY_SATURDAY),   // Algeria
            Map.entry("EG", FRIDAY_SATURDAY),   // Egypt
            Map.entry("IQ", FRIDAY_SATURDAY),   // Iraq
            Map.entry("LY", FRIDAY_SATURDAY),   // Libya
            Map.entry("SD", FRIDAY_SATURDAY),   // Sudan
            Map.entry("SO", FRIDAY_SATURDAY),   // Somalia
            Map.entry("SY", FRIDAY_SATURDAY),   // Syria
            Map.entry("YE", FRIDAY_SATURDAY),   // Yemen
            Map.entry("IL", FRIDAY_SATURDAY),   // Israel
            Map.entry("JO", FRIDAY_SATURDAY),   // Jordan
            Map.entry("KW", FRIDAY_SATURDAY),   // Kuwait
            Map.entry("MV", FRIDAY_SATURDAY),   // Maldives
            Map.entry("OM", FRIDAY_SATURDAY),   // Oman
            Map.entry("PS", FRIDAY_SATURDAY),   // Palestine
            Map.entry("QA", FRIDAY_SATURDAY),   // Qatar
            Map.entry("SA", FRIDAY_SATURDAY));  // Saudi Arabia

    private WeekendCalendar() {
    }

    public static Set<DayOfWeek> weekendIn(CountryCode countryCode) {
        return BY_COUNTRY.getOrDefault(countryCode.value(), SATURDAY_SUNDAY);
    }

    public static boolean isWorkingDay(LocalDate date, CountryCode countryCode) {
        return !weekendIn(countryCode).contains(date.getDayOfWeek());
    }
}
