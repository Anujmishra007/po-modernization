package com.wms.po.config;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler with legacy error code support.
 *
 * Returns error responses that include:
 * - Modern error code (e.g., "PO_001")
 * - Legacy numeric code (e.g., 68800) for backward compatibility
 * - Human-readable message
 * - Additional context details
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle BusinessException with legacy error code mapping
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception [{}({})] : {}",
            ex.getModernCode(), ex.getLegacyCode(), ex.getMessage());

        Map<String, Object> error = ex.toErrorResponse();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", ex.getHttpStatus());

        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Handle ValidationException
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("Validation exception: {}", ex.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("errorCode", ErrorCode.VALIDATION_BUSINESS_RULE.getModernCode());
        error.put("legacyCode", ErrorCode.VALIDATION_BUSINESS_RULE.getLegacyCode());
        error.put("message", ex.getMessage());
        error.put("errors", ex.getErrors());
        error.put("retryable", false);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle Spring validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("errorCode", ErrorCode.VALIDATION_REQUIRED_FIELD.getModernCode());
        error.put("legacyCode", ErrorCode.VALIDATION_REQUIRED_FIELD.getLegacyCode());
        error.put("error", "Validation Failed");

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        error.put("fieldErrors", fieldErrors);
        error.put("retryable", false);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle JSON parse errors (HttpMessageNotReadableException)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("errorCode", "VAL_000");
        error.put("legacyCode", 69000);
        error.put("error", "Bad Request");
        error.put("message", "Invalid JSON payload - " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
        error.put("retryable", false);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("errorCode", ErrorCode.VALIDATION_INVALID_FORMAT.getModernCode());
        error.put("legacyCode", ErrorCode.VALIDATION_INVALID_FORMAT.getLegacyCode());
        error.put("error", "Bad Request");
        error.put("message", ex.getMessage());
        error.put("retryable", false);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle generic RuntimeException - catch-all
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("errorCode", ErrorCode.UNKNOWN_ERROR.getModernCode());
        error.put("legacyCode", ErrorCode.UNKNOWN_ERROR.getLegacyCode());
        error.put("message", ex.getMessage());
        error.put("retryable", false);

        // Check for specific error patterns
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not found")) {
            error.put("status", HttpStatus.NOT_FOUND.value());
            error.put("error", "Not Found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("error", "Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("errorCode", ErrorCode.UNKNOWN_ERROR.getModernCode());
        error.put("legacyCode", ErrorCode.UNKNOWN_ERROR.getLegacyCode());
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred");
        error.put("retryable", false);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
