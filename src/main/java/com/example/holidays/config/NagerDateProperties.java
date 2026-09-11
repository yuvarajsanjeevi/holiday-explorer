package com.example.holidays.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Everything an operator might want to change per environment.
 *
 * @param maxRetries       retries after the first attempt; 5xx and I/O errors only
 * @param retryDelay       before the first retry, doubled each time up to {@code retryMaxDelay}
 * @param maxLookbackYears how far back the last-celebrated scan may walk
 * @param yearWindow       how far either side of now the API serves data. It is +/-50 and moves
 *                         with the year, so we take it off the clock
 */
@Validated
@ConfigurationProperties("holiday-explorer.nager")
public record NagerDateProperties(

        @NotBlank @DefaultValue("https://date.nager.at/api/v3") String baseUrl,

        @Min(0) @DefaultValue("2") int maxRetries,

        @DefaultValue("200ms") Duration retryDelay,

        @DefaultValue("2s") Duration retryMaxDelay,

        @Min(1) @DefaultValue("3") int maxLookbackYears,

        @NotBlank @DefaultValue("0 0 */6 * * *") String countryRefreshCron,

        @Min(1) @DefaultValue("50") int yearWindow) {
}
