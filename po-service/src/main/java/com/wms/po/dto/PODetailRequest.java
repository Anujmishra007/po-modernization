package com.wms.po.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for PO line item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PODetailRequest {

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal qtyOrdered;

    private String packKey;
    private String uom;

    private BigDecimal unitPrice;

    private Map<String, String> lottables;
}
