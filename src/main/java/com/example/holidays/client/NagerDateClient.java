package com.example.holidays.client;

import com.example.holidays.domain.CountryCode;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamRejectedException;
import com.example.holidays.exception.UpstreamUnavailableException;
import com.example.holidays.domain.Holiday;
import java.util.List;
import java.util.Map;

/** What we need from date.nager.at. The service depends on this, not on {@code RestClient}, so the queries test without HTTP. */
public interface NagerDateClient {

    /**
     * One country, one year, most recent first. Sorted here so callers need not re-sort a cached list.
     *
     * @throws UnknownCountryException      if the country is not published
     * @throws UpstreamRejectedException    if the year is out of range
     * @throws UpstreamUnavailableException if the API cannot be reached
     */
    List<Holiday> publicHolidays(int year, CountryCode countryCode);

    /** Every country the API publishes holidays for, as code to English name. Served from cache. */
    Map<String, String> availableCountries();

    /**
     * The same list, fetched and written over the cached copy. Separate from
     * {@link #availableCountries()} because a refresh served by the cache it is meant to update would
     * read back its own copy, never see a new country, and still look like it worked while the API was down.
     */
    Map<String, String> refreshAvailableCountries();
}
