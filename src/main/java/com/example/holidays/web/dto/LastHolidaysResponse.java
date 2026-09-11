package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code asOf} is the date this was computed against - "the last three" needs a reference date.
 * {@code holidays} is most recent first and may be shorter than asked for.
 */
@Schema(description = "The most recently celebrated holidays for one country")
public record LastHolidaysResponse(String countryCode, java.time.LocalDate asOf,
        List<HolidayResponse> holidays) {
}
