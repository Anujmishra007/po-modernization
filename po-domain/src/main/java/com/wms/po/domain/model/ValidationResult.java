package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validation activity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    @Builder.Default
    private boolean valid = true;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public static ValidationResult success() {
        return ValidationResult.builder()
            .valid(true)
            .build();
    }

    public static ValidationResult failure(String error) {
        return ValidationResult.builder()
            .valid(false)
            .errors(List.of(error))
            .build();
    }

    public static ValidationResult failure(List<String> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errors(errors)
            .build();
    }

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
        valid = false;
    }

    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }
}
