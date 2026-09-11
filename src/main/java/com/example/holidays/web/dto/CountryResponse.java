package com.example.holidays.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A country code and the English name it resolves to")
public record CountryResponse(

        @Schema(example = "NL") String countryCode,

        @Schema(example = "Netherlands") String countryName) {
}
