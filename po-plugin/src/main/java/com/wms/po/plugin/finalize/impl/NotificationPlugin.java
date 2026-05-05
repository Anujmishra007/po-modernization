package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Notification Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ05 (280 LOC)
 * Client: Universal
 *
 * Sends notifications after receipt finalization:
 * - Creates TRANSMITLOG entries for EDI
 * - Queues webhook notifications
 * - Logs audit events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ05";
    private static final String CLIENT_KEY = "*";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getClientKey() {
        return CLIENT_KEY;
    }

    @Override
    public int getPriority() {
        return 200; // Run late - after all other post-finalize
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Processing notifications for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();

        try {
            // 1. Create TRANSMITLOG entry for EDI (if enabled)
            if (isEDIEnabled(context.getStorerKey())) {
                createTransmitlogEntry(context);
                result.addMessage("Created EDI transmitlog entry");
            }

            // 2. Queue webhook notification (if configured)
            if (isWebhookEnabled(context.getStorerKey())) {
                queueWebhookNotification(context);
                result.addMessage("Queued webhook notification");
            }

            // 3. Log audit event
            logAuditEvent(context);
            result.addMessage("Logged audit event");

            // 4. Update storer receipt counters
            updateStorerCounters(context);

        } catch (Exception e) {
            log.error("Notification processing failed: {}", e.getMessage());
            result.addWarning("Some notifications failed: " + e.getMessage());
        }

        return result;
    }

    private boolean isEDIEnabled(String storerKey) {
        try {
            String enabled = jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.storerconfig " +
                "WHERE storerkey = ? AND configkey = 'EDIEnabled'",
                String.class,
                storerKey
            );
            return "1".equals(enabled) || "Y".equalsIgnoreCase(enabled);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWebhookEnabled(String storerKey) {
        try {
            String url = jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.storerconfig " +
                "WHERE storerkey = ? AND configkey = 'WebhookURL'",
                String.class,
                storerKey
            );
            return url != null && !url.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void createTransmitlogEntry(FinalizeContext context) {
        // Calculate totals
        BigDecimal totalQty = context.getTotalQuantity();
        int lineCount = context.getLineCount();

        String transmitlogKey = generateKey("TRANSMITLOG");

        jdbcTemplate.update(
            "INSERT INTO dbo.transmitlog " +
            "(transmitlogkey, storerkey, tablename, keyvalue, transmitflag, " +
            "transmittype, qty, linecount, adddate, addwho) " +
            "VALUES (?, ?, 'RECEIPT', ?, '0', 'FINALIZE', ?, ?, GETDATE(), ?)",
            transmitlogKey,
            context.getStorerKey(),
            context.getReceiptKey(),
            totalQty,
            lineCount,
            context.getUserId()
        );

        log.debug("Created transmitlog entry: {}", transmitlogKey);
    }

    private void queueWebhookNotification(FinalizeContext context) {
        String payload = buildWebhookPayload(context);
        String webhookKey = generateKey("WEBHOOK");

        jdbcTemplate.update(
            "INSERT INTO dbo.webhookqueue " +
            "(webhookkey, storerkey, eventtype, payload, status, adddate) " +
            "VALUES (?, ?, 'RECEIPT_FINALIZED', ?, '0', GETDATE())",
            webhookKey,
            context.getStorerKey(),
            payload
        );

        log.debug("Queued webhook notification: {}", webhookKey);
    }

    private String buildWebhookPayload(FinalizeContext context) {
        // Simple JSON payload
        return String.format(
            "{\"receiptKey\":\"%s\",\"storerKey\":\"%s\",\"facility\":\"%s\"," +
            "\"lineCount\":%d,\"totalQty\":%s,\"timestamp\":\"%s\"}",
            context.getReceiptKey(),
            context.getStorerKey(),
            context.getFacility(),
            context.getLineCount(),
            context.getTotalQuantity().toPlainString(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private void logAuditEvent(FinalizeContext context) {
        String auditKey = generateKey("AUDITLOG");

        jdbcTemplate.update(
            "INSERT INTO dbo.auditlog " +
            "(auditlogkey, tablename, keyvalue, operation, userid, " +
            "oldvalue, newvalue, adddate) " +
            "VALUES (?, 'RECEIPT', ?, 'FINALIZE', ?, NULL, ?, GETDATE())",
            auditKey,
            context.getReceiptKey(),
            context.getUserId(),
            "Status changed to FINALIZED, Lines: " + context.getLineCount() +
                ", Qty: " + context.getTotalQuantity()
        );
    }

    private void updateStorerCounters(FinalizeContext context) {
        jdbcTemplate.update(
            "UPDATE dbo.storer SET " +
            "receiptsfinalized = ISNULL(receiptsfinalized, 0) + 1, " +
            "lastfinalized = GETDATE() " +
            "WHERE storerkey = ?",
            context.getStorerKey()
        );
    }

    private String generateKey(String tableName) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT dbo.nspg_GetKey(?, 1)",
                String.class,
                tableName
            );
        } catch (Exception e) {
            return tableName + "_" + System.currentTimeMillis();
        }
    }
}
