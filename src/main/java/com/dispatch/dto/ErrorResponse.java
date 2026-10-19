package com.dispatch.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every failing request.
 */
public record ErrorResponse(
        String status,
        String message,
        List<FieldErrorDetail> errors,
        Instant timestamp
) {

    public static final String ERROR = "error";

    public static ErrorResponse of(String message, List<FieldErrorDetail> errors) {
        return new ErrorResponse(ERROR, message, List.copyOf(errors), Instant.now());
    }

    public static ErrorResponse of(String message) {
        return of(message, List.of());
    }

    /**
     * A single problem tied to a request field, e.g. {@code orders[2].latitude}.
     */
    public record FieldErrorDetail(String field, String message) {
    }
}
