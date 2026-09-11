package com.example.holidays.health;

import com.example.holidays.service.CountryService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reads the country list through the cache, like a request does, so scraping this does not call the
 * API every time. DOWN means no list at all; a stale but usable one stays UP.
 */
@Component("holidayApi")
public class HolidayApiHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HolidayApiHealthIndicator.class);

    private final CountryService countryService;

    public HolidayApiHealthIndicator(CountryService countryService) {
        this.countryService = countryService;
    }

    @Override
    public Health health() {
        int countries;
        try {
            countries = countryService.getAll().size();
        } catch (RuntimeException e) {
            log.debug("Reporting DOWN: the country list could not be read", e);
            return Health.down(e).build();
        }
        if (countries == 0) {
            log.debug("Reporting DOWN: the holiday API has not been reachable since startup");
            return Health.down()
                    .withDetail("reason", "The holiday API has not been reachable since startup.")
                    .build();
        }
        Instant refreshedAt = countryService.lastSuccessfulRefresh().orElse(null);
        log.debug("Reporting UP: {} countries, last refreshed {}", countries, refreshedAt);
        return Health.up()
                .withDetail("availableCountries", countries)
                .withDetail("lastSuccessfulRefresh", String.valueOf(refreshedAt))
                .build();
    }
}
