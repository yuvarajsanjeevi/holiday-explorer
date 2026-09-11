package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code asOf} is the date this was computed against - "the last three" needs a reference date.
 * {@code holidays} is most recent first and may be shorter than asked for.
 */
@Schema(description = "The most recently celebrated holidays for one country")
public record LastHolidaysResponse(

        @Schema(example = "NL") String countryCode,

        @Schema(example = "Netherlands") String countryName,

        @Schema(example = "2026-09-11") LocalDate asOf,

        List<HolidayResponse> holidays) {
}
