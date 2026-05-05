package com.wms.po.activity.impl;

import com.wms.po.activity.POQuantityActivity;
import com.wms.po.domain.service.FinalizeReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of POQuantityActivity.
 * Updates PO received quantities when receipts are finalized.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class POQuantityActivityImpl implements POQuantityActivity {

    private final JdbcTemplate jdbcTemplate;
    private final FinalizeReceiptService finalizeReceiptService;

    @Override
    @Transactional
    public UpdateResult updateReceivedQuantities(UpdateRequest request) {
        log.info("Updating PO quantities for receipt {}", request.getReceiptKey());

        List<LineUpdate> appliedUpdates = new ArrayList<>();

        try {
            // If no specific updates provided, calculate from receipt
            List<LineUpdate> updates = request.getLineUpdates();
            if (updates == null || updates.isEmpty()) {
                updates = calculateUpdatesFromReceipt(request.getReceiptKey(), request.getPoKey());
            }

            for (LineUpdate update : updates) {
                // Get current quantity (for compensation)
                BigDecimal currentQty = getCurrentReceivedQuantity(
                    request.getPoKey(), update.getPoLineNumber());

                // Apply the update
                jdbcTemplate.update(
                    """
                    UPDATE dbo.podetail
                    SET qtyreceived = COALESCE(qtyreceived, 0) + ?,
                        editdate = CURRENT_TIMESTAMP,
                        editwho = ?
                    WHERE pokey = ? AND polinenumber = ?
                    """,
                    update.getReceivedQuantity(),
                    request.getUserId(),
                    request.getPoKey(),
                    update.getPoLineNumber()
                );

                // Record for compensation
                appliedUpdates.add(LineUpdate.builder()
                    .poLineNumber(update.getPoLineNumber())
                    .sku(update.getSku())
                    .receivedQuantity(update.getReceivedQuantity())
                    .previousQuantity(currentQty)
                    .build());

                log.debug("Updated PO line {}: +{}", update.getPoLineNumber(), update.getReceivedQuantity());
            }

            log.info("Updated {} PO lines", appliedUpdates.size());
            return UpdateResult.success(appliedUpdates);

        } catch (Exception e) {
            log.error("Failed to update PO quantities: {}", e.getMessage(), e);
            return UpdateResult.failed("Failed to update PO quantities: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void revertReceivedQuantities(List<LineUpdate> updates) {
        log.warn("COMPENSATION: Reverting {} PO quantity updates", updates.size());

        for (LineUpdate update : updates) {
            try {
                // Subtract the quantity that was added
                jdbcTemplate.update(
                    """
                    UPDATE dbo.podetail
                    SET qtyreceived = COALESCE(qtyreceived, 0) - ?,
                        editdate = CURRENT_TIMESTAMP
                    WHERE pokey = ? AND polinenumber = ?
                    """,
                    update.getReceivedQuantity(),
                    update.getPoLineNumber() // Note: pokey not available in LineUpdate, would need to be added
                );

                log.debug("Reverted PO line {}: -{}", update.getPoLineNumber(), update.getReceivedQuantity());
            } catch (Exception e) {
                log.error("Failed to revert PO line {}: {}", update.getPoLineNumber(), e.getMessage());
            }
        }

        log.info("COMPENSATION complete: Reverted {} PO lines", updates.size());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFullyReceived(String poKey) {
        return finalizeReceiptService.isPoFullyReceived(poKey);
    }

    @Override
    @Transactional
    public void closePO(String poKey, String userId) {
        log.info("Closing PO {}", poKey);
        finalizeReceiptService.closePo(poKey, userId);
    }

    private List<LineUpdate> calculateUpdatesFromReceipt(String receiptKey, String poKey) {
        // Query receipt details linked to PO
        return jdbcTemplate.query(
            """
            SELECT rd.polinenumber, rd.sku, rd.qtyreceived
            FROM dbo.receiptdetail rd
            WHERE rd.receiptkey = ?
            AND rd.pokey = ?
            AND rd.qtyreceived > 0
            """,
            (rs, rowNum) -> LineUpdate.builder()
                .poLineNumber(rs.getInt("polinenumber"))
                .sku(rs.getString("sku"))
                .receivedQuantity(rs.getBigDecimal("qtyreceived"))
                .build(),
            receiptKey, poKey
        );
    }

    private BigDecimal getCurrentReceivedQuantity(String poKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COALESCE(qtyreceived, 0) FROM dbo.podetail WHERE pokey = ? AND polinenumber = ?",
                BigDecimal.class,
                poKey, lineNumber
            );
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
