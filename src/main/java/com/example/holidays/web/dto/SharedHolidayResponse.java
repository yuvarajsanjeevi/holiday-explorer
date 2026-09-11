package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** {@code localNames} is keyed by country: {@code {"NL": ["Eerste Kerstdag"], "DE": ["Erster Weihnachtstag"]}}. */
@Schema(description = "A date celebrated in both countries, with each country's own name(s) for it")
public record SharedHolidayResponse(

        @Schema(example = "2026-12-25") LocalDate date,

        Map<String, List<String>> localNames) {
}
