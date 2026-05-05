package com.wms.po.rules.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context for PO validation rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POValidationContext {

    // Request identifiers
    private String poKey;
    private String storerKey;
    private String facility;
    private String userId;

    // Variation context
    private String region;
    private String client;
    private String dbVersion;

    // Validation state
    @Builder.Default
    private boolean valid = true;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    // Rule execution tracking
    @Builder.Default
    private List<String> executedRules = new ArrayList<>();

    // Custom attributes for rules
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void markRuleExecuted(String ruleName) {
        this.executedRules.add(ruleName);
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) this.attributes.get(key);
    }

    public boolean hasAttribute(String key) {
        return this.attributes.containsKey(key);
    }

    public boolean isRegion(String region) {
        return region != null && region.equals(this.region);
    }

    public boolean isClient(String client) {
        return client != null && client.equals(this.client);
    }

    public boolean isVersion(String version) {
        return version != null && version.equals(this.dbVersion);
    }
}
