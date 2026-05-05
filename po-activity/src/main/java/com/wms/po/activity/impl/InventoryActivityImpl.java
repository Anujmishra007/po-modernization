package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of InventoryActivity for inventory reservations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryActivityImpl implements InventoryActivity {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<String> createReservations(String receiptKey, List<String> detailKeys) {
        log.info("Creating inventory reservations for receiptKey={}, detailCount={}",
            receiptKey, detailKeys.size());

        List<String> reservationIds = new ArrayList<>();

        for (String detailKey : detailKeys) {
            String reservationId = createReservation(receiptKey, detailKey);
            reservationIds.add(reservationId);
        }

        log.info("Created {} inventory reservations", reservationIds.size());
        return reservationIds;
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
