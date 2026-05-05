package com.wms.po.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity listener for Receipt and ReceiptDetail tables.
 *
 * Replaces SQL triggers:
 * - TR-007: ntrReceiptHeaderAdd (@PrePersist) - implicit via service
 * - TR-008: ntrReceiptHeaderUpdate (@PreUpdate)
 * - TR-009: ntrReceiptDetailAdd (@PrePersist) - implicit via service
 * - TR-010: ntrReceiptDetailUpdate (@PreUpdate)
 * - TR-011: ntrReceiptDetailDelete (@PreRemove)
 *
 * These listeners publish domain events that can be consumed
 * for notifications, auditing, inventory updates, and integration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptEntityListener {

    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    // ═══════════════════════════════════════════════════════════════════════
    // Receipt Header Events (ntrReceiptHeader* triggers)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle Receipt creation.
     * Implements ntrReceiptHeaderAdd trigger logic.
     *
     * @param receiptKey New receipt key
     * @param storerKey Storer key
     * @param poKey Associated PO key (if any)
     * @param userId User creating the receipt
     */
    @Transactional
    public void onReceiptCreated(String receiptKey, String storerKey, String poKey, String userId) {
        log.info("Receipt created: receiptKey={}, storer={}, poKey={}", receiptKey, storerKey, poKey);

        // 1. Set default values and timestamps (trigger logic)
        jdbcTemplate.update(
            """
            UPDATE dbo.receipt
            SET adddate = COALESCE(adddate, CURRENT_TIMESTAMP),
                addwho = COALESCE(addwho, ?),
                editdate = CURRENT_TIMESTAMP,
                editwho = ?,
                status = COALESCE(status, '0'),
                type = COALESCE(type, 'STD')
            WHERE receiptkey = ?
            """,
            userId, userId, receiptKey
        );

        // 2. Create audit log entry
        createAuditLog("RECEIPT", receiptKey, "INSERT", "PO=" + poKey, userId);

        // 3. Publish domain event
        ReceiptCreatedEvent event = ReceiptCreatedEvent.builder()
            .receiptKey(receiptKey)
            .storerKey(storerKey)
            .poKey(poKey)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);

        log.debug("Published ReceiptCreatedEvent for {}", receiptKey);
    }

    /**
     * Handle Receipt update.
     * Implements ntrReceiptHeaderUpdate trigger logic (TR-008).
     *
     * @param receiptKey Updated receipt key
     * @param oldValues Previous values (for audit)
     * @param newValues New values
     * @param userId User making the update
     */
    @Transactional
    public void onReceiptUpdated(String receiptKey, Map<String, Object> oldValues,
                                  Map<String, Object> newValues, String userId) {
        log.info("Receipt updated: receiptKey={}", receiptKey);

        // 1. Update edit timestamp
        jdbcTemplate.update(
            """
            UPDATE dbo.receipt
            SET editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE receiptkey = ?
            """,
            userId, receiptKey
        );

        // 2. Track status transitions
        String oldStatus = (String) oldValues.get("status");
        String newStatus = (String) newValues.get("status");

        if (oldStatus != null && newStatus != null && !oldStatus.equals(newStatus)) {
            log.info("Receipt {} status changed: {} → {}", receiptKey, oldStatus, newStatus);
            trackStatusTransition("RECEIPT", receiptKey, oldStatus, newStatus, userId);

            // Special handling for finalization status
            if ("9".equals(newStatus) || "11".equals(newStatus)) {
                handleReceiptFinalization(receiptKey, userId);
            }
        }

        // 3. Create audit log
        createAuditLog("RECEIPT", receiptKey, "UPDATE", buildChangeDescription(oldValues, newValues), userId);

        // 4. Publish domain event
        ReceiptUpdatedEvent event = ReceiptUpdatedEvent.builder()
            .receiptKey(receiptKey)
            .oldValues(oldValues)
            .newValues(newValues)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Handle Receipt deletion (soft or hard).
     *
     * @param receiptKey Deleted receipt key
     * @param userId User deleting
     */
    @Transactional
    public void onReceiptDeleted(String receiptKey, String userId) {
        log.warn("Receipt deleted: receiptKey={}", receiptKey);

        // 1. Check if receipt has any finalized inventory
        Integer inventoryCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.lotxlocxid WHERE receiptkey = ?",
            Integer.class,
            receiptKey
        );

        if (inventoryCount != null && inventoryCount > 0) {
            log.warn("Receipt {} has {} inventory records - deletion may cause data integrity issues",
                receiptKey, inventoryCount);
        }

        // 2. Create audit log
        createAuditLog("RECEIPT", receiptKey, "DELETE", null, userId);

        // 3. Publish domain event
        ReceiptDeletedEvent event = ReceiptDeletedEvent.builder()
            .receiptKey(receiptKey)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Receipt Detail Events (ntrReceiptDetail* triggers)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle Receipt Detail creation.
     * Implements ntrReceiptDetailAdd trigger logic.
     *
     * @param receiptKey Parent receipt key
     * @param lineNumber Line number
     * @param sku SKU
     * @param quantity Received quantity
     * @param userId User creating
     */
    @Transactional
    public void onReceiptDetailCreated(String receiptKey, int lineNumber, String sku,
                                        BigDecimal quantity, String userId) {
        log.info("Receipt Detail created: receiptKey={}, line={}, sku={}, qty={}",
            receiptKey, lineNumber, sku, quantity);

        // 1. Set default values
        jdbcTemplate.update(
            """
            UPDATE dbo.receiptdetail
            SET adddate = COALESCE(adddate, CURRENT_TIMESTAMP),
                addwho = COALESCE(addwho, ?),
                editdate = CURRENT_TIMESTAMP,
                editwho = ?,
                status = COALESCE(status, '0'),
                toloc = COALESCE(toloc, 'RECV')
            WHERE receiptkey = ? AND receiptlinenumber = ?
            """,
            userId, userId, receiptKey, lineNumber
        );

        // 2. Update receipt header totals
        updateReceiptTotals(receiptKey);

        // 3. Update PO received quantities (if linked to PO)
        updatePOReceivedQuantity(receiptKey, lineNumber, quantity);

        // 4. Create audit log
        createAuditLog("RECEIPTDETAIL", receiptKey + "-" + lineNumber, "INSERT",
            String.format("SKU=%s, QTY=%s", sku, quantity), userId);

        // 5. Publish domain event
        ReceiptDetailCreatedEvent event = ReceiptDetailCreatedEvent.builder()
            .receiptKey(receiptKey)
            .lineNumber(lineNumber)
            .sku(sku)
            .quantity(quantity)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Handle Receipt Detail update.
     * Implements ntrReceiptDetailUpdate trigger logic (TR-010).
     *
     * @param receiptKey Parent receipt key
     * @param lineNumber Line number
     * @param oldValues Previous values
     * @param newValues New values
     * @param userId User updating
     */
    @Transactional
    public void onReceiptDetailUpdated(String receiptKey, int lineNumber,
                                        Map<String, Object> oldValues,
                                        Map<String, Object> newValues, String userId) {
        log.info("Receipt Detail updated: receiptKey={}, line={}", receiptKey, lineNumber);

        // 1. Update edit timestamp
        jdbcTemplate.update(
            """
            UPDATE dbo.receiptdetail
            SET editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE receiptkey = ? AND receiptlinenumber = ?
            """,
            userId, receiptKey, lineNumber
        );

        // 2. Update receipt header totals
        updateReceiptTotals(receiptKey);

        // 3. Handle quantity changes
        BigDecimal oldQty = getBigDecimalValue(oldValues.get("qtyreceived"));
        BigDecimal newQty = getBigDecimalValue(newValues.get("qtyreceived"));

        if (oldQty != null && newQty != null && oldQty.compareTo(newQty) != 0) {
            log.info("Receipt {} line {} qty changed: {} → {}", receiptKey, lineNumber, oldQty, newQty);

            // Update PO received quantities
            BigDecimal qtyDelta = newQty.subtract(oldQty);
            updatePOReceivedQuantityDelta(receiptKey, lineNumber, qtyDelta);
        }

        // 4. Handle status changes
        String oldStatus = (String) oldValues.get("status");
        String newStatus = (String) newValues.get("status");

        if (oldStatus != null && newStatus != null && !oldStatus.equals(newStatus)) {
            log.info("Receipt {} line {} status changed: {} → {}", receiptKey, lineNumber, oldStatus, newStatus);
            trackStatusTransition("RECEIPTDETAIL", receiptKey + "-" + lineNumber, oldStatus, newStatus, userId);
        }

        // 5. Create audit log
        createAuditLog("RECEIPTDETAIL", receiptKey + "-" + lineNumber, "UPDATE",
            buildChangeDescription(oldValues, newValues), userId);

        // 6. Publish domain event
        ReceiptDetailUpdatedEvent event = ReceiptDetailUpdatedEvent.builder()
            .receiptKey(receiptKey)
            .lineNumber(lineNumber)
            .oldValues(oldValues)
            .newValues(newValues)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Handle Receipt Detail deletion.
     * Implements ntrReceiptDetailDelete trigger logic (TR-011).
     *
     * @param receiptKey Parent receipt key
     * @param lineNumber Line number
     * @param userId User deleting
     */
    @Transactional
    public void onReceiptDetailDeleted(String receiptKey, int lineNumber, String userId) {
        log.warn("Receipt Detail deleted: receiptKey={}, line={}", receiptKey, lineNumber);

        // 1. Get line details before deletion for rollback
        Map<String, Object> lineDetails = getReceiptLineDetails(receiptKey, lineNumber);

        // 2. Check if line has inventory
        Integer inventoryCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.lotxlocxid
            WHERE receiptkey = ? AND receiptlinenumber = ?
            """,
            Integer.class,
            receiptKey, lineNumber
        );

        if (inventoryCount != null && inventoryCount > 0) {
            log.warn("Receipt {} line {} has {} inventory records - cascading impact",
                receiptKey, lineNumber, inventoryCount);
        }

        // 3. Reverse PO received quantities
        BigDecimal qtyReceived = getBigDecimalValue(lineDetails.get("qtyreceived"));
        if (qtyReceived != null && qtyReceived.compareTo(BigDecimal.ZERO) > 0) {
            updatePOReceivedQuantityDelta(receiptKey, lineNumber, qtyReceived.negate());
        }

        // 4. Update receipt header totals
        updateReceiptTotals(receiptKey);

        // 5. Create audit log
        createAuditLog("RECEIPTDETAIL", receiptKey + "-" + lineNumber, "DELETE", null, userId);

        // 6. Publish domain event
        ReceiptDetailDeletedEvent event = ReceiptDetailDeletedEvent.builder()
            .receiptKey(receiptKey)
            .lineNumber(lineNumber)
            .deletedLineDetails(lineDetails)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private void handleReceiptFinalization(String receiptKey, String userId) {
        log.info("Receipt {} finalized - triggering downstream updates", receiptKey);

        try {
            // Update receipt finalization timestamp
            jdbcTemplate.update(
                """
                UPDATE dbo.receipt
                SET closedate = CURRENT_TIMESTAMP,
                    closedby = ?
                WHERE receiptkey = ?
                AND closedate IS NULL
                """,
                userId, receiptKey
            );
        } catch (Exception e) {
            log.debug("Could not update receipt close date: {}", e.getMessage());
        }
    }

    private void updateReceiptTotals(String receiptKey) {
        try {
            jdbcTemplate.update(
                """
                UPDATE dbo.receipt
                SET totalqty = (
                    SELECT COALESCE(SUM(qtyreceived), 0)
                    FROM dbo.receiptdetail WHERE receiptkey = ?
                ),
                totallines = (
                    SELECT COUNT(*) FROM dbo.receiptdetail WHERE receiptkey = ?
                ),
                editdate = CURRENT_TIMESTAMP
                WHERE receiptkey = ?
                """,
                receiptKey, receiptKey, receiptKey
            );
        } catch (Exception e) {
            log.debug("Could not update receipt totals (columns may not exist): {}", e.getMessage());
        }
    }

    private void updatePOReceivedQuantity(String receiptKey, int lineNumber, BigDecimal quantity) {
        try {
            // Get the PO and line from the receipt detail
            jdbcTemplate.update(
                """
                UPDATE dbo.podetail pd
                SET pd.qtyreceived = COALESCE(pd.qtyreceived, 0) + ?,
                    pd.editdate = CURRENT_TIMESTAMP
                FROM dbo.receiptdetail rd
                WHERE rd.receiptkey = ?
                AND rd.receiptlinenumber = ?
                AND pd.pokey = rd.pokey
                AND pd.polinenumber = rd.polinenumber
                """,
                quantity, receiptKey, lineNumber
            );
        } catch (Exception e) {
            log.debug("Could not update PO received qty: {}", e.getMessage());
        }
    }

    private void updatePOReceivedQuantityDelta(String receiptKey, int lineNumber, BigDecimal qtyDelta) {
        if (qtyDelta == null || qtyDelta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        updatePOReceivedQuantity(receiptKey, lineNumber, qtyDelta);
    }

    private Map<String, Object> getReceiptLineDetails(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT sku, qtyreceived, qtyexpected, pokey, polinenumber, status
                FROM dbo.receiptdetail
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                receiptKey, lineNumber
            );
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BigDecimal getBigDecimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void createAuditLog(String tableName, String recordKey, String action,
                                String description, String userId) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO dbo.auditlog (tablename, recordkey, action, description, adddate, addwho)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """,
                tableName, recordKey, action, description, userId
            );
        } catch (Exception e) {
            log.debug("Could not create audit log: {}", e.getMessage());
        }
    }

    private void trackStatusTransition(String tableName, String recordKey,
                                        String fromStatus, String toStatus, String userId) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO dbo.statushistory (tablename, recordkey, fromstatus, tostatus, transitiondate, addwho)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """,
                tableName, recordKey, fromStatus, toStatus, userId
            );
        } catch (Exception e) {
            log.debug("Could not track status transition: {}", e.getMessage());
        }
    }

    private String buildChangeDescription(Map<String, Object> oldValues, Map<String, Object> newValues) {
        StringBuilder sb = new StringBuilder();
        for (String key : newValues.keySet()) {
            Object oldVal = oldValues.get(key);
            Object newVal = newValues.get(key);
            if (oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(key).append(": ").append(oldVal).append(" → ").append(newVal);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Event Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceiptCreatedEvent {
        private String receiptKey;
        private String storerKey;
        private String poKey;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceiptUpdatedEvent {
        private String receiptKey;
        private Map<String, Object> oldValues;
        private Map<String, Object> newValues;
        private String oldStatus;
        private String newStatus;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceiptDeletedEvent {
        private String receiptKey;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceiptDetailCreatedEvent {
        private String receiptKey;
        private int lineNumber;
        private String sku;
        private BigDecimal quantity;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceiptDetailUpdatedEvent {
        private String receiptKey;
        private int lineNumber;
        private Map<String, Object> oldValues;
        private Map<String, Object> newValues;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceiptDetailDeletedEvent {
        private String receiptKey;
        private int lineNumber;
        private Map<String, Object> deletedLineDetails;
        private String userId;
        private LocalDateTime timestamp;
    }
}
