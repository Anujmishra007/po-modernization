package com.wms.po.domain.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nike CRW (Cross-dock Retail Warehouse) Batch Putaway Service.
 *
 * Replaces: ispBatPA02 (850 LOC)
 * Client: Nike
 *
 * Handles Nike CRW-specific batch putaway:
 * - Cross-dock flow-through processing
 * - Style-based consolidation
 * - Wave-based putaway release
 * - Pre-allocation to outbound orders
 * - Priority-based sequencing
 * - Carton-level tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NikeCRWBatchPAService {

    private final JdbcTemplate jdbcTemplate;

    // CRW Zone types
    private static final String ZONE_CROSSDOCK = "CROSSDOCK";
    private static final String ZONE_STAGING = "STAGING";
    private static final String ZONE_RESERVE = "RESERVE";
    private static final String ZONE_FORWARD = "FORWARD";

    // Putaway modes
    private static final String MODE_FLOWTHROUGH = "FLOWTHROUGH";
    private static final String MODE_STANDARD = "STANDARD";
    private static final String MODE_PREALLOCATE = "PREALLOCATE";

    // Priority levels
    private static final int PRIORITY_URGENT = 10;
    private static final int PRIORITY_HIGH = 30;
    private static final int PRIORITY_NORMAL = 50;
    private static final int PRIORITY_LOW = 70;

    /**
     * Process batch putaway for Nike CRW facility.
     *
     * @param request Batch putaway request
     * @return Batch result
     */
    @Transactional
    public CRWBatchResult processBatchPutaway(CRWBatchRequest request) {
        log.info("Nike CRW Batch PA: facility={}, receiptKey={}, mode={}",
            request.getFacility(), request.getReceiptKey(), request.getMode());

        CRWBatchResult result = CRWBatchResult.builder()
            .receiptKey(request.getReceiptKey())
            .tasks(new ArrayList<>())
            .build();

        try {
            // 1. Get pending inventory grouped by style
            Map<String, List<PendingInventory>> styleGroups = getStyleGroupedInventory(request);

            if (styleGroups.isEmpty()) {
                result.setSuccess(true);
                result.setMessage("No pending inventory for batch putaway");
                return result;
            }

            log.debug("Found {} style groups with {} total cartons",
                styleGroups.size(),
                styleGroups.values().stream().mapToInt(List::size).sum());

            // 2. Check for pre-allocation opportunities
            Map<String, AllocationTarget> preAllocations = new HashMap<>();
            if (MODE_PREALLOCATE.equals(request.getMode()) || MODE_FLOWTHROUGH.equals(request.getMode())) {
                preAllocations = findPreAllocationTargets(request, styleGroups.keySet());
            }

            // 3. Process each style group
            for (Map.Entry<String, List<PendingInventory>> entry : styleGroups.entrySet()) {
                String styleCode = entry.getKey();
                List<PendingInventory> cartons = entry.getValue();

                try {
                    processStyleGroup(request, styleCode, cartons, preAllocations, result);
                } catch (Exception e) {
                    log.warn("Failed to process style {}: {}", styleCode, e.getMessage());
                    result.getWarnings().add("Style " + styleCode + ": " + e.getMessage());
                }
            }

            // 4. Create wave if configured
            if (request.isCreateWave() && !result.getTasks().isEmpty()) {
                String waveKey = createPutawayWave(request, result.getTasks());
                result.setWaveKey(waveKey);
            }

            result.setSuccess(true);
            result.setTaskCount(result.getTasks().size());
            result.setMessage("Created " + result.getTaskCount() + " putaway tasks");

            log.info("Nike CRW Batch complete: {} tasks, {} warnings",
                result.getTaskCount(), result.getWarnings().size());

        } catch (Exception e) {
            log.error("Nike CRW Batch failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("Batch putaway failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Process a single style group.
     */
    private void processStyleGroup(CRWBatchRequest request, String styleCode,
                                    List<PendingInventory> cartons,
                                    Map<String, AllocationTarget> preAllocations,
                                    CRWBatchResult result) {
        log.debug("Processing style {} with {} cartons", styleCode, cartons.size());

        // Check for pre-allocation target
        AllocationTarget target = preAllocations.get(styleCode);
        String putawayMode = determinePutawayMode(request, target);

        // Determine target zone based on mode
        String targetZone = determineTargetZone(putawayMode, request);

        // Calculate priority
        int priority = calculatePriority(request, styleCode, target);

        // Process each carton
        for (PendingInventory carton : cartons) {
            CRWTask task = processCarton(request, carton, targetZone, putawayMode, priority, target);
            if (task != null) {
                result.getTasks().add(task);
            }
        }
    }

    /**
     * Process a single carton for putaway.
     */
    private CRWTask processCarton(CRWBatchRequest request, PendingInventory carton,
                                   String targetZone, String mode, int priority,
                                   AllocationTarget target) {
        // Find optimal location
        String toLoc = findCartonLocation(request, carton, targetZone, mode);
        if (toLoc == null) {
            log.warn("No location found for carton {} in zone {}", carton.getId(), targetZone);
            return null;
        }

        // Create task
        String taskKey = createPutawayTask(request, carton, toLoc, priority, mode, target);

        return CRWTask.builder()
            .taskKey(taskKey)
            .sku(carton.getSku())
            .cartonId(carton.getId())
            .fromLoc(carton.getLoc())
            .toLoc(toLoc)
            .qty(carton.getQty())
            .priority(priority)
            .mode(mode)
            .allocationKey(target != null ? target.getOrderKey() : null)
            .build();
    }

    /**
     * Determine putaway mode based on configuration and allocation.
     */
    private String determinePutawayMode(CRWBatchRequest request, AllocationTarget target) {
        // If pre-allocated, use flow-through
        if (target != null && target.getAllocatedQty().compareTo(BigDecimal.ZERO) > 0) {
            return MODE_FLOWTHROUGH;
        }

        // Use request mode or default to standard
        return request.getMode() != null ? request.getMode() : MODE_STANDARD;
    }

    /**
     * Determine target zone based on putaway mode.
     */
    private String determineTargetZone(String mode, CRWBatchRequest request) {
        switch (mode) {
            case MODE_FLOWTHROUGH:
                return ZONE_CROSSDOCK;
            case MODE_PREALLOCATE:
                return ZONE_STAGING;
            default:
                // Check for configured zone override
                String configZone = getConfiguredZone(request.getStorerKey());
                return configZone != null ? configZone : ZONE_RESERVE;
        }
    }

    /**
     * Calculate task priority.
     */
    private int calculatePriority(CRWBatchRequest request, String styleCode, AllocationTarget target) {
        int priority = PRIORITY_NORMAL;

        // Pre-allocated gets higher priority
        if (target != null) {
            priority = PRIORITY_HIGH;

            // Rush orders get urgent priority
            if (target.isRush()) {
                priority = PRIORITY_URGENT;
            }
        }

        // New launch items get higher priority
        if (isNewLaunch(request.getStorerKey(), styleCode)) {
            priority = Math.min(priority, PRIORITY_HIGH);
        }

        return priority;
    }

    /**
     * Find optimal location for carton.
     */
    private String findCartonLocation(CRWBatchRequest request, PendingInventory carton,
                                       String zone, String mode) {
        try {
            // For flow-through, find staging lane
            if (MODE_FLOWTHROUGH.equals(mode)) {
                return findStagingLane(request, carton);
            }

            // For standard putaway, find available location
            return jdbcTemplate.queryForObject(
                "SELECT TOP 1 l.loc FROM dbo.LOC l " +
                "WHERE l.storerkey = ? AND l.putawayzone = ? " +
                "AND l.status = '0' AND l.locationflag NOT IN ('H', 'D') " +
                "AND NOT EXISTS (SELECT 1 FROM dbo.LOTXLOCXID lx WHERE lx.loc = l.loc AND lx.qty > 0) " +
                "ORDER BY l.logicallocnum",
                String.class,
                request.getStorerKey(), zone
            );
        } catch (Exception e) {
            log.debug("Location search failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Find staging lane for flow-through.
     */
    private String findStagingLane(CRWBatchRequest request, PendingInventory carton) {
        try {
            // Find lane assigned to same order/wave if pre-allocated
            String orderLane = jdbcTemplate.queryForObject(
                "SELECT TOP 1 l.loc FROM dbo.LOC l " +
                "JOIN dbo.STAGINGLANE sl ON l.loc = sl.lane " +
                "WHERE sl.storerkey = ? AND sl.orderkey = ? AND sl.status = 'ACTIVE'",
                String.class,
                request.getStorerKey(), carton.getOrderKey()
            );
            if (orderLane != null) {
                return orderLane;
            }

            // Find any available staging lane
            return jdbcTemplate.queryForObject(
                "SELECT TOP 1 l.loc FROM dbo.LOC l " +
                "WHERE l.storerkey = ? AND l.locationtype = 'STAGING' " +
                "AND l.status = '0' " +
                "ORDER BY l.logicallocnum",
                String.class,
                request.getStorerKey()
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create putaway task record.
     */
    private String createPutawayTask(CRWBatchRequest request, PendingInventory carton,
                                      String toLoc, int priority, String mode,
                                      AllocationTarget target) {
        String taskKey = generateTaskKey();

        jdbcTemplate.update(
            "INSERT INTO dbo.TASKDETAIL (taskdetailkey, whseid, storerkey, sku, lot, " +
            "fromloc, fromid, toloc, qty, tasktype, status, priority, " +
            "orderkey, wavekey, sourcekey, sourcetype, susr1, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PA', '0', ?, ?, ?, ?, 'CRW', ?, GETDATE(), ?)",
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            carton.getSku(),
            carton.getLot(),
            carton.getLoc(),
            carton.getId(),
            toLoc,
            carton.getQty(),
            priority,
            target != null ? target.getOrderKey() : null,
            target != null ? target.getWaveKey() : null,
            request.getReceiptKey(),
            mode,
            request.getUserId()
        );

        // Update inventory status
        jdbcTemplate.update(
            "UPDATE dbo.LOTXLOCXID SET status = '7' WHERE lotxlocxidkey = ?",
            carton.getLotxlocxidKey()
        );

        return taskKey;
    }

    /**
     * Create putaway wave for batch tasks.
     */
    private String createPutawayWave(CRWBatchRequest request, List<CRWTask> tasks) {
        String waveKey = generateWaveKey();

        // Create wave header
        jdbcTemplate.update(
            "INSERT INTO dbo.WAVEDETAIL (wavekey, storerkey, wavetype, status, " +
            "taskcount, adddate, addwho) " +
            "VALUES (?, ?, 'PA', '0', ?, GETDATE(), ?)",
            waveKey,
            request.getStorerKey(),
            tasks.size(),
            request.getUserId()
        );

        // Update tasks with wave key
        for (CRWTask task : tasks) {
            jdbcTemplate.update(
                "UPDATE dbo.TASKDETAIL SET wavekey = ? WHERE taskdetailkey = ?",
                waveKey, task.getTaskKey()
            );
        }

        return waveKey;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, List<PendingInventory>> getStyleGroupedInventory(CRWBatchRequest request) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT lx.lotxlocxidkey, lx.storerkey, lx.sku, lx.lot, lx.loc, lx.id, lx.qty, " +
            "lx.lottable01 as stylecolor, " +
            "SUBSTRING(lx.lottable01, 1, 6) as stylecode, " +
            "lx.sourcekey as orderkey " +
            "FROM dbo.LOTXLOCXID lx " +
            "WHERE lx.sourcekey = ? AND lx.sourcetype = 'RECEIPT' " +
            "AND lx.status = '0' AND lx.qty > 0",
            request.getReceiptKey()
        );

        return rows.stream()
            .map(row -> PendingInventory.builder()
                .lotxlocxidKey((String) row.get("lotxlocxidkey"))
                .storerKey((String) row.get("storerkey"))
                .sku((String) row.get("sku"))
                .lot((String) row.get("lot"))
                .loc((String) row.get("loc"))
                .id((String) row.get("id"))
                .qty((BigDecimal) row.get("qty"))
                .styleColor((String) row.get("stylecolor"))
                .styleCode((String) row.get("stylecode"))
                .orderKey((String) row.get("orderkey"))
                .build())
            .collect(Collectors.groupingBy(
                inv -> inv.getStyleCode() != null ? inv.getStyleCode() : "UNKNOWN"
            ));
    }

    private Map<String, AllocationTarget> findPreAllocationTargets(CRWBatchRequest request,
                                                                     java.util.Set<String> styleCodes) {
        Map<String, AllocationTarget> targets = new HashMap<>();

        for (String styleCode : styleCodes) {
            try {
                Map<String, Object> allocation = jdbcTemplate.queryForMap(
                    "SELECT TOP 1 od.orderkey, od.wavekey, od.sku, " +
                    "SUM(od.openqty) as openqty, " +
                    "CASE WHEN o.priority <= 10 THEN 1 ELSE 0 END as rush " +
                    "FROM dbo.ORDERDETAIL od " +
                    "JOIN dbo.ORDERS o ON od.orderkey = o.orderkey " +
                    "WHERE od.storerkey = ? " +
                    "AND od.sku LIKE ? + '%' " +
                    "AND od.status IN ('0', '1') " +
                    "GROUP BY od.orderkey, od.wavekey, od.sku, o.priority " +
                    "HAVING SUM(od.openqty) > 0 " +
                    "ORDER BY o.priority, od.adddate",
                    request.getStorerKey(), styleCode
                );

                targets.put(styleCode, AllocationTarget.builder()
                    .orderKey((String) allocation.get("orderkey"))
                    .waveKey((String) allocation.get("wavekey"))
                    .allocatedQty((BigDecimal) allocation.get("openqty"))
                    .rush((Integer) allocation.get("rush") == 1)
                    .build());

            } catch (Exception e) {
                // No allocation target found
            }
        }

        return targets;
    }

    private String getConfiguredZone(String storerKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr1 FROM dbo.STORER WHERE storerkey = ? AND type = '1'",
                String.class,
                storerKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNewLaunch(String storerKey, String styleCode) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.SKU " +
                "WHERE storerkey = ? AND sku LIKE ? + '%' " +
                "AND launchdate >= DATEADD(day, -30, GETDATE())",
                Integer.class,
                storerKey, styleCode
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String generateTaskKey() {
        try {
            return jdbcTemplate.queryForObject(
                "EXEC dbo.nspg_GetKey @tablename = 'TASKDETAIL'",
                String.class
            );
        } catch (Exception e) {
            return "TSK" + System.currentTimeMillis();
        }
    }

    private String generateWaveKey() {
        try {
            return jdbcTemplate.queryForObject(
                "EXEC dbo.nspg_GetKey @tablename = 'WAVEDETAIL'",
                String.class
            );
        } catch (Exception e) {
            return "WV" + System.currentTimeMillis();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    @Builder
    public static class CRWBatchRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
        private String mode;  // FLOWTHROUGH, STANDARD, PREALLOCATE
        private boolean createWave;
        private String targetZone;
    }

    @Data
    @Builder
    public static class CRWBatchResult {
        private boolean success;
        private String receiptKey;
        private String waveKey;
        private int taskCount;
        private String message;
        @Builder.Default
        private List<CRWTask> tasks = new ArrayList<>();
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @Builder
    public static class CRWTask {
        private String taskKey;
        private String sku;
        private String cartonId;
        private String fromLoc;
        private String toLoc;
        private BigDecimal qty;
        private int priority;
        private String mode;
        private String allocationKey;
    }

    @Data
    @Builder
    private static class PendingInventory {
        private String lotxlocxidKey;
        private String storerKey;
        private String sku;
        private String lot;
        private String loc;
        private String id;
        private BigDecimal qty;
        private String styleColor;
        private String styleCode;
        private String orderKey;
    }

    @Data
    @Builder
    private static class AllocationTarget {
        private String orderKey;
        private String waveKey;
        private BigDecimal allocatedQty;
        private boolean rush;
    }
}
