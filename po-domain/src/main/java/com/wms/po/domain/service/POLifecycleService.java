package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PO Lifecycle Service.
 *
 * Replaces:
 * - SP-012: WM.lsp_Pre_Delete_PO_STD (125 LOC)
 * - SP-013: WM.lsp_Pre_Delete_PODetail_STD (128 LOC)
 * - SP-014: WM.lsp_RCMConfigSP_PO_Wrapper (172 LOC)
 *
 * Handles PO lifecycle operations including:
 * - Pre-delete validation and cleanup
 * - PO voiding
 * - PO closing
 * - PO re-opening
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POLifecycleService {

    private final JdbcTemplate jdbcTemplate;

    // Status constants
    private static final String STATUS_NEW = "0";
    private static final String STATUS_IN_PROGRESS = "5";
    private static final String STATUS_CLOSED = "9";
    private static final String STATUS_VOIDED = "V";

    // ═══════════════════════════════════════════════════════════════════════
    // Pre-Delete Validation
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Validate and prepare PO for deletion.
     *
     * Replaces: WM.lsp_Pre_Delete_PO_STD
     *
     * @param poKey PO key to delete
     * @throws IllegalStateException if PO cannot be deleted
     */
    @Transactional
    public void preDeletePO(String poKey) {
        log.info("Pre-delete validation for PO: {}", poKey);

        // 1. Check PO exists
        Map<String, Object> po = getPO(poKey);
        if (po == null) {
            throw new IllegalStateException("PO not found: " + poKey);
        }

        String status = (String) po.get("status");
        String storerKey = (String) po.get("storerkey");

        // 2. Check status allows deletion
        if (STATUS_CLOSED.equals(status)) {
            throw new IllegalStateException("Cannot delete closed PO: " + poKey);
        }

        // 3. Check for linked receipts
        int receiptCount = countLinkedReceipts(poKey);
        if (receiptCount > 0) {
            throw new IllegalStateException(
                "Cannot delete PO with " + receiptCount + " linked receipts: " + poKey);
        }

        // 4. Check for received quantity
        BigDecimal receivedQty = getTotalReceivedQuantity(poKey);
        if (receivedQty != null && receivedQty.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                "Cannot delete PO with received inventory: " + poKey);
        }

        // 5. Delete PO details first
        int detailsDeleted = deletePODetails(poKey);
        log.debug("Deleted {} PO details for {}", detailsDeleted, poKey);

        // 6. Create audit record
        createAuditRecord("PO", poKey, "DELETE", storerKey, null);

        // 7. Create transmitlog for EDI
        createTransmitlogEntry(poKey, storerKey, "DELETE");

        log.info("PO {} ready for deletion", poKey);
    }

    /**
     * Validate and prepare PO detail for deletion.
     *
     * Replaces: WM.lsp_Pre_Delete_PODetail_STD
     *
     * @param poKey PO key
     * @param poLineNumber Line number to delete
     * @throws IllegalStateException if detail cannot be deleted
     */
    @Transactional
    public void preDeletePODetail(String poKey, int poLineNumber) {
        log.info("Pre-delete validation for PODetail: {}/{}", poKey, poLineNumber);

        // 1. Check PO exists and status
        Map<String, Object> po = getPO(poKey);
        if (po == null) {
            throw new IllegalStateException("PO not found: " + poKey);
        }

        String status = (String) po.get("status");
        if (STATUS_CLOSED.equals(status)) {
            throw new IllegalStateException("Cannot modify closed PO: " + poKey);
        }

        // 2. Check detail exists
        Map<String, Object> detail = getPODetail(poKey, poLineNumber);
        if (detail == null) {
            throw new IllegalStateException(
                "PO detail not found: " + poKey + "/" + poLineNumber);
        }

        // 3. Check for received quantity on this line
        BigDecimal receivedQty = (BigDecimal) detail.get("qtyreceived");
        if (receivedQty != null && receivedQty.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                "Cannot delete line with received quantity: " + poKey + "/" + poLineNumber);
        }

        // 4. Check for linked receipt details
        int linkedDetails = countLinkedReceiptDetails(poKey, poLineNumber);
        if (linkedDetails > 0) {
            throw new IllegalStateException(
                "Cannot delete line with " + linkedDetails + " linked receipts");
        }

        // 5. Update PO header totals
        updatePOTotals(poKey);

        // 6. Create audit record
        createAuditRecord("PODETAIL", poKey + "/" + poLineNumber, "DELETE",
            (String) po.get("storerkey"), null);

        log.info("PODetail {}/{} ready for deletion", poKey, poLineNumber);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PO Status Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Void a PO (soft delete).
     */
    @Transactional
    public void voidPO(String poKey, String reason, String userId) {
        log.info("Voiding PO: {} reason: {}", poKey, reason);

        Map<String, Object> po = getPO(poKey);
        if (po == null) {
            throw new IllegalStateException("PO not found: " + poKey);
        }

        String currentStatus = (String) po.get("status");
        if (STATUS_VOIDED.equals(currentStatus)) {
            log.warn("PO {} already voided", poKey);
            return;
        }

        if (STATUS_CLOSED.equals(currentStatus)) {
            throw new IllegalStateException("Cannot void closed PO: " + poKey);
        }

        // Check for received inventory
        BigDecimal receivedQty = getTotalReceivedQuantity(poKey);
        if (receivedQty != null && receivedQty.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                "Cannot void PO with received inventory. Use close instead.");
        }

        // Update status
        jdbcTemplate.update(
            "UPDATE dbo.po SET status = ?, notes = ISNULL(notes, '') + ' Voided: ' + ?, " +
            "editdate = GETDATE(), editwho = ? WHERE pokey = ?",
            STATUS_VOIDED, reason, userId, poKey
        );

        // Void all details
        jdbcTemplate.update(
            "UPDATE dbo.podetail SET status = ?, editdate = GETDATE(), editwho = ? " +
            "WHERE pokey = ?",
            STATUS_VOIDED, userId, poKey
        );

        // Audit
        createAuditRecord("PO", poKey, "VOID", (String) po.get("storerkey"),
            "Voided by " + userId + ": " + reason);

        log.info("PO {} voided", poKey);
    }

    /**
     * Close a PO (normal completion).
     */
    @Transactional
    public void closePO(String poKey, String userId) {
        log.info("Closing PO: {}", poKey);

        Map<String, Object> po = getPO(poKey);
        if (po == null) {
            throw new IllegalStateException("PO not found: " + poKey);
        }

        String currentStatus = (String) po.get("status");
        if (STATUS_CLOSED.equals(currentStatus)) {
            log.warn("PO {} already closed", poKey);
            return;
        }

        // Update status
        jdbcTemplate.update(
            "UPDATE dbo.po SET status = ?, closedate = GETDATE(), " +
            "editdate = GETDATE(), editwho = ? WHERE pokey = ?",
            STATUS_CLOSED, userId, poKey
        );

        // Close all details
        jdbcTemplate.update(
            "UPDATE dbo.podetail SET status = ?, editdate = GETDATE(), editwho = ? " +
            "WHERE pokey = ? AND status != ?",
            STATUS_CLOSED, userId, poKey, STATUS_VOIDED
        );

        // Audit
        createAuditRecord("PO", poKey, "CLOSE", (String) po.get("storerkey"),
            "Closed by " + userId);

        // Transmitlog
        createTransmitlogEntry(poKey, (String) po.get("storerkey"), "CLOSE");

        log.info("PO {} closed", poKey);
    }

    /**
     * Re-open a closed PO.
     */
    @Transactional
    public void reopenPO(String poKey, String reason, String userId) {
        log.info("Re-opening PO: {} reason: {}", poKey, reason);

        Map<String, Object> po = getPO(poKey);
        if (po == null) {
            throw new IllegalStateException("PO not found: " + poKey);
        }

        String currentStatus = (String) po.get("status");
        if (!STATUS_CLOSED.equals(currentStatus)) {
            throw new IllegalStateException("Can only re-open closed POs. Current status: " + currentStatus);
        }

        // Determine new status based on received quantity
        BigDecimal receivedQty = getTotalReceivedQuantity(poKey);
        String newStatus = (receivedQty != null && receivedQty.compareTo(BigDecimal.ZERO) > 0)
            ? STATUS_IN_PROGRESS : STATUS_NEW;

        // Update status
        jdbcTemplate.update(
            "UPDATE dbo.po SET status = ?, closedate = NULL, " +
            "notes = ISNULL(notes, '') + ' Re-opened: ' + ?, " +
            "editdate = GETDATE(), editwho = ? WHERE pokey = ?",
            newStatus, reason, userId, poKey
        );

        // Re-open details that have remaining quantity
        jdbcTemplate.update(
            "UPDATE dbo.podetail SET status = CASE WHEN qtyreceived > 0 THEN '5' ELSE '0' END, " +
            "editdate = GETDATE(), editwho = ? " +
            "WHERE pokey = ? AND status = '9'",
            userId, poKey
        );

        // Audit
        createAuditRecord("PO", poKey, "REOPEN", (String) po.get("storerkey"),
            "Re-opened by " + userId + ": " + reason);

        log.info("PO {} re-opened to status {}", poKey, newStatus);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> getPO(String poKey) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT pokey, storerkey, status, externpokey FROM dbo.po WHERE pokey = ?",
                poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> getPODetail(String poKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT pokey, polinenumber, sku, qtyordered, qtyreceived " +
                "FROM dbo.podetail WHERE pokey = ? AND polinenumber = ?",
                poKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int countLinkedReceipts(String poKey) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT r.receiptkey) FROM dbo.receipt r " +
                "JOIN dbo.receiptdetail rd ON r.receiptkey = rd.receiptkey " +
                "WHERE rd.pokey = ?",
                Integer.class,
                poKey
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int countLinkedReceiptDetails(String poKey, int lineNumber) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.receiptdetail " +
                "WHERE pokey = ? AND polinenumber = ?",
                Integer.class,
                poKey, lineNumber
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal getTotalReceivedQuantity(String poKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT SUM(qtyreceived) FROM dbo.podetail WHERE pokey = ?",
                BigDecimal.class,
                poKey
            );
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private int deletePODetails(String poKey) {
        return jdbcTemplate.update(
            "DELETE FROM dbo.podetail WHERE pokey = ?",
            poKey
        );
    }

    private void updatePOTotals(String poKey) {
        jdbcTemplate.update(
            "UPDATE dbo.po SET " +
            "totalqtyordered = (SELECT SUM(qtyordered) FROM dbo.podetail WHERE pokey = ?), " +
            "totalqtyreceived = (SELECT SUM(qtyreceived) FROM dbo.podetail WHERE pokey = ?), " +
            "editdate = GETDATE() WHERE pokey = ?",
            poKey, poKey, poKey
        );
    }

    private void createAuditRecord(String tableName, String keyValue, String operation,
                                    String storerKey, String details) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.auditlog (tablename, keyvalue, operation, " +
                "storerkey, newvalue, adddate, addwho) " +
                "VALUES (?, ?, ?, ?, ?, GETDATE(), 'SYSTEM')",
                tableName, keyValue, operation, storerKey, details
            );
        } catch (Exception e) {
            log.debug("Could not create audit record: {}", e.getMessage());
        }
    }

    private void createTransmitlogEntry(String poKey, String storerKey, String action) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.transmitlog (tablename, keyvalue, storerkey, " +
                "transmitflag, transmittype, adddate) " +
                "VALUES ('PO', ?, ?, '0', ?, GETDATE())",
                poKey, storerKey, action
            );
        } catch (Exception e) {
            log.debug("Could not create transmitlog: {}", e.getMessage());
        }
    }

    /**
     * Get PO lifecycle history.
     */
    public List<Map<String, Object>> getPOHistory(String poKey) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT operation, newvalue, adddate, addwho " +
                "FROM dbo.auditlog WHERE tablename = 'PO' AND keyvalue = ? " +
                "ORDER BY adddate DESC",
                poKey
            );
        } catch (Exception e) {
            return List.of();
        }
    }
}
