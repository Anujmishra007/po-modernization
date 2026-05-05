package com.wms.po.domain.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Nike CallOfModel Batch Putaway Service.
 *
 * Replaces: ispBatPA05 (1,815 LOC)
 * Client: Nike CN
 *
 * Advanced batch putaway supporting CASE and PIECE ASN types:
 * - CASE type: UCC carton tracking, HighBay FP → SafetyStock
 * - PIECE type: SKU-group-based calculation
 * - Multi-receipt input (comma-separated)
 * - Cube-based case count via CARTONIZATION table
 * - UCC.Userdefined10 updated with suggested location
 * - Model-based consolidation
 * - Wave planning integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NikeCallOfModelPAService {

    private final JdbcTemplate jdbcTemplate;
    private final PutawayStrategyService putawayStrategyService;

    // ASN Types
    private static final String ASN_TYPE_CASE = "CASE";
    private static final String ASN_TYPE_PIECE = "PIECE";

    // Zone types
    private static final String ZONE_HIGHBAY = "HIGHBAY";
    private static final String ZONE_FORWARD_PICK = "FP";
    private static final String ZONE_SAFETY_STOCK = "SS";
    private static final String ZONE_RESERVE = "RESERVE";

    // Location types
    private static final String LOC_TYPE_HIGHBAY = "HB";
    private static final String LOC_TYPE_FORWARD = "FW";
    private static final String LOC_TYPE_SAFETY = "SF";

    // Task types
    private static final String TASK_TYPE_PUTAWAY = "PA";
    private static final String TASK_TYPE_REPLEN = "RP";

    /**
     * Process batch putaway for Nike CallOfModel.
     *
     * @param request Batch request with receipt keys
     * @return Processing result
     */
    @Transactional
    public CallOfModelResult processBatchPutaway(CallOfModelRequest request) {
        log.info("Nike CallOfModel PA: receipts={}, asnType={}, storerKey={}",
            request.getReceiptKeys(), request.getAsnType(), request.getStorerKey());

        List<String> parsedReceiptKeys = parseReceiptKeys(request.getReceiptKeys());

        CallOfModelResult result = CallOfModelResult.builder()
            .receiptKeys(parsedReceiptKeys)
            .tasks(new ArrayList<>())
            .uccUpdates(new ArrayList<>())
            .build();

        try {
            // 1. Validate receipt keys
            if (parsedReceiptKeys.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No valid receipt keys provided");
                return result;
            }

            // 2. Get pending inventory grouped by model
            Map<String, List<ModelInventory>> modelGroups = getModelGroupedInventory(request, parsedReceiptKeys);

            if (modelGroups.isEmpty()) {
                result.setSuccess(true);
                result.setMessage("No pending inventory for batch putaway");
                return result;
            }

            log.debug("Found {} model groups with {} total items",
                modelGroups.size(),
                modelGroups.values().stream().mapToInt(List::size).sum());

            // 3. Process based on ASN type
            if (ASN_TYPE_CASE.equalsIgnoreCase(request.getAsnType())) {
                processCaseType(request, modelGroups, result);
            } else {
                processPieceType(request, modelGroups, result);
            }

            // 4. Create wave if configured
            if (request.isCreateWave() && !result.getTasks().isEmpty()) {
                String waveKey = createPutawayWave(request, result.getTasks());
                result.setWaveKey(waveKey);
            }

            result.setSuccess(true);
            result.setTaskCount(result.getTasks().size());
            result.setMessage("Created " + result.getTaskCount() + " putaway tasks, " +
                result.getUccUpdates().size() + " UCC updates");

            log.info("Nike CallOfModel complete: {} tasks, {} UCC updates",
                result.getTaskCount(), result.getUccUpdates().size());

        } catch (Exception e) {
            log.error("Nike CallOfModel PA failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("Batch putaway failed: " + e.getMessage());
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE Type Processing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Process CASE type ASN with UCC carton tracking.
     */
    private void processCaseType(CallOfModelRequest request,
                                  Map<String, List<ModelInventory>> modelGroups,
                                  CallOfModelResult result) {
        log.debug("Processing CASE type with {} model groups", modelGroups.size());

        for (Map.Entry<String, List<ModelInventory>> entry : modelGroups.entrySet()) {
            String modelCode = entry.getKey();
            List<ModelInventory> cartons = entry.getValue();

            try {
                // Group cartons by UCC
                Map<String, List<ModelInventory>> uccGroups = cartons.stream()
                    .filter(c -> c.getUccKey() != null)
                    .collect(Collectors.groupingBy(ModelInventory::getUccKey));

                // Process each UCC carton
                for (Map.Entry<String, List<ModelInventory>> uccEntry : uccGroups.entrySet()) {
                    String uccKey = uccEntry.getKey();
                    List<ModelInventory> uccItems = uccEntry.getValue();

                    processCaseCarton(request, modelCode, uccKey, uccItems, result);
                }

                // Process non-UCC inventory
                List<ModelInventory> nonUcc = cartons.stream()
                    .filter(c -> c.getUccKey() == null)
                    .collect(Collectors.toList());

                if (!nonUcc.isEmpty()) {
                    processNonUCCCartons(request, modelCode, nonUcc, result);
                }

            } catch (Exception e) {
                log.warn("Failed to process model {}: {}", modelCode, e.getMessage());
                result.getWarnings().add("Model " + modelCode + ": " + e.getMessage());
            }
        }
    }

    /**
     * Process a single UCC carton.
     */
    private void processCaseCarton(CallOfModelRequest request, String modelCode,
                                    String uccKey, List<ModelInventory> items,
                                    CallOfModelResult result) {
        // Calculate total cube and weight for the carton
        BigDecimal totalCube = items.stream()
            .map(ModelInventory::getCube)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWeight = items.stream()
            .map(ModelInventory::getWeight)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Determine location strategy: HighBay FP → SafetyStock
        String targetZone = determineZoneForCase(request, modelCode, totalCube, totalWeight);
        String toLoc = findLocationForCase(request, targetZone, totalCube, totalWeight);

        if (toLoc == null) {
            // Fallback to safety stock
            toLoc = findLocationForCase(request, ZONE_SAFETY_STOCK, totalCube, totalWeight);
        }

        if (toLoc == null) {
            log.warn("No location found for UCC carton {}", uccKey);
            result.getWarnings().add("UCC " + uccKey + ": No suitable location found");
            return;
        }

        // Create putaway task for the carton
        ModelInventory primaryItem = items.get(0);
        String taskKey = createCartonTask(request, primaryItem, toLoc, items.size());

        result.getTasks().add(CallOfModelTask.builder()
            .taskKey(taskKey)
            .modelCode(modelCode)
            .uccKey(uccKey)
            .fromLoc(primaryItem.getLoc())
            .toLoc(toLoc)
            .itemCount(items.size())
            .totalQty(items.stream()
                .map(ModelInventory::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .build());

        // Update UCC with suggested location
        updateUCCLocation(uccKey, toLoc);
        result.getUccUpdates().add(uccKey);
    }

    /**
     * Process non-UCC cartons (loose items).
     */
    private void processNonUCCCartons(CallOfModelRequest request, String modelCode,
                                       List<ModelInventory> items, CallOfModelResult result) {
        // Group by SKU for consolidation
        Map<String, List<ModelInventory>> skuGroups = items.stream()
            .collect(Collectors.groupingBy(ModelInventory::getSku));

        for (Map.Entry<String, List<ModelInventory>> skuEntry : skuGroups.entrySet()) {
            String sku = skuEntry.getKey();
            List<ModelInventory> skuItems = skuEntry.getValue();

            for (ModelInventory item : skuItems) {
                String targetZone = determineZoneForPiece(request, sku);
                String toLoc = findLocationForPiece(request, sku, targetZone, item.getQty());

                if (toLoc != null) {
                    String taskKey = createPieceTask(request, item, toLoc);
                    result.getTasks().add(CallOfModelTask.builder()
                        .taskKey(taskKey)
                        .modelCode(modelCode)
                        .sku(sku)
                        .fromLoc(item.getLoc())
                        .toLoc(toLoc)
                        .itemCount(1)
                        .totalQty(item.getQty())
                        .build());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PIECE Type Processing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Process PIECE type ASN with SKU-group calculation.
     */
    private void processPieceType(CallOfModelRequest request,
                                   Map<String, List<ModelInventory>> modelGroups,
                                   CallOfModelResult result) {
        log.debug("Processing PIECE type with {} model groups", modelGroups.size());

        for (Map.Entry<String, List<ModelInventory>> entry : modelGroups.entrySet()) {
            String modelCode = entry.getKey();
            List<ModelInventory> items = entry.getValue();

            try {
                // Group by SKU
                Map<String, List<ModelInventory>> skuGroups = items.stream()
                    .collect(Collectors.groupingBy(ModelInventory::getSku));

                // Process each SKU group
                for (Map.Entry<String, List<ModelInventory>> skuEntry : skuGroups.entrySet()) {
                    String sku = skuEntry.getKey();
                    List<ModelInventory> skuItems = skuEntry.getValue();

                    processSkuGroup(request, modelCode, sku, skuItems, result);
                }

            } catch (Exception e) {
                log.warn("Failed to process model {}: {}", modelCode, e.getMessage());
                result.getWarnings().add("Model " + modelCode + ": " + e.getMessage());
            }
        }
    }

    /**
     * Process a SKU group with cube-based case count calculation.
     */
    private void processSkuGroup(CallOfModelRequest request, String modelCode,
                                  String sku, List<ModelInventory> items,
                                  CallOfModelResult result) {
        // Calculate total quantity
        BigDecimal totalQty = items.stream()
            .map(ModelInventory::getQty)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get cartonization info for cube-based case count
        CartonizationInfo cartonInfo = getCartonizationInfo(request.getStorerKey(), sku);

        // Calculate case count based on cube
        int caseCount = calculateCaseCount(totalQty, cartonInfo);

        // Determine zone and location
        String targetZone = determineZoneForPiece(request, sku);
        String toLoc = findLocationForPiece(request, sku, targetZone, totalQty);

        if (toLoc == null) {
            log.warn("No location found for SKU {} in model {}", sku, modelCode);
            result.getWarnings().add("SKU " + sku + ": No location found");
            return;
        }

        // Create tasks for each inventory item
        for (ModelInventory item : items) {
            String taskKey = createPieceTask(request, item, toLoc);
            result.getTasks().add(CallOfModelTask.builder()
                .taskKey(taskKey)
                .modelCode(modelCode)
                .sku(sku)
                .fromLoc(item.getLoc())
                .toLoc(toLoc)
                .itemCount(1)
                .totalQty(item.getQty())
                .caseCount(caseCount)
                .build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Location Determination
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Determine zone for CASE type: HighBay FP → SafetyStock.
     */
    private String determineZoneForCase(CallOfModelRequest request, String modelCode,
                                         BigDecimal cube, BigDecimal weight) {
        // Check if model is high-velocity (goes to forward pick)
        if (isHighVelocityModel(request.getStorerKey(), modelCode)) {
            return ZONE_FORWARD_PICK;
        }

        // Heavy items go to highbay
        if (weight != null && weight.compareTo(new BigDecimal("500")) > 0) {
            return ZONE_HIGHBAY;
        }

        // Default to highbay for cases
        return ZONE_HIGHBAY;
    }

    /**
     * Determine zone for PIECE type based on SKU attributes.
     */
    private String determineZoneForPiece(CallOfModelRequest request, String sku) {
        // Check SKU velocity
        String velocity = getSkuVelocity(request.getStorerKey(), sku);

        if ("A".equals(velocity)) {
            return ZONE_FORWARD_PICK;
        } else if ("B".equals(velocity)) {
            return ZONE_SAFETY_STOCK;
        }

        return ZONE_RESERVE;
    }

    /**
     * Find location for CASE type.
     */
    private String findLocationForCase(CallOfModelRequest request, String zone,
                                        BigDecimal cube, BigDecimal weight) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT TOP 1 l.loc FROM dbo.LOC l " +
                "WHERE l.storerkey = ? " +
                "AND l.putawayzone = ? " +
                "AND l.status = '0' " +
                "AND l.locationflag NOT IN ('H', 'D') " +
                "AND (l.maxcube IS NULL OR l.maxcube >= ?) " +
                "AND (l.maxweight IS NULL OR l.maxweight >= ?) " +
                "AND NOT EXISTS (SELECT 1 FROM dbo.LOTXLOCXID lx WHERE lx.loc = l.loc AND lx.qty > 0) " +
                "ORDER BY l.logicallocnum",
                String.class,
                request.getStorerKey(), zone,
                cube != null ? cube : BigDecimal.ZERO,
                weight != null ? weight : BigDecimal.ZERO
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Find location for PIECE type with SKU-specific logic.
     */
    private String findLocationForPiece(CallOfModelRequest request, String sku,
                                         String zone, BigDecimal qty) {
        try {
            // First try SKU-specific location
            String skuLoc = jdbcTemplate.queryForObject(
                "SELECT putawayloc FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class,
                request.getStorerKey(), sku
            );
            if (skuLoc != null && !skuLoc.isEmpty()) {
                return skuLoc;
            }
        } catch (Exception e) {
            // Continue to zone-based search
        }

        try {
            return jdbcTemplate.queryForObject(
                "SELECT TOP 1 l.loc FROM dbo.LOC l " +
                "WHERE l.storerkey = ? " +
                "AND l.putawayzone = ? " +
                "AND l.status = '0' " +
                "AND l.locationflag NOT IN ('H', 'D') " +
                "AND NOT EXISTS (SELECT 1 FROM dbo.LOTXLOCXID lx WHERE lx.loc = l.loc AND lx.qty > 0) " +
                "ORDER BY l.logicallocnum",
                String.class,
                request.getStorerKey(), zone
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Task Creation
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create putaway task for carton.
     */
    private String createCartonTask(CallOfModelRequest request, ModelInventory item,
                                     String toLoc, int itemCount) {
        String taskKey = generateTaskKey();

        jdbcTemplate.update(
            "INSERT INTO dbo.TASKDETAIL (taskdetailkey, whseid, storerkey, sku, lot, " +
            "fromloc, fromid, toloc, qty, tasktype, status, priority, " +
            "sourcekey, sourcetype, susr1, susr2, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '0', ?, ?, 'CALLOFMODEL', ?, ?, GETDATE(), ?)",
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            item.getSku(),
            item.getLot(),
            item.getLoc(),
            item.getId(),
            toLoc,
            item.getQty(),
            TASK_TYPE_PUTAWAY,
            30, // Priority
            item.getReceiptKey(),
            item.getModelCode(),
            String.valueOf(itemCount),
            request.getUserId()
        );

        // Update inventory status
        jdbcTemplate.update(
            "UPDATE dbo.LOTXLOCXID SET status = '7' WHERE lotxlocxidkey = ?",
            item.getLotxlocxidKey()
        );

        return taskKey;
    }

    /**
     * Create putaway task for piece.
     */
    private String createPieceTask(CallOfModelRequest request, ModelInventory item, String toLoc) {
        String taskKey = generateTaskKey();

        jdbcTemplate.update(
            "INSERT INTO dbo.TASKDETAIL (taskdetailkey, whseid, storerkey, sku, lot, " +
            "fromloc, fromid, toloc, qty, tasktype, status, priority, " +
            "sourcekey, sourcetype, susr1, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '0', ?, ?, 'CALLOFMODEL', ?, GETDATE(), ?)",
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            item.getSku(),
            item.getLot(),
            item.getLoc(),
            item.getId(),
            toLoc,
            item.getQty(),
            TASK_TYPE_PUTAWAY,
            40, // Priority
            item.getReceiptKey(),
            item.getModelCode(),
            request.getUserId()
        );

        // Update inventory status
        jdbcTemplate.update(
            "UPDATE dbo.LOTXLOCXID SET status = '7' WHERE lotxlocxidkey = ?",
            item.getLotxlocxidKey()
        );

        return taskKey;
    }

    /**
     * Update UCC with suggested location (Userdefined10).
     */
    private void updateUCCLocation(String uccKey, String location) {
        try {
            jdbcTemplate.update(
                "UPDATE dbo.UCC SET userdefined10 = ?, editdate = GETDATE() WHERE ucckey = ?",
                location, uccKey
            );
        } catch (Exception e) {
            log.debug("UCC update skipped for {}: {}", uccKey, e.getMessage());
        }
    }

    /**
     * Create putaway wave.
     */
    private String createPutawayWave(CallOfModelRequest request, List<CallOfModelTask> tasks) {
        String waveKey = generateWaveKey();

        jdbcTemplate.update(
            "INSERT INTO dbo.WAVEDETAIL (wavekey, storerkey, wavetype, status, " +
            "taskcount, adddate, addwho) " +
            "VALUES (?, ?, 'CALLOFMODEL', '0', ?, GETDATE(), ?)",
            waveKey,
            request.getStorerKey(),
            tasks.size(),
            request.getUserId()
        );

        // Update tasks with wave key
        for (CallOfModelTask task : tasks) {
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

    private List<String> parseReceiptKeys(String receiptKeysInput) {
        if (receiptKeysInput == null || receiptKeysInput.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(receiptKeysInput.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private Map<String, List<ModelInventory>> getModelGroupedInventory(
            CallOfModelRequest request, List<String> receiptKeys) {

        String inClause = receiptKeys.stream()
            .map(k -> "'" + k.replace("'", "''") + "'")
            .collect(Collectors.joining(","));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT lx.lotxlocxidkey, lx.storerkey, lx.sku, lx.lot, lx.loc, lx.id, lx.qty, " +
            "lx.lottable01 as stylecolor, lx.sourcekey as receiptkey, " +
            "SUBSTRING(lx.lottable01, 1, 6) as modelcode, " +
            "u.ucckey, " +
            "s.stdgrosswgt * lx.qty as weight, " +
            "s.stdcube * lx.qty as cube " +
            "FROM dbo.LOTXLOCXID lx " +
            "LEFT JOIN dbo.UCC u ON lx.id = u.lpn AND lx.storerkey = u.storerkey " +
            "JOIN dbo.SKU s ON lx.storerkey = s.storerkey AND lx.sku = s.sku " +
            "WHERE lx.sourcekey IN (" + inClause + ") " +
            "AND lx.sourcetype = 'RECEIPT' " +
            "AND lx.status = '0' AND lx.qty > 0"
        );

        return rows.stream()
            .map(row -> ModelInventory.builder()
                .lotxlocxidKey((String) row.get("lotxlocxidkey"))
                .storerKey((String) row.get("storerkey"))
                .sku((String) row.get("sku"))
                .lot((String) row.get("lot"))
                .loc((String) row.get("loc"))
                .id((String) row.get("id"))
                .qty((BigDecimal) row.get("qty"))
                .styleColor((String) row.get("stylecolor"))
                .modelCode((String) row.get("modelcode"))
                .receiptKey((String) row.get("receiptkey"))
                .uccKey((String) row.get("ucckey"))
                .weight((BigDecimal) row.get("weight"))
                .cube((BigDecimal) row.get("cube"))
                .build())
            .collect(Collectors.groupingBy(
                inv -> inv.getModelCode() != null ? inv.getModelCode() : "UNKNOWN"
            ));
    }

    private CartonizationInfo getCartonizationInfo(String storerKey, String sku) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT c.cartontype, c.maxcube, c.maxweight, c.maxqty " +
                "FROM dbo.CARTONIZATION c " +
                "JOIN dbo.SKU s ON c.storerkey = s.storerkey AND c.cartongroup = s.cartongroup " +
                "WHERE s.storerkey = ? AND s.sku = ?",
                storerKey, sku
            );

            return CartonizationInfo.builder()
                .cartonType((String) row.get("cartontype"))
                .maxCube((BigDecimal) row.get("maxcube"))
                .maxWeight((BigDecimal) row.get("maxweight"))
                .maxQty((BigDecimal) row.get("maxqty"))
                .build();
        } catch (Exception e) {
            return CartonizationInfo.builder().build();
        }
    }

    private int calculateCaseCount(BigDecimal totalQty, CartonizationInfo cartonInfo) {
        if (cartonInfo.getMaxQty() != null && cartonInfo.getMaxQty().compareTo(BigDecimal.ZERO) > 0) {
            return totalQty.divide(cartonInfo.getMaxQty(), 0, RoundingMode.CEILING).intValue();
        }
        return 1;
    }

    private boolean isHighVelocityModel(String storerKey, String modelCode) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.SKU " +
                "WHERE storerkey = ? AND sku LIKE ? + '%' AND abcvelocity = 'A'",
                Integer.class,
                storerKey, modelCode
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
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
            return "C";
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
    public static class CallOfModelRequest {
        private String receiptKeys;  // Comma-separated
        private String storerKey;
        private String facility;
        private String userId;
        private String asnType;  // CASE or PIECE
        private boolean createWave;
    }

    @Data
    @Builder
    public static class CallOfModelResult {
        private boolean success;
        private List<String> receiptKeys;
        private String waveKey;
        private int taskCount;
        private String message;
        @Builder.Default
        private List<CallOfModelTask> tasks = new ArrayList<>();
        @Builder.Default
        private List<String> uccUpdates = new ArrayList<>();
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @Builder
    public static class CallOfModelTask {
        private String taskKey;
        private String modelCode;
        private String sku;
        private String uccKey;
        private String fromLoc;
        private String toLoc;
        private int itemCount;
        private BigDecimal totalQty;
        private int caseCount;
    }

    @Data
    @Builder
    private static class ModelInventory {
        private String lotxlocxidKey;
        private String storerKey;
        private String sku;
        private String lot;
        private String loc;
        private String id;
        private BigDecimal qty;
        private String styleColor;
        private String modelCode;
        private String receiptKey;
        private String uccKey;
        private BigDecimal weight;
        private BigDecimal cube;
    }

    @Data
    @Builder
    private static class CartonizationInfo {
        private String cartonType;
        private BigDecimal maxCube;
        private BigDecimal maxWeight;
        private BigDecimal maxQty;
    }
}
