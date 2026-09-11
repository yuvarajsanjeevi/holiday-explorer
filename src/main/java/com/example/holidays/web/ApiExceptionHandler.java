package com.example.holidays.web;

import com.example.holidays.constant.ApplicationConstants;
import com.example.holidays.exception.ErrorCode;
import com.example.holidays.exception.InvalidRequestException;
import com.example.holidays.exception.UnknownCountryException;
import com.example.holidays.exception.UpstreamRejectedException;
import com.example.holidays.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Messages here are safe to show a caller; stack traces and upstream URLs stay in the log. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> onInvalidRequest(InvalidRequestException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UpstreamRejectedException.class)
    ResponseEntity<ProblemDetail> onUpstreamRejected(UpstreamRejectedException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownCountryException.class)
    ResponseEntity<ProblemDetail> onUnknownCountry(UnknownCountryException e) {
        return errorResponse(ErrorCode.UNKNOWN_COUNTRY, e.getMessage());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ResponseEntity<ProblemDetail> onUpstreamUnavailable(UpstreamUnavailableException e) {
        log.warn("Upstream holiday API unavailable", e);
        return errorResponse(ErrorCode.UPSTREAM_UNAVAILABLE, e.getMessage());
    }

    /** A missing {@code year}, {@code first} or {@code second} query parameter. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> onMissingParameter(MissingServletRequestParameterException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST,
                ApplicationConstants.MISSING_PARAMETER_MESSAGE.formatted(e.getParameterName()));
    }

    /** A non-numeric {@code year} or {@code limit}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST,
                ApplicationConstants.NOT_A_NUMBER_MESSAGE.formatted(e.getName()));
    }

    /** Without this the catch-all below makes a client typo a 500 and logs an ERROR, as if it were our fault. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> onNoHandler(NoResourceFoundException e) {
        return errorResponse(ErrorCode.NOT_FOUND, "No endpoint for %s %s.".formatted(e.getHttpMethod(), e.getResourcePath()));
    }

    /** Right path, wrong verb. Also a 500 without this. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> onMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return errorResponse(ErrorCode.METHOD_NOT_ALLOWED, "%s is not supported here.".formatted(e.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> onUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return errorResponse(ErrorCode.INTERNAL_ERROR, ApplicationConstants.INTERNAL_ERROR_MESSAGE);
    }

    /** Plain {@code application/json} so callers deal with one content type; Spring would otherwise send problem+json. */
    private static ResponseEntity<ProblemDetail> errorResponse(ErrorCode errorCode, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        body.setTitle(errorCode.title());
        body.setType(errorCode.type());
        return ResponseEntity.status(errorCode.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
