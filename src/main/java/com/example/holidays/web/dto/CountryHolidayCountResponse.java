package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A country and its number of public holidays that do not fall on its own weekend")
public record CountryHolidayCountResponse(

        @Schema(example = "NL") String countryCode,

        @Schema(example = "Netherlands") String countryName,

        @Schema(description = "Public holidays falling on one of that country's working days", example = "8")
        long weekdayHolidayCount) {
}
