package com.example.holidays.exception;

/** Upstream unreachable, timed out, or still failing after retries. Rendered as 502, and the only retryable one. */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message) {
        super(message);
    }

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
