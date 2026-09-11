package com.example.holidays.domain;


/** A country's holiday count for a year, excluding that country's own weekend days. */
public record CountryHolidayCount(CountryCode countryCode, String countryName, long count) {
}
