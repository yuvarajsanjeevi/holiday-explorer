package com.example.holidays.service.impl;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.service.CountryService;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Checked on every request, so it is cached in Redis: reads go through the cached client method, the
 * refresh through the one that always fetches. Startup does not depend on the API being up - a
 * failed warm-up logs, reports unhealthy and retries later rather than blocking a deploy.
 */
@Service
public class CountryServiceImpl implements CountryService {

    private static final Logger log = LoggerFactory.getLogger(CountryServiceImpl.class);

    private final NagerDateClient client;

    /** Volatile: written by the scheduler, read by request threads. */
    private volatile Instant lastSuccessfulRefresh;

    public CountryServiceImpl(NagerDateClient client) {
        this.client = client;
    }

    /** Warm the cache so the first request does not pay for the fetch. */
    @PostConstruct
    void loadOnStartup() {
        log.info("Warming the country cache from the holiday API");
        refresh();
    }

    /** Also our only scheduled contact with the API, so a failure here is how the health check learns about an outage. */
    @Scheduled(cron = "${holiday-explorer.nager.country-refresh-cron}")
    @Override
    public void refresh() {
        try {
            Map<String, String> loaded = client.refreshAvailableCountries();
            lastSuccessfulRefresh = Instant.now();
            log.info("Refreshed the available countries; {} known", loaded.size());
        } catch (RuntimeException e) {
            // Never rethrow. On startup that aborts the boot; on the timer Spring stops
            // rescheduling the task for good. The cached list is still there to serve.
            log.warn("Could not refresh the available countries; serving the cached list", e);
        }
    }

    @Override
    public Map<String, String> getAll() {
        return client.availableCountries();
    }

    @Override
    public Optional<Instant> lastSuccessfulRefresh() {
        return Optional.ofNullable(lastSuccessfulRefresh);
    }

    @Override
    public void validateCountryCodes(Collection<CountryCode> codes) {
        Map<String, String> known = getAll();
        List<String> unknown = codes.stream()
                .map(CountryCode::value)
                .filter(code -> !known.containsKey(code))
                .toList();
        if (!unknown.isEmpty()) {
            log.debug("Rejecting unknown country code(s) {}; {} are known", unknown, known.size());
            throw new UnknownCountryException(
                    "Unknown country code(s): %s.".formatted(String.join(", ", unknown)));
        }
    }
}
