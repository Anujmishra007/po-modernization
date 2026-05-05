package com.wms.po.plugin.finalize;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Abstract base class for post-finalize plugins.
 *
 * Post-finalize plugins execute AFTER receipt finalization and can:
 * - Create UCC/LPN records
 * - Trigger downstream processes (putaway, allocation)
 * - Send notifications
 * - Update external systems
 * - Create audit records
 *
 * Replaces ispASNFZ* stored procedure series.
 */
@Slf4j
public abstract class AbstractPostFinalizePlugin implements FinalizePlugin {

    @Override
    public PluginType getType() {
        return PluginType.POST_FINALIZE;
    }

    @Override
    public FinalizePluginResult execute(FinalizeContext context) {
        log.debug("Executing post-finalize plugin: {} for receipt: {}",
            getPluginId(), context.getReceiptKey());

        try {
            // 1. Check if should execute
            if (!shouldProcessReceipt(context)) {
                log.debug("Plugin {} skipped for receipt {}", getPluginId(), context.getReceiptKey());
                return FinalizePluginResult.success("Skipped - not applicable");
            }

            // 2. Execute main logic
            FinalizePluginResult result = doExecute(context);

            // 3. Handle result
            if (result.isSuccess()) {
                onSuccess(context, result);
            } else {
                onFailure(context, result);
            }

            return result;

        } catch (Exception e) {
            log.error("Post-finalize plugin {} failed: {}", getPluginId(), e.getMessage(), e);

            // Post-finalize failures typically don't abort - they're non-critical
            FinalizePluginResult result = FinalizePluginResult.builder()
                .success(false)
                .errorCode("POST_FINALIZE_ERROR")
                .errorMessage("Post-finalize plugin " + getPluginId() + " failed: " + e.getMessage())
                .continueProcessing(true)  // Continue with other plugins
                .build();

            result.addWarning("Plugin " + getPluginId() + " failed but processing continued");
            return result;
        }
    }

    /**
     * Check if this receipt should be processed.
     * Override to add custom filtering.
     *
     * @param context The finalization context
     * @return true if should process
     */
    protected boolean shouldProcessReceipt(FinalizeContext context) {
        return true;
    }

    /**
     * Execute the main plugin logic.
     * Subclasses must implement this method.
     *
     * @param context The finalization context
     * @return Execution result
     */
    protected abstract FinalizePluginResult doExecute(FinalizeContext context);

    /**
     * Called after successful execution.
     * Override to add custom success handling.
     *
     * @param context The finalization context
     * @param result The execution result
     */
    protected void onSuccess(FinalizeContext context, FinalizePluginResult result) {
        log.info("Post-finalize plugin {} completed successfully for receipt {}",
            getPluginId(), context.getReceiptKey());
    }

    /**
     * Called after failed execution.
     * Override to add custom failure handling.
     *
     * @param context The finalization context
     * @param result The execution result
     */
    protected void onFailure(FinalizeContext context, FinalizePluginResult result) {
        log.warn("Post-finalize plugin {} failed for receipt {}: {}",
            getPluginId(), context.getReceiptKey(), result.getErrorMessage());
    }

    /**
     * Helper to check if receipt is of a specific type.
     */
    protected boolean isReceiptType(FinalizeContext context, String... types) {
        String receiptType = context.getReceiptType();
        if (receiptType == null) {
            return false;
        }
        for (String type : types) {
            if (type.equalsIgnoreCase(receiptType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to check if client matches.
     */
    protected boolean isClient(FinalizeContext context, String... clients) {
        String clientKey = context.getClientKey();
        if (clientKey == null) {
            clientKey = context.getStorerKey();
        }
        for (String client : clients) {
            if (client.equalsIgnoreCase(clientKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to get inventory keys from finalized lines.
     */
    protected List<String> getInventoryKeys(FinalizeContext context) {
        return context.getLines().stream()
            .filter(line -> line.getLotxlocxidKey() != null)
            .map(FinalizeContext.ReceiptLineContext::getLotxlocxidKey)
            .toList();
    }
}
