package com.wms.po.domain.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Business Exception with legacy error code support.
 *
 * Maintains compatibility with SQL Server SP error codes while
 * providing modern exception handling in Java.
 *
 * Covers all error categories from MIGRATION_MASTER_TRACKER.md:
 * - SPs (134 total): Wrapper, Pre/Post Finalize, Core Engine, Putaway, etc.
 * - Triggers (22 total): PO, Receipt, TransmitLog listeners
 * - Jobs (57 total): Auto-processing, Interface, Archive, Client-specific
 * - Views (60 total): Core PO/ASN, BI views
 * - Functions (70 total): Utility, Barcode, Putaway functions
 *
 * Usage:
 * <pre>
 * throw new BusinessException(ErrorCode.PO_NOT_FOUND)
 *     .withDetail("poKey", "PO-001");
 *
 * throw BusinessException.poNotFound("PO-001");
 *
 * throw BusinessException.pluginFailed("HMPreFinalizePlugin", cause);
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDescription(), cause);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    /**
     * Add detail to the exception for debugging/logging
     */
    public BusinessException withDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    /**
     * Get legacy numeric error code (for backward compatibility)
     */
    public int getLegacyCode() {
        return errorCode.getLegacyCode();
    }

    /**
     * Get modern error code string
     */
    public String getModernCode() {
        return errorCode.getModernCode();
    }

    /**
     * Check if this exception is retryable
     */
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    /**
     * Get suggested HTTP status code
     */
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    /**
     * Get error category
     */
    public String getCategory() {
        return errorCode.getCategory();
    }

    /**
     * Build error response map for API
     */
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("errorCode", errorCode.getModernCode());
        response.put("legacyCode", errorCode.getLegacyCode());
        response.put("message", getMessage());
        response.put("description", errorCode.getDescription());
        response.put("category", errorCode.getCategory());
        if (!details.isEmpty()) {
            response.put("details", details);
        }
        response.put("retryable", isRetryable());
        return response;
    }

    @Override
    public String toString() {
        return String.format("BusinessException[code=%s(%d), category=%s, message=%s, details=%s]",
            errorCode.getModernCode(),
            errorCode.getLegacyCode(),
            errorCode.getCategory(),
            getMessage(),
            details);
    }

    // ═══════════════════════════════════════════════════════════════
    // PO/ASN Factory Methods
    // Source: WM.lsp_ASN_PopulatePOs_Wrapper, WM.lsp_ASN_PopulatePODs_Wrapper
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException poNotFound(String poKey) {
        return new BusinessException(ErrorCode.PO_NOT_FOUND, "PO not found: " + poKey)
            .withDetail("poKey", poKey);
    }

    public static BusinessException poAlreadyClosed(String poKey) {
        return new BusinessException(ErrorCode.PO_ALREADY_CLOSED, "PO already closed: " + poKey)
            .withDetail("poKey", poKey);
    }

    public static BusinessException poCancelled(String poKey) {
        return new BusinessException(ErrorCode.PO_CANCELLED, "PO cancelled: " + poKey)
            .withDetail("poKey", poKey);
    }

    public static BusinessException poLineNotFound(String poKey, String lineNumber) {
        return new BusinessException(ErrorCode.PO_LINE_NOT_FOUND,
            String.format("PO Line not found: %s line %s", poKey, lineNumber))
            .withDetail("poKey", poKey)
            .withDetail("lineNumber", lineNumber);
    }

    public static BusinessException poLineFullyReceived(String poKey, String lineNumber) {
        return new BusinessException(ErrorCode.PO_LINE_FULLY_RECEIVED,
            String.format("PO Line fully received: %s line %s", poKey, lineNumber))
            .withDetail("poKey", poKey)
            .withDetail("lineNumber", lineNumber);
    }

    public static BusinessException asnNotFound(String receiptKey) {
        return new BusinessException(ErrorCode.ASN_NOT_FOUND, "ASN not found: " + receiptKey)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException asnAlreadyFinalized(String receiptKey) {
        return new BusinessException(ErrorCode.ASN_ALREADY_FINALIZED, "ASN already finalized: " + receiptKey)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException asnInvalidStatus(String receiptKey, String currentStatus, String expectedStatus) {
        return new BusinessException(ErrorCode.ASN_INVALID_STATUS,
            String.format("ASN %s has invalid status: %s (expected: %s)", receiptKey, currentStatus, expectedStatus))
            .withDetail("receiptKey", receiptKey)
            .withDetail("currentStatus", currentStatus)
            .withDetail("expectedStatus", expectedStatus);
    }

    public static BusinessException storerMismatch(String expected, String actual) {
        return new BusinessException(ErrorCode.STORER_MISMATCH,
            String.format("Storer mismatch: expected %s, got %s", expected, actual))
            .withDetail("expected", expected)
            .withDetail("actual", actual);
    }

    public static BusinessException skuNotFound(String sku) {
        return new BusinessException(ErrorCode.SKU_NOT_FOUND, "SKU not found: " + sku)
            .withDetail("sku", sku);
    }

    public static BusinessException poVarianceExceeded(String poKey, Object expected, Object actual, Object tolerance) {
        return new BusinessException(ErrorCode.PO_VARIANCE_EXCEEDED,
            String.format("PO variance exceeded for %s: expected=%s, actual=%s, tolerance=%s",
                poKey, expected, actual, tolerance))
            .withDetail("poKey", poKey)
            .withDetail("expected", expected)
            .withDetail("actual", actual)
            .withDetail("tolerance", tolerance);
    }

    // ═══════════════════════════════════════════════════════════════
    // Receipt/Finalization Factory Methods
    // Source: WM.lsp_FinalizeReceipt_Wrapper, ispFinalizeReceipt
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException receiptNotFound(String receiptKey) {
        return new BusinessException(ErrorCode.RECEIPT_NOT_FOUND, "Receipt not found: " + receiptKey)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException receiptAlreadyFinalized(String receiptKey) {
        return new BusinessException(ErrorCode.RECEIPT_ALREADY_FINALIZED, "Receipt already finalized: " + receiptKey)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException receiptInvalidStatus(String receiptKey, String currentStatus, String expectedStatus) {
        return new BusinessException(ErrorCode.RECEIPT_INVALID_STATUS,
            String.format("Receipt %s has invalid status: %s (expected: %s)", receiptKey, currentStatus, expectedStatus))
            .withDetail("receiptKey", receiptKey)
            .withDetail("currentStatus", currentStatus)
            .withDetail("expectedStatus", expectedStatus);
    }

    public static BusinessException receiptDetailNotFound(String receiptKey, String receiptLineNumber) {
        return new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
            String.format("Receipt detail not found: %s line %s", receiptKey, receiptLineNumber))
            .withDetail("receiptKey", receiptKey)
            .withDetail("receiptLineNumber", receiptLineNumber);
    }

    public static BusinessException receiptQuantityMismatch(String receiptKey, Object expected, Object actual) {
        return new BusinessException(ErrorCode.RECEIPT_QUANTITY_MISMATCH,
            String.format("Quantity mismatch for receipt %s: expected=%s, actual=%s", receiptKey, expected, actual))
            .withDetail("receiptKey", receiptKey)
            .withDetail("expected", expected)
            .withDetail("actual", actual);
    }

    public static BusinessException overReceiveNotAllowed(String receiptKey, Object ordered, Object received) {
        return new BusinessException(ErrorCode.RECEIPT_OVERRECEIVE_NOT_ALLOWED,
            String.format("Over-receive not allowed for %s: ordered=%s, received=%s", receiptKey, ordered, received))
            .withDetail("receiptKey", receiptKey)
            .withDetail("ordered", ordered)
            .withDetail("received", received);
    }

    public static BusinessException finalizeValidationFailed(String receiptKey, String reason) {
        return new BusinessException(ErrorCode.FINALIZE_VALIDATION_FAILED,
            String.format("Finalize validation failed for %s: %s", receiptKey, reason))
            .withDetail("receiptKey", receiptKey)
            .withDetail("reason", reason);
    }

    public static BusinessException finalizeInventoryPostFailed(String receiptKey, Throwable cause) {
        return new BusinessException(ErrorCode.FINALIZE_INVENTORY_POST_FAILED,
            "Inventory posting failed for receipt: " + receiptKey, cause)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException finalizePutawayReleaseFailed(String receiptKey, Throwable cause) {
        return new BusinessException(ErrorCode.FINALIZE_PUTAWAY_RELEASE_FAILED,
            "Putaway release failed for receipt: " + receiptKey, cause)
            .withDetail("receiptKey", receiptKey);
    }

    // ═══════════════════════════════════════════════════════════════
    // Inventory Factory Methods
    // Source: nspInventoryHoldWrapper, Inventory processing SPs
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException inventoryNotFound(String sku, String location) {
        return new BusinessException(ErrorCode.INVENTORY_NOT_FOUND,
            String.format("Inventory not found for SKU %s at %s", sku, location))
            .withDetail("sku", sku)
            .withDetail("location", location);
    }

    public static BusinessException inventoryInsufficient(String sku, String location, Object required, Object available) {
        return new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT,
            String.format("Insufficient inventory for SKU %s at %s: required=%s, available=%s",
                sku, location, required, available))
            .withDetail("sku", sku)
            .withDetail("location", location)
            .withDetail("required", required)
            .withDetail("available", available);
    }

    public static BusinessException inventoryOnHold(String sku, String location, String holdCode) {
        return new BusinessException(ErrorCode.INVENTORY_ON_HOLD,
            String.format("Inventory on hold for SKU %s at %s: hold=%s", sku, location, holdCode))
            .withDetail("sku", sku)
            .withDetail("location", location)
            .withDetail("holdCode", holdCode);
    }

    public static BusinessException locationNotFound(String location) {
        return new BusinessException(ErrorCode.LOCATION_NOT_FOUND, "Location not found: " + location)
            .withDetail("location", location);
    }

    public static BusinessException locationFull(String location) {
        return new BusinessException(ErrorCode.LOCATION_FULL, "Location full: " + location)
            .withDetail("location", location);
    }

    public static BusinessException licensePlateNotFound(String id) {
        return new BusinessException(ErrorCode.LICENSE_PLATE_NOT_FOUND, "License plate not found: " + id)
            .withDetail("id", id);
    }

    public static BusinessException lotNotFound(String lot) {
        return new BusinessException(ErrorCode.LOT_NOT_FOUND, "Lot not found: " + lot)
            .withDetail("lot", lot);
    }

    public static BusinessException lotExpired(String lot, String expiryDate) {
        return new BusinessException(ErrorCode.LOT_EXPIRED,
            String.format("Lot expired: %s (expired: %s)", lot, expiryDate))
            .withDetail("lot", lot)
            .withDetail("expiryDate", expiryDate);
    }

    public static BusinessException holdApplicationFailed(String receiptKey, String holdCode, Throwable cause) {
        return new BusinessException(ErrorCode.HOLD_APPLICATION_FAILED,
            String.format("Hold application failed for %s: hold=%s", receiptKey, holdCode), cause)
            .withDetail("receiptKey", receiptKey)
            .withDetail("holdCode", holdCode);
    }

    // ═══════════════════════════════════════════════════════════════
    // Putaway Factory Methods
    // Source: nspPASTD, WM.lsp_ASNReleasePATask_Wrapper, ispPARL01-08
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException putawayStrategyNotFound(String sku, String storerKey) {
        return new BusinessException(ErrorCode.PUTAWAY_STRATEGY_NOT_FOUND,
            String.format("Putaway strategy not found for SKU %s, storer %s", sku, storerKey))
            .withDetail("sku", sku)
            .withDetail("storerKey", storerKey);
    }

    public static BusinessException putawayLocationNotFound(String receiptKey) {
        return new BusinessException(ErrorCode.PUTAWAY_LOCATION_NOT_FOUND,
            "No putaway location found for receipt: " + receiptKey)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException putawayTaskCreateFailed(String receiptKey, Throwable cause) {
        return new BusinessException(ErrorCode.PUTAWAY_TASK_CREATE_FAILED,
            "Putaway task creation failed for receipt: " + receiptKey, cause)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException putawayTaskReleaseFailed(String taskKey, Throwable cause) {
        return new BusinessException(ErrorCode.PUTAWAY_TASK_RELEASE_FAILED,
            "Putaway task release failed: " + taskKey, cause)
            .withDetail("taskKey", taskKey);
    }

    public static BusinessException putawayBatchFailed(String receiptKey, String variant, Throwable cause) {
        ErrorCode errorCode = switch (variant) {
            case "PA01" -> ErrorCode.PUTAWAY_BATCH_PA01_FAILED;
            case "PA02" -> ErrorCode.PUTAWAY_BATCH_PA02_FAILED;
            case "PA03" -> ErrorCode.PUTAWAY_BATCH_PA03_FAILED;
            case "PA04" -> ErrorCode.PUTAWAY_BATCH_PA04_FAILED;
            case "PA05" -> ErrorCode.PUTAWAY_BATCH_PA05_FAILED;
            case "PA06" -> ErrorCode.PUTAWAY_BATCH_PA06_FAILED;
            default -> ErrorCode.PUTAWAY_BATCH_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Batch putaway %s failed for receipt: %s", variant, receiptKey), cause)
            .withDetail("receiptKey", receiptKey)
            .withDetail("variant", variant);
    }

    public static BusinessException putawayReleaseFailed(String receiptKey, String variant, Throwable cause) {
        ErrorCode errorCode = switch (variant) {
            case "PARL01" -> ErrorCode.PUTAWAY_PARL01_FAILED;
            case "PARL02" -> ErrorCode.PUTAWAY_PARL02_FAILED;
            case "PARL03" -> ErrorCode.PUTAWAY_PARL03_FAILED;
            case "PARL04" -> ErrorCode.PUTAWAY_PARL04_FAILED;
            case "PARL05" -> ErrorCode.PUTAWAY_PARL05_FAILED;
            case "PARL06" -> ErrorCode.PUTAWAY_PARL06_FAILED;
            case "PARL07" -> ErrorCode.PUTAWAY_PARL07_FAILED;
            case "PARL08" -> ErrorCode.PUTAWAY_PARL08_FAILED;
            default -> ErrorCode.PUTAWAY_TASK_RELEASE_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("PA release %s failed for receipt: %s", variant, receiptKey), cause)
            .withDetail("receiptKey", receiptKey)
            .withDetail("variant", variant);
    }

    // ═══════════════════════════════════════════════════════════════
    // XDock/Allocation Factory Methods
    // Source: WM.lsp_FlowThruAllocate_Wrapper, nsp_xdockorderprocessing
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException xdockOrderNotFound(String orderKey) {
        return new BusinessException(ErrorCode.XDOCK_ORDER_NOT_FOUND,
            "XDock order not found: " + orderKey)
            .withDetail("orderKey", orderKey);
    }

    public static BusinessException xdockAllocationFailed(String receiptKey, Throwable cause) {
        return new BusinessException(ErrorCode.XDOCK_ALLOCATION_FAILED,
            "XDock allocation failed for receipt: " + receiptKey, cause)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException xdockProcessingFailed(String orderKey, Throwable cause) {
        return new BusinessException(ErrorCode.XDOCK_PROCESSING_FAILED,
            "XDock processing failed for order: " + orderKey, cause)
            .withDetail("orderKey", orderKey);
    }

    public static BusinessException flowThruAllocationFailed(String receiptKey, Throwable cause) {
        return new BusinessException(ErrorCode.FLOWTHRU_ALLOCATION_FAILED,
            "Flow-thru allocation failed for receipt: " + receiptKey, cause)
            .withDetail("receiptKey", receiptKey);
    }

    // ═══════════════════════════════════════════════════════════════
    // Lottable Rule Factory Methods
    // Source: ispLottableRule_Wrapper, ispDefLot*, ispGenLot*
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException lottableRuleNotFound(String ruleName) {
        return new BusinessException(ErrorCode.LOTTABLE_RULE_NOT_FOUND,
            "Lottable rule not found: " + ruleName)
            .withDetail("ruleName", ruleName);
    }

    public static BusinessException lottableRuleExecutionFailed(String ruleName, Throwable cause) {
        return new BusinessException(ErrorCode.LOTTABLE_RULE_EXECUTION_FAILED,
            "Lottable rule execution failed: " + ruleName, cause)
            .withDetail("ruleName", ruleName);
    }

    public static BusinessException lottableMappingFailed(String field, String reason) {
        return new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
            String.format("Lottable mapping failed for %s: %s", field, reason))
            .withDetail("field", field)
            .withDetail("reason", reason);
    }

    public static BusinessException lottableGenerationFailed(int lottableIndex, String reason) {
        ErrorCode errorCode = switch (lottableIndex) {
            case 1 -> ErrorCode.LOTTABLE01_GENERATION_FAILED;
            case 2 -> ErrorCode.LOTTABLE02_GENERATION_FAILED;
            case 3 -> ErrorCode.LOTTABLE03_GENERATION_FAILED;
            case 4 -> ErrorCode.LOTTABLE04_GENERATION_FAILED;
            case 5 -> ErrorCode.LOTTABLE05_GENERATION_FAILED;
            default -> ErrorCode.LOTTABLE_RULE_EXECUTION_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Lottable%02d generation failed: %s", lottableIndex, reason))
            .withDetail("lottableIndex", lottableIndex)
            .withDetail("reason", reason);
    }

    public static BusinessException droolsRuleFailed(String ruleName, Throwable cause) {
        return new BusinessException(ErrorCode.LOTTABLE_DROOLS_RULE_FAILED,
            "Drools lottable rule failed: " + ruleName, cause)
            .withDetail("ruleName", ruleName);
    }

    // ═══════════════════════════════════════════════════════════════
    // Plugin/Hook Factory Methods
    // Source: ispPRREC*, ispASNFZ*, ispPRPPLPO*, Client plugins
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException pluginNotFound(String pluginName) {
        return new BusinessException(ErrorCode.PLUGIN_NOT_FOUND,
            "Plugin not found: " + pluginName)
            .withDetail("pluginName", pluginName);
    }

    public static BusinessException pluginExecutionFailed(String pluginName, Throwable cause) {
        return new BusinessException(ErrorCode.PLUGIN_EXECUTION_FAILED,
            "Plugin execution failed: " + pluginName, cause)
            .withDetail("pluginName", pluginName);
    }

    public static BusinessException preFinalizeHookFailed(String hookName, String receiptKey, Throwable cause) {
        ErrorCode errorCode = switch (hookName.toUpperCase()) {
            case "HM", "H&M" -> ErrorCode.PRE_FINALIZE_HM_FAILED;
            case "NIKE" -> ErrorCode.PRE_FINALIZE_NIKE_FAILED;
            case "ADIDAS" -> ErrorCode.PRE_FINALIZE_ADIDAS_FAILED;
            case "COLUMBIA" -> ErrorCode.PRE_FINALIZE_COLUMBIA_FAILED;
            case "UNILEVER" -> ErrorCode.PRE_FINALIZE_UNILEVER_FAILED;
            case "NEWLOOK" -> ErrorCode.PRE_FINALIZE_NEWLOOK_FAILED;
            case "INDIA" -> ErrorCode.PRE_FINALIZE_INDIA_FAILED;
            case "DSG_TH", "DSG THAILAND" -> ErrorCode.PRE_FINALIZE_DSG_TH_FAILED;
            case "REGIONAL" -> ErrorCode.PRE_FINALIZE_REGIONAL_FAILED;
            default -> ErrorCode.PRE_FINALIZE_HOOK_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Pre-finalize hook %s failed for receipt: %s", hookName, receiptKey), cause)
            .withDetail("hookName", hookName)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException postFinalizeHookFailed(String hookName, String receiptKey, Throwable cause) {
        ErrorCode errorCode = switch (hookName.toUpperCase()) {
            case "BATCH_RELEASE" -> ErrorCode.POST_FINALIZE_BATCH_RELEASE_FAILED;
            case "UCC_STAMP" -> ErrorCode.POST_FINALIZE_UCC_STAMP_FAILED;
            case "AUTO_PA" -> ErrorCode.POST_FINALIZE_AUTO_PA_FAILED;
            case "NOTIFICATION" -> ErrorCode.POST_FINALIZE_NOTIFICATION_FAILED;
            case "INV_SYNC" -> ErrorCode.POST_FINALIZE_INV_SYNC_FAILED;
            case "QUALITY_CHECK" -> ErrorCode.POST_FINALIZE_QUALITY_CHECK_FAILED;
            case "CUSTOMS" -> ErrorCode.POST_FINALIZE_CUSTOMS_FAILED;
            case "AUTO_ALLOCATE" -> ErrorCode.POST_FINALIZE_AUTO_ALLOC_FAILED;
            case "NEWLOOK_ADJUST" -> ErrorCode.POST_FINALIZE_NEWLOOK_ADJ_FAILED;
            case "COLUMBIA_UCC" -> ErrorCode.POST_FINALIZE_COLUMBIA_UCC_FAILED;
            default -> ErrorCode.POST_FINALIZE_HOOK_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Post-finalize hook %s failed for receipt: %s", hookName, receiptKey), cause)
            .withDetail("hookName", hookName)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException prePopulateHookFailed(String hookName, String poKey, Throwable cause) {
        ErrorCode errorCode = switch (hookName.toUpperCase()) {
            case "STANDARD" -> ErrorCode.PRE_POPULATE_STANDARD_FAILED;
            case "DATE_VALIDATION" -> ErrorCode.PRE_POPULATE_DATE_VAL_FAILED;
            case "ADIDAS" -> ErrorCode.PRE_POPULATE_ADIDAS_FAILED;
            case "QUANTITY" -> ErrorCode.PRE_POPULATE_QTY_FAILED;
            case "CROSS_REFERENCE" -> ErrorCode.PRE_POPULATE_XREF_FAILED;
            case "JCB" -> ErrorCode.PRE_POPULATE_JCB_FAILED;
            default -> ErrorCode.PRE_POPULATE_HOOK_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Pre-populate hook %s failed for PO: %s", hookName, poKey), cause)
            .withDetail("hookName", hookName)
            .withDetail("poKey", poKey);
    }

    public static BusinessException clientAutoAsnFailed(String clientName, String poKey, Throwable cause) {
        ErrorCode errorCode = switch (clientName.toUpperCase()) {
            case "NIKE_KR", "NIKE KOREA" -> ErrorCode.CLIENT_NIKE_KR_FAILED;
            case "HM_IND", "H&M INDIA" -> ErrorCode.CLIENT_HM_IND_FAILED;
            case "FLEXTRONICS" -> ErrorCode.CLIENT_FLEX_FAILED;
            case "ULM" -> ErrorCode.CLIENT_ULM_FAILED;
            default -> ErrorCode.CLIENT_AUTO_ASN_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Client %s auto-ASN failed for PO: %s", clientName, poKey), cause)
            .withDetail("clientName", clientName)
            .withDetail("poKey", poKey);
    }

    // ═══════════════════════════════════════════════════════════════
    // Job/Scheduler Factory Methods
    // Source: SQL Agent Jobs → Spring @Scheduled
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException jobExecutionFailed(String jobName, Throwable cause) {
        return new BusinessException(ErrorCode.JOB_EXECUTION_FAILED,
            "Job execution failed: " + jobName, cause)
            .withDetail("jobName", jobName);
    }

    public static BusinessException jobAlreadyRunning(String jobName) {
        return new BusinessException(ErrorCode.JOB_ALREADY_RUNNING,
            "Job already running: " + jobName)
            .withDetail("jobName", jobName);
    }

    public static BusinessException autoPopulateJobFailed(Throwable cause) {
        return new BusinessException(ErrorCode.JOB_AUTO_POPULATE_FAILED,
            "Auto populate PO→ASN job failed", cause);
    }

    public static BusinessException autoFinalizeJobFailed(Throwable cause) {
        return new BusinessException(ErrorCode.JOB_AUTO_FINALIZE_FAILED,
            "Auto finalize ASN job failed", cause);
    }

    public static BusinessException autoPaReleaseJobFailed(Throwable cause) {
        return new BusinessException(ErrorCode.JOB_AUTO_PA_RELEASE_FAILED,
            "Auto PA release job failed", cause);
    }

    public static BusinessException inboundMasterJobFailed(Throwable cause) {
        return new BusinessException(ErrorCode.JOB_INBOUND_MASTER_FAILED,
            "Inbound master job failed", cause);
    }

    public static BusinessException archiveJobFailed(Throwable cause) {
        return new BusinessException(ErrorCode.JOB_ARCHIVE_FAILED,
            "Archive job failed", cause);
    }

    // ═══════════════════════════════════════════════════════════════
    // Trigger/Event Factory Methods
    // Source: SQL Triggers → JPA EntityListeners
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException triggerExecutionFailed(String triggerName, Throwable cause) {
        return new BusinessException(ErrorCode.TRIGGER_EXECUTION_FAILED,
            "Trigger execution failed: " + triggerName, cause)
            .withDetail("triggerName", triggerName);
    }

    public static BusinessException eventPublishFailed(String eventType, Throwable cause) {
        return new BusinessException(ErrorCode.EVENT_PUBLISH_FAILED,
            "Event publish failed: " + eventType, cause)
            .withDetail("eventType", eventType);
    }

    public static BusinessException poTriggerFailed(String operation, String poKey, Throwable cause) {
        ErrorCode errorCode = switch (operation.toUpperCase()) {
            case "ADD", "INSERT" -> ErrorCode.PO_HEADER_ADD_TRIGGER_FAILED;
            case "UPDATE" -> ErrorCode.PO_HEADER_UPDATE_TRIGGER_FAILED;
            case "DELETE" -> ErrorCode.PO_HEADER_DELETE_TRIGGER_FAILED;
            default -> ErrorCode.TRIGGER_EXECUTION_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("PO %s trigger failed for: %s", operation, poKey), cause)
            .withDetail("operation", operation)
            .withDetail("poKey", poKey);
    }

    public static BusinessException poDetailTriggerFailed(String operation, String poKey, String lineNumber, Throwable cause) {
        ErrorCode errorCode = switch (operation.toUpperCase()) {
            case "ADD", "INSERT" -> ErrorCode.PO_DETAIL_ADD_TRIGGER_FAILED;
            case "UPDATE" -> ErrorCode.PO_DETAIL_UPDATE_TRIGGER_FAILED;
            case "DELETE" -> ErrorCode.PO_DETAIL_DELETE_TRIGGER_FAILED;
            default -> ErrorCode.TRIGGER_EXECUTION_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("PO detail %s trigger failed for: %s line %s", operation, poKey, lineNumber), cause)
            .withDetail("operation", operation)
            .withDetail("poKey", poKey)
            .withDetail("lineNumber", lineNumber);
    }

    public static BusinessException receiptTriggerFailed(String operation, String receiptKey, Throwable cause) {
        ErrorCode errorCode = switch (operation.toUpperCase()) {
            case "ADD", "INSERT" -> ErrorCode.RECEIPT_HEADER_ADD_TRIGGER_FAILED;
            case "UPDATE" -> ErrorCode.RECEIPT_HEADER_UPDATE_TRIGGER_FAILED;
            default -> ErrorCode.TRIGGER_EXECUTION_FAILED;
        };
        return new BusinessException(errorCode,
            String.format("Receipt %s trigger failed for: %s", operation, receiptKey), cause)
            .withDetail("operation", operation)
            .withDetail("receiptKey", receiptKey);
    }

    // ═══════════════════════════════════════════════════════════════
    // Function/Utility Factory Methods
    // Source: SQL Functions → Java Utils
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException functionExecutionFailed(String functionName, Throwable cause) {
        return new BusinessException(ErrorCode.FUNCTION_EXECUTION_FAILED,
            "Function execution failed: " + functionName, cause)
            .withDetail("functionName", functionName);
    }

    public static BusinessException uomConversionFailed(String fromUom, String toUom, Throwable cause) {
        return new BusinessException(ErrorCode.UOM_CONVERSION_FAILED,
            String.format("UOM conversion failed: %s → %s", fromUom, toUom), cause)
            .withDetail("fromUom", fromUom)
            .withDetail("toUom", toUom);
    }

    public static BusinessException barcodeGenerationFailed(String barcode, Throwable cause) {
        return new BusinessException(ErrorCode.BARCODE_GENERATION_FAILED,
            "Barcode generation failed: " + barcode, cause)
            .withDetail("barcode", barcode);
    }

    public static BusinessException putawayRestrictionBuildFailed(String sku, Throwable cause) {
        return new BusinessException(ErrorCode.PUTAWAY_RESTRICTION_BUILD_FAILED,
            "Putaway restriction build failed for SKU: " + sku, cause)
            .withDetail("sku", sku);
    }

    // ═══════════════════════════════════════════════════════════════
    // Trade Return Factory Methods
    // Source: WM.lsp_ASN_PopulateSOs_Wrapper, WM.lsp_ASN_PopulateSODs_Wrapper
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException tradeReturnNotFound(String orderKey) {
        return new BusinessException(ErrorCode.TRADE_RETURN_NOT_FOUND,
            "Trade return not found: " + orderKey)
            .withDetail("orderKey", orderKey);
    }

    public static BusinessException tradeReturnCreationFailed(String receiptKey, Throwable cause) {
        return new BusinessException(ErrorCode.TRADE_RETURN_CREATION_FAILED,
            "Trade return creation failed for receipt: " + receiptKey, cause)
            .withDetail("receiptKey", receiptKey);
    }

    public static BusinessException tradeReturnProcessingFailed(String orderKey, Throwable cause) {
        return new BusinessException(ErrorCode.TRADE_RETURN_PROCESSING_FAILED,
            "Trade return processing failed for order: " + orderKey, cause)
            .withDetail("orderKey", orderKey);
    }

    // ═══════════════════════════════════════════════════════════════
    // Integration Factory Methods
    // Source: Kafka, Temporal, Legacy Bridge
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException legacySyncFailed(String operation, Throwable cause) {
        return new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
            "Legacy sync failed for operation: " + operation, cause)
            .withDetail("operation", operation);
    }

    public static BusinessException kafkaPublishFailed(String topic, Throwable cause) {
        return new BusinessException(ErrorCode.KAFKA_PUBLISH_FAILED,
            "Kafka publish failed to topic: " + topic, cause)
            .withDetail("topic", topic);
    }

    public static BusinessException temporalWorkflowFailed(String workflowId, Throwable cause) {
        return new BusinessException(ErrorCode.TEMPORAL_WORKFLOW_FAILED,
            "Temporal workflow failed: " + workflowId, cause)
            .withDetail("workflowId", workflowId);
    }

    public static BusinessException temporalActivityFailed(String activityName, Throwable cause) {
        return new BusinessException(ErrorCode.TEMPORAL_ACTIVITY_FAILED,
            "Temporal activity failed: " + activityName, cause)
            .withDetail("activityName", activityName);
    }

    public static BusinessException dualWriteSyncFailed(String entity, String key, Throwable cause) {
        return new BusinessException(ErrorCode.DUAL_WRITE_SYNC_FAILED,
            String.format("Dual write sync failed for %s: %s", entity, key), cause)
            .withDetail("entity", entity)
            .withDetail("key", key);
    }

    // ═══════════════════════════════════════════════════════════════
    // Validation Factory Methods
    // Source: WM.lsp_Validate_Receipt_Std, isp_ASN_ExtendedValidation
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException validationFailed(String field, String reason) {
        return new BusinessException(ErrorCode.VALIDATION_REQUIRED_FIELD,
            String.format("Validation failed for %s: %s", field, reason))
            .withDetail("field", field)
            .withDetail("reason", reason);
    }

    public static BusinessException invalidFormat(String field, String value, String expectedFormat) {
        return new BusinessException(ErrorCode.VALIDATION_INVALID_FORMAT,
            String.format("Invalid format for %s: '%s' (expected: %s)", field, value, expectedFormat))
            .withDetail("field", field)
            .withDetail("value", value)
            .withDetail("expectedFormat", expectedFormat);
    }

    public static BusinessException duplicateValue(String field, String value) {
        return new BusinessException(ErrorCode.VALIDATION_DUPLICATE,
            String.format("Duplicate value for %s: %s", field, value))
            .withDetail("field", field)
            .withDetail("value", value);
    }

    public static BusinessException valueOutOfRange(String field, Object value, Object min, Object max) {
        return new BusinessException(ErrorCode.VALIDATION_RANGE,
            String.format("Value out of range for %s: %s (allowed: %s - %s)", field, value, min, max))
            .withDetail("field", field)
            .withDetail("value", value)
            .withDetail("min", min)
            .withDetail("max", max);
    }

    public static BusinessException invalidStatus(String entity, String key, String currentStatus, String expectedStatus) {
        return new BusinessException(ErrorCode.RECEIPT_INVALID_STATUS,
            String.format("%s %s has invalid status: %s (expected: %s)", entity, key, currentStatus, expectedStatus))
            .withDetail("entity", entity)
            .withDetail("key", key)
            .withDetail("currentStatus", currentStatus)
            .withDetail("expectedStatus", expectedStatus);
    }

    // ═══════════════════════════════════════════════════════════════
    // Configuration Factory Methods
    // Source: CODELKUP, StorerConfig
    // ═══════════════════════════════════════════════════════════════

    public static BusinessException configNotFound(String configKey) {
        return new BusinessException(ErrorCode.CONFIG_NOT_FOUND,
            "Configuration not found: " + configKey)
            .withDetail("configKey", configKey);
    }

    public static BusinessException codeLkupNotFound(String listName, String code) {
        return new BusinessException(ErrorCode.CONFIG_CODELKUP_NOT_FOUND,
            String.format("CodeLkup entry not found: %s / %s", listName, code))
            .withDetail("listName", listName)
            .withDetail("code", code);
    }

    public static BusinessException storerConfigNotFound(String storerKey, String configKey) {
        return new BusinessException(ErrorCode.CONFIG_STORER_NOT_FOUND,
            String.format("Storer config not found: %s / %s", storerKey, configKey))
            .withDetail("storerKey", storerKey)
            .withDetail("configKey", configKey);
    }
}
