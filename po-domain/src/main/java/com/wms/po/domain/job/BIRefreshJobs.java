package com.wms.po.domain.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * BI (Business Intelligence) Refresh Jobs.
 *
 * Replaces: JOB-031 - BI Refresh Jobs (6 total)
 * - RefreshDailyMetrics: Aggregate daily KPIs
 * - RefreshInventorySnapshot: Daily inventory position
 * - RefreshReceiptMetrics: Receipt processing metrics
 * - RefreshOrderMetrics: Order fulfillment metrics
 * - RefreshProductivityMetrics: User/shift productivity
 * - RefreshStorerDashboard: Per-storer dashboard data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BIRefreshJobs {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Daily BI refresh - runs at 1 AM.
     */
    @Scheduled(cron = "${wms.bi.daily.cron:0 0 1 * * ?}")
    public void runDailyBIRefresh() {
        log.info("Starting daily BI refresh");
        long startTime = System.currentTimeMillis();

        try {
            refreshDailyMetrics();
            refreshInventorySnapshot();
            refreshReceiptMetrics();
            refreshOrderMetrics();
            refreshProductivityMetrics();
            refreshStorerDashboard();

            log.info("Daily BI refresh complete in {}ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Daily BI refresh failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Hourly metrics refresh - for real-time dashboards.
     */
    @Scheduled(cron = "${wms.bi.hourly.cron:0 0 * * * ?}")
    public void runHourlyBIRefresh() {
        log.debug("Starting hourly BI refresh");

        try {
            refreshRealtimeMetrics();
        } catch (Exception e) {
            log.error("Hourly BI refresh failed: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Daily Metrics
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Refresh daily KPI metrics.
     */
    @Transactional
    public void refreshDailyMetrics() {
        log.debug("Refreshing daily metrics");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            // Check if metrics already exist for yesterday
            Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.bi_daily_metrics WHERE metricdate = ?",
                Integer.class,
                yesterday
            );

            if (existing != null && existing > 0) {
                log.debug("Daily metrics already exist for {}", yesterday);
                return;
            }

            // Insert daily metrics
            jdbcTemplate.update(
                "INSERT INTO dbo.bi_daily_metrics " +
                "(metricdate, receipts_received, receipts_finalized, units_received, " +
                "orders_created, orders_shipped, units_shipped, picks_completed, " +
                "putaways_completed, adddate) " +
                "SELECT CAST(? AS DATE), " +
                "(SELECT COUNT(*) FROM dbo.receipt WHERE CAST(adddate AS DATE) = ?), " +
                "(SELECT COUNT(*) FROM dbo.receipt WHERE status = '15' AND CAST(editdate AS DATE) = ?), " +
                "(SELECT ISNULL(SUM(qtyreceived), 0) FROM dbo.receiptdetail WHERE CAST(adddate AS DATE) = ?), " +
                "(SELECT COUNT(*) FROM dbo.orders WHERE CAST(adddate AS DATE) = ?), " +
                "(SELECT COUNT(*) FROM dbo.orders WHERE status = '95' AND CAST(editdate AS DATE) = ?), " +
                "(SELECT ISNULL(SUM(shippedqty), 0) FROM dbo.orderdetail WHERE CAST(editdate AS DATE) = ?), " +
                "(SELECT COUNT(*) FROM dbo.taskdetail WHERE tasktype = 'PK' AND status = '9' AND CAST(editdate AS DATE) = ?), " +
                "(SELECT COUNT(*) FROM dbo.taskdetail WHERE tasktype = 'PA' AND status = '9' AND CAST(editdate AS DATE) = ?), " +
                "GETDATE()",
                yesterday, yesterday, yesterday, yesterday, yesterday,
                yesterday, yesterday, yesterday, yesterday
            );

            log.info("Daily metrics refreshed for {}", yesterday);

        } catch (Exception e) {
            log.warn("Could not refresh daily metrics: {}", e.getMessage());
        }
    }

    /**
     * Refresh inventory snapshot.
     */
    @Transactional
    public void refreshInventorySnapshot() {
        log.debug("Refreshing inventory snapshot");
        LocalDate today = LocalDate.now();

        try {
            // Clear today's snapshot if exists
            jdbcTemplate.update(
                "DELETE FROM dbo.bi_inventory_snapshot WHERE snapshotdate = ?",
                today
            );

            // Insert current inventory snapshot
            jdbcTemplate.update(
                "INSERT INTO dbo.bi_inventory_snapshot " +
                "(snapshotdate, storerkey, sku, totalqty, availableqty, allocatedqty, " +
                "pickedqty, locationcount, adddate) " +
                "SELECT ?, storerkey, sku, SUM(qty), " +
                "SUM(qty - qtyallocated - qtypicked), SUM(qtyallocated), SUM(qtypicked), " +
                "COUNT(DISTINCT loc), GETDATE() " +
                "FROM dbo.lotxlocxid " +
                "GROUP BY storerkey, sku",
                today
            );

            log.info("Inventory snapshot refreshed for {}", today);

        } catch (Exception e) {
            log.warn("Could not refresh inventory snapshot: {}", e.getMessage());
        }
    }

    /**
     * Refresh receipt processing metrics.
     */
    @Transactional
    public void refreshReceiptMetrics() {
        log.debug("Refreshing receipt metrics");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            jdbcTemplate.update(
                "DELETE FROM dbo.bi_receipt_metrics WHERE metricdate = ?",
                yesterday
            );

            jdbcTemplate.update(
                "INSERT INTO dbo.bi_receipt_metrics " +
                "(metricdate, storerkey, receipts_total, receipts_finalized, " +
                "avg_finalize_time_min, units_total, lines_total, adddate) " +
                "SELECT ?, r.storerkey, COUNT(*), " +
                "SUM(CASE WHEN r.status = '15' THEN 1 ELSE 0 END), " +
                "AVG(CASE WHEN r.status = '15' THEN DATEDIFF(minute, r.adddate, r.editdate) END), " +
                "SUM(rd.totalqty), COUNT(DISTINCT rd.receiptdetailkey), GETDATE() " +
                "FROM dbo.receipt r " +
                "LEFT JOIN (SELECT receiptkey, SUM(qtyreceived) as totalqty, receiptdetailkey " +
                "           FROM dbo.receiptdetail GROUP BY receiptkey, receiptdetailkey) rd " +
                "ON r.receiptkey = rd.receiptkey " +
                "WHERE CAST(r.adddate AS DATE) = ? " +
                "GROUP BY r.storerkey",
                yesterday, yesterday
            );

            log.info("Receipt metrics refreshed for {}", yesterday);

        } catch (Exception e) {
            log.warn("Could not refresh receipt metrics: {}", e.getMessage());
        }
    }

    /**
     * Refresh order fulfillment metrics.
     */
    @Transactional
    public void refreshOrderMetrics() {
        log.debug("Refreshing order metrics");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            jdbcTemplate.update(
                "DELETE FROM dbo.bi_order_metrics WHERE metricdate = ?",
                yesterday
            );

            jdbcTemplate.update(
                "INSERT INTO dbo.bi_order_metrics " +
                "(metricdate, storerkey, orders_total, orders_shipped, orders_partial, " +
                "avg_ship_time_min, units_ordered, units_shipped, lines_total, adddate) " +
                "SELECT ?, o.storerkey, COUNT(*), " +
                "SUM(CASE WHEN o.status = '95' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN o.status = '55' THEN 1 ELSE 0 END), " +
                "AVG(CASE WHEN o.status = '95' THEN DATEDIFF(minute, o.adddate, o.actualshipdate) END), " +
                "SUM(od.orderedqty), SUM(od.shippedqty), COUNT(DISTINCT od.orderdetailkey), GETDATE() " +
                "FROM dbo.orders o " +
                "LEFT JOIN (SELECT orderkey, SUM(openqty) as orderedqty, SUM(shippedqty) as shippedqty, orderdetailkey " +
                "           FROM dbo.orderdetail GROUP BY orderkey, orderdetailkey) od " +
                "ON o.orderkey = od.orderkey " +
                "WHERE CAST(o.adddate AS DATE) = ? " +
                "GROUP BY o.storerkey",
                yesterday, yesterday
            );

            log.info("Order metrics refreshed for {}", yesterday);

        } catch (Exception e) {
            log.warn("Could not refresh order metrics: {}", e.getMessage());
        }
    }

    /**
     * Refresh productivity metrics.
     */
    @Transactional
    public void refreshProductivityMetrics() {
        log.debug("Refreshing productivity metrics");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            jdbcTemplate.update(
                "DELETE FROM dbo.bi_productivity_metrics WHERE metricdate = ?",
                yesterday
            );

            jdbcTemplate.update(
                "INSERT INTO dbo.bi_productivity_metrics " +
                "(metricdate, userid, picks_completed, putaways_completed, " +
                "units_picked, units_putaway, adddate) " +
                "SELECT ?, editwho, " +
                "SUM(CASE WHEN tasktype = 'PK' AND status = '9' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN tasktype = 'PA' AND status = '9' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN tasktype = 'PK' AND status = '9' THEN qty ELSE 0 END), " +
                "SUM(CASE WHEN tasktype = 'PA' AND status = '9' THEN qty ELSE 0 END), " +
                "GETDATE() " +
                "FROM dbo.taskdetail " +
                "WHERE status = '9' AND CAST(editdate AS DATE) = ? " +
                "GROUP BY editwho",
                yesterday, yesterday
            );

            log.info("Productivity metrics refreshed for {}", yesterday);

        } catch (Exception e) {
            log.warn("Could not refresh productivity metrics: {}", e.getMessage());
        }
    }

    /**
     * Refresh storer dashboard data.
     */
    @Transactional
    public void refreshStorerDashboard() {
        log.debug("Refreshing storer dashboard");

        try {
            // Truncate and reload dashboard data
            jdbcTemplate.update("TRUNCATE TABLE dbo.bi_storer_dashboard");

            jdbcTemplate.update(
                "INSERT INTO dbo.bi_storer_dashboard " +
                "(storerkey, open_receipts, pending_putaway, open_orders, " +
                "pending_picks, inventory_value, sku_count, location_utilization, adddate) " +
                "SELECT s.storerkey, " +
                "ISNULL((SELECT COUNT(*) FROM dbo.receipt WHERE storerkey = s.storerkey AND status NOT IN ('15', 'V')), 0), " +
                "ISNULL((SELECT COUNT(*) FROM dbo.taskdetail WHERE storerkey = s.storerkey AND tasktype = 'PA' AND status < '9'), 0), " +
                "ISNULL((SELECT COUNT(*) FROM dbo.orders WHERE storerkey = s.storerkey AND status NOT IN ('95', '99')), 0), " +
                "ISNULL((SELECT COUNT(*) FROM dbo.taskdetail WHERE storerkey = s.storerkey AND tasktype = 'PK' AND status < '9'), 0), " +
                "ISNULL((SELECT SUM(qty * ISNULL(sk.stdcost, 0)) FROM dbo.lotxlocxid l " +
                "        JOIN dbo.sku sk ON l.storerkey = sk.storerkey AND l.sku = sk.sku " +
                "        WHERE l.storerkey = s.storerkey), 0), " +
                "ISNULL((SELECT COUNT(DISTINCT sku) FROM dbo.lotxlocxid WHERE storerkey = s.storerkey AND qty > 0), 0), " +
                "ISNULL((SELECT CAST(COUNT(DISTINCT loc) AS FLOAT) / NULLIF((SELECT COUNT(*) FROM dbo.loc), 0) * 100 " +
                "        FROM dbo.lotxlocxid WHERE storerkey = s.storerkey AND qty > 0), 0), " +
                "GETDATE() " +
                "FROM dbo.storer s WHERE s.type = '1'"
            );

            log.info("Storer dashboard refreshed");

        } catch (Exception e) {
            log.warn("Could not refresh storer dashboard: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Realtime Metrics (Hourly)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Refresh real-time metrics for dashboards.
     */
    @Transactional
    public void refreshRealtimeMetrics() {
        log.debug("Refreshing realtime metrics");

        try {
            // Update current hour metrics
            LocalDateTime now = LocalDateTime.now();

            jdbcTemplate.update(
                "MERGE INTO dbo.bi_realtime_metrics AS target " +
                "USING (SELECT " +
                "(SELECT COUNT(*) FROM dbo.receipt WHERE status NOT IN ('15', 'V')) as open_receipts, " +
                "(SELECT COUNT(*) FROM dbo.orders WHERE status NOT IN ('95', '99')) as open_orders, " +
                "(SELECT COUNT(*) FROM dbo.taskdetail WHERE status < '9') as pending_tasks, " +
                "(SELECT COUNT(*) FROM dbo.taskdetail WHERE status = '9' AND CAST(editdate AS DATE) = CAST(GETDATE() AS DATE)) as completed_tasks " +
                ") AS source " +
                "ON 1=1 " +
                "WHEN MATCHED THEN UPDATE SET " +
                "open_receipts = source.open_receipts, " +
                "open_orders = source.open_orders, " +
                "pending_tasks = source.pending_tasks, " +
                "completed_tasks_today = source.completed_tasks, " +
                "last_updated = GETDATE() " +
                "WHEN NOT MATCHED THEN INSERT " +
                "(open_receipts, open_orders, pending_tasks, completed_tasks_today, last_updated) " +
                "VALUES (source.open_receipts, source.open_orders, source.pending_tasks, source.completed_tasks, GETDATE());"
            );

        } catch (Exception e) {
            log.debug("Could not refresh realtime metrics: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Manual Execution
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Run full BI refresh manually.
     */
    public void runFullRefresh() {
        runDailyBIRefresh();
        runHourlyBIRefresh();
    }

    /**
     * Get BI refresh status.
     */
    public Map<String, Object> getRefreshStatus() {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT " +
                "(SELECT MAX(metricdate) FROM dbo.bi_daily_metrics) as lastDailyRefresh, " +
                "(SELECT MAX(snapshotdate) FROM dbo.bi_inventory_snapshot) as lastInventorySnapshot, " +
                "(SELECT last_updated FROM dbo.bi_realtime_metrics) as lastRealtimeRefresh"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
