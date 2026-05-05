package com.wms.po.infrastructure.transaction;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import lombok.Data;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes compensation actions for saga rollback.
 * Maps to legacy SP rollback patterns.
 *
 * Legacy Rollback Pattern:
 * ───────────────────────────────
 * -- On error, rollback in reverse order:
 * DELETE FROM RECEIPTDETAIL WHERE RECEIPTKEY = @RECEIPTKEY
 * DELETE FROM RECEIPT WHERE RECEIPTKEY = @RECEIPTKEY
 * UPDATE ORDERS SET STATUS = @ORIGINAL_STATUS WHERE POKEY = @POKEY
 *
 * Modern Compensation Pattern:
 * ───────────────────────────────
 * Step 5 compensation: releaseInventory(reservationIds)
 * Step 4 compensation: deleteReceiptDetails(detailKeys)
 * Step 3 compensation: deleteReceiptHeader(receiptKey)
 * Step 2 compensation: restorePOStatus(poKey, originalStatus)
 *
 * Error codes:
 * - INT_024 (69024) - Transaction Rollback
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationExecutor {

    private final JdbcTemplate jdbcTemplate;

    // Track compensations for audit/recovery
    private final Map<String, CompensationRecord> compensationHistory = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // RECEIPT COMPENSATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compensate receipt header creation (maps to dsp_Receipt equivalent).
     *
     * Error codes:
     * - INT_024 (69024) - Transaction Rollback
     *
     * @param receiptKey Receipt key to delete
     * @param sagaId Saga identifier for tracking
     * @return Compensation result
     */
    public CompensationResult compensateReceiptHeader(String receiptKey, String sagaId) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for compensation, skipping");
            return CompensationResult.failure("null", "Receipt key is null or blank");
        }

        log.info("Compensating receipt header: {} (saga: {})", receiptKey, sagaId);

        CompensationRecord record = startCompensation(sagaId, "DELETE_RECEIPT_HEADER", receiptKey);

        try {
            // First delete any linked records
            int poLinksDeleted = jdbcTemplate.update(
                    "DELETE FROM RECEIPTPO WHERE RECEIPTKEY = ?", receiptKey);

            // Then delete the header
            int rowsDeleted = jdbcTemplate.update(
                    "DELETE FROM RECEIPT WHERE RECEIPTKEY = ?", receiptKey);

            record.setSuccess(true);
            record.setRowsAffected(rowsDeleted);
            record.setDetails(Map.of(
                    "receiptDeleted", rowsDeleted,
                    "poLinksDeleted", poLinksDeleted
            ));

            log.info("Compensated receipt header: {} (deleted {} rows)", receiptKey, rowsDeleted);
            return CompensationResult.success(receiptKey, rowsDeleted);

        } catch (DataAccessException e) {
            log.error("Failed to compensate receipt header: receiptKey={}, error={} (legacy error 69024)",
                receiptKey, e.getMessage(), e);
            record.setSuccess(false);
            record.setError(e.getMessage());
            return CompensationResult.failure(receiptKey, e.getMessage());
        } finally {
            completeCompensation(record);
        }
    }

    /**
     * Compensate receipt details creation (maps to dsp_ReceiptDetail equivalent)
     */
    public CompensationResult compensateReceiptDetails(List<String> detailKeys, String sagaId) {
        log.info("Compensating {} receipt details (saga: {})", detailKeys.size(), sagaId);

        CompensationRecord record = startCompensation(sagaId, "DELETE_RECEIPT_DETAILS",
                String.join(",", detailKeys));

        try {
            int totalDeleted = 0;
            for (String detailKey : detailKeys) {
                int deleted = jdbcTemplate.update(
                        "DELETE FROM RECEIPTDETAIL WHERE RECEIPTDETAILKEY = ?", detailKey);
                totalDeleted += deleted;
            }

            record.setSuccess(true);
            record.setRowsAffected(totalDeleted);

            log.info("Compensated {} receipt details", totalDeleted);
            return CompensationResult.success("BATCH", totalDeleted);

        } catch (Exception e) {
            log.error("Failed to compensate receipt details: {}", e.getMessage());
            record.setSuccess(false);
            record.setError(e.getMessage());
            return CompensationResult.failure("BATCH", e.getMessage());
        } finally {
            completeCompensation(record);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INVENTORY COMPENSATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compensate inventory allocation (maps to usp_ReleaseInventory equivalent)
     */
    public CompensationResult compensateInventoryAllocation(List<String> reservationIds, String sagaId) {
        log.info("Compensating {} inventory reservations (saga: {})", reservationIds.size(), sagaId);

        CompensationRecord record = startCompensation(sagaId, "RELEASE_INVENTORY",
                String.join(",", reservationIds));

        try {
            int totalReleased = 0;
            for (String reservationId : reservationIds) {
                // Mark reservation as released
                int updated = jdbcTemplate.update(
                        "UPDATE INVENTORYRESERVATION SET STATUS = 'RELEASED', EDITDATE = CURRENT_TIMESTAMP " +
                                "WHERE RESERVATIONID = ?", reservationId);
                totalReleased += updated;
            }

            record.setSuccess(true);
            record.setRowsAffected(totalReleased);

            log.info("Released {} inventory reservations", totalReleased);
            return CompensationResult.success("BATCH", totalReleased);

        } catch (Exception e) {
            log.error("Failed to compensate inventory: {}", e.getMessage());
            record.setSuccess(false);
            record.setError(e.getMessage());
            return CompensationResult.failure("BATCH", e.getMessage());
        } finally {
            completeCompensation(record);
        }
    }

    /**
     * Compensate LOTxLOCxID creation (maps to dsp_LOTxLOCxID equivalent)
     */
    public CompensationResult compensateLotLocId(String lotLocIdKey, String sagaId) {
        log.info("Compensating LOTxLOCxID: {} (saga: {})", lotLocIdKey, sagaId);

        CompensationRecord record = startCompensation(sagaId, "DELETE_LOTXLOCXID", lotLocIdKey);

        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM LOTxLOCxID WHERE LOTXLOCXIDKEY = ?", lotLocIdKey);

            record.setSuccess(true);
            record.setRowsAffected(deleted);

            return CompensationResult.success(lotLocIdKey, deleted);

        } catch (Exception e) {
            log.error("Failed to compensate LOTxLOCxID: {}", e.getMessage());
            record.setSuccess(false);
            record.setError(e.getMessage());
            return CompensationResult.failure(lotLocIdKey, e.getMessage());
        } finally {
            completeCompensation(record);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PO STATUS COMPENSATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compensate PO status update (restore original status)
     */
    public CompensationResult compensatePOStatus(String poKey, String originalStatus, String sagaId) {
        log.info("Compensating PO status: {} -> {} (saga: {})", poKey, originalStatus, sagaId);

        CompensationRecord record = startCompensation(sagaId, "RESTORE_PO_STATUS", poKey);

        try {
            int updated = jdbcTemplate.update(
                    "UPDATE ORDERS SET STATUS = ?, EDITDATE = CURRENT_TIMESTAMP WHERE POKEY = ?",
                    originalStatus, poKey);

            record.setSuccess(true);
            record.setRowsAffected(updated);
            record.setDetails(Map.of("restoredStatus", originalStatus));

            log.info("Restored PO {} status to {}", poKey, originalStatus);
            return CompensationResult.success(poKey, updated);

        } catch (Exception e) {
            log.error("Failed to compensate PO status: {}", e.getMessage());
            record.setSuccess(false);
            record.setError(e.getMessage());
            return CompensationResult.failure(poKey, e.getMessage());
        } finally {
            completeCompensation(record);
        }
    }

    /**
     * Compensate receipt status update
     */
    public CompensationResult compensateReceiptStatus(String receiptKey, String originalStatus, String sagaId) {
        log.info("Compensating receipt status: {} -> {} (saga: {})", receiptKey, originalStatus, sagaId);

        CompensationRecord record = startCompensation(sagaId, "RESTORE_RECEIPT_STATUS", receiptKey);

        try {
            int updated = jdbcTemplate.update(
                    "UPDATE RECEIPT SET STATUS = ?, EDITDATE = CURRENT_TIMESTAMP WHERE RECEIPTKEY = ?",
                    originalStatus, receiptKey);

            record.setSuccess(true);
            record.setRowsAffected(updated);

            return CompensationResult.success(receiptKey, updated);

        } catch (Exception e) {
            log.error("Failed to compensate receipt status: {}", e.getMessage());
            record.setSuccess(false);
            record.setError(e.getMessage());
            return CompensationResult.failure(receiptKey, e.getMessage());
        } finally {
            completeCompensation(record);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPENSATION TRACKING
    // ═══════════════════════════════════════════════════════════════

    private CompensationRecord startCompensation(String sagaId, String type, String targetId) {
        CompensationRecord record = CompensationRecord.builder()
                .compensationId(UUID.randomUUID().toString())
                .sagaId(sagaId)
                .type(type)
                .targetId(targetId)
                .startTime(System.currentTimeMillis())
                .build();
        compensationHistory.put(record.getCompensationId(), record);
        return record;
    }

    private void completeCompensation(CompensationRecord record) {
        record.setEndTime(System.currentTimeMillis());
        record.setDuration(record.getEndTime() - record.getStartTime());
    }

    /**
     * Get compensation history for a saga
     */
    public List<CompensationRecord> getCompensationHistory(String sagaId) {
        return compensationHistory.values().stream()
                .filter(r -> sagaId.equals(r.getSagaId()))
                .sorted(Comparator.comparing(CompensationRecord::getStartTime))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════

    @Data
    @Builder
    public static class CompensationRecord {
        private String compensationId;
        private String sagaId;
        private String type;
        private String targetId;
        private long startTime;
        private long endTime;
        private long duration;
        private boolean success;
        private int rowsAffected;
        private String error;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    public static class CompensationResult {
        private String targetId;
        private boolean success;
        private int rowsAffected;
        private String error;

        public static CompensationResult success(String targetId, int rowsAffected) {
            return CompensationResult.builder()
                    .targetId(targetId)
                    .success(true)
                    .rowsAffected(rowsAffected)
                    .build();
        }

        public static CompensationResult failure(String targetId, String error) {
            return CompensationResult.builder()
                    .targetId(targetId)
                    .success(false)
                    .error(error)
                    .build();
        }
    }
}
