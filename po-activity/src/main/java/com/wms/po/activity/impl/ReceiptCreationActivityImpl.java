package com.wms.po.activity.impl;

import com.wms.po.activity.ReceiptCreationActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementation of ReceiptCreationActivity.
 * Creates receipt header and links to POs.
 *
 * Maps to legacy SPs:
 * - SP-001-002: WM.lsp_ASN_PopulatePOs_Wrapper (receipt header, error code 68904)
 * - SP-002: WM.lsp_ASN_PopulatePODs_Wrapper (receipt detail, error code 68905)
 *
 * Error codes:
 * - RCV_005 (68904) - Receipt Header Creation Failed
 * - RCV_006 (68905) - Receipt Detail Creation Failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptCreationActivityImpl implements ReceiptCreationActivity {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    /**
     * Create receipt header and link to POs.
     *
     * Error codes:
     * - RCV_005 (68904) - Receipt Header Creation Failed
     */
    @Override
    public String createReceipt(PopulateRequest request, VariationContext context) {
        log.info("Creating receipt for {} POs, storer: {}, facility: {}",
            request.getPoKeys().size(), request.getStorerKey(), request.getFacility());

        String receiptKey = null;

        try {
            // Generate receipt key
            receiptKey = keyGeneratorService.generateKey("RECEIPT");

            // Create receipt header
            String sql = """
                INSERT INTO RECEIPT (
                    RECEIPTKEY, STORERKEY, FACILITY, STATUS, TYPE,
                    ADDDATE, ADDWHO, EDITDATE, EDITWHO
                ) VALUES (?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), ?)
                """;

            jdbcTemplate.update(sql,
                receiptKey,
                request.getStorerKey(),
                request.getFacility(),
                "0", // Initial status - Open
                "PO", // Receipt type
                request.getUserId(),
                request.getUserId()
            );

            // Link POs to receipt
            for (String poKey : request.getPoKeys()) {
                linkPOToReceipt(receiptKey, poKey, request.getUserId());
            }

            log.info("Created receipt: {}", receiptKey);
            return receiptKey;

        } catch (DataAccessException e) {
            log.error("Failed to create receipt header: {} (legacy error 68904)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to create receipt header for storer: " + request.getStorerKey(), e)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility())
                .withDetail("poKeys", request.getPoKeys());
        }
    }

    @Override
    public void compensateCreateReceipt(String receiptKey, VariationContext context) {
        log.warn("COMPENSATION: Deleting receipt: {}", receiptKey);

        try {
            // Delete receipt detail lines first
            jdbcTemplate.update("DELETE FROM RECEIPTDETAIL WHERE RECEIPTKEY = ?", receiptKey);

            // Delete PO links
            jdbcTemplate.update("DELETE FROM RECEIPTPO WHERE RECEIPTKEY = ?", receiptKey);

            // Delete receipt header
            jdbcTemplate.update("DELETE FROM RECEIPT WHERE RECEIPTKEY = ?", receiptKey);

            log.info("COMPENSATION: Successfully deleted receipt: {}", receiptKey);
        } catch (Exception e) {
            log.error("COMPENSATION: Failed to delete receipt {}: {}", receiptKey, e.getMessage());
            throw e;
        }
    }

    private void linkPOToReceipt(String receiptKey, String poKey, String userId) {
        try {
            String sql = """
                INSERT INTO RECEIPTPO (RECEIPTKEY, POKEY, ADDDATE, ADDWHO)
                VALUES (?, ?, GETDATE(), ?)
                """;
            jdbcTemplate.update(sql, receiptKey, poKey, userId);
        } catch (Exception e) {
            log.warn("Could not link PO {} to receipt {}: {}", poKey, receiptKey, e.getMessage());
        }
    }
}
