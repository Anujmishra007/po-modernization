package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryAllocationActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Implementation of InventoryAllocationActivity.
 * Allocates inventory records for receipt details.
 *
 * Error codes:
 * - XD_020 (69320) - Allocation Build Failed
 * - XD_021 (69321) - Allocation Release Failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryAllocationActivityImpl implements InventoryAllocationActivity {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Allocate inventory for receipt details.
     *
     * Error codes:
     * - XD_020 (69320) - Allocation Build Failed
     *
     * @param receiptKey Receipt key
     * @param request Populate request
     * @param context Variation context
     * @throws BusinessException if allocation fails
     */
    @Override
    public void allocateInventory(String receiptKey, PopulateRequest request, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is null/blank for inventory allocation (legacy error 69320)");
            throw new BusinessException(ErrorCode.ALLOCATION_BUILD_FAILED,
                "Receipt key is required for inventory allocation")
                .withDetail("receiptKey", "null or blank");
        }

        if (request == null) {
            log.error("Populate request is null for inventory allocation (legacy error 69320)");
            throw new BusinessException(ErrorCode.ALLOCATION_BUILD_FAILED,
                "Populate request is required for inventory allocation")
                .withDetail("receiptKey", receiptKey);
        }

        log.info("Allocating inventory for receipt: {}", receiptKey);

        try {
            // Get receipt details
            List<Map<String, Object>> details = getReceiptDetails(receiptKey);

            if (details.isEmpty()) {
                log.warn("No receipt details found for receipt: {}", receiptKey);
                return;
            }

            for (Map<String, Object> detail : details) {
                String sku = (String) detail.get("SKU");
                String lot = (String) detail.get("LOT");
                String location = (String) detail.get("LOC");

                allocateInventoryRecord(receiptKey, sku, lot, location, detail, request.getUserId());
            }

            log.info("Successfully allocated inventory for {} items", details.size());

        } catch (DataAccessException e) {
            log.error("Failed to allocate inventory for receipt {}: {} (legacy error 69320)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ALLOCATION_BUILD_FAILED,
                "Failed to allocate inventory: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey);
        }
    }

    /**
     * Compensate inventory allocation (rollback).
     *
     * Error codes:
     * - XD_021 (69321) - Allocation Release Failed
     *
     * @param receiptKey Receipt key
     * @param context Variation context
     * @throws BusinessException if compensation fails
     */
    @Override
    public void compensateAllocateInventory(String receiptKey, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for inventory deallocation, skipping");
            return;
        }

        log.warn("COMPENSATION: Deallocating inventory for receipt: {}", receiptKey);

        try {
            // Delete allocated inventory records
            String sql = "DELETE FROM LOTxLOCxID WHERE RECEIPTKEY = ?";
            int deleted = jdbcTemplate.update(sql, receiptKey);

            log.info("COMPENSATION: Deallocated {} inventory records for receipt: {}", deleted, receiptKey);

        } catch (DataAccessException e) {
            log.error("COMPENSATION: Failed to deallocate inventory for receipt {}: {} (legacy error 69321)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ALLOCATION_RELEASE_FAILED,
                "Failed to deallocate inventory: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey);
        }
    }

    private List<Map<String, Object>> getReceiptDetails(String receiptKey) {
        String sql = """
            SELECT RD.SKU, RD.LOT, RD.LOC, RD.ID, RD.QTYEXPECTED, RD.PACKKEY,
                   RD.LOTTABLE01, RD.LOTTABLE02, RD.LOTTABLE03, RD.LOTTABLE04, RD.LOTTABLE05
            FROM RECEIPTDETAIL RD
            WHERE RD.RECEIPTKEY = ?
            """;

        return jdbcTemplate.queryForList(sql, receiptKey);
    }

    private void allocateInventoryRecord(String receiptKey, String sku, String lot,
                                          String location, Map<String, Object> detail, String userId) {
        String sql = """
            INSERT INTO LOTxLOCxID (
                STORERKEY, SKU, LOT, LOC, ID, QTY, QTYALLOCATED, QTYPICKED,
                STATUS, RECEIPTKEY, ADDDATE, ADDWHO, EDITDATE, EDITWHO
            ) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 'OK', ?, GETDATE(), ?, GETDATE(), ?)
            """;

        Object qty = detail.get("QTYEXPECTED");
        String id = (String) detail.get("ID");
        String storerKey = ""; // Would come from receipt

        jdbcTemplate.update(sql,
            storerKey, sku, lot, location, id, qty,
            receiptKey, userId, userId
        );

        log.debug("Allocated inventory: SKU={}, LOT={}, LOC={}, QTY={}", sku, lot, location, qty);
    }
}
