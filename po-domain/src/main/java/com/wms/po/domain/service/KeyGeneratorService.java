package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for generating unique keys using the WMS nspg_GetKey pattern.
 * Uses NCOUNTER table for atomic key generation (PostgreSQL compatible).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyGeneratorService {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong fallbackCounter = new AtomicLong(System.currentTimeMillis());

    /**
     * Generate a unique key for the given key type using NCOUNTER table.
     * PostgreSQL-compatible implementation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateKey(String keyType) {
        try {
            // Update counter and get new value (PostgreSQL compatible)
            jdbcTemplate.update(
                "UPDATE dbo.ncounter SET countervalue = countervalue + 1, lastused = CURRENT_TIMESTAMP WHERE countername = ?",
                keyType
            );

            // Get the updated value with prefix
            return jdbcTemplate.queryForObject(
                "SELECT CONCAT(COALESCE(prefix, ''), CAST(countervalue AS VARCHAR)) FROM dbo.ncounter WHERE countername = ?",
                String.class,
                keyType
            );
        } catch (Exception e) {
            log.warn("Failed to use NCOUNTER table, falling back to Java generation: {}", e.getMessage());
            return generateKeyFallback(keyType);
        }
    }

    /**
     * Fallback key generation using atomic counter
     */
    private String generateKeyFallback(String keyType) {
        String prefix = getPrefix(keyType);
        long counter = fallbackCounter.incrementAndGet();
        return prefix + counter;
    }

    private String getPrefix(String keyType) {
        return switch (keyType) {
            case "RECEIPTKEY", "RECEIPT" -> "RCV-";
            case "RECEIPTDETAILKEY" -> "RCVD-";
            case "POKEY", "PO" -> "PO-";
            case "ORDERKEY", "ORDER" -> "SO-";
            case "ORDERDETAILKEY" -> "SOD-";
            case "UCCKEY", "UCC" -> "UCC-";
            default -> "KEY-";
        };
    }

    /**
     * Generate a receipt key
     */
    public String generateReceiptKey() {
        return generateKey("RECEIPTKEY");
    }

    /**
     * Generate a receipt detail key
     */
    public String generateReceiptDetailKey() {
        return generateKey("RECEIPTDETAILKEY");
    }

    /**
     * Generate a PO key
     */
    public String generatePOKey() {
        return generateKey("POKEY");
    }

    /**
     * Generate an Order key (for Sales Orders)
     */
    public String generateOrderKey() {
        return generateKey("ORDERKEY");
    }

    /**
     * Generate an Order Detail key
     */
    public String generateOrderDetailKey() {
        return generateKey("ORDERDETAILKEY");
    }

    /**
     * Generate a UCC key
     */
    public String generateUCCKey() {
        return generateKey("UCCKEY");
    }
}
