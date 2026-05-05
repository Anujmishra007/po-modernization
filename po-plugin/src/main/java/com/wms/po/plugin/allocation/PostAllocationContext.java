package com.wms.po.plugin.allocation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context for Post-Allocation Plugin execution.
 *
 * Contains all allocation details and execution context.
 */
@Data
@Builder
public class PostAllocationContext {

    // ═══════════════════════════════════════════════════════════════════════
    // Order Context
    // ═══════════════════════════════════════════════════════════════════════

    private String orderKey;
    private String orderDetailKey;
    private String storerKey;
    private String sku;
    private String packKey;

    // ═══════════════════════════════════════════════════════════════════════
    // Allocation Details
    // ═══════════════════════════════════════════════════════════════════════

    private BigDecimal qtyAllocated;
    private BigDecimal qtyRequested;
    private BigDecimal qtyShorted;
    private String allocationStrategy;
    private String allocationType;
    private LocalDateTime allocationDate;
    private String allocatedBy;

    // ═══════════════════════════════════════════════════════════════════════
    // Inventory Details
    // ═══════════════════════════════════════════════════════════════════════

    private List<AllocationLine> allocationLines;
    private String lot;
    private String id;
    private String locationKey;

    // ═══════════════════════════════════════════════════════════════════════
    // Client/Region Context
    // ═══════════════════════════════════════════════════════════════════════

    private String countryCode;
    private String clientCode;
    private String warehouseCode;
    private String facilityType;

    // ═══════════════════════════════════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════════════════════════════════

    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    @Builder.Default
    private Map<String, Object> storerConfig = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // Execution State
    // ═══════════════════════════════════════════════════════════════════════

    private String transactionId;
    private String userId;
    private LocalDateTime executionTime;

    @Builder.Default
    private Map<String, Object> executionData = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // Nested Types
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    @Builder
    public static class AllocationLine {
        private String pickDetailKey;
        private String orderKey;
        private String orderDetailKey;
        private String sku;
        private String lot;
        private String id;
        private String locationKey;
        private BigDecimal qtyAllocated;
        private BigDecimal qtyPicked;
        private String status;
        private String packKey;
        private String uom;
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private LocalDateTime lottable04;
        private LocalDateTime lottable05;
        private String conditionCode;
        private Integer priority;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    public boolean isFullyAllocated() {
        return qtyShorted == null || qtyShorted.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPartialAllocation() {
        return qtyShorted != null && qtyShorted.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getAllocationPercent() {
        if (qtyRequested == null || qtyRequested.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return qtyAllocated.divide(qtyRequested, 4, java.math.RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100));
    }

    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        return value != null ? (T) value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <T> T getStorerConfigValue(String key, T defaultValue) {
        Object value = storerConfig.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public void setExecutionData(String key, Object value) {
        executionData.put(key, value);
    }

    public Object getExecutionData(String key) {
        return executionData.get(key);
    }
}
