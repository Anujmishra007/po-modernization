package com.wms.po.domain.service.putaway;

import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Putaway Release Variant Service.
 *
 * Replaces:
 * - SP-072: ispPARL02 (180 LOC) - Zone-based release
 * - SP-073: ispPARL03 (160 LOC) - FIFO-based release
 * - SP-074: ispPARL04 (140 LOC) - Priority zone release
 * - SP-075: ispPARL05 (130 LOC) - Consolidation release
 * - SP-077: ispPARL07 (170 LOC) - Velocity-based release
 * - SP-078: ispPARL08 (160 LOC) - Weight-balanced release
 *
 * Each variant implements different location selection logic
 * based on client/warehouse requirements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PAReleaseVariantService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    // Variant identifiers
    public static final String VARIANT_ZONE_BASED = "PARL02";
    public static final String VARIANT_FIFO = "PARL03";
    public static final String VARIANT_PRIORITY_ZONE = "PARL04";
    public static final String VARIANT_CONSOLIDATION = "PARL05";
    public static final String VARIANT_VELOCITY = "PARL07";
    public static final String VARIANT_WEIGHT_BALANCED = "PARL08";

    /**
     * Execute putaway release using specified variant logic.
     *
     * @param variant Variant identifier (PARL02-PARL08)
     * @param request Release request parameters
     * @return Release result
     */
    @Transactional
    public ReleaseResult executeRelease(String variant, ReleaseRequest request) {
        log.info("Executing PA release variant {} for receipt: {}", variant, request.getReceiptKey());

        List<ReceiptLine> eligibleLines = getEligibleLines(request.getReceiptKey());
        if (eligibleLines.isEmpty()) {
            return ReleaseResult.builder()
                .success(true)
                .message("No eligible lines for PA release")
                .taskIds(Collections.emptyList())
                .build();
        }

        List<String> taskIds = switch (variant.toUpperCase()) {
            case VARIANT_ZONE_BASED, "ISPPARL02" -> executeZoneBasedRelease(request, eligibleLines);
            case VARIANT_FIFO, "ISPPARL03" -> executeFIFORelease(request, eligibleLines);
            case VARIANT_PRIORITY_ZONE, "ISPPARL04" -> executePriorityZoneRelease(request, eligibleLines);
            case VARIANT_CONSOLIDATION, "ISPPARL05" -> executeConsolidationRelease(request, eligibleLines);
            case VARIANT_VELOCITY, "ISPPARL07" -> executeVelocityRelease(request, eligibleLines);
            case VARIANT_WEIGHT_BALANCED, "ISPPARL08" -> executeWeightBalancedRelease(request, eligibleLines);
            default -> executeZoneBasedRelease(request, eligibleLines); // Default
        };

        return ReleaseResult.builder()
            .success(true)
            .taskIds(taskIds)
            .tasksCreated(taskIds.size())
            .variant(variant)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 2: Zone-Based Release (ispPARL02)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Zone-based putaway release.
     * Assigns items to zones based on SKU category/commodity class.
     *
     * Logic:
     * 1. Look up SKU's preferred zone (from SKU master or category)
     * 2. Find available location in preferred zone
     * 3. Fall back to default zone if preferred full
     * 4. Create task with zone-appropriate location
     */
    private List<String> executeZoneBasedRelease(ReleaseRequest request, List<ReceiptLine> lines) {
        log.debug("Executing zone-based PA release");

        List<String> taskIds = new ArrayList<>();

        for (ReceiptLine line : lines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                // 1. Get preferred zone for SKU
                String preferredZone = getPreferredZone(request.getStorerKey(), line.getSku());

                // 2. Find location in zone
                String targetLocation = findLocationInZone(
                    request.getFacility(), preferredZone, line.getSku(), line.getQuantity()
                );

                // 3. Fall back to default zone
                if (targetLocation == null) {
                    String defaultZone = getDefaultZone(request.getFacility());
                    targetLocation = findLocationInZone(
                        request.getFacility(), defaultZone, null, line.getQuantity()
                    );
                }

                if (targetLocation == null) {
                    log.warn("No available location for SKU {} in any zone", line.getSku());
                    continue;
                }

                // 4. Create task
                String taskId = createPutawayTask(request, line, targetLocation, 5);
                taskIds.add(taskId);

            } catch (Exception e) {
                log.error("Zone-based release failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 3: FIFO-Based Release (ispPARL03)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * FIFO-based putaway release.
     * Prioritizes locations to maintain FIFO order.
     *
     * Logic:
     * 1. Check existing inventory locations for same SKU/lot
     * 2. Route new inventory to back of FIFO queue (newer dates)
     * 3. Consider expiration dates for FEFO variant
     */
    private List<String> executeFIFORelease(ReleaseRequest request, List<ReceiptLine> lines) {
        log.debug("Executing FIFO-based PA release");

        List<String> taskIds = new ArrayList<>();

        for (ReceiptLine line : lines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                // 1. Find location that maintains FIFO - route to "back"
                String targetLocation = findFIFOLocation(
                    request.getFacility(),
                    request.getStorerKey(),
                    line.getSku(),
                    line.getLottable02(), // Lot/batch
                    line.getLottable05()  // Expiration date
                );

                // 2. Fall back to empty location
                if (targetLocation == null) {
                    targetLocation = findEmptyLocation(request.getFacility(), line.getSku());
                }

                if (targetLocation == null) {
                    log.warn("No FIFO location for SKU {}", line.getSku());
                    continue;
                }

                String taskId = createPutawayTask(request, line, targetLocation, 5);
                taskIds.add(taskId);

            } catch (Exception e) {
                log.error("FIFO release failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 4: Priority Zone Release (ispPARL04)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Priority zone putaway release.
     * Uses tiered priority for location selection.
     *
     * Logic:
     * 1. Check primary zone (fast-moving)
     * 2. Check secondary zone (medium velocity)
     * 3. Check bulk zone (slow-moving)
     * 4. Priority based on SKU velocity class
     */
    private List<String> executePriorityZoneRelease(ReleaseRequest request, List<ReceiptLine> lines) {
        log.debug("Executing priority zone PA release");

        List<String> taskIds = new ArrayList<>();

        for (ReceiptLine line : lines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                // Get SKU velocity class (A/B/C)
                String velocityClass = getSkuVelocityClass(request.getStorerKey(), line.getSku());

                // Try zones in priority order based on velocity
                String targetLocation = null;
                int priority = 5;

                if ("A".equals(velocityClass)) {
                    // Fast movers - primary (pick face) zone first
                    targetLocation = findLocationByZoneType(request.getFacility(), "PICKFACE");
                    priority = 3; // Higher priority
                } else if ("B".equals(velocityClass)) {
                    // Medium - case flow zone
                    targetLocation = findLocationByZoneType(request.getFacility(), "CASEFLOW");
                    priority = 5;
                } else {
                    // Slow - bulk/reserve zone
                    targetLocation = findLocationByZoneType(request.getFacility(), "BULK");
                    priority = 7;
                }

                // Fallback
                if (targetLocation == null) {
                    targetLocation = findAnyAvailableLocation(request.getFacility());
                }

                if (targetLocation == null) {
                    continue;
                }

                String taskId = createPutawayTask(request, line, targetLocation, priority);
                taskIds.add(taskId);

            } catch (Exception e) {
                log.error("Priority zone release failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 5: Consolidation Release (ispPARL05)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Consolidation putaway release.
     * Consolidates same SKU/lot into existing locations.
     *
     * Logic:
     * 1. Find existing locations with same SKU/lot
     * 2. Check if location can accept additional qty (capacity)
     * 3. Consolidate to minimize storage footprint
     * 4. Only create new location if consolidation not possible
     */
    private List<String> executeConsolidationRelease(ReleaseRequest request, List<ReceiptLine> lines) {
        log.debug("Executing consolidation PA release");

        List<String> taskIds = new ArrayList<>();

        // Group by SKU for consolidation
        Map<String, List<ReceiptLine>> bySku = new HashMap<>();
        for (ReceiptLine line : lines) {
            bySku.computeIfAbsent(line.getSku(), k -> new ArrayList<>()).add(line);
        }

        for (Map.Entry<String, List<ReceiptLine>> entry : bySku.entrySet()) {
            String sku = entry.getKey();
            List<ReceiptLine> skuLines = entry.getValue();

            // Find existing locations with this SKU
            List<LocationCapacity> existingLocs = getLocationsWithSku(
                request.getFacility(), request.getStorerKey(), sku
            );

            for (ReceiptLine line : skuLines) {
                try {
                    if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                        continue;
                    }

                    String targetLocation = null;

                    // Try to consolidate
                    for (LocationCapacity loc : existingLocs) {
                        if (loc.getAvailableCapacity().compareTo(line.getQuantity()) >= 0) {
                            targetLocation = loc.getLocation();
                            loc.setAvailableCapacity(
                                loc.getAvailableCapacity().subtract(line.getQuantity())
                            );
                            break;
                        }
                    }

                    // No consolidation possible - find empty
                    if (targetLocation == null) {
                        targetLocation = findEmptyLocation(request.getFacility(), sku);
                    }

                    if (targetLocation == null) {
                        continue;
                    }

                    String taskId = createPutawayTask(request, line, targetLocation, 5);
                    taskIds.add(taskId);

                } catch (Exception e) {
                    log.error("Consolidation release failed for line {}: {}", line.getLineNumber(), e.getMessage());
                }
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 7: Velocity-Based Release (ispPARL07)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Velocity-based putaway release.
     * Uses item movement history to optimize placement.
     *
     * Logic:
     * 1. Calculate SKU velocity (picks per day/week)
     * 2. High velocity → ground level, near shipping
     * 3. Medium velocity → mid-height, middle aisles
     * 4. Low velocity → high bay, far aisles
     */
    private List<String> executeVelocityRelease(ReleaseRequest request, List<ReceiptLine> lines) {
        log.debug("Executing velocity-based PA release");

        List<String> taskIds = new ArrayList<>();

        for (ReceiptLine line : lines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                // Calculate velocity score
                int velocityScore = calculateVelocityScore(request.getStorerKey(), line.getSku());

                // Determine optimal location based on velocity
                String targetLocation;
                int priority;

                if (velocityScore > 100) {
                    // High velocity - ground level, front
                    targetLocation = findVelocityLocation(request.getFacility(), "GROUND", "FRONT");
                    priority = 2;
                } else if (velocityScore > 50) {
                    // Medium velocity - mid level
                    targetLocation = findVelocityLocation(request.getFacility(), "MID", "MIDDLE");
                    priority = 5;
                } else if (velocityScore > 10) {
                    // Low velocity - high level
                    targetLocation = findVelocityLocation(request.getFacility(), "HIGH", "BACK");
                    priority = 7;
                } else {
                    // Very slow - bulk storage
                    targetLocation = findVelocityLocation(request.getFacility(), "BULK", null);
                    priority = 9;
                }

                if (targetLocation == null) {
                    targetLocation = findAnyAvailableLocation(request.getFacility());
                }

                if (targetLocation == null) {
                    continue;
                }

                String taskId = createPutawayTask(request, line, targetLocation, priority);
                taskIds.add(taskId);

            } catch (Exception e) {
                log.error("Velocity release failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 8: Weight-Balanced Release (ispPARL08)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Weight-balanced putaway release.
     * Distributes weight evenly across racks/zones.
     *
     * Logic:
     * 1. Calculate total weight of incoming items
     * 2. Query current weight distribution by zone/rack
     * 3. Assign to lightest loaded zone first
     * 4. Heavy items → ground/floor level only
     */
    private List<String> executeWeightBalancedRelease(ReleaseRequest request, List<ReceiptLine> lines) {
        log.debug("Executing weight-balanced PA release");

        List<String> taskIds = new ArrayList<>();

        // Get current weight distribution
        Map<String, BigDecimal> zoneWeights = getZoneWeightDistribution(request.getFacility());

        for (ReceiptLine line : lines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                BigDecimal itemWeight = getItemWeight(request.getStorerKey(), line.getSku(), line.getQuantity());

                String targetLocation;
                int priority = 5;

                // Heavy items (>50kg) must go to floor level
                if (itemWeight != null && itemWeight.compareTo(BigDecimal.valueOf(50)) > 0) {
                    targetLocation = findFloorLocation(request.getFacility(), line.getSku());
                    priority = 3; // Higher priority for heavy items
                } else {
                    // Find lightest loaded zone
                    String lightestZone = findLightestZone(zoneWeights);
                    targetLocation = findLocationInZone(
                        request.getFacility(), lightestZone, line.getSku(), line.getQuantity()
                    );

                    // Update tracking
                    if (targetLocation != null && itemWeight != null) {
                        zoneWeights.merge(lightestZone, itemWeight, BigDecimal::add);
                    }
                }

                if (targetLocation == null) {
                    targetLocation = findAnyAvailableLocation(request.getFacility());
                }

                if (targetLocation == null) {
                    continue;
                }

                String taskId = createPutawayTask(request, line, targetLocation, priority);
                taskIds.add(taskId);

            } catch (Exception e) {
                log.error("Weight-balanced release failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ReceiptLine> getEligibleLines(String receiptKey) {
        try {
            return jdbcTemplate.query(
                """
                SELECT rd.receiptlinenumber, rd.sku, rd.qtyreceived, rd.packkey, rd.uom,
                       rd.toloc, rd.toid, rd.tolottable01, rd.tolottable02, rd.tolottable05,
                       rd.lotxlocxidkey
                FROM dbo.RECEIPTDETAIL rd
                WHERE rd.receiptkey = ?
                AND rd.finalizeflag = 'Y'
                AND rd.qtyreceived > 0
                AND COALESCE(rd.toid, '') != ''
                ORDER BY rd.receiptlinenumber
                """,
                (rs, rowNum) -> ReceiptLine.builder()
                    .lineNumber(rs.getInt("receiptlinenumber"))
                    .sku(rs.getString("sku"))
                    .quantity(rs.getBigDecimal("qtyreceived"))
                    .packKey(rs.getString("packkey"))
                    .uom(rs.getString("uom"))
                    .toLocation(rs.getString("toloc"))
                    .toId(rs.getString("toid"))
                    .lottable01(rs.getString("tolottable01"))
                    .lottable02(rs.getString("tolottable02"))
                    .lottable05(rs.getString("tolottable05"))
                    .lotxlocxidKey(rs.getString("lotxlocxidkey"))
                    .build(),
                receiptKey
            );
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean taskExists(String receiptKey, int lineNumber) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM dbo.TASKDETAIL
                WHERE receiptkey = ? AND receiptlinenumber = ?
                AND status NOT IN ('C', '9')
                """,
                Integer.class,
                receiptKey, lineNumber
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String createPutawayTask(ReleaseRequest request, ReceiptLine line,
                                      String targetLocation, int priority) {
        String taskKey = keyGeneratorService.generateKey("TASKDETAIL");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.TASKDETAIL
            (taskdetailkey, whseid, storerkey, sku, lot, fromloc, fromid, toloc,
             qty, tasktype, status, priority, receiptkey, receiptlinenumber,
             lotxlocxidkey, adddate, addwho)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PA', '0', ?, ?, ?, ?, GETDATE(), ?)
            """,
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            line.getSku(),
            line.getLottable01(),
            line.getToLocation(),
            line.getToId(),
            targetLocation,
            line.getQuantity(),
            priority,
            request.getReceiptKey(),
            line.getLineNumber(),
            line.getLotxlocxidKey(),
            request.getUserId()
        );

        return taskKey;
    }

    private String getPreferredZone(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(s.putawayzone, cc.zone, 'DEFAULT')
                FROM dbo.SKU s
                LEFT JOIN dbo.COMMODITYCLASS cc ON s.commodityclass = cc.commodityclass
                WHERE s.storerkey = ? AND s.sku = ?
                """,
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return "DEFAULT";
        }
    }

    private String getDefaultZone(String facility) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT defaultpazone FROM dbo.WHSE WHERE whseid = ?",
                String.class,
                facility
            );
        } catch (Exception e) {
            return "RESERVE";
        }
    }

    private String findLocationInZone(String facility, String zone, String sku, BigDecimal qty) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 l.loc FROM dbo.LOC l
                WHERE l.facility = ? AND l.putawayzone = ?
                AND l.status = '1' AND l.locationflag = '0'
                AND (l.maxqty = 0 OR l.currentqty + ? <= l.maxqty)
                ORDER BY l.currentqty ASC, l.loc
                """,
                String.class,
                facility, zone, qty
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findFIFOLocation(String facility, String storerKey, String sku,
                                     String lot, String expDate) {
        try {
            // Find location with oldest existing inventory for same SKU
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 l.loc FROM dbo.LOC l
                JOIN dbo.LOTXLOCXID inv ON l.loc = inv.loc
                WHERE l.facility = ? AND inv.storerkey = ? AND inv.sku = ?
                AND l.status = '1'
                AND (l.maxqty = 0 OR l.currentqty < l.maxqty * 0.9)
                ORDER BY inv.adddate ASC, inv.lottable05 ASC
                """,
                String.class,
                facility, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findEmptyLocation(String facility, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1' AND currentqty = 0
                ORDER BY loc
                """,
                String.class,
                facility
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getSkuVelocityClass(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COALESCE(abcclass, 'C') FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return "C";
        }
    }

    private String findLocationByZoneType(String facility, String zoneType) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND loctype = ?
                AND status = '1' AND locationflag = '0'
                AND (maxqty = 0 OR currentqty < maxqty * 0.9)
                ORDER BY currentqty ASC
                """,
                String.class,
                facility, zoneType
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findAnyAvailableLocation(String facility) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1' AND locationflag = '0'
                AND (maxqty = 0 OR currentqty < maxqty)
                ORDER BY currentqty ASC
                """,
                String.class,
                facility
            );
        } catch (Exception e) {
            return null;
        }
    }

    private List<LocationCapacity> getLocationsWithSku(String facility, String storerKey, String sku) {
        try {
            return jdbcTemplate.query(
                """
                SELECT l.loc, l.maxqty - l.currentqty as available
                FROM dbo.LOC l
                JOIN dbo.LOTXLOCXID inv ON l.loc = inv.loc
                WHERE l.facility = ? AND inv.storerkey = ? AND inv.sku = ?
                AND l.status = '1' AND (l.maxqty = 0 OR l.currentqty < l.maxqty)
                ORDER BY available DESC
                """,
                (rs, rowNum) -> LocationCapacity.builder()
                    .location(rs.getString("loc"))
                    .availableCapacity(rs.getBigDecimal("available"))
                    .build(),
                facility, storerKey, sku
            );
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private int calculateVelocityScore(String storerKey, String sku) {
        try {
            Integer picks = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM dbo.PICKDETAIL
                WHERE storerkey = ? AND sku = ?
                AND adddate > DATEADD(day, -30, GETDATE())
                """,
                Integer.class,
                storerKey, sku
            );
            return picks != null ? picks : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String findVelocityLocation(String facility, String level, String position) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT TOP 1 loc FROM dbo.LOC WHERE facility = ? AND status = '1' "
            );

            if ("GROUND".equals(level)) {
                sql.append("AND SUBSTRING(loc, 1, 2) = '01' ");
            } else if ("MID".equals(level)) {
                sql.append("AND SUBSTRING(loc, 1, 2) IN ('02', '03', '04') ");
            } else if ("HIGH".equals(level)) {
                sql.append("AND SUBSTRING(loc, 1, 2) > '04' ");
            }

            sql.append("AND (maxqty = 0 OR currentqty < maxqty) ORDER BY currentqty ASC");

            return jdbcTemplate.queryForObject(sql.toString(), String.class, facility);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, BigDecimal> getZoneWeightDistribution(String facility) {
        Map<String, BigDecimal> weights = new HashMap<>();
        try {
            jdbcTemplate.query(
                """
                SELECT l.putawayzone, SUM(l.currentweight) as totalweight
                FROM dbo.LOC l
                WHERE l.facility = ?
                GROUP BY l.putawayzone
                """,
                rs -> {
                    weights.put(rs.getString("putawayzone"),
                        rs.getBigDecimal("totalweight"));
                },
                facility
            );
        } catch (Exception e) {
            // Return empty map
        }
        return weights;
    }

    private BigDecimal getItemWeight(String storerKey, String sku, BigDecimal qty) {
        try {
            BigDecimal unitWeight = jdbcTemplate.queryForObject(
                "SELECT stdgrosswgt FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                BigDecimal.class,
                storerKey, sku
            );
            if (unitWeight != null && qty != null) {
                return unitWeight.multiply(qty);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String findLightestZone(Map<String, BigDecimal> zoneWeights) {
        return zoneWeights.entrySet().stream()
            .min(Comparator.comparing(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse("DEFAULT");
    }

    private String findFloorLocation(String facility, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1'
                AND (loctype = 'FLOOR' OR SUBSTRING(loc, 1, 2) = '01')
                AND (maxqty = 0 OR currentqty < maxqty)
                ORDER BY currentweight ASC
                """,
                String.class,
                facility
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class ReleaseRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
    }

    @lombok.Data
    @lombok.Builder
    public static class ReleaseResult {
        private boolean success;
        private List<String> taskIds;
        private int tasksCreated;
        private String variant;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    private static class ReceiptLine {
        private int lineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String toLocation;
        private String toId;
        private String lottable01;
        private String lottable02;
        private String lottable05;
        private String lotxlocxidKey;
    }

    @lombok.Data
    @lombok.Builder
    private static class LocationCapacity {
        private String location;
        private BigDecimal availableCapacity;
    }
}
