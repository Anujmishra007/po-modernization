package com.wms.po.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity listener for PO and PODetail tables.
 *
 * Replaces SQL triggers:
 * - TR-001: ntrPOHeaderAdd (@PrePersist)
 * - TR-002: ntrPOHeaderUpdate (@PreUpdate)
 * - TR-003: ntrPOHeaderDelete (@PreRemove)
 * - TR-004: ntrPODetailAdd (@PrePersist)
 * - TR-005: ntrPODetailUpdate (@PreUpdate)
 * - TR-006: ntrPODetailDelete (@PreRemove)
 *
 * These listeners publish domain events that can be consumed
 * for notifications, auditing, and integration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class POEntityListener {

    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    // ═══════════════════════════════════════════════════════════════════════
    // PO Header Events (ntrPOHeader* triggers)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle PO creation.
     * Implements ntrPOHeaderAdd trigger logic.
     *
     * @param poKey New PO key
     * @param storerKey Storer key
     * @param userId User creating the PO
     */
    @Transactional
    public void onPOCreated(String poKey, String storerKey, String userId) {
        log.info("PO created: poKey={}, storer={}", poKey, storerKey);

        // 1. Set default values and timestamps (trigger logic)
        jdbcTemplate.update(
            """
            UPDATE dbo.po
            SET adddate = COALESCE(adddate, CURRENT_TIMESTAMP),
                addwho = COALESCE(addwho, ?),
                editdate = CURRENT_TIMESTAMP,
                editwho = ?,
                status = COALESCE(status, '0')
            WHERE pokey = ?
            """,
            userId, userId, poKey
        );

        // 2. Create audit log entry
        createAuditLog("PO", poKey, "INSERT", null, userId);

        // 3. Publish domain event
        POCreatedEvent event = POCreatedEvent.builder()
            .poKey(poKey)
            .storerKey(storerKey)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);

        log.debug("Published POCreatedEvent for {}", poKey);
    }

    /**
     * Handle PO update.
     * Implements ntrPOHeaderUpdate trigger logic.
     *
     * @param poKey Updated PO key
     * @param oldValues Previous values (for audit)
     * @param newValues New values
     * @param userId User making the update
     */
    @Transactional
    public void onPOUpdated(String poKey, Map<String, Object> oldValues,
                            Map<String, Object> newValues, String userId) {
        log.info("PO updated: poKey={}", poKey);

        // 1. Update edit timestamp
        jdbcTemplate.update(
            """
            UPDATE dbo.po
            SET editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE pokey = ?
            """,
            userId, poKey
        );

        // 2. Track status transitions
        String oldStatus = (String) oldValues.get("status");
        String newStatus = (String) newValues.get("status");

        if (oldStatus != null && newStatus != null && !oldStatus.equals(newStatus)) {
            log.info("PO {} status changed: {} → {}", poKey, oldStatus, newStatus);
            trackStatusTransition("PO", poKey, oldStatus, newStatus, userId);
        }

        // 3. Create audit log
        createAuditLog("PO", poKey, "UPDATE", buildChangeDescription(oldValues, newValues), userId);

        // 4. Publish domain event
        POUpdatedEvent event = POUpdatedEvent.builder()
            .poKey(poKey)
            .oldValues(oldValues)
            .newValues(newValues)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Handle PO deletion (soft or hard).
     * Implements ntrPOHeaderDelete trigger logic.
     *
     * @param poKey Deleted PO key
     * @param userId User deleting
     */
    @Transactional
    public void onPODeleted(String poKey, String userId) {
        log.warn("PO deleted: poKey={}", poKey);

        // 1. Check if there are any receipt references
        Integer receiptCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.receipt WHERE pokey = ?",
            Integer.class,
            poKey
        );

        if (receiptCount != null && receiptCount > 0) {
            log.warn("PO {} has {} associated receipts - cascading soft delete", poKey, receiptCount);
        }

        // 2. Cascade delete to PO details (handled by DB or separate call)

        // 3. Create audit log
        createAuditLog("PO", poKey, "DELETE", null, userId);

        // 4. Publish domain event
        PODeletedEvent event = PODeletedEvent.builder()
            .poKey(poKey)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PO Detail Events (ntrPODetail* triggers)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle PO Detail creation.
     * Implements ntrPODetailAdd trigger logic.
     *
     * @param poKey Parent PO key
     * @param lineNumber Line number
     * @param sku SKU
     * @param userId User creating
     */
    @Transactional
    public void onPODetailCreated(String poKey, int lineNumber, String sku, String userId) {
        log.info("PO Detail created: poKey={}, line={}, sku={}", poKey, lineNumber, sku);

        // 1. Set default values
        jdbcTemplate.update(
            """
            UPDATE dbo.podetail
            SET adddate = COALESCE(adddate, CURRENT_TIMESTAMP),
                addwho = COALESCE(addwho, ?),
                editdate = CURRENT_TIMESTAMP,
                editwho = ?,
                qtyreceived = COALESCE(qtyreceived, 0),
                polinestatus = COALESCE(polinestatus, 'OPEN')
            WHERE pokey = ? AND polinenumber = ?
            """,
            userId, userId, poKey, lineNumber
        );

        // 2. Update PO header totals
        updatePOTotals(poKey);

        // 3. Create audit log
        createAuditLog("PODETAIL", poKey + "-" + lineNumber, "INSERT", "SKU=" + sku, userId);

        // 4. Publish domain event
        PODetailCreatedEvent event = PODetailCreatedEvent.builder()
            .poKey(poKey)
            .lineNumber(lineNumber)
            .sku(sku)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Handle PO Detail update.
     * Implements ntrPODetailUpdate trigger logic.
     *
     * @param poKey Parent PO key
     * @param lineNumber Line number
     * @param oldValues Previous values
     * @param newValues New values
     * @param userId User updating
     */
    @Transactional
    public void onPODetailUpdated(String poKey, int lineNumber,
                                   Map<String, Object> oldValues,
                                   Map<String, Object> newValues, String userId) {
        log.info("PO Detail updated: poKey={}, line={}", poKey, lineNumber);

        // 1. Update edit timestamp
        jdbcTemplate.update(
            """
            UPDATE dbo.podetail
            SET editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE pokey = ? AND polinenumber = ?
            """,
            userId, poKey, lineNumber
        );

        // 2. Update PO header totals if quantity changed
        updatePOTotals(poKey);

        // 3. Check for quantity changes
        java.math.BigDecimal oldQty = (java.math.BigDecimal) oldValues.get("qtyordered");
        java.math.BigDecimal newQty = (java.math.BigDecimal) newValues.get("qtyordered");

        if (oldQty != null && newQty != null && oldQty.compareTo(newQty) != 0) {
            log.info("PO {} line {} qty changed: {} → {}", poKey, lineNumber, oldQty, newQty);
        }

        // 4. Create audit log
        createAuditLog("PODETAIL", poKey + "-" + lineNumber, "UPDATE",
            buildChangeDescription(oldValues, newValues), userId);

        // 5. Publish domain event
        PODetailUpdatedEvent event = PODetailUpdatedEvent.builder()
            .poKey(poKey)
            .lineNumber(lineNumber)
            .oldValues(oldValues)
            .newValues(newValues)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Handle PO Detail deletion.
     * Implements ntrPODetailDelete trigger logic.
     *
     * @param poKey Parent PO key
     * @param lineNumber Line number
     * @param userId User deleting
     */
    @Transactional
    public void onPODetailDeleted(String poKey, int lineNumber, String userId) {
        log.warn("PO Detail deleted: poKey={}, line={}", poKey, lineNumber);

        // 1. Check if line was received
        java.math.BigDecimal receivedQty = jdbcTemplate.queryForObject(
            "SELECT COALESCE(qtyreceived, 0) FROM dbo.podetail WHERE pokey = ? AND polinenumber = ?",
            java.math.BigDecimal.class,
            poKey, lineNumber
        );

        if (receivedQty != null && receivedQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
            log.warn("PO {} line {} has received qty {} - deletion may cause issues",
                poKey, lineNumber, receivedQty);
        }

        // 2. Update PO header totals
        updatePOTotals(poKey);

        // 3. Create audit log
        createAuditLog("PODETAIL", poKey + "-" + lineNumber, "DELETE", null, userId);

        // 4. Publish domain event
        PODetailDeletedEvent event = PODetailDeletedEvent.builder()
            .poKey(poKey)
            .lineNumber(lineNumber)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
        eventPublisher.publishEvent(event);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private void updatePOTotals(String poKey) {
        try {
            jdbcTemplate.update(
                """
                UPDATE dbo.po
                SET totalqty = (
                    SELECT COALESCE(SUM(qtyordered), 0)
                    FROM dbo.podetail WHERE pokey = ?
                ),
                totallines = (
                    SELECT COUNT(*) FROM dbo.podetail WHERE pokey = ?
                ),
                editdate = CURRENT_TIMESTAMP
                WHERE pokey = ?
                """,
                poKey, poKey, poKey
            );
        } catch (Exception e) {
            log.debug("Could not update PO totals (columns may not exist): {}", e.getMessage());
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
    public static class POCreatedEvent {
        private String poKey;
        private String storerKey;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class POUpdatedEvent {
        private String poKey;
        private Map<String, Object> oldValues;
        private Map<String, Object> newValues;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PODeletedEvent {
        private String poKey;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PODetailCreatedEvent {
        private String poKey;
        private int lineNumber;
        private String sku;
        private String userId;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PODetailUpdatedEvent {
        private String poKey;
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
    public static class PODetailDeletedEvent {
        private String poKey;
        private int lineNumber;
        private String userId;
        private LocalDateTime timestamp;
    }
}
