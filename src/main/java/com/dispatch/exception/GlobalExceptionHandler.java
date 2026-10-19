package com.dispatch.exception;

import com.dispatch.dto.ErrorResponse;
import com.dispatch.dto.ErrorResponse.FieldErrorDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Translates exceptions into the uniform {@link ErrorResponse} body.
 * Internal details (stack traces, exception messages) are logged, never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String MSG_VALIDATION_FAILED = "Validation failed";
    static final String MSG_MALFORMED_JSON = "Malformed JSON request";
    static final String MSG_INVALID_VALUE = "Invalid value in request body";
    static final String MSG_DUPLICATE_IDS = "Duplicate IDs in request";
    static final String MSG_UNEXPECTED = "An unexpected error occurred. Please try again later.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                // Stable ordering makes responses deterministic and easier to read
                .sorted(Comparator.comparing(FieldErrorDetail::field).thenComparing(FieldErrorDetail::message))
                .toList();
        return badRequest(ErrorResponse.of(MSG_VALIDATION_FAILED, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalid) {
            // Wrong type for a field, e.g. "priority": "URGENT" or "latitude": "abc"
            String field = pathOf(invalid);
            return badRequest(ErrorResponse.of(MSG_INVALID_VALUE,
                    List.of(new FieldErrorDetail(field, describeInvalidValue(invalid)))));
        }
        if (cause instanceof MismatchedInputException mismatched && !mismatched.getPath().isEmpty()) {
            // Structurally wrong input, e.g. an object where a list was expected
            String field = pathOf(mismatched);
            return badRequest(ErrorResponse.of(MSG_INVALID_VALUE,
                    List.of(new FieldErrorDetail(field, "Value has an unexpected type or structure"))));
        }
        if (cause instanceof JsonProcessingException) {
            return badRequest(ErrorResponse.of(MSG_MALFORMED_JSON));
        }
        return badRequest(ErrorResponse.of(MSG_MALFORMED_JSON + ": request body is missing or unreadable"));
    }

    @ExceptionHandler(DuplicateIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateIds(DuplicateIdException ex) {
        List<FieldErrorDetail> errors = ex.getDuplicateIds().stream()
                .map(id -> new FieldErrorDetail(ex.getField(),
                        "'%s' appears more than once in the request".formatted(id)))
                .toList();
        return badRequest(ErrorResponse.of(MSG_DUPLICATE_IDS, errors));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of("HTTP method %s is not supported for this endpoint".formatted(ex.getMethod())));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("Content-Type must be application/json"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("Resource not found: /" + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(MSG_UNEXPECTED));
    }

    private static ResponseEntity<ErrorResponse> badRequest(ErrorResponse body) {
        return ResponseEntity.badRequest().body(body);
    }

    private static FieldErrorDetail toDetail(FieldError error) {
        return new FieldErrorDetail(error.getField(), error.getDefaultMessage());
    }

    private static String describeInvalidValue(InvalidFormatException ex) {
        Class<?> targetType = ex.getTargetType();
        if (targetType != null && targetType.isEnum()) {
            return "Invalid value '%s'. Allowed values: %s"
                    .formatted(ex.getValue(), Arrays.toString(targetType.getEnumConstants()));
        }
        String typeName = targetType == null ? "the expected type" : targetType.getSimpleName();
        return "Invalid value '%s'; expected %s".formatted(ex.getValue(), typeName);
    }

    /** Builds a request path such as {@code orders[3].priority} from Jackson's reference chain. */
    static String pathOf(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "body" : path.toString();
    }
}
