package com.example.holidays.exception;

/** Well-formed country code, but not one the upstream API publishes. Rendered as 404. */
public class UnknownCountryException extends RuntimeException {

    public UnknownCountryException(String message) {
        super(message);
    }
}
