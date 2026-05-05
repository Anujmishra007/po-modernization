package com.wms.po.plugin.finalize;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object passed to finalization plugins.
 *
 * Contains all information about the receipt being finalized,
 * allowing plugins to make decisions and modifications.
 */
@Data
@Builder
public class FinalizeContext {

    // ═══════════════════════════════════════════════════════════════════════
    // Receipt Header Information
    // ═══════════════════════════════════════════════════════════════════════

    private String receiptKey;
    private String storerKey;
    private String facility;
    private String receiptType;
    private String status;
    private String externalReceiptKey;
    private String poKey;

    // ═══════════════════════════════════════════════════════════════════════
    // Receipt Details
    // ═══════════════════════════════════════════════════════════════════════

    @Builder.Default
    private List<ReceiptLineContext> lines = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════
    // Execution Context
    // ═══════════════════════════════════════════════════════════════════════

    private String userId;
    private LocalDateTime executionTime;
    private String clientKey;
    private String countryCode;

    // ═══════════════════════════════════════════════════════════════════════
    // Plugin Communication
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Shared data between plugins.
     * Plugins can read/write to share state.
     */
    @Builder.Default
    private Map<String, Object> sharedData = new HashMap<>();

    /**
     * Plugin-specific parameters from configuration.
     */
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Messages/warnings accumulated during plugin execution.
     */
    @Builder.Default
    private List<String> messages = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════
    // Flags
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Flag to skip remaining plugins.
     */
    private boolean skipRemainingPlugins;

    /**
     * Flag indicating this is a re-finalization.
     */
    private boolean reFinalize;

    /**
     * Flag for dry-run mode (no actual changes).
     */
    private boolean dryRun;

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get total quantity across all lines.
     */
    public BigDecimal getTotalQuantity() {
        return lines.stream()
            .map(ReceiptLineContext::getQuantityReceived)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total line count.
     */
    public int getLineCount() {
        return lines.size();
    }

    /**
     * Add a message to the context.
     */
    public void addMessage(String message) {
        messages.add(message);
    }

    /**
     * Set shared data value.
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }

    /**
     * Get shared data value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        return (T) sharedData.get(key);
    }

    /**
     * Get parameter with default value.
     */
    public String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Receipt Line Context
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    @Builder
    public static class ReceiptLineContext {
        private int lineNumber;
        private String sku;
        private String skuDescription;
        private BigDecimal quantityExpected;
        private BigDecimal quantityReceived;
        private String packKey;
        private String uom;
        private String toLocation;
        private String toId;
        private String status;

        // Lottables
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lottable04;
        private String lottable05;
        private String lottable06;
        private String lottable07;
        private String lottable08;
        private String lottable09;
        private String lottable10;

        // Additional fields
        private String poKey;
        private Integer poLineNumber;
        private String supplierCode;
        private String conditionCode;
        private String holdCode;
        private String caseid;  // Carton/Case ID for tracking

        // Inventory reference (after finalization)
        private String lotxlocxidKey;

        // Plugin modification flags
        private boolean modified;
        private boolean skipped;

        /**
         * Mark line as modified by plugin.
         */
        public void markModified() {
            this.modified = true;
        }

        /**
         * Mark line to be skipped from finalization.
         */
        public void markSkipped() {
            this.skipped = true;
        }
    }
}
