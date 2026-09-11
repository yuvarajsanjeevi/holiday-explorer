package com.example.holidays.exception;

import com.example.holidays.constant.ApplicationConstants;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * Every failure a caller can see. One place, so the responses we send and the examples we document
 * stay in step - both read this enum. The {@code detail} varies per request and stays on the exception.
 */
public enum ErrorCode {

    /** A malformed country code, an unsupported year, a non-positive limit, or a rejected upstream call. */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request"),

    UNKNOWN_COUNTRY(HttpStatus.NOT_FOUND, "unknown-country", "Unknown country"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "Not found"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed"),

    /** The upstream holiday API could not be reached, or kept failing after retries. */
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "upstream-unavailable", "Holiday API unavailable"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error");

    private final HttpStatus status;
    private final String code;
    private final String title;

    ErrorCode(HttpStatus status, String code, String title) {
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(ApplicationConstants.ERROR_TYPE_BASE + code);
    }
}
