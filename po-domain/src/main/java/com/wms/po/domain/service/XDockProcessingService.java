package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for XDock (Cross-Dock) Order Processing.
 *
 * Replaces: SP-064 nsp_xdockorderprocessing (1,783 LOC)
 *
 * XDock processing handles the automatic matching and allocation of
 * inbound receipts to outbound orders, bypassing traditional putaway
 * when product can be directly cross-docked to shipping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XDockProcessingService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGenerator;

    // XDock configuration flags
    private static final String CONFIG_XDOCK_AUTO = "XDOCKAUTO";
    private static final String CONFIG_XDOCK_PRIORITY = "XDOCKPRIORITY";
    private static final String CONFIG_XDOCK_TOLERANCE = "XDOCKTOLERANCE";

    // Status constants
    private static final String STATUS_NEW = "0";
    private static final String STATUS_ALLOCATED = "5";
    private static final String STATUS_RELEASED = "9";

    /**
     * Process all pending XDock orders for a facility.
     *
     * @param facility Facility code
     * @return Processing result summary
     */
    @Transactional
    public XDockProcessingResult processXDockOrders(String facility) {
        log.info("Starting XDock order processing for facility: {}", facility);

        XDockProcessingResult result = new XDockProcessingResult();

        try {
            // Find orders eligible for XDock processing
            List<String> eligibleOrders = findXDockEligibleOrders(facility);
            result.ordersProcessed = eligibleOrders.size();

            for (String orderKey : eligibleOrders) {
                try {
                    XDockOrderResult orderResult = processOrder(orderKey, facility);
                    if (orderResult.success) {
                        result.ordersAllocated++;
                        result.totalLinesAllocated += orderResult.linesAllocated;
                        result.totalQtyAllocated = result.totalQtyAllocated.add(orderResult.qtyAllocated);
                    } else {
                        result.warnings.addAll(orderResult.warnings);
                    }
                } catch (Exception e) {
                    log.error("Failed to process XDock order {}: {}", orderKey, e.getMessage());
                    result.errors.add("Order " + orderKey + ": " + e.getMessage());
                }
            }

            log.info("XDock processing complete: {} orders processed, {} allocated, {} lines, qty={}",
                result.ordersProcessed, result.ordersAllocated,
                result.totalLinesAllocated, result.totalQtyAllocated);

        } catch (Exception e) {
            log.error("XDock processing failed: {}", e.getMessage(), e);
            result.errors.add("Processing failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Process a single XDock order.
     *
     * @param orderKey Order to process
     * @param facility Facility
     * @return Result of order processing
     */
    @Transactional
    public XDockOrderResult processOrder(String orderKey, String facility) {
        log.debug("Processing XDock order: {}", orderKey);

        XDockOrderResult result = new XDockOrderResult();
        result.orderKey = orderKey;

        // Get order details
        List<Map<String, Object>> orderLines = getOrderLines(orderKey);

        for (Map<String, Object> line : orderLines) {
            String sku = (String) line.get("sku");
            String storerKey = (String) line.get("storerkey");
            int lineNum = ((Number) line.get("orderlinenumber")).intValue();
            BigDecimal openQty = (BigDecimal) line.get("openqty");

            if (openQty == null || openQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Find available inventory in receiving (not yet put away)
            List<Map<String, Object>> availableInventory = findAvailableReceivingInventory(
                sku, storerKey, facility);

            BigDecimal remainingQty = openQty;

            for (Map<String, Object> inv : availableInventory) {
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                String receiptKey = (String) inv.get("receiptkey");
                int receiptLineNum = ((Number) inv.get("receiptlinenumber")).intValue();
                BigDecimal availQty = (BigDecimal) inv.get("availqty");
                String lot = (String) inv.get("lottable01");
                String loc = (String) inv.get("toloc");
                String id = (String) inv.get("toid");

                // Check lot matching requirements
                if (!checkLotMatch(line, inv)) {
                    continue;
                }

                BigDecimal allocQty = remainingQty.min(availQty);

                // Create allocation
                createXDockAllocation(
                    orderKey, lineNum, receiptKey, receiptLineNum,
                    sku, storerKey, allocQty, lot, loc, id
                );

                remainingQty = remainingQty.subtract(allocQty);
                result.linesAllocated++;
                result.qtyAllocated = result.qtyAllocated.add(allocQty);

                log.debug("XDock allocated: order={}/{} <- receipt={}/{}, qty={}",
                    orderKey, lineNum, receiptKey, receiptLineNum, allocQty);
            }

            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                result.warnings.add(String.format("Order %s line %d: %.0f units unallocated",
                    orderKey, lineNum, remainingQty));
            }
        }

        // Update order status if fully allocated
        if (result.linesAllocated > 0) {
            updateOrderXDockStatus(orderKey);
            result.success = true;
        }

        return result;
    }

    /**
     * Find orders eligible for XDock processing.
     */
    private List<String> findXDockEligibleOrders(String facility) {
        String sql = """
            SELECT DISTINCT o.orderkey
            FROM dbo.ORDERS o
            JOIN dbo.ORDERDETAIL od ON o.orderkey = od.orderkey
            WHERE o.facility = ?
            AND o.status IN ('0', '1')
            AND o.xdockflag = '1'
            AND od.openqty > COALESCE(od.allocatedqty, 0)
            ORDER BY o.priority ASC, o.orderkey ASC
            """;

        return jdbcTemplate.queryForList(sql, String.class, facility);
    }

    /**
     * Get order detail lines.
     */
    private List<Map<String, Object>> getOrderLines(String orderKey) {
        String sql = """
            SELECT od.orderlinenumber, od.sku, od.storerkey,
                   od.openqty - COALESCE(od.allocatedqty, 0) as openqty,
                   od.lottable01, od.lottable02, od.lottable03, od.lottable04, od.lottable05
            FROM dbo.ORDERDETAIL od
            WHERE od.orderkey = ?
            AND od.openqty > COALESCE(od.allocatedqty, 0)
            AND od.status IN ('0', '1')
            ORDER BY od.orderlinenumber
            """;

        return jdbcTemplate.queryForList(sql, orderKey);
    }

    /**
     * Find available inventory in receiving area.
     */
    private List<Map<String, Object>> findAvailableReceivingInventory(
            String sku, String storerKey, String facility) {

        String sql = """
            SELECT rd.receiptkey, rd.receiptlinenumber,
                   rd.qtyreceived - COALESCE(rd.qtyallocated, 0) as availqty,
                   rd.lottable01, rd.lottable02, rd.lottable03, rd.lottable04, rd.lottable05,
                   rd.toloc, rd.toid
            FROM dbo.RECEIPTDETAIL rd
            JOIN dbo.RECEIPT r ON rd.receiptkey = r.receiptkey
            WHERE rd.sku = ?
            AND rd.storerkey = ?
            AND r.facility = ?
            AND r.status IN ('5', '9')
            AND rd.qtyreceived > COALESCE(rd.qtyallocated, 0)
            ORDER BY r.adddate ASC, rd.receiptkey ASC
            """;

        return jdbcTemplate.queryForList(sql, sku, storerKey, facility);
    }

    /**
     * Check if lot attributes match requirements.
     */
    private boolean checkLotMatch(Map<String, Object> orderLine, Map<String, Object> inventory) {
        // Check each lottable field if order has requirements
        for (int i = 1; i <= 5; i++) {
            String lotKey = "lottable0" + i;
            Object orderLot = orderLine.get(lotKey);
            Object invLot = inventory.get(lotKey);

            // If order specifies a lot, inventory must match
            if (orderLot != null && !orderLot.toString().isEmpty()) {
                if (invLot == null || !orderLot.toString().equals(invLot.toString())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Create XDock allocation record.
     */
    private void createXDockAllocation(String orderKey, int orderLineNum,
                                       String receiptKey, int receiptLineNum,
                                       String sku, String storerKey,
                                       BigDecimal qty, String lot,
                                       String loc, String id) {

        String pickDetailKey = java.util.UUID.randomUUID().toString().substring(0, 20);

        // Create pick detail
        String sql = """
            INSERT INTO dbo.PICKDETAIL (pickdetailkey, orderkey, orderlinenumber,
                receiptkey, receiptlinenumber, sku, storerkey, qty,
                fromloc, fromid, lot, status, picktype, adddate, addwho)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '0', 'XDOCK', GETDATE(), 'XDOCK')
            """;

        jdbcTemplate.update(sql,
            pickDetailKey, orderKey, orderLineNum,
            receiptKey, receiptLineNum, sku, storerKey, qty,
            loc, id, lot
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
    }

    /**
     * Update order status after XDock allocation.
     */
    private void updateOrderXDockStatus(String orderKey) {
        // Check if fully allocated
        Integer unallocated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.ORDERDETAIL " +
            "WHERE orderkey = ? AND openqty > COALESCE(allocatedqty, 0)",
            Integer.class, orderKey
        );

        String newStatus = (unallocated != null && unallocated == 0) ? STATUS_ALLOCATED : STATUS_NEW;

        jdbcTemplate.update(
            "UPDATE dbo.ORDERS SET status = ?, xdockstatus = 'P', " +
            "editdate = GETDATE(), editwho = 'XDOCK' WHERE orderkey = ?",
            newStatus, orderKey
        );
    }

    /**
     * Result of XDock processing run.
     */
    public static class XDockProcessingResult {
        public int ordersProcessed = 0;
        public int ordersAllocated = 0;
        public int totalLinesAllocated = 0;
        public BigDecimal totalQtyAllocated = BigDecimal.ZERO;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }

    /**
     * Result of single order processing.
     */
    public static class XDockOrderResult {
        public String orderKey;
        public boolean success = false;
        public int linesAllocated = 0;
        public BigDecimal qtyAllocated = BigDecimal.ZERO;
        public List<String> warnings = new ArrayList<>();
    }
}
