package com.wms.po.plugin.finalize;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for pre-finalize plugins.
 *
 * Pre-finalize plugins execute BEFORE receipt finalization and can:
 * - Validate receipt data
 * - Transform/modify receipt lines
 * - Apply client-specific business rules
 * - Skip lines from finalization
 * - Abort finalization if validation fails
 *
 * Replaces ispPRREC* stored procedure series.
 */
@Slf4j
public abstract class AbstractPreFinalizePlugin implements FinalizePlugin {

    @Override
    public FinalizePlugin.PluginType getType() {
        return FinalizePlugin.PluginType.PRE_FINALIZE;
    }

    @Override
    public FinalizePluginResult execute(FinalizeContext context) {
        log.debug("Executing pre-finalize plugin: {} for receipt: {}",
            getPluginId(), context.getReceiptKey());

        try {
            // 1. Validate preconditions
            FinalizePluginResult validation = validatePreconditions(context);
            if (!validation.isSuccess()) {
                return validation;
            }

            // 2. Execute main logic
            FinalizePluginResult result = doExecute(context);

            // 3. Post-processing
            if (result.isSuccess()) {
                postProcess(context, result);
            }

            return result;

        } catch (Exception e) {
            log.error("Pre-finalize plugin {} failed: {}", getPluginId(), e.getMessage(), e);
            return FinalizePluginResult.failure("PRE_FINALIZE_ERROR",
                "Pre-finalize plugin " + getPluginId() + " failed: " + e.getMessage());
        }
    }

    /**
     * Validate preconditions before execution.
     * Override to add custom validation.
     *
     * @param context The finalization context
     * @return Validation result
     */
    protected FinalizePluginResult validatePreconditions(FinalizeContext context) {
        // Default: no validation, always proceed
        return FinalizePluginResult.success();
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
     * Post-processing after successful execution.
     * Override to add custom post-processing.
     *
     * @param context The finalization context
     * @param result The execution result
     */
    protected void postProcess(FinalizeContext context, FinalizePluginResult result) {
        // Default: no post-processing
    }

    /**
     * Helper to fail with a validation error.
     */
    protected FinalizePluginResult validationError(String message) {
        return FinalizePluginResult.failure("VALIDATION_ERROR", message);
    }

    /**
     * Helper to skip a line.
     */
    protected void skipLine(FinalizeContext context, int lineNumber, String reason) {
        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            if (line.getLineNumber() == lineNumber) {
                line.markSkipped();
                context.addMessage("Line " + lineNumber + " skipped: " + reason);
                break;
            }
        }
    }

    /**
     * Helper to modify a line's lottable.
     */
    protected void setLineLottable(FinalizeContext context, int lineNumber,
                                    String lottableField, String value) {
        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            if (line.getLineNumber() == lineNumber) {
                switch (lottableField.toUpperCase()) {
                    case "LOTTABLE01" -> line.setLottable01(value);
                    case "LOTTABLE02" -> line.setLottable02(value);
                    case "LOTTABLE03" -> line.setLottable03(value);
                    case "LOTTABLE04" -> line.setLottable04(value);
                    case "LOTTABLE05" -> line.setLottable05(value);
                    case "LOTTABLE06" -> line.setLottable06(value);
                    case "LOTTABLE07" -> line.setLottable07(value);
                    case "LOTTABLE08" -> line.setLottable08(value);
                    case "LOTTABLE09" -> line.setLottable09(value);
                    case "LOTTABLE10" -> line.setLottable10(value);
                }
                line.markModified();
                break;
            }
        }
    }
}
