package com.example.holidays.exception;

/** Upstream rejected a request we built - here, an unsupported year. Rendered as 400: the caller supplied it. */
public class UpstreamRejectedException extends RuntimeException {

    public UpstreamRejectedException(String message) {
        super(message);
    }
}
