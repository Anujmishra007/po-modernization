package com.wms.po.activity.impl;

import com.wms.po.activity.ReceiptCreationActivity;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementation of ReceiptCreationActivity
 * Creates receipt header and links to POs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptCreationActivityImpl implements ReceiptCreationActivity {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    @Override
    public String createReceipt(PopulateRequest request, VariationContext context) {
        log.info("Creating receipt for {} POs, storer: {}, facility: {}",
            request.getPoKeys().size(), request.getStorerKey(), request.getFacility());

        // Generate receipt key
        String receiptKey = keyGeneratorService.generateKey("RECEIPT");

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
