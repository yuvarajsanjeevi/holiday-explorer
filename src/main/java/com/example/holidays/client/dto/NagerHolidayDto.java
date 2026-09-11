package com.example.holidays.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * The fields we use from {@code /PublicHolidays/{year}/{code}}; the payload also carries
 * {@code countryCode}, {@code fixed}, {@code counties} and {@code launchYear}. Binding a subset
 * means a new field upstream will not break us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NagerHolidayDto(
        LocalDate date, String localName, String name, List<HolidayType> types, Boolean global) {
}
