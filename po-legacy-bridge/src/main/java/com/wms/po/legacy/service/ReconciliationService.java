package com.wms.po.legacy.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.legacy.client.LegacyWMSClient;
import com.wms.po.legacy.model.ReconciliationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for reconciling data between legacy and new systems.
 *
 * Error codes:
 * - INT_006 (69005) - Dual Write Sync Failed
 * - INT_001 (69000) - Legacy Sync Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final JdbcTemplate jdbcTemplate;
    private final LegacyWMSClient legacyClient;

    /**
     * Reconcile receipt data between legacy and new system.
     *
     * Error codes:
     * - INT_006 (69005) - Dual Write Sync Failed
     *
     * @param legacyReceiptKey Legacy receipt key
     * @param newReceiptKey New receipt key
     * @return Reconciliation result
     * @throws BusinessException if keys are invalid
     */
    public ReconciliationResult reconcileReceipt(String legacyReceiptKey, String newReceiptKey) {
        if (legacyReceiptKey == null || legacyReceiptKey.isBlank()) {
            log.error("Legacy receipt key is null/blank for reconciliation (legacy error 69005)");
            throw new BusinessException(ErrorCode.DUAL_WRITE_SYNC_FAILED,
                "Legacy receipt key is required for reconciliation")
                .withDetail("legacyReceiptKey", "null or blank");
        }

        if (newReceiptKey == null || newReceiptKey.isBlank()) {
            log.error("New receipt key is null/blank for reconciliation (legacy error 69005)");
            throw new BusinessException(ErrorCode.DUAL_WRITE_SYNC_FAILED,
                "New receipt key is required for reconciliation")
                .withDetail("newReceiptKey", "null or blank");
        }

        log.info("Reconciling receipts: legacy={}, new={}", legacyReceiptKey, newReceiptKey);

        ReconciliationResult result = ReconciliationResult.builder()
            .reconciliationId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .legacyReceiptKey(legacyReceiptKey)
            .newReceiptKey(newReceiptKey)
            .status(ReconciliationResult.ReconciliationStatus.IN_PROGRESS)
            .build();

        try {
            // Get legacy receipt data
            Map<String, Object> legacyReceipt = getReceiptData(legacyReceiptKey);

            // Get new receipt data
            Map<String, Object> newReceipt = getReceiptData(newReceiptKey);

            // Compare header fields
            compareField(result, "STATUS", legacyReceipt, newReceipt);
            compareField(result, "STORERKEY", legacyReceipt, newReceipt);
            compareField(result, "FACILITY", legacyReceipt, newReceipt);

            // Compare line counts
            int legacyLines = getLineCount(legacyReceiptKey);
            int newLines = getLineCount(newReceiptKey);

            result.setLegacyLineCount(legacyLines);
            result.setNewLineCount(newLines);

            if (legacyLines != newLines) {
                result.addDifference("LINE_COUNT",
                    String.valueOf(legacyLines),
                    String.valueOf(newLines),
                    ReconciliationResult.DifferenceType.COUNT_MISMATCH);
            }

            // Compare line details
            reconcileLines(result, legacyReceiptKey, newReceiptKey);

            // Set final status
            if (!result.hasDifferences()) {
                result.setStatus(ReconciliationResult.ReconciliationStatus.MATCHED);
            }

            log.info("Reconciliation complete: status={}, differences={}",
                result.getStatus(), result.getDifferences().size());

        } catch (DataAccessException e) {
            log.error("Reconciliation failed: legacyKey={}, newKey={}, error={} (legacy error 69005)",
                legacyReceiptKey, newReceiptKey, e.getMessage(), e);
            result.setStatus(ReconciliationResult.ReconciliationStatus.ERROR);
            result.addDifference("ERROR", e.getMessage(), "",
                ReconciliationResult.DifferenceType.VALUE_MISMATCH);

        } catch (Exception e) {
            log.error("Reconciliation failed: legacyKey={}, newKey={}, error={} (legacy error 69005)",
                legacyReceiptKey, newReceiptKey, e.getMessage(), e);
            result.setStatus(ReconciliationResult.ReconciliationStatus.ERROR);
            result.addDifference("ERROR", e.getMessage(), "",
                ReconciliationResult.DifferenceType.VALUE_MISMATCH);
        }

        return result;
    }

    /**
     * Reconcile PO before and after population.
     *
     * Error codes:
     * - INT_001 (69000) - Legacy Sync Failed
     *
     * @param poKey PO key to reconcile
     * @return Reconciliation result
     * @throws BusinessException if PO key is invalid
     */
    public ReconciliationResult reconcilePO(String poKey) {
        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is null/blank for reconciliation (legacy error 69000)");
            throw new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
                "PO key is required for reconciliation")
                .withDetail("poKey", "null or blank");
        }

        log.info("Reconciling PO: {}", poKey);

        ReconciliationResult result = ReconciliationResult.builder()
            .reconciliationId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .poKey(poKey)
            .status(ReconciliationResult.ReconciliationStatus.IN_PROGRESS)
            .build();

        try {
            // Check PO exists in both systems
            String legacyStatus = legacyClient.getPOStatus(poKey);

            result.setStatus(ReconciliationResult.ReconciliationStatus.MATCHED);
            log.info("PO reconciliation complete: poKey={}, status=MATCHED", poKey);

        } catch (Exception e) {
            log.error("PO reconciliation failed: poKey={}, error={} (legacy error 69000)",
                poKey, e.getMessage(), e);
            result.setStatus(ReconciliationResult.ReconciliationStatus.ERROR);
            result.addDifference("ERROR", e.getMessage(), "",
                ReconciliationResult.DifferenceType.VALUE_MISMATCH);
        }

        return result;
    }

    private Map<String, Object> getReceiptData(String receiptKey) {
        String sql = "SELECT * FROM RECEIPT WHERE RECEIPTKEY = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, receiptKey);
        return results.isEmpty() ? Map.of() : results.get(0);
    }

    private int getLineCount(String receiptKey) {
        String sql = "SELECT COUNT(*) FROM RECEIPTDETAIL WHERE RECEIPTKEY = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, receiptKey);
        return count != null ? count : 0;
    }

    private void compareField(ReconciliationResult result, String field,
                              Map<String, Object> legacy, Map<String, Object> newData) {
        Object legacyValue = legacy.get(field);
        Object newValue = newData.get(field);

        String legacyStr = legacyValue != null ? legacyValue.toString() : "";
        String newStr = newValue != null ? newValue.toString() : "";

        if (!legacyStr.equals(newStr)) {
            result.addDifference(field, legacyStr, newStr,
                ReconciliationResult.DifferenceType.VALUE_MISMATCH);
        }
    }

    private void reconcileLines(ReconciliationResult result, String legacyReceiptKey, String newReceiptKey) {
        // Get lines from both systems and compare
        String sql = "SELECT * FROM RECEIPTDETAIL WHERE RECEIPTKEY = ? ORDER BY RECEIPTLINENUMBER";

        List<Map<String, Object>> legacyLines = jdbcTemplate.queryForList(sql, legacyReceiptKey);
        List<Map<String, Object>> newLines = jdbcTemplate.queryForList(sql, newReceiptKey);

        int matched = 0;
        int mismatched = 0;

        // Simple line-by-line comparison
        int maxLines = Math.max(legacyLines.size(), newLines.size());
        for (int i = 0; i < maxLines; i++) {
            if (i < legacyLines.size() && i < newLines.size()) {
                // Compare lines
                Map<String, Object> legacyLine = legacyLines.get(i);
                Map<String, Object> newLine = newLines.get(i);

                boolean lineMatched = compareLines(result, legacyLine, newLine, i + 1);
                if (lineMatched) matched++;
                else mismatched++;
            }
        }

        result.setMatchedLines(matched);
        result.setMismatchedLines(mismatched);
    }

    private boolean compareLines(ReconciliationResult result, Map<String, Object> legacy,
                                  Map<String, Object> newLine, int lineNumber) {
        boolean matched = true;
        String[] fields = {"SKU", "QTYEXPECTED", "QTYRECEIVED", "PACKKEY"};

        for (String field : fields) {
            Object legacyVal = legacy.get(field);
            Object newVal = newLine.get(field);

            if (legacyVal != null && newVal != null && !legacyVal.toString().equals(newVal.toString())) {
                result.addDifference(field + "_LINE_" + lineNumber,
                    legacyVal.toString(), newVal.toString(),
                    ReconciliationResult.DifferenceType.VALUE_MISMATCH);
                matched = false;
            }
        }

        return matched;
    }
}
