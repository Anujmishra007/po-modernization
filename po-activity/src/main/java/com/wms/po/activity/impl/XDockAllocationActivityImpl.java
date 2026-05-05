package com.wms.po.activity.impl;

import com.wms.po.activity.XDockAllocationActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of XDock Allocation Activity.
 *
 * Maps to legacy SPs:
 * - SP-007: WM.lsp_FlowThruAllocate_Wrapper (error code 69304)
 * - SP-008: WM.lsp_XDockAllocation_Wrapper (error code 69301)
 * - SP-080-083: nsp_xdockorderprocessing* (error codes 69300-69307)
 *
 * Error codes:
 * - XD_001 (69300) - XDock Order Not Found
 * - XD_002 (69301) - XDock Allocation Failed
 * - XD_003 (69302) - XDock No Eligible Orders
 * - XD_004 (69303) - XDock Processing Failed
 * - XD_005 (69304) - Flow-Thru Allocation Failed
 * - XD_006 (69305) - XDock Line Not Found
 * - XD_007 (69306) - XDock Insufficient Quantity
 *
 * Flow-through (XDock) processing directly allocates received inventory
 * to outbound orders without going through traditional putaway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XDockAllocationActivityImpl implements XDockAllocationActivity {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGenerator;

    private static final String STATUS_ALLOCATED = "5";
    private static final String ALLOCATION_TYPE_XDOCK = "XDOCK";

    @Override
    @Transactional
    public XDockAllocationResult allocateFlowThrough(String receiptKey, String storerKey) {
        log.info("Starting XDock allocation for receipt={}, storer={}", receiptKey, storerKey);

        List<AllocationDetail> allocations = new ArrayList<>();
        BigDecimal totalAllocated = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();

        try {
            // Get receipt lines eligible for XDock
            String receiptSql = """
                SELECT rd.receiptlinenumber, rd.sku, rd.qtyreceived - COALESCE(rd.qtyallocated, 0) as availqty,
                       rd.lottable01, rd.toloc, rd.toid
                FROM dbo.RECEIPTDETAIL rd
                WHERE rd.receiptkey = ?
                AND rd.qtyreceived > COALESCE(rd.qtyallocated, 0)
                ORDER BY rd.receiptlinenumber
                """;

            List<Map<String, Object>> receiptLines;
            try {
                receiptLines = jdbcTemplate.queryForList(receiptSql, receiptKey);
            } catch (Exception e) {
                log.error("Failed to get receipt lines for XDock: {} (legacy error 69305)",
                    receiptKey, e);
                throw BusinessException.receiptNotFound(receiptKey);
            }

            if (receiptLines.isEmpty()) {
                log.warn("No receipt lines found for XDock allocation: {} (legacy warning 69302)", receiptKey);
                return XDockAllocationResult.noMatch();
            }

            for (Map<String, Object> line : receiptLines) {
                int lineNum = ((Number) line.get("receiptlinenumber")).intValue();
                String sku = (String) line.get("sku");
                BigDecimal availQty = (BigDecimal) line.get("availqty");
                String lot = (String) line.get("lottable01");
                String loc = (String) line.get("toloc");
                String id = (String) line.get("toid");

                if (availQty == null || availQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Find eligible orders for this SKU
                List<EligibleOrder> eligibleOrders = findEligibleOrders(sku, storerKey, availQty);

                BigDecimal remainingQty = availQty;

                for (EligibleOrder order : eligibleOrders) {
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }

                    BigDecimal allocQty = remainingQty.min(order.openQty());

                    // Create allocation
                    String allocId = createAllocation(
                        receiptKey, lineNum, order.orderKey(), order.lineNumber(),
                        sku, allocQty, lot, loc, id, storerKey
                    );

                    AllocationDetail detail = new AllocationDetail(
                        allocId, receiptKey, lineNum, order.orderKey(), order.lineNumber(),
                        sku, allocQty, lot, loc, id
                    );

                    allocations.add(detail);
                    totalAllocated = totalAllocated.add(allocQty);
                    remainingQty = remainingQty.subtract(allocQty);

                    log.debug("Created XDock allocation: receipt={}/{} -> order={}/{}, qty={}",
                        receiptKey, lineNum, order.orderKey(), order.lineNumber(), allocQty);
                }

                if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    warnings.add(String.format("Line %d: %.0f units unallocated (no matching orders)",
                        lineNum, remainingQty));
                }
            }

            log.info("XDock allocation complete: {} allocations, total qty={}",
                allocations.size(), totalAllocated);

            if (allocations.isEmpty()) {
                log.info("No eligible orders found for XDock allocation (legacy info 69302)");
                return XDockAllocationResult.noMatch();
            }

            return new XDockAllocationResult(true, allocations.size(), totalAllocated,
                allocations, List.of(), warnings);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("XDock flow-through allocation failed for receipt {}: {} (legacy error 69304)",
                receiptKey, e.getMessage(), e);
            throw BusinessException.flowThruAllocationFailed(receiptKey, e);
        }
    }

    @Override
    @Transactional
    public XDockAllocationResult allocateLine(String receiptKey, int lineNumber, List<String> orderKeys) {
        log.info("Allocating receipt line: receipt={}, line={}, orders={}",
            receiptKey, lineNumber, orderKeys);

        try {
            // Get receipt line details
            String sql = """
                SELECT rd.sku, rd.storerkey, rd.qtyreceived - COALESCE(rd.qtyallocated, 0) as availqty,
                       rd.lottable01, rd.toloc, rd.toid
                FROM dbo.RECEIPTDETAIL rd
                WHERE rd.receiptkey = ? AND rd.receiptlinenumber = ?
                """;

            Map<String, Object> line;
            try {
                line = jdbcTemplate.queryForMap(sql, receiptKey, lineNumber);
            } catch (EmptyResultDataAccessException e) {
                log.error("Receipt line not found: {}/{} (legacy error 69305)", receiptKey, lineNumber);
                throw BusinessException.receiptDetailNotFound(receiptKey, String.valueOf(lineNumber));
            }

            String sku = (String) line.get("sku");
            String storerKey = (String) line.get("storerkey");
            BigDecimal availQty = (BigDecimal) line.get("availqty");
            String lot = (String) line.get("lottable01");
            String loc = (String) line.get("toloc");
            String id = (String) line.get("toid");

            if (availQty == null || availQty.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("No available quantity on receipt line {}/{} (legacy warning 69302)",
                    receiptKey, lineNumber);
                throw new BusinessException(ErrorCode.XDOCK_INSUFFICIENT_INVENTORY,
                    String.format("No available quantity on receipt %s line %d", receiptKey, lineNumber))
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("lineNumber", lineNumber);
            }

            List<AllocationDetail> allocations = new ArrayList<>();
            BigDecimal totalAllocated = BigDecimal.ZERO;
            BigDecimal remainingQty = availQty;

            // Get target orders (either specified or auto-match)
            List<EligibleOrder> orders;
            if (orderKeys != null && !orderKeys.isEmpty()) {
                orders = getSpecificOrders(orderKeys, sku, storerKey);
                if (orders.isEmpty()) {
                    log.error("Specified orders not found or not eligible: {} (legacy error 69300)",
                        orderKeys);
                    throw BusinessException.xdockOrderNotFound(orderKeys.get(0));
                }
            } else {
                orders = findEligibleOrders(sku, storerKey, availQty);
            }

            for (EligibleOrder order : orders) {
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal allocQty = remainingQty.min(order.openQty());

                String allocId = createAllocation(
                    receiptKey, lineNumber, order.orderKey(), order.lineNumber(),
                    sku, allocQty, lot, loc, id, storerKey
                );

                allocations.add(new AllocationDetail(
                    allocId, receiptKey, lineNumber, order.orderKey(), order.lineNumber(),
                    sku, allocQty, lot, loc, id
                ));

                totalAllocated = totalAllocated.add(allocQty);
                remainingQty = remainingQty.subtract(allocQty);
            }

            if (allocations.isEmpty()) {
                log.info("No eligible orders found for XDock line allocation (legacy info 69302)");
                return XDockAllocationResult.noMatch();
            }

            return XDockAllocationResult.success(allocations, totalAllocated);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("XDock line allocation failed for receipt {}/{}: {} (legacy error 69301)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw BusinessException.xdockAllocationFailed(receiptKey, e);
        }
    }

    @Override
    @Transactional
    public void releaseAllocation(String allocationId) {
        log.info("Releasing XDock allocation: {}", allocationId);

        jdbcTemplate.update(
            "UPDATE dbo.PICKDETAIL SET status = '9' WHERE pickdetailkey = ?",
            allocationId
        );
    }

    /**
     * Cancel XDock allocation (compensation action).
     *
     * Error codes:
     * - XD_004 (69303) - XDock Processing Failed
     */
    @Override
    @Transactional
    public void cancelAllocation(String allocationId) {
        log.warn("COMPENSATION: Canceling XDock allocation: {}", allocationId);

        // Get allocation details for reversal
        try {
            Map<String, Object> alloc;
            try {
                alloc = jdbcTemplate.queryForMap(
                    "SELECT receiptkey, receiptlinenumber, qty FROM dbo.PICKDETAIL WHERE pickdetailkey = ?",
                    allocationId
                );
            } catch (EmptyResultDataAccessException e) {
                log.warn("Allocation {} not found (may already be cancelled)", allocationId);
                return; // Already cancelled - not an error
            }

            String receiptKey = (String) alloc.get("receiptkey");
            int lineNum = ((Number) alloc.get("receiptlinenumber")).intValue();
            BigDecimal qty = (BigDecimal) alloc.get("qty");

            // Reverse receipt line allocation qty
            jdbcTemplate.update(
                "UPDATE dbo.RECEIPTDETAIL SET qtyallocated = qtyallocated - ? " +
                "WHERE receiptkey = ? AND receiptlinenumber = ?",
                qty, receiptKey, lineNum
            );

            // Delete allocation
            jdbcTemplate.update("DELETE FROM dbo.PICKDETAIL WHERE pickdetailkey = ?", allocationId);

            log.info("COMPENSATION complete: Cancelled allocation {}", allocationId);

        } catch (Exception e) {
            log.error("Failed to cancel allocation {}: {} (legacy error 69303)",
                allocationId, e.getMessage(), e);
            throw BusinessException.xdockProcessingFailed(allocationId, e);
        }
    }

    @Override
    public List<EligibleOrder> findEligibleOrders(String sku, String storerKey, BigDecimal qty) {
        log.debug("Finding eligible orders for SKU={}, storer={}, qty={}", sku, storerKey, qty);

        String sql = """
            SELECT od.orderkey, od.orderlinenumber, od.sku,
                   od.openqty - COALESCE(od.allocatedqty, 0) as openqty,
                   o.priority, o.requestedshipdate,
                   od.lottable01, od.lottable02, od.lottable03, od.lottable04, od.lottable05
            FROM dbo.ORDERDETAIL od
            JOIN dbo.ORDERS o ON od.orderkey = o.orderkey
            WHERE od.sku = ?
            AND od.storerkey = ?
            AND od.status IN ('0', '1', '5')
            AND od.openqty > COALESCE(od.allocatedqty, 0)
            AND o.status IN ('0', '1', '5')
            AND o.ordertype IN ('SO', 'TR', 'XD')
            ORDER BY o.priority ASC, o.requestedshipdate ASC, od.orderkey ASC
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, sku, storerKey);

        List<EligibleOrder> orders = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> lottables = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                String key = "lottable0" + i;
                Object val = row.get(key);
                if (val != null) {
                    lottables.put(key, val.toString());
                }
            }

            orders.add(new EligibleOrder(
                (String) row.get("orderkey"),
                ((Number) row.get("orderlinenumber")).intValue(),
                (String) row.get("sku"),
                (BigDecimal) row.get("openqty"),
                row.get("priority") != null ? row.get("priority").toString() : "5",
                row.get("requestedshipdate") != null ? row.get("requestedshipdate").toString() : null,
                lottables
            ));
        }

        log.debug("Found {} eligible orders", orders.size());
        return orders;
    }

    // ═══════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════

    private String createAllocation(String receiptKey, int receiptLineNum,
                                    String orderKey, int orderLineNum,
                                    String sku, BigDecimal qty,
                                    String lot, String loc, String id,
                                    String storerKey) {

        String allocId = UUID.randomUUID().toString().substring(0, 20);

        // Create pick detail (allocation record)
        String sql = """
            INSERT INTO dbo.PICKDETAIL (pickdetailkey, orderkey, orderlinenumber,
                receiptkey, receiptlinenumber, sku, qty, storerkey,
                fromloc, fromid, lot, status, picktype, adddate, addwho)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '0', ?, GETDATE(), ?)
            """;

        jdbcTemplate.update(sql,
            allocId, orderKey, orderLineNum,
            receiptKey, receiptLineNum, sku, qty, storerKey,
            loc, id, lot, ALLOCATION_TYPE_XDOCK, "XDOCK"
        );

        // Update receipt detail allocated qty
        jdbcTemplate.update(
            "UPDATE dbo.RECEIPTDETAIL SET qtyallocated = COALESCE(qtyallocated, 0) + ? " +
            "WHERE receiptkey = ? AND receiptlinenumber = ?",
            qty, receiptKey, receiptLineNum
        );

        // Update order detail allocated qty
        jdbcTemplate.update(
            "UPDATE dbo.ORDERDETAIL SET allocatedqty = COALESCE(allocatedqty, 0) + ? " +
            "WHERE orderkey = ? AND orderlinenumber = ?",
            qty, orderKey, orderLineNum
        );

        return allocId;
    }

    private List<EligibleOrder> getSpecificOrders(List<String> orderKeys, String sku, String storerKey) {
        List<EligibleOrder> orders = new ArrayList<>();

        for (String orderKey : orderKeys) {
            String sql = """
                SELECT od.orderkey, od.orderlinenumber, od.sku,
                       od.openqty - COALESCE(od.allocatedqty, 0) as openqty,
                       o.priority, o.requestedshipdate
                FROM dbo.ORDERDETAIL od
                JOIN dbo.ORDERS o ON od.orderkey = o.orderkey
                WHERE od.orderkey = ?
                AND od.sku = ?
                AND od.storerkey = ?
                AND od.openqty > COALESCE(od.allocatedqty, 0)
                """;

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, orderKey, sku, storerKey);

                for (Map<String, Object> row : rows) {
                    orders.add(new EligibleOrder(
                        (String) row.get("orderkey"),
                        ((Number) row.get("orderlinenumber")).intValue(),
                        (String) row.get("sku"),
                        (BigDecimal) row.get("openqty"),
                        row.get("priority") != null ? row.get("priority").toString() : "5",
                        row.get("requestedshipdate") != null ? row.get("requestedshipdate").toString() : null,
                        Map.of()
                    ));
                }
            } catch (Exception e) {
                log.debug("Order {} not found or not eligible: {}", orderKey, e.getMessage());
            }
        }

        return orders;
    }
}
