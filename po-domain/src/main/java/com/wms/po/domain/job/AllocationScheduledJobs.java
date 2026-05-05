package com.wms.po.domain.job;

import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Allocation Scheduled Jobs.
 *
 * Replaces:
 * - JOB-004: BuildAutoAllocation (lsp_BuildAutoAllocation) - 1 min interval
 * - JOB-005: XDockAutoAL (lsp_XDockAutoAllocate) - 1 min interval
 * - JOB-006: XDockCreateSO (lsp_XDockCreateSalesOrder) - 1 min interval
 *
 * Handles automated allocation and cross-dock processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllocationScheduledJobs {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    @Value("${wms.jobs.auto-allocation.enabled:true}")
    private boolean autoAllocationEnabled;

    @Value("${wms.jobs.xdock.enabled:true}")
    private boolean xdockEnabled;

    @Value("${wms.jobs.auto-allocation.batch-size:100}")
    private int allocationBatchSize;

    // ═══════════════════════════════════════════════════════════════════════════
    // JOB-004: Build Auto Allocation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Automatically allocate inventory to open sales orders.
     * Runs every 1 minute.
     *
     * Replaces: lsp_BuildAutoAllocation
     *
     * Logic:
     * 1. Find orders eligible for auto-allocation
     * 2. Match available inventory to order lines
     * 3. Create allocation records (pickdetail)
     * 4. Update order status
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000) // 1 min
    public void buildAutoAllocation() {
        if (!autoAllocationEnabled) {
            return;
        }

        log.debug("Starting BuildAutoAllocation job");
        int allocated = 0;
        int ordersProcessed = 0;

        try {
            // Find orders needing allocation
            List<OrderLine> pendingLines = findOrdersForAllocation();

            if (pendingLines.isEmpty()) {
                return;
            }

            // Group by SKU for efficient inventory lookup
            Map<String, List<OrderLine>> bySku = new HashMap<>();
            for (OrderLine line : pendingLines) {
                String key = line.getStorerKey() + "|" + line.getSku();
                bySku.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
            }

            // Process each SKU group
            for (Map.Entry<String, List<OrderLine>> entry : bySku.entrySet()) {
                String[] parts = entry.getKey().split("\\|");
                String storerKey = parts[0];
                String sku = parts[1];
                List<OrderLine> lines = entry.getValue();

                try {
                    // Find available inventory for this SKU
                    List<InventoryAvailable> inventory = findAvailableInventory(storerKey, sku);

                    // Allocate to order lines
                    for (OrderLine line : lines) {
                        if (line.getOpenQty().compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }

                        for (InventoryAvailable inv : inventory) {
                            if (inv.getAvailableQty().compareTo(BigDecimal.ZERO) <= 0) {
                                continue;
                            }

                            BigDecimal allocQty = line.getOpenQty().min(inv.getAvailableQty());

                            if (allocQty.compareTo(BigDecimal.ZERO) > 0) {
                                createAllocation(line, inv, allocQty);

                                // Update tracking
                                line.setOpenQty(line.getOpenQty().subtract(allocQty));
                                inv.setAvailableQty(inv.getAvailableQty().subtract(allocQty));
                                allocated++;
                            }

                            if (line.getOpenQty().compareTo(BigDecimal.ZERO) <= 0) {
                                break;
                            }
                        }
                        ordersProcessed++;
                    }

                } catch (Exception e) {
                    log.warn("Allocation failed for SKU {}: {}", sku, e.getMessage());
                }
            }

            if (allocated > 0) {
                log.info("BuildAutoAllocation complete: {} allocations for {} order lines",
                    allocated, ordersProcessed);
            }

        } catch (Exception e) {
            log.error("BuildAutoAllocation job failed: {}", e.getMessage(), e);
        }
    }

    private List<OrderLine> findOrdersForAllocation() {
        return jdbcTemplate.query(
            """
            SELECT TOP (?) od.orderkey, od.orderlinenumber, od.storerkey, od.sku,
                   od.openqty, od.packkey, o.priority, o.whseid
            FROM dbo.ORDERDETAIL od
            JOIN dbo.ORDERS o ON od.orderkey = o.orderkey
            WHERE od.status IN ('0', '1')
            AND od.openqty > 0
            AND o.status IN ('0', '1', '2')
            AND EXISTS (SELECT 1 FROM dbo.STORERCONFIG sc
                WHERE sc.storerkey = od.storerkey
                AND sc.configkey = 'AUTOALLOCATE' AND sc.configvalue = '1')
            ORDER BY o.priority ASC, o.deliverydate ASC, o.adddate ASC
            """,
            (rs, rowNum) -> OrderLine.builder()
                .orderKey(rs.getString("orderkey"))
                .orderLineNumber(rs.getInt("orderlinenumber"))
                .storerKey(rs.getString("storerkey"))
                .sku(rs.getString("sku"))
                .openQty(rs.getBigDecimal("openqty"))
                .packKey(rs.getString("packkey"))
                .priority(rs.getInt("priority"))
                .facility(rs.getString("whseid"))
                .build(),
            allocationBatchSize
        );
    }

    private List<InventoryAvailable> findAvailableInventory(String storerKey, String sku) {
        return jdbcTemplate.query(
            """
            SELECT lx.lotxlocxidkey, lx.loc, lx.id, lx.lottable01, lx.lottable02,
                   lx.qty - COALESCE(lx.qtyallocated, 0) - COALESCE(lx.qtypicked, 0) as availqty
            FROM dbo.LOTXLOCXID lx
            JOIN dbo.LOC l ON lx.loc = l.loc
            WHERE lx.storerkey = ? AND lx.sku = ?
            AND lx.status = '0'
            AND l.pickable = '1'
            AND (lx.qty - COALESCE(lx.qtyallocated, 0) - COALESCE(lx.qtypicked, 0)) > 0
            AND COALESCE(lx.hold, '0') != '1'
            ORDER BY lx.lottable05 ASC, lx.adddate ASC
            """,
            (rs, rowNum) -> InventoryAvailable.builder()
                .lotxlocxidKey(rs.getString("lotxlocxidkey"))
                .location(rs.getString("loc"))
                .id(rs.getString("id"))
                .lottable01(rs.getString("lottable01"))
                .lottable02(rs.getString("lottable02"))
                .availableQty(rs.getBigDecimal("availqty"))
                .build(),
            storerKey, sku
        );
    }

    private void createAllocation(OrderLine order, InventoryAvailable inv, BigDecimal qty) {
        String pickDetailKey = keyGeneratorService.generateKey("PICKDETAIL");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.PICKDETAIL
            (pickdetailkey, orderkey, orderlinenumber, storerkey, sku, lot, loc, id,
             qty, status, picktype, adddate, addwho)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '0', 'AUTO', GETDATE(), 'AUTOJOB')
            """,
            pickDetailKey,
            order.getOrderKey(),
            order.getOrderLineNumber(),
            order.getStorerKey(),
            order.getSku(),
            inv.getLottable01(),
            inv.getLocation(),
            inv.getId(),
            qty
        );

        // Update inventory allocated quantity
        jdbcTemplate.update(
            """
            UPDATE dbo.LOTXLOCXID
            SET qtyallocated = COALESCE(qtyallocated, 0) + ?
            WHERE lotxlocxidkey = ?
            """,
            qty, inv.getLotxlocxidKey()
        );

        // Update order detail
        jdbcTemplate.update(
            """
            UPDATE dbo.ORDERDETAIL
            SET qtyallocated = COALESCE(qtyallocated, 0) + ?,
                openqty = openqty - ?,
                status = CASE WHEN openqty - ? <= 0 THEN '5' ELSE status END
            WHERE orderkey = ? AND orderlinenumber = ?
            """,
            qty, qty, qty, order.getOrderKey(), order.getOrderLineNumber()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JOB-005: XDock Auto Allocate
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Automatically allocate cross-dock inventory to waiting orders.
     * Runs every 1 minute.
     *
     * Replaces: lsp_XDockAutoAllocate
     *
     * Logic:
     * 1. Find newly received cross-dock inventory
     * 2. Match to pre-allocated orders
     * 3. Create allocation linkage
     * 4. Update order for immediate pick
     */
    @Scheduled(fixedRate = 60000, initialDelay = 45000) // 1 min
    public void xdockAutoAllocate() {
        if (!xdockEnabled) {
            return;
        }

        log.debug("Starting XDockAutoAllocate job");
        int allocated = 0;

        try {
            // Find cross-dock receipts needing allocation
            List<XDockReceipt> xdockReceipts = findXDockReceipts();

            for (XDockReceipt receipt : xdockReceipts) {
                try {
                    // Find matching waiting order
                    XDockOrder matchingOrder = findMatchingXDockOrder(receipt);

                    if (matchingOrder != null) {
                        // Create allocation
                        createXDockAllocation(receipt, matchingOrder);
                        allocated++;

                        log.debug("XDock allocated receipt {} to order {}",
                            receipt.getReceiptKey(), matchingOrder.getOrderKey());
                    }

                } catch (Exception e) {
                    log.warn("XDock allocation failed for receipt {}: {}",
                        receipt.getReceiptKey(), e.getMessage());
                }
            }

            if (allocated > 0) {
                log.info("XDockAutoAllocate complete: {} allocations created", allocated);
            }

        } catch (Exception e) {
            log.error("XDockAutoAllocate job failed: {}", e.getMessage(), e);
        }
    }

    private List<XDockReceipt> findXDockReceipts() {
        return jdbcTemplate.query(
            """
            SELECT rd.receiptkey, rd.receiptlinenumber, rd.storerkey, rd.sku,
                   rd.qtyreceived, rd.lotxlocxidkey, rd.toloc, rd.toid
            FROM dbo.RECEIPTDETAIL rd
            JOIN dbo.RECEIPT r ON rd.receiptkey = r.receiptkey
            WHERE r.type = 'XDOCK'
            AND rd.status = '5'
            AND rd.xdockallocated != '1'
            AND rd.qtyreceived > 0
            ORDER BY r.adddate
            """,
            (rs, rowNum) -> XDockReceipt.builder()
                .receiptKey(rs.getString("receiptkey"))
                .lineNumber(rs.getInt("receiptlinenumber"))
                .storerKey(rs.getString("storerkey"))
                .sku(rs.getString("sku"))
                .quantity(rs.getBigDecimal("qtyreceived"))
                .lotxlocxidKey(rs.getString("lotxlocxidkey"))
                .location(rs.getString("toloc"))
                .id(rs.getString("toid"))
                .build()
        );
    }

    private XDockOrder findMatchingXDockOrder(XDockReceipt receipt) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 od.orderkey, od.orderlinenumber, od.openqty
                FROM dbo.ORDERDETAIL od
                JOIN dbo.ORDERS o ON od.orderkey = o.orderkey
                WHERE od.storerkey = ? AND od.sku = ?
                AND od.status IN ('0', '1')
                AND od.openqty > 0
                AND o.xdockflag = '1'
                ORDER BY o.priority ASC, o.deliverydate ASC
                """,
                (rs, rowNum) -> XDockOrder.builder()
                    .orderKey(rs.getString("orderkey"))
                    .orderLineNumber(rs.getInt("orderlinenumber"))
                    .openQty(rs.getBigDecimal("openqty"))
                    .build(),
                receipt.getStorerKey(), receipt.getSku()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void createXDockAllocation(XDockReceipt receipt, XDockOrder order) {
        BigDecimal allocQty = receipt.getQuantity().min(order.getOpenQty());

        String pickDetailKey = keyGeneratorService.generateKey("PICKDETAIL");

        // Create pick detail for immediate picking
        jdbcTemplate.update(
            """
            INSERT INTO dbo.PICKDETAIL
            (pickdetailkey, orderkey, orderlinenumber, storerkey, sku, loc, id,
             qty, status, picktype, adddate, addwho)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, '0', 'XDOCK', GETDATE(), 'XDOCKJOB')
            """,
            pickDetailKey,
            order.getOrderKey(),
            order.getOrderLineNumber(),
            receipt.getStorerKey(),
            receipt.getSku(),
            receipt.getLocation(),
            receipt.getId(),
            allocQty
        );

        // Mark receipt as xdock allocated
        jdbcTemplate.update(
            """
            UPDATE dbo.RECEIPTDETAIL
            SET xdockallocated = '1', xdockorderkey = ?, editdate = GETDATE()
            WHERE receiptkey = ? AND receiptlinenumber = ?
            """,
            order.getOrderKey(), receipt.getReceiptKey(), receipt.getLineNumber()
        );

        // Update inventory
        if (receipt.getLotxlocxidKey() != null) {
            jdbcTemplate.update(
                """
                UPDATE dbo.LOTXLOCXID
                SET qtyallocated = COALESCE(qtyallocated, 0) + ?
                WHERE lotxlocxidkey = ?
                """,
                allocQty, receipt.getLotxlocxidKey()
            );
        }

        // Update order detail
        jdbcTemplate.update(
            """
            UPDATE dbo.ORDERDETAIL
            SET qtyallocated = COALESCE(qtyallocated, 0) + ?,
                openqty = openqty - ?
            WHERE orderkey = ? AND orderlinenumber = ?
            """,
            allocQty, allocQty, order.getOrderKey(), order.getOrderLineNumber()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JOB-006: XDock Create Sales Order
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Automatically create sales orders for pre-allocated cross-dock shipments.
     * Runs every 1 minute.
     *
     * Replaces: lsp_XDockCreateSalesOrder
     *
     * Logic:
     * 1. Find cross-dock receipts with pre-allocated destination
     * 2. Create corresponding sales order
     * 3. Link receipt to order for flow-through
     */
    @Scheduled(fixedRate = 60000, initialDelay = 60000) // 1 min
    public void xdockCreateSO() {
        if (!xdockEnabled) {
            return;
        }

        log.debug("Starting XDockCreateSO job");
        int created = 0;

        try {
            // Find receipts needing SO creation
            List<XDockSORequest> requests = findXDockSORequests();

            for (XDockSORequest request : requests) {
                try {
                    String orderKey = createXDockSalesOrder(request);
                    if (orderKey != null) {
                        created++;
                        log.debug("Created xdock SO {} from receipt {}", orderKey, request.getReceiptKey());
                    }
                } catch (Exception e) {
                    log.warn("XDock SO creation failed for receipt {}: {}",
                        request.getReceiptKey(), e.getMessage());
                }
            }

            if (created > 0) {
                log.info("XDockCreateSO complete: {} orders created", created);
            }

        } catch (Exception e) {
            log.error("XDockCreateSO job failed: {}", e.getMessage(), e);
        }
    }

    private List<XDockSORequest> findXDockSORequests() {
        return jdbcTemplate.query(
            """
            SELECT r.receiptkey, r.storerkey, r.whseid, r.xdockdestination,
                   r.xdockcustomer, r.expectedshipdate
            FROM dbo.RECEIPT r
            WHERE r.type = 'XDOCK'
            AND r.status IN ('5', '9')
            AND r.xdockdestination IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM dbo.ORDERS o WHERE o.externorderkey = r.receiptkey)
            ORDER BY r.expectedshipdate
            """,
            (rs, rowNum) -> XDockSORequest.builder()
                .receiptKey(rs.getString("receiptkey"))
                .storerKey(rs.getString("storerkey"))
                .facility(rs.getString("whseid"))
                .destination(rs.getString("xdockdestination"))
                .customer(rs.getString("xdockcustomer"))
                .shipDate(rs.getDate("expectedshipdate"))
                .build()
        );
    }

    private String createXDockSalesOrder(XDockSORequest request) {
        String orderKey = keyGeneratorService.generateKey("ORDERS");

        // Create order header
        jdbcTemplate.update(
            """
            INSERT INTO dbo.ORDERS
            (orderkey, storerkey, externorderkey, ordertype, status, whseid,
             consigneekey, deliverydate, xdockflag, adddate, addwho)
            VALUES (?, ?, ?, 'XDOCK', '0', ?, ?, ?, '1', GETDATE(), 'XDOCKJOB')
            """,
            orderKey,
            request.getStorerKey(),
            request.getReceiptKey(),
            request.getFacility(),
            request.getCustomer(),
            request.getShipDate()
        );

        // Create order details from receipt details
        jdbcTemplate.update(
            """
            INSERT INTO dbo.ORDERDETAIL
            (orderkey, orderlinenumber, storerkey, sku, qtyordered, openqty,
             packkey, uom, status, adddate, addwho)
            SELECT ?, rd.receiptlinenumber, rd.storerkey, rd.sku, rd.qtyreceived,
                   rd.qtyreceived, rd.packkey, rd.uom, '0', GETDATE(), 'XDOCKJOB'
            FROM dbo.RECEIPTDETAIL rd
            WHERE rd.receiptkey = ?
            AND rd.qtyreceived > 0
            """,
            orderKey, request.getReceiptKey()
        );

        // Link receipt to order
        jdbcTemplate.update(
            """
            UPDATE dbo.RECEIPT
            SET xdockorderkey = ?
            WHERE receiptkey = ?
            """,
            orderKey, request.getReceiptKey()
        );

        return orderKey;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    private static class OrderLine {
        private String orderKey;
        private int orderLineNumber;
        private String storerKey;
        private String sku;
        private BigDecimal openQty;
        private String packKey;
        private int priority;
        private String facility;
    }

    @lombok.Data
    @lombok.Builder
    private static class InventoryAvailable {
        private String lotxlocxidKey;
        private String location;
        private String id;
        private String lottable01;
        private String lottable02;
        private BigDecimal availableQty;
    }

    @lombok.Data
    @lombok.Builder
    private static class XDockReceipt {
        private String receiptKey;
        private int lineNumber;
        private String storerKey;
        private String sku;
        private BigDecimal quantity;
        private String lotxlocxidKey;
        private String location;
        private String id;
    }

    @lombok.Data
    @lombok.Builder
    private static class XDockOrder {
        private String orderKey;
        private int orderLineNumber;
        private BigDecimal openQty;
    }

    @lombok.Data
    @lombok.Builder
    private static class XDockSORequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String destination;
        private String customer;
        private java.sql.Date shipDate;
    }
}
