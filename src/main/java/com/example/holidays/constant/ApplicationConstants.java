package com.example.holidays.constant;

import java.time.Duration;

/** Fixed values. Anything an operator may want to change per environment goes in {@code NagerDateProperties}. */
public final class ApplicationConstants {

    private ApplicationConstants() {
    }

    // Caching

    public static final String HOLIDAYS_CACHE = "holidays";

    public static final String COUNTRIES_CACHE = "countries";

    /** SpEL, evaluated against the client method's arguments. */
    public static final String HOLIDAYS_CACHE_KEY = "#year + ':' + #countryCode.value()";

    public static final Duration CACHE_TTL = Duration.ofHours(24);

    // Web

    public static final String API_BASE_PATH = "/api/v1";

    /** The three the assignment asks for; {@code ?limit=} overrides it. */
    public static final int DEFAULT_HOLIDAY_LIMIT = 3;

    /** Room for the whole catalogue (204 today) with headroom, but not an unbounded list. */
    public static final int MAX_COUNTRIES_PER_REQUEST = 250;

    /** Without this the ceiling would be whatever the lookback years happen to hold. */
    public static final int MAX_HOLIDAY_LIMIT = 100;

    public static final String ERROR_TYPE_BASE = "https://example.com/holiday-explorer/errors/";

    // Error details. The %s is the parameter name.

    public static final String MISSING_PARAMETER_MESSAGE = "Required query parameter '%s' is missing.";

    public static final String NOT_A_NUMBER_MESSAGE = "Query parameter '%s' must be a whole number.";

    /** Vague on purpose. The real cause goes to the log, not to the caller. */
    public static final String INTERNAL_ERROR_MESSAGE = "The request could not be completed.";

    public static final String COUNTRY_CODE_PATTERN = "[A-Za-z]{2}";
}
