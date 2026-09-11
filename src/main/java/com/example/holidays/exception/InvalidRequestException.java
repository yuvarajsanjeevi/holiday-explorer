package com.example.holidays.exception;

/** Rejected without contacting upstream. Rendered as 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
