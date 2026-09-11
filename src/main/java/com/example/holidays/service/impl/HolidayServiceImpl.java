package com.example.holidays.service.impl;

import com.example.holidays.client.NagerDateClient;
import com.example.holidays.config.NagerDateProperties;
import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.domain.CountryCode;
import com.example.holidays.domain.CountryHolidayCount;
import com.example.holidays.domain.Holiday;
import com.example.holidays.domain.LastCelebratedHolidays;
import com.example.holidays.domain.SharedHoliday;
import com.example.holidays.domain.SharedHolidays;
import com.example.holidays.domain.WeekendCalendar;
import com.example.holidays.exception.InvalidRequestException;
import com.example.holidays.exception.UpstreamUnavailableException;
import com.example.holidays.service.CountryService;
import com.example.holidays.service.HolidayService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Costs quoted below count holidays processed, not HTTP calls - the client caches, so a warm cache makes none. */
@Service
public class HolidayServiceImpl implements HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayServiceImpl.class);

    private static final Comparator<CountryHolidayCount> BY_COUNT_DESC =
            Comparator.comparingLong(CountryHolidayCount::count).reversed()
              .thenComparing(CountryHolidayCount::countryCode);

    private final NagerDateClient client;
    private final CountryService countryService;
    private final NagerDateProperties properties;

    public HolidayServiceImpl(NagerDateClient client, CountryService countryService,
            NagerDateProperties properties) {
        this.client = client;
        this.countryService = countryService;
        this.properties = properties;
    }

    /**
     * Walks years backwards and stops once it has enough, since an earlier year can only hold older
     * dates - that saves an HTTP call.
     *
     * @throws InvalidRequestException if {@code limit} is not positive
     */
    @Override
    public LastCelebratedHolidays lastCelebratedHolidays(
            CountryCode countryCode, int limit, LocalDate today) {
        if (limit < 1 || limit > ApplicationConstants.MAX_HOLIDAY_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and %d, but was %d."
                    .formatted(ApplicationConstants.MAX_HOLIDAY_LIMIT, limit));
        }
        countryService.validateCountryCodes(List.of(countryCode));
        log.debug("Last {} celebrated holidays for {}, as of {}", limit, countryCode, today);

        List<Holiday> celebrated = new ArrayList<>();
        // Switzerland lists the same holiday once per canton, so without this the answer can be
        // "Maria Himmelfahrt" twice and a caller asking for three gets two.
        Set<String> seen = new HashSet<>();

        int earliestYear = today.getYear() - properties.maxLookbackYears() + 1;
        for (int year = today.getYear(); year >= earliestYear; year--) {

            List<Holiday> yearHolidays = client.publicHolidays(year, countryCode);

            for (Holiday holiday : yearHolidays) {
                if (holiday.date().isBefore(today) && seen.add(sameHoliday(holiday))) {
                    celebrated.add(holiday);
                    if (celebrated.size() == limit) {
                        log.debug("Found all {} for {} within {} year(s)",
                                limit, countryCode, today.getYear() - year + 1);
                        return named(countryCode, celebrated);
                    }
                }
            }
        }
        log.debug("Only {} of the {} requested holidays exist for {} in the last {} years",
                celebrated.size(), limit, countryCode, properties.maxLookbackYears());
        return named(countryCode, celebrated);
    }

    private LastCelebratedHolidays named(CountryCode countryCode, List<Holiday> holidays) {
        return new LastCelebratedHolidays(
                countryCode, countryService.getAll().get(countryCode.value()), holidays);
    }

    /**
     * Countries are fetched at the same time, so the wait is the slowest one, not the total.
     *
     * Counts only holidays that are Public and nationwide. This query asks how many days off a
     * country gets, so an observance does not qualify, and neither does a regional holiday: without
     * that second filter Switzerland reports 21 against the Netherlands' 9, because each canton's
     * day is a separate entry. The other two queries ask what is celebrated, so they keep everything.
     */
    @Override
    public List<CountryHolidayCount> weekdayHolidayCounts(
            int year, Set<CountryCode> countryCodes, LocalDate today) {
        validateYear(year, today);
        if (countryCodes.size() > ApplicationConstants.MAX_COUNTRIES_PER_REQUEST) {
            throw new InvalidRequestException("At most %d country codes may be requested at once, but %d were given."
                    .formatted(ApplicationConstants.MAX_COUNTRIES_PER_REQUEST, countryCodes.size()));
        }
        countryService.validateCountryCodes(countryCodes);
        log.debug("Weekday holiday counts for {} in {}", countryCodes, year);
        Map<String, String> countryNames = countryService.getAll();

        Map<CountryCode, List<Holiday>> holidaysByCountry = fetchConcurrently(year, countryCodes);

        List<CountryHolidayCount> counts = new ArrayList<>(countryCodes.size());
        for (CountryCode code : countryCodes) {
            long weekdayHolidays = holidaysByCountry.get(code).stream()
                    .filter(Holiday::publicHoliday)
                    .filter(Holiday::nationwide)
                    .filter(holiday -> WeekendCalendar.isWorkingDay(holiday.date(), code))
                    .count();
            counts.add(new CountryHolidayCount(code, countryNames.get(code.value()), weekdayHolidays));
        }
        counts.sort(BY_COUNT_DESC);
        return counts;
    }

    /** Grouping by date and intersecting is O(n + m) against a nested loop's O(n * m), and dedupes on the way. */
    @Override
    public SharedHolidays sharedHolidays(
            int year, CountryCode first, CountryCode second, LocalDate today) {
        if (first.equals(second)) {
            throw new InvalidRequestException(
                    "The two country codes must differ, but both were '%s'.".formatted(first));
        }
        validateYear(year, today);
        countryService.validateCountryCodes(List.of(first, second));
        log.debug("Holidays shared by {} and {} in {}", first, second, year);
        Map<String, String> countryNames = countryService.getAll();

        Map<CountryCode, List<Holiday>> holidaysByCountry = fetchConcurrently(year, List.of(first, second));
        Map<LocalDate, Set<String>> firstByDate = localNamesByDate(holidaysByCountry.get(first));
        Map<LocalDate, Set<String>> secondByDate = localNamesByDate(holidaysByCountry.get(second));

        // Scanning the smaller side is the same answer for fewer lookups.
        boolean firstHasFewer = firstByDate.size() <= secondByDate.size();
        Map<LocalDate, Set<String>> fewerDates = firstHasFewer ? firstByDate : secondByDate;
        Map<LocalDate, Set<String>> otherDates = firstHasFewer ? secondByDate : firstByDate;

        List<SharedHoliday> shared = fewerDates.keySet().stream()
                .filter(otherDates::containsKey)
                .sorted()
                .map(date -> toSharedHoliday(
                        date, first, firstByDate.get(date), second, secondByDate.get(date)))
                .toList();
        log.debug("{} and {} share {} of {}/{} dates in {}",
                first, second, shared.size(), firstByDate.size(), secondByDate.size(), year);

        Map<CountryCode, String> named = LinkedHashMap.newLinkedHashMap(2);
        named.put(first, countryNames.get(first.value()));
        named.put(second, countryNames.get(second.value()));
        return new SharedHolidays(year, named, shared);
    }

    /** Two entries for the same day and name are one holiday, listed once per region. */
    private static String sameHoliday(Holiday holiday) {
        return holiday.date() + "|" + holiday.localName();
    }

    /** A set of names per date, not one: Spain lists two holidays on 23 April, Switzerland the same name twice. */
    private static Map<LocalDate, Set<String>> localNamesByDate(List<Holiday> holidays) {
        Map<LocalDate, Set<String>> namesByDate = HashMap.newHashMap(holidays.size());
        for (Holiday holiday : holidays) {
            namesByDate.computeIfAbsent(holiday.date(), ignored -> new LinkedHashSet<>())
              .add(holiday.localName());
        }
        return namesByDate;
    }

    /** Caller's country first. */
    private static SharedHoliday toSharedHoliday(LocalDate date,
            CountryCode first, Set<String> firstLocalNames,
            CountryCode second, Set<String> secondLocalNames) {
    Map<CountryCode, Set<String>> localNamesByCountry = LinkedHashMap.newLinkedHashMap(2);
        localNamesByCountry.put(first, firstLocalNames);
        localNamesByCountry.put(second, secondLocalNames);
        return new SharedHoliday(date, localNamesByCountry);
    }

    /**
     * Keep the two loops apart: reading each future as it is submitted makes this serial again, with
     * the same results and no test to catch it.
     */
    private Map<CountryCode, List<Holiday>> fetchConcurrently(int year, Collection<CountryCode> countryCodes) {

        long startedAt = System.nanoTime();
        Map<CountryCode, Future<List<Holiday>>> futures = new LinkedHashMap<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (CountryCode code : countryCodes) {
                futures.put(code, executor.submit(() -> client.publicHolidays(year, code)));
            }
        }

        Map<CountryCode, List<Holiday>> holidaysByCountry = new LinkedHashMap<>();
        futures.forEach((code, future) -> holidaysByCountry.put(code, resultOf(code, future)));

        log.debug("Fetched {} country/countries for {} in {} ms",
                countryCodes.size(), year, (System.nanoTime() - startedAt) / 1_000_000);
        return holidaysByCountry;
    }

    static List<Holiday> resultOf(CountryCode countryCode, Future<List<Holiday>> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new UpstreamUnavailableException(
                    "Failed to retrieve holidays for '%s'.".formatted(countryCode), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException(
                    "Interrupted while retrieving holidays for '%s'.".formatted(countryCode), e);
        }
    }

    /** The API's year range is a rolling window, so the bounds come off the clock. Checking here saves a round trip. */
    private void validateYear(int year, LocalDate today) {
        int currentYear = today.getYear();
        int minYear = currentYear - properties.yearWindow();
        int maxYear = currentYear + properties.yearWindow();
        if (year < minYear || year > maxYear) {
            throw new InvalidRequestException(
                    "Year %d is outside the supported range %d-%d.".formatted(year, minYear, maxYear));
        }
    }
}
