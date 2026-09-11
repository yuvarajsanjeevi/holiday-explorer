package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A single public holiday")
public record HolidayResponse(

        @Schema(example = "2026-08-31") LocalDate date,

        @Schema(description = "Name in the country's own language", example = "Tweede Kerstdag")
        String localName,

        @Schema(description = "English name", example = "St. Stephen's Day") String name) {
}
