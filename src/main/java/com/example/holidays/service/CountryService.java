package com.example.holidays.service;

import com.example.holidays.domain.CountryCode;
import com.example.holidays.exception.UnknownCountryException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** The countries the API publishes holidays for, behind an interface so storage and refresh can change. */
public interface CountryService {

    /** Country code to English name. */
    Map<String, String> getAll();

    /** Never throws: a failed refresh leaves the previous list serving. Runs at startup and on a timer. */
    void refresh();

    Optional<Instant> lastSuccessfulRefresh();

    /**
     * Checks every code at once, so a caller who got several wrong hears about all of them.
     *
     * @throws UnknownCountryException naming every unknown code
     */
    void validateCountryCodes(Collection<CountryCode> codes);
}
