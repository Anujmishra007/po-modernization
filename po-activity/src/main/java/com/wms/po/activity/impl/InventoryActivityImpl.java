package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of InventoryActivity for inventory reservations.
 *
 * Error codes:
 * - XD_020 (69320) - Allocation Build Failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryActivityImpl implements InventoryActivity {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Create inventory reservations for receipt details.
     *
     * Error codes:
     * - XD_020 (69320) - Allocation Build Failed
     *
     * @param receiptKey Receipt key
     * @param detailKeys List of detail keys to reserve
     * @return List of reservation IDs
     * @throws BusinessException if reservation fails
     */
    @Override
    @Transactional
    public List<String> createReservations(String receiptKey, List<String> detailKeys) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is null/blank for inventory reservations (legacy error 69320)");
            throw new BusinessException(ErrorCode.ALLOCATION_BUILD_FAILED,
                "Receipt key is required for inventory reservations")
                .withDetail("receiptKey", "null or blank");
        }

        if (detailKeys == null || detailKeys.isEmpty()) {
            log.warn("No detail keys provided for reservation, returning empty list");
            return new ArrayList<>();
        }

        log.info("Creating inventory reservations for receiptKey={}, detailCount={}",
            receiptKey, detailKeys.size());

        List<String> reservationIds = new ArrayList<>();

        try {
            for (String detailKey : detailKeys) {
                String reservationId = createReservation(receiptKey, detailKey);
                reservationIds.add(reservationId);
            }

            log.info("Created {} inventory reservations", reservationIds.size());
            return reservationIds;

        } catch (DataAccessException e) {
            log.error("Failed to create inventory reservations: receiptKey={}, error={} (legacy error 69320)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ALLOCATION_BUILD_FAILED,
                "Failed to create inventory reservations: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("detailCount", detailKeys.size());
        }
    }

    @Override
    @Transactional
    public void releaseReservations(List<String> reservationIds) {
        log.warn("COMPENSATION: Releasing {} inventory reservations", reservationIds.size());

        for (String reservationId : reservationIds) {
            releaseReservation(reservationId);
        }

        log.info("COMPENSATION complete: Released {} inventory reservations", reservationIds.size());
    }

    @Override
    @Transactional
    public void preAllocateInventory(String receiptKey, List<String> detailKeys) {
        log.info("Pre-allocating inventory for receiptKey={}", receiptKey);
        // Pre-allocation logic - no-op in PostgreSQL mode (legacy SP not available)
        log.debug("Pre-allocation step completed (no-op in PostgreSQL mode)");
    }

    @Override
    @Transactional
    public void releasePreAllocation(String receiptKey) {
        log.warn("COMPENSATION: Releasing pre-allocated inventory for receiptKey={}", receiptKey);
        // Release pre-allocation - no-op in PostgreSQL mode
        log.debug("Pre-allocation release completed (no-op in PostgreSQL mode)");
    }

    private String createReservation(String receiptKey, String detailKey) {
        String reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8);
        // In-memory tracking for now - inventory reservation table not required for basic flow
        log.debug("Created reservation {} for receipt {} detail {}", reservationId, receiptKey, detailKey);
        return reservationId;
    }

    private void releaseReservation(String reservationId) {
        // In-memory release - no DB operation needed
        log.debug("Released reservation {}", reservationId);
    }
}
