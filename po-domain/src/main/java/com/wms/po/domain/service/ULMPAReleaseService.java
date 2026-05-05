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
import java.util.List;
import java.util.Map;

/**
 * ULM (Unit Load Management) Putaway Release Service.
 *
 * Replaces: ispPARL06 (450 LOC)
 * Region: Malaysia (MY)
 *
 * Handles ULM-specific putaway task release:
 * - Pallet-level putaway (full pallets to reserve)
 * - Case-level putaway (cases to pick face)
 * - Multi-zone assignment based on velocity
 * - Weight/height restrictions for racking
 * - Forklift vs reach truck assignment
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ULMPAReleaseService {

    private final JdbcTemplate jdbcTemplate;

    // ULM Zone types
    private static final String ZONE_RESERVE = "RESERVE";
    private static final String ZONE_FORWARD = "FORWARD";
    private static final String ZONE_BULK = "BULK";

    // Equipment types
    private static final String EQUIP_FORKLIFT = "FL";
    private static final String EQUIP_REACH = "RT";
    private static final String EQUIP_MANUAL = "MN";

    // Weight thresholds (kg)
    private static final BigDecimal FORKLIFT_WEIGHT_THRESHOLD = new BigDecimal("50");
    private static final BigDecimal HIGHBAY_MAX_WEIGHT = new BigDecimal("1000");

    /**
     * Release putaway tasks for ULM facility.
     *
     * @param releaseRequest Release request parameters
     * @return Release result
     */
    @Transactional
    public ULMReleaseResult releasePutawayTasks(ULMReleaseRequest releaseRequest) {
        log.info("ULM PA Release for receipt: {}, storerKey: {}",
            releaseRequest.getReceiptKey(), releaseRequest.getStorerKey());

        ULMReleaseResult result = ULMReleaseResult.builder()
            .receiptKey(releaseRequest.getReceiptKey())
            .releasedTasks(new ArrayList<>())
            .build();

        try {
            // 1. Get pending inventory from receipt
            List<Map<String, Object>> pendingInventory = getPendingInventory(releaseRequest);

            if (pendingInventory.isEmpty()) {
                result.setSuccess(true);
                result.setMessage("No pending inventory to release");
                return result;
            }

            log.debug("Found {} pending inventory records", pendingInventory.size());

            // 2. Process each inventory record
            for (Map<String, Object> inv : pendingInventory) {
                try {
                    ULMTaskDetail taskDetail = processInventoryForPutaway(releaseRequest, inv);
                    if (taskDetail != null) {
                        result.getReleasedTasks().add(taskDetail);
                    }
                } catch (Exception e) {
                    log.warn("Failed to process inventory {}: {}", inv.get("lotxlocxidkey"), e.getMessage());
                    result.getWarnings().add("Inventory " + inv.get("lotxlocxidkey") + ": " + e.getMessage());
                }
            }

            result.setSuccess(true);
            result.setTaskCount(result.getReleasedTasks().size());
            result.setMessage("Released " + result.getTaskCount() + " ULM putaway tasks");

            log.info("ULM Release complete: {} tasks released, {} warnings",
                result.getTaskCount(), result.getWarnings().size());

        } catch (Exception e) {
            log.error("ULM Release failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("ULM Release failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Process single inventory record for ULM putaway.
     */
    private ULMTaskDetail processInventoryForPutaway(ULMReleaseRequest request, Map<String, Object> inv) {
        String lotxlocxidKey = (String) inv.get("lotxlocxidkey");
        String sku = (String) inv.get("sku");
        String fromLoc = (String) inv.get("loc");
        String fromId = (String) inv.get("id");
        BigDecimal qty = (BigDecimal) inv.get("qty");
        BigDecimal grossWeight = getGrossWeight(inv);

        // 1. Determine putaway type (pallet vs case)
        ULMPutawayType putawayType = determinePutawayType(request, inv);

        // 2. Determine target zone based on SKU velocity and putaway type
        String targetZone = determineTargetZone(request, sku, putawayType, grossWeight);

        // 3. Find optimal location
        String toLoc = findOptimalLocation(request, sku, targetZone, qty, grossWeight, putawayType);

        if (toLoc == null) {
            log.warn("No suitable location found for SKU {} in zone {}", sku, targetZone);
            return null;
        }

        // 4. Determine equipment type
        String equipmentType = determineEquipmentType(grossWeight, toLoc);

        // 5. Calculate task priority
        int priority = calculateTaskPriority(request, sku, putawayType);

        // 6. Create putaway task
        String taskKey = createPutawayTask(request, inv, toLoc, equipmentType, priority, putawayType);

        return ULMTaskDetail.builder()
            .taskKey(taskKey)
            .sku(sku)
            .fromLoc(fromLoc)
            .toLoc(toLoc)
            .qty(qty)
            .equipmentType(equipmentType)
            .putawayType(putawayType)
            .priority(priority)
            .build();
    }

    /**
     * Determine if this is pallet-level or case-level putaway.
     */
    private ULMPutawayType determinePutawayType(ULMReleaseRequest request, Map<String, Object> inv) {
        String id = (String) inv.get("id");
        BigDecimal qty = (BigDecimal) inv.get("qty");

        // Check if this is a full pallet
        if (id != null && id.startsWith("PL")) {
            return ULMPutawayType.PALLET;
        }

        // Check if quantity indicates full pallet
        String sku = (String) inv.get("sku");
        BigDecimal palletQty = getPalletQuantity(request.getStorerKey(), sku);
        if (palletQty != null && qty.compareTo(palletQty) >= 0) {
            return ULMPutawayType.PALLET;
        }

        // Check case quantity
        BigDecimal caseQty = getCaseQuantity(request.getStorerKey(), sku);
        if (caseQty != null && qty.compareTo(caseQty) >= 0) {
            return ULMPutawayType.CASE;
        }

        return ULMPutawayType.EACH;
    }

    /**
     * Determine target zone based on SKU velocity and putaway type.
     */
    private String determineTargetZone(ULMReleaseRequest request, String sku,
                                        ULMPutawayType putawayType, BigDecimal weight) {
        // Get SKU velocity class (A/B/C/D)
        String velocityClass = getSkuVelocity(request.getStorerKey(), sku);

        // Heavy items go to bulk/floor
        if (weight.compareTo(HIGHBAY_MAX_WEIGHT) > 0) {
            return ZONE_BULK;
        }

        switch (putawayType) {
            case PALLET:
                // High velocity pallets go to forward, others to reserve
                if ("A".equals(velocityClass) || "B".equals(velocityClass)) {
                    return ZONE_FORWARD;
                }
                return ZONE_RESERVE;

            case CASE:
                // Cases typically go to forward pick locations
                return ZONE_FORWARD;

            case EACH:
                // Eaches go to forward pick face
                return ZONE_FORWARD;

            default:
                return ZONE_RESERVE;
        }
    }

    /**
     * Find optimal location within zone.
     */
    private String findOptimalLocation(ULMReleaseRequest request, String sku, String zone,
                                        BigDecimal qty, BigDecimal weight, ULMPutawayType putawayType) {
        try {
            // Build location query based on zone and putaway type
            String sql = buildLocationQuery(zone, putawayType);

            List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                sql,
                request.getStorerKey(),
                zone,
                weight,
                qty
            );

            // Find first available location
            for (Map<String, Object> loc : candidates) {
                String locId = (String) loc.get("loc");
                if (isLocationAvailable(locId, sku, qty)) {
                    return locId;
                }
            }

            // Fallback: any available location in zone
            return findAnyAvailableLocation(request.getStorerKey(), zone);

        } catch (Exception e) {
            log.warn("Location search failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildLocationQuery(String zone, ULMPutawayType putawayType) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT l.loc, l.locationtype, l.maxweight, l.maxcube ");
        sql.append("FROM dbo.LOC l ");
        sql.append("WHERE l.storerkey = ? ");
        sql.append("AND l.putawayzone = ? ");
        sql.append("AND l.maxweight >= ? ");
        sql.append("AND l.status = '0' ");
        sql.append("AND l.locationflag NOT IN ('H', 'D') ");

        // Add putaway type specific conditions
        if (putawayType == ULMPutawayType.PALLET) {
            sql.append("AND l.locationtype IN ('RESERVE', 'BULK', 'PALLET') ");
        } else if (putawayType == ULMPutawayType.CASE) {
            sql.append("AND l.locationtype IN ('FORWARD', 'CASE', 'PICK') ");
        }

        sql.append("ORDER BY l.logicallocnum ");
        sql.append("OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY");

        return sql.toString();
    }

    /**
     * Determine equipment type based on weight and location.
     */
    private String determineEquipmentType(BigDecimal weight, String location) {
        // Check location height
        int level = getLocationLevel(location);

        if (level > 3) {
            // High locations need reach truck
            return EQUIP_REACH;
        }

        if (weight.compareTo(FORKLIFT_WEIGHT_THRESHOLD) > 0) {
            // Heavy loads need forklift
            return EQUIP_FORKLIFT;
        }

        // Light loads at low levels can be manual
        return EQUIP_MANUAL;
    }

    /**
     * Calculate task priority based on various factors.
     */
    private int calculateTaskPriority(ULMReleaseRequest request, String sku, ULMPutawayType putawayType) {
        int basePriority = 50;

        // Pallet putaway is higher priority (get staging clear)
        if (putawayType == ULMPutawayType.PALLET) {
            basePriority -= 10;
        }

        // High velocity SKUs get higher priority
        String velocity = getSkuVelocity(request.getStorerKey(), sku);
        if ("A".equals(velocity)) {
            basePriority -= 20;
        } else if ("B".equals(velocity)) {
            basePriority -= 10;
        }

        return Math.max(1, Math.min(99, basePriority));
    }

    /**
     * Create putaway task record.
     */
    private String createPutawayTask(ULMReleaseRequest request, Map<String, Object> inv,
                                      String toLoc, String equipment, int priority,
                                      ULMPutawayType putawayType) {
        String taskKey = generateTaskKey();
        String lotxlocxidKey = (String) inv.get("lotxlocxidkey");
        String sku = (String) inv.get("sku");
        String fromLoc = (String) inv.get("loc");
        String fromId = (String) inv.get("id");
        BigDecimal qty = (BigDecimal) inv.get("qty");
        String lot = (String) inv.get("lot");

        jdbcTemplate.update(
            "INSERT INTO dbo.TASKDETAIL (taskdetailkey, whseid, storerkey, sku, lot, " +
            "fromloc, fromid, toloc, qty, tasktype, status, priority, " +
            "equipmenttype, sourcekey, sourcetype, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PA', '0', ?, ?, ?, 'RECEIPT', GETDATE(), ?)",
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            sku,
            lot,
            fromLoc,
            fromId,
            toLoc,
            qty,
            priority,
            equipment,
            request.getReceiptKey(),
            request.getUserId()
        );

        // Update inventory to "in putaway" status
        jdbcTemplate.update(
            "UPDATE dbo.LOTXLOCXID SET status = '7' WHERE lotxlocxidkey = ?",
            lotxlocxidKey
        );

        log.debug("Created ULM task {} for SKU {} from {} to {}", taskKey, sku, fromLoc, toLoc);
        return taskKey;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> getPendingInventory(ULMReleaseRequest request) {
        return jdbcTemplate.queryForList(
            "SELECT lx.lotxlocxidkey, lx.storerkey, lx.sku, lx.lot, lx.loc, lx.id, lx.qty, " +
            "ISNULL(s.stdgrosswgt * lx.qty, 0) as grossweight " +
            "FROM dbo.LOTXLOCXID lx " +
            "JOIN dbo.SKU s ON lx.storerkey = s.storerkey AND lx.sku = s.sku " +
            "WHERE lx.sourcekey = ? AND lx.sourcetype = 'RECEIPT' " +
            "AND lx.status = '0' AND lx.qty > 0",
            request.getReceiptKey()
        );
    }

    private BigDecimal getGrossWeight(Map<String, Object> inv) {
        Object weight = inv.get("grossweight");
        if (weight instanceof BigDecimal) {
            return (BigDecimal) weight;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal getPalletQuantity(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT p.qty FROM dbo.PACK p " +
                "JOIN dbo.SKU s ON s.packkey = p.packkey " +
                "WHERE s.storerkey = ? AND s.sku = ? AND p.uom = 'PL'",
                BigDecimal.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getCaseQuantity(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT p.qty FROM dbo.PACK p " +
                "JOIN dbo.SKU s ON s.packkey = p.packkey " +
                "WHERE s.storerkey = ? AND s.sku = ? AND p.uom = 'CS'",
                BigDecimal.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getSkuVelocity(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT abcvelocity FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return "C"; // Default to C velocity
        }
    }

    private boolean isLocationAvailable(String loc, String sku, BigDecimal qty) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.LOTXLOCXID WHERE loc = ? AND qty > 0",
                Integer.class,
                loc
            );
            return count == null || count == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String findAnyAvailableLocation(String storerKey, String zone) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT TOP 1 loc FROM dbo.LOC " +
                "WHERE storerkey = ? AND putawayzone = ? AND status = '0' " +
                "AND NOT EXISTS (SELECT 1 FROM dbo.LOTXLOCXID WHERE loc = LOC.loc AND qty > 0) " +
                "ORDER BY logicallocnum",
                String.class,
                storerKey, zone
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int getLocationLevel(String location) {
        try {
            // Typical location format: AISLE-BAY-LEVEL (e.g., A01-01-03)
            if (location != null && location.contains("-")) {
                String[] parts = location.split("-");
                if (parts.length >= 3) {
                    return Integer.parseInt(parts[2]);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 1;
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

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    public enum ULMPutawayType {
        PALLET, CASE, EACH
    }

    @Data
    @Builder
    public static class ULMReleaseRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
        private boolean prioritizePallets;
        private String targetZone;
    }

    @Data
    @Builder
    public static class ULMReleaseResult {
        private boolean success;
        private String receiptKey;
        private int taskCount;
        private String message;
        @Builder.Default
        private List<ULMTaskDetail> releasedTasks = new ArrayList<>();
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @Builder
    public static class ULMTaskDetail {
        private String taskKey;
        private String sku;
        private String fromLoc;
        private String toLoc;
        private BigDecimal qty;
        private String equipmentType;
        private ULMPutawayType putawayType;
        private int priority;
    }
}
