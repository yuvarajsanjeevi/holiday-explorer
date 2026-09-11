package com.example.holidays.service;

import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.CountryHolidayCount;
import com.example.holidays.domain.Holiday;
import com.example.holidays.domain.SharedHoliday;
import com.example.holidays.exception.InvalidRequestException;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamUnavailableException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** The three holiday queries. Domain types only, so an implementation tests without a web or network stack. */
public interface HolidayService {

    /**
     * "Celebrated" means strictly before {@code today}, which the caller supplies so the answer is
     * reproducible. Returns fewer than {@code limit} rather than failing if the country has too few.
     *
     * @throws InvalidRequestException      if {@code limit} is not positive
     * @throws UnknownCountryException      if the country is not published upstream
     * @throws UpstreamUnavailableException if the API cannot be reached
     */
    List<Holiday> lastCelebratedHolidays(
            CountryCode countryCode, int limit, LocalDate today);

    /**
     * Highest count first, ties broken by country code. {@code today} fixes the supported year
     * range, which moves with the calendar.
     *
     * @throws InvalidRequestException      if the year is unsupported or too many countries are asked for
     * @throws UnknownCountryException      naming every code not published upstream
     * @throws UpstreamUnavailableException if any country cannot be retrieved
     */
    List<CountryHolidayCount> weekdayHolidayCounts(
            int year, Set<CountryCode> countryCodes, LocalDate today);

    /**
     * Ascending, each date once. {@code today} fixes the supported year range, which moves with the
     * calendar.
     *
     * @throws InvalidRequestException      if the year is unsupported or the countries are the same
     * @throws UnknownCountryException      if either country is not published upstream
     * @throws UpstreamUnavailableException if either country cannot be retrieved
     */
    List<SharedHoliday> sharedHolidays(
            int year, CountryCode first, CountryCode second, LocalDate today);
}
