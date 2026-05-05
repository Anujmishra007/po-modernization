package com.wms.po.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SKU entity for rules processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SKUEntity {

    private String sku;
    private String storerKey;
    private String description;
    private String packKey;

    // SKU attributes
    private String skuGroup;
    private String skuClass;
    private String skuType;
    private String hazmatCode;
    private Boolean serialTracked;
    private Boolean lotTracked;
    private Boolean expiryTracked;

    // Dimensions
    private BigDecimal weight;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal cube;

    // Lottable configuration
    private Map<String, String> lottableConfig;

    // Validation results (set by rules)
    private boolean valid = true;
    private String validationError;

    // Computed fields (set by rules)
    private String computedStorageType;
    private String computedRotation;
    private Integer computedPalletTieHigh;

    public void markInvalid(String error) {
        this.valid = false;
        this.validationError = error;
    }
}
