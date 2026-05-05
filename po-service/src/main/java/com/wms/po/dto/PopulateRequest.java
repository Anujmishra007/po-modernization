package com.wms.po.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for populate operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulateRequest {

    @NotNull(message = "At least one PO key is required")
    @Size(min = 1, message = "At least one PO key is required")
    private List<String> poKeys;

    @NotBlank(message = "Storer key is required")
    private String storerKey;

    @NotBlank(message = "Facility is required")
    private String facility;

    private String userId;

    private Map<String, Object> metadata;

    // Optional flags
    private Boolean skipValidation;
    private Boolean dryRun;
    private Boolean async;

    public boolean shouldSkipValidation() {
        return Boolean.TRUE.equals(skipValidation);
    }

    public boolean isDryRun() {
        return Boolean.TRUE.equals(dryRun);
    }

    public boolean isAsync() {
        return Boolean.TRUE.equals(async);
    }
}
