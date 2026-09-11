package com.example.holidays.domain;

import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.exception.InvalidRequestException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An ISO 3166-1 alpha-2 code, upper-cased. A type rather than a String so the format is checked once
 * at the edge. Whether the code is actually known is a separate question, for {@code CountryService}.
 */
public record CountryCode(String value) implements Comparable<CountryCode> {

    private static final Pattern FORMAT = Pattern.compile(ApplicationConstants.COUNTRY_CODE_PATTERN);

    public CountryCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidRequestException(
                    "Invalid country code '%s': expected two letters (ISO 3166-1 alpha-2).".formatted(value));
        }
        value = value.toUpperCase(Locale.ROOT);
    }

    public static CountryCode of(String value) {
        return new CountryCode(value);
    }

    /**
     * Parses a comma-separated list, keeping order. The dedup stops {@code ?countryCodes=NL,nl,NL}
     * costing three requests.
     *
     * @throws InvalidRequestException if the list is blank or an entry is malformed
     */
    public static Set<CountryCode> parseList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            throw new InvalidRequestException("At least one country code is required.");
        }
        Set<CountryCode> codes = new LinkedHashSet<>();
        for (String token : commaSeparated.split(",", -1)) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                codes.add(of(trimmed));
            }
        }
        if (codes.isEmpty()) {
            throw new InvalidRequestException("At least one country code is required.");
        }
        return codes;
    }

    @Override
    public int compareTo(CountryCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
