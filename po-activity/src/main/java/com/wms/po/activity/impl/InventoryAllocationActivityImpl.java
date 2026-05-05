package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryAllocationActivity;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Implementation of InventoryAllocationActivity
 * Allocates inventory records for receipt details
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryAllocationActivityImpl implements InventoryAllocationActivity {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void allocateInventory(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Allocating inventory for receipt: {}", receiptKey);

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

            try {
                allocateInventoryRecord(receiptKey, sku, lot, location, detail, request.getUserId());
            } catch (Exception e) {
                log.error("Failed to allocate inventory for SKU {}: {}", sku, e.getMessage());
                throw e;
            }
        }

        log.info("Successfully allocated inventory for {} items", details.size());
    }

    @Override
    public void compensateAllocateInventory(String receiptKey, VariationContext context) {
        log.warn("COMPENSATION: Deallocating inventory for receipt: {}", receiptKey);

        try {
            // Delete allocated inventory records
            String sql = "DELETE FROM LOTxLOCxID WHERE RECEIPTKEY = ?";
            int deleted = jdbcTemplate.update(sql, receiptKey);

            log.info("COMPENSATION: Deallocated {} inventory records for receipt: {}", deleted, receiptKey);
        } catch (Exception e) {
            log.error("COMPENSATION: Failed to deallocate inventory for receipt {}: {}",
                receiptKey, e.getMessage());
            throw e;
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
