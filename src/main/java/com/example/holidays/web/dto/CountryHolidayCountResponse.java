package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A country and its number of public holidays that do not fall on a weekend")
public record CountryHolidayCountResponse(

        @Schema(example = "NL") String countryCode,

        @Schema(example = "Netherlands") String countryName,

        @Schema(description = "Public holidays falling Monday to Friday", example = "8")
        long weekdayHolidayCount) {
}
