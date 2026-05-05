package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request to populate POs to ASN/Receipt
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PopulateRequest {

    private List<String> poKeys;
    private String facility;
    private String storerKey;
    private String userId;
    private Map<String, Object> metadata;

    // Optional override flags
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
