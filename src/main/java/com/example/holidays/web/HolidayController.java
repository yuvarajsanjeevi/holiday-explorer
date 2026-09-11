package com.example.holidays.web;

import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.service.HolidayService;
import com.example.holidays.web.dto.CountryHolidayCountResponse;
import com.example.holidays.web.dto.LastHolidaysResponse;
import com.example.holidays.web.dto.SharedHolidaysResponse;
import com.example.holidays.web.mapper.HolidayMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kept thin: parse into domain types, delegate, map the result. Value checks live in
 * {@link CountryCode} and {@link HolidayService} so they apply however the service is called.
 */
@RestController
@RequestMapping(path = ApplicationConstants.API_BASE_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Holidays", description = "Queries over the Nager.Date public holiday API")
public class HolidayController {

    private static final String ERROR_JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String ERROR_SCHEMA = "#/components/schemas/ApiError";

    private final HolidayService holidayService;
    private final HolidayMapper mapper;

    public HolidayController(HolidayService holidayService, HolidayMapper mapper) {
        this.holidayService = holidayService;
        this.mapper = mapper;
    }

    @ApiResponse(responseCode = "200", description = "The holidays, most recent first")
    @ApiResponse(responseCode = "400", description = "Malformed country code, or a limit outside 1 to 100",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/invalid-request", "title": "Invalid request", "status": 400,
                              "detail": "limit must be between 1 and 100, but was 0.", "instance": "/api/v1/countries/NL/holidays/last"}""")))
    @ApiResponse(responseCode = "404", description = "A well-formed country code the API publishes nothing for",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/unknown-country", "title": "Unknown country", "status": 404,
                              "detail": "Unknown country code(s): XX.", "instance": "/api/v1/countries/NL/holidays/last"}""")))
    @ApiResponse(responseCode = "502", description = "The holiday API could not be reached, or kept failing after retries",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/upstream-unavailable", "title": "Holiday API unavailable", "status": 502,
                              "detail": "The holiday API returned 503 SERVICE_UNAVAILABLE.", "instance": "/api/v1/countries/NL/holidays/last"}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected internal error",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/internal-error", "title": "Internal error", "status": 500,
                              "detail": "The request could not be completed.", "instance": "/api/v1/countries/NL/holidays/last"}""")))
    @Operation(summary = "The last celebrated holidays for a country",
            description = """
                    Returns the most recently celebrated public holidays, most recent first. \
                    "Celebrated" means strictly before today.""")
    @GetMapping("/countries/{countryCode}/holidays/last")
    public LastHolidaysResponse lastCelebrated(

            @Parameter(description = "ISO 3166-1 alpha-2 country code", example = "NL",
                    schema = @Schema(pattern = ApplicationConstants.COUNTRY_CODE_PATTERN))
            @PathVariable String countryCode,

            @Parameter(description = "How many holidays to return, 1 to 100", example = "3")
            @RequestParam(defaultValue = "" + ApplicationConstants.DEFAULT_HOLIDAY_LIMIT) int limit) {

        LocalDate today = LocalDate.now();
        return mapper.toLastHolidaysResponse(
                holidayService.lastCelebratedHolidays(CountryCode.of(countryCode), limit, today), today);
    }

    @ApiResponse(responseCode = "200", description = "One row per country, highest count first")
    @ApiResponse(responseCode = "400", description = "Unsupported year, malformed country code, or too many countries",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/invalid-request", "title": "Invalid request", "status": 400,
                              "detail": "Year 1800 is outside the supported range 1976-2076.", "instance": "/api/v1/holidays/weekday-counts"}""")))
    @ApiResponse(responseCode = "404", description = "A well-formed country code the API publishes nothing for",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/unknown-country", "title": "Unknown country", "status": 404,
                              "detail": "Unknown country code(s): XX.", "instance": "/api/v1/holidays/weekday-counts"}""")))
    @ApiResponse(responseCode = "502", description = "The holiday API could not be reached, or kept failing after retries",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/upstream-unavailable", "title": "Holiday API unavailable", "status": 502,
                              "detail": "The holiday API returned 503 SERVICE_UNAVAILABLE.", "instance": "/api/v1/holidays/weekday-counts"}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected internal error",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/internal-error", "title": "Internal error", "status": 500,
                              "detail": "The request could not be completed.", "instance": "/api/v1/holidays/weekday-counts"}""")))
    @Operation(summary = "Public holidays not falling on a weekend, per country",
            description = """
                    For each country, counts the public holidays in the given year that fall Monday
                    to Friday. Sorted by count descending, ties broken by country code.""")
    @GetMapping("/holidays/weekday-counts")
    public List<CountryHolidayCountResponse> weekdayCounts(

            @Parameter(description = "Calendar year", example = "2026") @RequestParam int year,

            @Parameter(description = "Comma-separated country codes; duplicates are ignored",
                    example = "NL,DE,US")
            @RequestParam String countryCodes) {

        return mapper.toCountResponses(holidayService.weekdayHolidayCounts(
                year, CountryCode.parseList(countryCodes), LocalDate.now()));
    }

    @ApiResponse(responseCode = "200", description = "The shared dates, earliest first")
    @ApiResponse(responseCode = "400", description = "Unsupported year, malformed country code, or the same country twice",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/invalid-request", "title": "Invalid request", "status": 400,
                              "detail": "The two country codes must differ, but both were 'NL'.", "instance": "/api/v1/holidays/shared"}""")))
    @ApiResponse(responseCode = "404", description = "A well-formed country code the API publishes nothing for",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/unknown-country", "title": "Unknown country", "status": 404,
                              "detail": "Unknown country code(s): XX.", "instance": "/api/v1/holidays/shared"}""")))
    @ApiResponse(responseCode = "502", description = "The holiday API could not be reached, or kept failing after retries",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/upstream-unavailable", "title": "Holiday API unavailable", "status": 502,
                              "detail": "The holiday API returned 503 SERVICE_UNAVAILABLE.", "instance": "/api/v1/holidays/shared"}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected internal error",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = ERROR_SCHEMA),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/holiday-explorer/errors/internal-error", "title": "Internal error", "status": 500,
                              "detail": "The request could not be completed.", "instance": "/api/v1/holidays/shared"}""")))
    @Operation(summary = "Dates celebrated in both countries",
            description = """
                    The deduplicated list of dates that both countries celebrate in the given year, \
                    ascending, each with both countries' local names for it.""")
    @GetMapping("/holidays/shared")
    public SharedHolidaysResponse shared(

            @Parameter(description = "Calendar year", example = "2026") @RequestParam int year,

            @Parameter(description = "First country code", example = "NL",
                    schema = @Schema(pattern = ApplicationConstants.COUNTRY_CODE_PATTERN))
            @RequestParam String first,

            @Parameter(description = "Second country code", example = "DE",
                    schema = @Schema(pattern = ApplicationConstants.COUNTRY_CODE_PATTERN))
            @RequestParam String second) {

        return mapper.toSharedHolidaysResponse(holidayService.sharedHolidays(
                year, CountryCode.of(first), CountryCode.of(second), LocalDate.now()));
    }
}
