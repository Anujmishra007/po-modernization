package com.wms.po.domain.exception;

import java.util.List;

/**
 * Exception thrown when validation fails - not retryable
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of(message);
    }

    public ValidationException(String message, List<String> errors) {
        super(message + ": " + String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
