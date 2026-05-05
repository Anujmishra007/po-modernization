package com.wms.po.activity.impl;

import com.wms.po.activity.XDockAllocationActivity;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Replaces:
 * - SP-007: WM.lsp_FlowThruAllocate_Wrapper
 * - SP-008: WM.lsp_XDockAllocation_Wrapper
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

        // Get receipt lines eligible for XDock
        String receiptSql = """
            SELECT rd.receiptlinenumber, rd.sku, rd.qtyreceived - COALESCE(rd.qtyallocated, 0) as availqty,
                   rd.lottable01, rd.toloc, rd.toid
            FROM dbo.RECEIPTDETAIL rd
            WHERE rd.receiptkey = ?
            AND rd.qtyreceived > COALESCE(rd.qtyallocated, 0)
            ORDER BY rd.receiptlinenumber
            """;

        List<Map<String, Object>> receiptLines = jdbcTemplate.queryForList(receiptSql, receiptKey);

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
            return XDockAllocationResult.noMatch();
        }

        return new XDockAllocationResult(true, allocations.size(), totalAllocated,
            allocations, List.of(), warnings);
    }

    @Override
    @Transactional
    public XDockAllocationResult allocateLine(String receiptKey, int lineNumber, List<String> orderKeys) {
        log.info("Allocating receipt line: receipt={}, line={}, orders={}",
            receiptKey, lineNumber, orderKeys);

        // Get receipt line details
        String sql = """
            SELECT rd.sku, rd.storerkey, rd.qtyreceived - COALESCE(rd.qtyallocated, 0) as availqty,
                   rd.lottable01, rd.toloc, rd.toid
            FROM dbo.RECEIPTDETAIL rd
            WHERE rd.receiptkey = ? AND rd.receiptlinenumber = ?
            """;

        Map<String, Object> line = jdbcTemplate.queryForMap(sql, receiptKey, lineNumber);

        String sku = (String) line.get("sku");
        String storerKey = (String) line.get("storerkey");
        BigDecimal availQty = (BigDecimal) line.get("availqty");
        String lot = (String) line.get("lottable01");
        String loc = (String) line.get("toloc");
        String id = (String) line.get("toid");

        if (availQty == null || availQty.compareTo(BigDecimal.ZERO) <= 0) {
            return XDockAllocationResult.failure(List.of("No available quantity on line"));
        }

        List<AllocationDetail> allocations = new ArrayList<>();
        BigDecimal totalAllocated = BigDecimal.ZERO;
        BigDecimal remainingQty = availQty;

        // Get target orders (either specified or auto-match)
        List<EligibleOrder> orders;
        if (orderKeys != null && !orderKeys.isEmpty()) {
            orders = getSpecificOrders(orderKeys, sku, storerKey);
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
            return XDockAllocationResult.noMatch();
        }

        return XDockAllocationResult.success(allocations, totalAllocated);
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

    @Override
    @Transactional
    public void cancelAllocation(String allocationId) {
        log.warn("COMPENSATION: Canceling XDock allocation: {}", allocationId);

        // Get allocation details for reversal
        try {
            Map<String, Object> alloc = jdbcTemplate.queryForMap(
                "SELECT receiptkey, receiptlinenumber, qty FROM dbo.PICKDETAIL WHERE pickdetailkey = ?",
                allocationId
            );

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
            log.error("Failed to cancel allocation {}: {}", allocationId, e.getMessage());
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
