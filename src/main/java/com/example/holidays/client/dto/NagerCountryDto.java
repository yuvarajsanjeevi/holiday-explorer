package com.example.holidays.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One entry of {@code /AvailableCountries}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NagerCountryDto(String countryCode, String name) {
}
