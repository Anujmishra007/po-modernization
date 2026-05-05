package com.wms.po.service;

import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import com.wms.po.dto.ReceiptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Receipt service for managing receipts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    /**
     * Get receipt by key
     */
    public ReceiptResponse getReceipt(String receiptKey) {
        log.debug("Getting receipt: {}", receiptKey);

        String sql = """
            SELECT RECEIPTKEY, STORERKEY, FACILITY, EXTERNRECEIPTKEY,
                   STATUS, TYPE, CARRIERKEY, TRAILERNUMBER,
                   RECEIPTDATE, ADDDATE, ADDWHO
            FROM RECEIPT WHERE RECEIPTKEY = ?
            """;

        List<ReceiptResponse> results = jdbcTemplate.query(sql, (rs, rowNum) ->
            ReceiptResponse.builder()
                .receiptKey(rs.getString("RECEIPTKEY"))
                .storerKey(rs.getString("STORERKEY"))
                .facility(rs.getString("FACILITY"))
                .externReceiptKey(rs.getString("EXTERNRECEIPTKEY"))
                .status(rs.getString("STATUS"))
                .type(rs.getString("TYPE"))
                .carrierKey(rs.getString("CARRIERKEY"))
                .trailerNumber(rs.getString("TRAILERNUMBER"))
                .receiptDate(rs.getTimestamp("RECEIPTDATE") != null ?
                    rs.getTimestamp("RECEIPTDATE").toLocalDateTime() : null)
                .addDate(rs.getTimestamp("ADDDATE") != null ?
                    rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                .addWho(rs.getString("ADDWHO"))
                .build(),
            receiptKey);

        if (results.isEmpty()) {
            throw new RuntimeException("Receipt not found: " + receiptKey);
        }
        return results.get(0);
    }

    /**
     * Get receipts by storer and facility
     */
    public List<ReceiptResponse> getReceiptsByStorerAndFacility(String storerKey, String facility) {
        log.debug("Getting receipts for storer: {}, facility: {}", storerKey, facility);

        String sql = """
            SELECT RECEIPTKEY, STORERKEY, FACILITY, EXTERNRECEIPTKEY,
                   STATUS, TYPE, CARRIERKEY, TRAILERNUMBER,
                   RECEIPTDATE, ADDDATE, ADDWHO
            FROM RECEIPT WHERE STORERKEY = ? AND FACILITY = ?
            ORDER BY ADDDATE DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
            ReceiptResponse.builder()
                .receiptKey(rs.getString("RECEIPTKEY"))
                .storerKey(rs.getString("STORERKEY"))
                .facility(rs.getString("FACILITY"))
                .externReceiptKey(rs.getString("EXTERNRECEIPTKEY"))
                .status(rs.getString("STATUS"))
                .type(rs.getString("TYPE"))
                .carrierKey(rs.getString("CARRIERKEY"))
                .trailerNumber(rs.getString("TRAILERNUMBER"))
                .receiptDate(rs.getTimestamp("RECEIPTDATE") != null ?
                    rs.getTimestamp("RECEIPTDATE").toLocalDateTime() : null)
                .addDate(rs.getTimestamp("ADDDATE") != null ?
                    rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                .addWho(rs.getString("ADDWHO"))
                .build(),
            storerKey, facility);
    }

    /**
     * Get receipts by PO key
     */
    public List<ReceiptResponse> getReceiptsByPO(String poKey) {
        log.debug("Getting receipts for PO: {}", poKey);

        String sql = """
            SELECT r.RECEIPTKEY, r.STORERKEY, r.FACILITY, r.EXTERNRECEIPTKEY,
                   r.STATUS, r.TYPE, r.CARRIERKEY, r.TRAILERNUMBER,
                   r.RECEIPTDATE, r.ADDDATE, r.ADDWHO
            FROM RECEIPT r
            INNER JOIN RECEIPTPO rp ON r.RECEIPTKEY = rp.RECEIPTKEY
            WHERE rp.POKEY = ?
            ORDER BY r.ADDDATE DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
            ReceiptResponse.builder()
                .receiptKey(rs.getString("RECEIPTKEY"))
                .storerKey(rs.getString("STORERKEY"))
                .facility(rs.getString("FACILITY"))
                .externReceiptKey(rs.getString("EXTERNRECEIPTKEY"))
                .status(rs.getString("STATUS"))
                .type(rs.getString("TYPE"))
                .carrierKey(rs.getString("CARRIERKEY"))
                .trailerNumber(rs.getString("TRAILERNUMBER"))
                .receiptDate(rs.getTimestamp("RECEIPTDATE") != null ?
                    rs.getTimestamp("RECEIPTDATE").toLocalDateTime() : null)
                .addDate(rs.getTimestamp("ADDDATE") != null ?
                    rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                .addWho(rs.getString("ADDWHO"))
                .build(),
            poKey);
    }

    /**
     * Create a new receipt
     */
    @Transactional
    public String createReceipt(String storerKey, String facility, List<String> poKeys,
                                String userId, VariationContext context) {
        String receiptKey = keyGeneratorService.generateKey("RECEIPT");
        log.info("Creating receipt {} for {} POs", receiptKey, poKeys.size());

        String sql = """
            INSERT INTO RECEIPT (RECEIPTKEY, STORERKEY, FACILITY, STATUS, TYPE,
                ADDDATE, ADDWHO, EDITDATE, EDITWHO)
            VALUES (?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), ?)
            """;

        jdbcTemplate.update(sql,
            receiptKey,
            storerKey,
            facility,
            "0", // Initial status
            "PO", // Receipt type
            userId,
            userId
        );

        // Link POs to receipt
        for (String poKey : poKeys) {
            linkPOToReceipt(receiptKey, poKey, userId);
        }

        return receiptKey;
    }

    /**
     * Update receipt status
     */
    @Transactional
    public void updateReceiptStatus(String receiptKey, String status, String userId) {
        log.info("Updating receipt {} status to {}", receiptKey, status);

        String sql = """
            UPDATE RECEIPT SET STATUS = ?, EDITDATE = GETDATE(), EDITWHO = ?
            WHERE RECEIPTKEY = ?
            """;

        jdbcTemplate.update(sql, status, userId, receiptKey);
    }

    /**
     * Delete receipt
     */
    @Transactional
    public void deleteReceipt(String receiptKey) {
        log.info("Deleting receipt {}", receiptKey);

        // Delete receipt details first
        jdbcTemplate.update("DELETE FROM RECEIPTDETAIL WHERE RECEIPTKEY = ?", receiptKey);

        // Delete PO links
        jdbcTemplate.update("DELETE FROM RECEIPTPO WHERE RECEIPTKEY = ?", receiptKey);

        // Delete receipt
        jdbcTemplate.update("DELETE FROM RECEIPT WHERE RECEIPTKEY = ?", receiptKey);
    }

    private void linkPOToReceipt(String receiptKey, String poKey, String userId) {
        String sql = """
            INSERT INTO RECEIPTPO (RECEIPTKEY, POKEY, ADDDATE, ADDWHO)
            VALUES (?, ?, GETDATE(), ?)
            """;

        try {
            jdbcTemplate.update(sql, receiptKey, poKey, userId);
        } catch (Exception e) {
            log.warn("Could not link PO {} to receipt {}: {}", poKey, receiptKey, e.getMessage());
        }
    }
}
