package com.wms.po.domain.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Alert Scheduled Jobs.
 *
 * Replaces: JOB-030 - Alert Jobs (4 total)
 * - CheckLowStock: Alert on inventory below reorder point
 * - CheckExpiringSKU: Alert on SKUs approaching expiry
 * - CheckStuckReceipts: Alert on receipts stuck in processing
 * - CheckOverdueShipments: Alert on shipments past expected date
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertJobs {

    private final JdbcTemplate jdbcTemplate;

    // Alert thresholds
    private static final int EXPIRY_WARNING_DAYS = 30;
    private static final int STUCK_RECEIPT_HOURS = 24;
    private static final int OVERDUE_DAYS = 1;

    /**
     * Run all alert checks - every 15 minutes.
     */
    @Scheduled(fixedDelayString = "${wms.alerts.interval:900000}")
    public void runAlertChecks() {
        log.debug("Running alert checks");

        List<Alert> alerts = new ArrayList<>();

        alerts.addAll(checkLowStock());
        alerts.addAll(checkExpiringSKU());
        alerts.addAll(checkStuckReceipts());
        alerts.addAll(checkOverdueShipments());

        if (!alerts.isEmpty()) {
            processAlerts(alerts);
        }
    }

    /**
     * Check for low stock alerts.
     *
     * Replaces: SQL Job - CheckLowStock
     */
    public List<Alert> checkLowStock() {
        List<Alert> alerts = new ArrayList<>();

        try {
            List<Map<String, Object>> lowStockItems = jdbcTemplate.queryForList(
                "SELECT s.storerkey, s.sku, s.descr, s.reorderpoint, " +
                "ISNULL(i.totalqty, 0) as onhandqty " +
                "FROM dbo.sku s " +
                "LEFT JOIN (SELECT storerkey, sku, SUM(qty - qtyallocated - qtypicked) as totalqty " +
                "           FROM dbo.lotxlocxid GROUP BY storerkey, sku) i " +
                "ON s.storerkey = i.storerkey AND s.sku = i.sku " +
                "WHERE s.reorderpoint > 0 AND ISNULL(i.totalqty, 0) <= s.reorderpoint"
            );

            for (Map<String, Object> item : lowStockItems) {
                alerts.add(new Alert(
                    AlertType.LOW_STOCK,
                    AlertSeverity.WARNING,
                    (String) item.get("storerkey"),
                    String.format("Low stock for SKU %s: %s on hand, reorder point %s",
                        item.get("sku"),
                        item.get("onhandqty"),
                        item.get("reorderpoint")),
                    Map.of("sku", item.get("sku"), "onhand", item.get("onhandqty"))
                ));
            }

        } catch (Exception e) {
            log.debug("Could not check low stock: {}", e.getMessage());
        }

        if (!alerts.isEmpty()) {
            log.info("Found {} low stock alerts", alerts.size());
        }

        return alerts;
    }

    /**
     * Check for expiring SKU alerts.
     *
     * Replaces: SQL Job - CheckExpiringSKU
     */
    public List<Alert> checkExpiringSKU() {
        List<Alert> alerts = new ArrayList<>();

        try {
            List<Map<String, Object>> expiringItems = jdbcTemplate.queryForList(
                "SELECT storerkey, sku, lot, loc, qty, lottable04 as expirydate, " +
                "DATEDIFF(day, GETDATE(), lottable04) as daystoexpiry " +
                "FROM dbo.lotxlocxid " +
                "WHERE lottable04 IS NOT NULL " +
                "AND lottable04 > GETDATE() " +
                "AND DATEDIFF(day, GETDATE(), lottable04) <= ?",
                EXPIRY_WARNING_DAYS
            );

            for (Map<String, Object> item : expiringItems) {
                Integer daysToExpiry = (Integer) item.get("daystoexpiry");
                AlertSeverity severity = daysToExpiry <= 7 ? AlertSeverity.HIGH : AlertSeverity.WARNING;

                alerts.add(new Alert(
                    AlertType.EXPIRING_INVENTORY,
                    severity,
                    (String) item.get("storerkey"),
                    String.format("SKU %s in %s expires in %d days (Lot: %s, Qty: %s)",
                        item.get("sku"),
                        item.get("loc"),
                        daysToExpiry,
                        item.get("lot"),
                        item.get("qty")),
                    Map.of("sku", item.get("sku"), "daysToExpiry", daysToExpiry)
                ));
            }

        } catch (Exception e) {
            log.debug("Could not check expiring SKU: {}", e.getMessage());
        }

        if (!alerts.isEmpty()) {
            log.info("Found {} expiring inventory alerts", alerts.size());
        }

        return alerts;
    }

    /**
     * Check for stuck receipt alerts.
     *
     * Replaces: SQL Job - CheckStuckReceipts
     */
    public List<Alert> checkStuckReceipts() {
        List<Alert> alerts = new ArrayList<>();

        try {
            List<Map<String, Object>> stuckReceipts = jdbcTemplate.queryForList(
                "SELECT receiptkey, storerkey, externreceiptkey, status, adddate, " +
                "DATEDIFF(hour, adddate, GETDATE()) as hoursstuck " +
                "FROM dbo.receipt " +
                "WHERE status IN ('0', '1', '5') " +  // New, Processing, Receiving
                "AND DATEDIFF(hour, adddate, GETDATE()) > ?",
                STUCK_RECEIPT_HOURS
            );

            for (Map<String, Object> receipt : stuckReceipts) {
                Integer hoursStuck = (Integer) receipt.get("hoursstuck");
                AlertSeverity severity = hoursStuck > 48 ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;

                alerts.add(new Alert(
                    AlertType.STUCK_RECEIPT,
                    severity,
                    (String) receipt.get("storerkey"),
                    String.format("Receipt %s stuck in status %s for %d hours",
                        receipt.get("receiptkey"),
                        receipt.get("status"),
                        hoursStuck),
                    Map.of("receiptKey", receipt.get("receiptkey"), "hoursStuck", hoursStuck)
                ));
            }

        } catch (Exception e) {
            log.debug("Could not check stuck receipts: {}", e.getMessage());
        }

        if (!alerts.isEmpty()) {
            log.info("Found {} stuck receipt alerts", alerts.size());
        }

        return alerts;
    }

    /**
     * Check for overdue shipment alerts.
     *
     * Replaces: SQL Job - CheckOverdueShipments
     */
    public List<Alert> checkOverdueShipments() {
        List<Alert> alerts = new ArrayList<>();

        try {
            List<Map<String, Object>> overdueOrders = jdbcTemplate.queryForList(
                "SELECT orderkey, storerkey, externorderkey, requestedshipdate, " +
                "DATEDIFF(day, requestedshipdate, GETDATE()) as daysoverdue " +
                "FROM dbo.orders " +
                "WHERE status NOT IN ('9', '95', '99') " +  // Not shipped/closed
                "AND requestedshipdate < DATEADD(day, -?, GETDATE())",
                OVERDUE_DAYS
            );

            for (Map<String, Object> order : overdueOrders) {
                Integer daysOverdue = (Integer) order.get("daysoverdue");
                AlertSeverity severity = daysOverdue > 3 ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;

                alerts.add(new Alert(
                    AlertType.OVERDUE_SHIPMENT,
                    severity,
                    (String) order.get("storerkey"),
                    String.format("Order %s is %d days overdue for shipment",
                        order.get("orderkey"),
                        daysOverdue),
                    Map.of("orderKey", order.get("orderkey"), "daysOverdue", daysOverdue)
                ));
            }

        } catch (Exception e) {
            log.debug("Could not check overdue shipments: {}", e.getMessage());
        }

        if (!alerts.isEmpty()) {
            log.info("Found {} overdue shipment alerts", alerts.size());
        }

        return alerts;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Alert Processing
    // ═══════════════════════════════════════════════════════════════════════

    private void processAlerts(List<Alert> alerts) {
        log.info("Processing {} alerts", alerts.size());

        for (Alert alert : alerts) {
            try {
                // Store alert in database
                storeAlert(alert);

                // Send notification for high/critical alerts
                if (alert.severity == AlertSeverity.HIGH || alert.severity == AlertSeverity.CRITICAL) {
                    sendAlertNotification(alert);
                }

            } catch (Exception e) {
                log.warn("Failed to process alert: {}", e.getMessage());
            }
        }
    }

    private void storeAlert(Alert alert) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.wmsalert (alerttype, severity, storerkey, message, " +
                "alertdata, status, adddate) VALUES (?, ?, ?, ?, ?, '0', GETDATE())",
                alert.type.name(),
                alert.severity.name(),
                alert.storerKey,
                alert.message.length() > 500 ? alert.message.substring(0, 500) : alert.message,
                alert.data != null ? alert.data.toString() : null
            );
        } catch (Exception e) {
            log.debug("Could not store alert: {}", e.getMessage());
        }
    }

    private void sendAlertNotification(Alert alert) {
        // In production, this would send email/SMS/webhook notification
        log.warn("ALERT [{}] {}: {}",
            alert.severity,
            alert.type,
            alert.message);
    }

    /**
     * Get active alerts.
     */
    public List<Map<String, Object>> getActiveAlerts(String storerKey) {
        try {
            if (storerKey != null) {
                return jdbcTemplate.queryForList(
                    "SELECT * FROM dbo.wmsalert WHERE status = '0' AND storerkey = ? " +
                    "ORDER BY adddate DESC",
                    storerKey
                );
            } else {
                return jdbcTemplate.queryForList(
                    "SELECT * FROM dbo.wmsalert WHERE status = '0' ORDER BY adddate DESC"
                );
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Acknowledge alert.
     */
    public void acknowledgeAlert(String alertKey, String userId) {
        jdbcTemplate.update(
            "UPDATE dbo.wmsalert SET status = '1', editwho = ?, editdate = GETDATE() " +
            "WHERE alertkey = ?",
            userId, alertKey
        );
    }

    /**
     * Get alert summary by type.
     */
    public Map<String, Long> getAlertSummary() {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT alerttype, COUNT(*) as count FROM dbo.wmsalert " +
                "WHERE status = '0' GROUP BY alerttype"
            );

            return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                    r -> (String) r.get("alerttype"),
                    r -> ((Number) r.get("count")).longValue()
                ));
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Alert Types
    // ═══════════════════════════════════════════════════════════════════════

    public enum AlertType {
        LOW_STOCK,
        EXPIRING_INVENTORY,
        STUCK_RECEIPT,
        OVERDUE_SHIPMENT,
        INTERFACE_ERROR,
        SYSTEM_ERROR
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        HIGH,
        CRITICAL
    }

    public record Alert(
        AlertType type,
        AlertSeverity severity,
        String storerKey,
        String message,
        Map<String, Object> data
    ) {}
}
