package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code countries} resolves the two codes once, in the order they were asked for. The names are
 * the same for every date, so they are not repeated inside {@code commonHolidays}.
 */
@Schema(description = "The dates two countries both celebrate in a year")
public record SharedHolidaysResponse(

        @Schema(example = "2026") int year,

        List<CountryResponse> countries,

        List<SharedHolidayResponse> commonHolidays) {
}
