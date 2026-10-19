package com.dispatch.exception;

import java.util.List;

/**
 * Thrown when the same ID appears more than once in a single request.
 */
public class DuplicateIdException extends RuntimeException {

    private final String field;
    private final List<String> duplicateIds;

    /**
     * @param field        the request field the IDs belong to, e.g. {@code orderId}
     * @param duplicateIds the repeated IDs, in first-seen order
     */
    public DuplicateIdException(String field, List<String> duplicateIds) {
        super("Duplicate %s values in request: %s".formatted(field, duplicateIds));
        this.field = field;
        this.duplicateIds = List.copyOf(duplicateIds);
    }

    public String getField() {
        return field;
    }

    public List<String> getDuplicateIds() {
        return duplicateIds;
    }
}
