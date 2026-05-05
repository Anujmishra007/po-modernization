package com.wms.po.domain.service.putaway;

import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Batch Putaway Variant Service.
 *
 * Replaces:
 * - SP-080: ispBatPA01 (200 LOC) - Standard batch PA
 * - SP-082: ispBatPA03 (300 LOC) - ASN-level batch PA
 * - SP-083: ispBatPA04 (280 LOC) - Pallet-based batch PA
 * - SP-085: ispBatPA06 (250 LOC) - SKU-grouped batch PA
 *
 * Note: SP-081 (Nike CRW) and SP-084 (Nike CallOfModel) are separate services.
 *
 * Batch PA processes multiple items/lines together for efficiency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchPAVariantService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    // Variant identifiers
    public static final String VARIANT_STANDARD = "BATPA01";
    public static final String VARIANT_ASN_LEVEL = "BATPA03";
    public static final String VARIANT_PALLET_BASED = "BATPA04";
    public static final String VARIANT_SKU_GROUPED = "BATPA06";

    /**
     * Execute batch putaway using specified variant.
     *
     * @param variant Variant identifier
     * @param request Batch PA request
     * @return Batch PA result
     */
    @Transactional
    public BatchPAResult executeBatchPA(String variant, BatchPARequest request) {
        log.info("Executing batch PA variant {} for receipt: {}", variant, request.getReceiptKey());

        return switch (variant.toUpperCase()) {
            case VARIANT_STANDARD, "ISPBATPA01" -> executeStandardBatchPA(request);
            case VARIANT_ASN_LEVEL, "ISPBATPA03" -> executeASNLevelBatchPA(request);
            case VARIANT_PALLET_BASED, "ISPBATPA04" -> executePalletBasedBatchPA(request);
            case VARIANT_SKU_GROUPED, "ISPBATPA06" -> executeSkuGroupedBatchPA(request);
            default -> executeStandardBatchPA(request);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 1: Standard Batch PA (ispBatPA01)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Standard batch putaway.
     * Groups receipt lines by location and creates consolidated tasks.
     *
     * Logic:
     * 1. Group lines by current staging location
     * 2. Calculate total quantity per location
     * 3. Find optimal destination for batch
     * 4. Create one task per batch with aggregated qty
     */
    private BatchPAResult executeStandardBatchPA(BatchPARequest request) {
        log.debug("Executing standard batch PA for {}", request.getReceiptKey());

        List<ReceiptLine> lines = getEligibleLines(request.getReceiptKey());
        if (lines.isEmpty()) {
            return BatchPAResult.success(Collections.emptyList(), "No eligible lines");
        }

        // Group by staging location
        Map<String, List<ReceiptLine>> byLocation = lines.stream()
            .collect(Collectors.groupingBy(ReceiptLine::getFromLocation));

        List<String> taskIds = new ArrayList<>();
        List<BatchInfo> batches = new ArrayList<>();

        for (Map.Entry<String, List<ReceiptLine>> entry : byLocation.entrySet()) {
            String fromLocation = entry.getKey();
            List<ReceiptLine> batch = entry.getValue();

            try {
                // Aggregate by SKU within location
                Map<String, BigDecimal> skuQtys = new HashMap<>();
                for (ReceiptLine line : batch) {
                    skuQtys.merge(line.getSku(), line.getQuantity(), BigDecimal::add);
                }

                // Create task for each SKU in batch
                for (Map.Entry<String, BigDecimal> skuEntry : skuQtys.entrySet()) {
                    String sku = skuEntry.getKey();
                    BigDecimal totalQty = skuEntry.getValue();

                    String destLocation = findBatchDestination(
                        request.getFacility(), request.getStorerKey(), sku, totalQty
                    );

                    if (destLocation == null) {
                        destLocation = findAnyAvailable(request.getFacility());
                    }

                    if (destLocation != null) {
                        ReceiptLine firstLine = batch.stream()
                            .filter(l -> l.getSku().equals(sku))
                            .findFirst()
                            .orElse(batch.get(0));

                        String taskId = createBatchTask(request, firstLine, fromLocation,
                            destLocation, totalQty, batch.size());
                        taskIds.add(taskId);

                        batches.add(BatchInfo.builder()
                            .sku(sku)
                            .fromLocation(fromLocation)
                            .toLocation(destLocation)
                            .quantity(totalQty)
                            .lineCount(batch.size())
                            .build());

                        // Update all lines in batch
                        for (ReceiptLine line : batch) {
                            if (line.getSku().equals(sku)) {
                                updateLineWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Standard batch PA failed for location {}: {}", fromLocation, e.getMessage());
            }
        }

        return BatchPAResult.builder()
            .success(true)
            .taskIds(taskIds)
            .batches(batches)
            .tasksCreated(taskIds.size())
            .variant(VARIANT_STANDARD)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 3: ASN-Level Batch PA (ispBatPA03)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ASN-level batch putaway.
     * Processes entire ASN as one batch regardless of line count.
     *
     * Logic:
     * 1. Treat entire receipt as one batch
     * 2. Find location that can accommodate all SKUs
     * 3. Create single task for entire ASN
     * 4. Used for small/single-vendor shipments
     */
    private BatchPAResult executeASNLevelBatchPA(BatchPARequest request) {
        log.debug("Executing ASN-level batch PA for {}", request.getReceiptKey());

        List<ReceiptLine> lines = getEligibleLines(request.getReceiptKey());
        if (lines.isEmpty()) {
            return BatchPAResult.success(Collections.emptyList(), "No eligible lines");
        }

        List<String> taskIds = new ArrayList<>();
        List<BatchInfo> batches = new ArrayList<>();

        try {
            // Calculate total quantity across all lines
            BigDecimal totalQty = lines.stream()
                .map(ReceiptLine::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            int uniqueSkus = (int) lines.stream()
                .map(ReceiptLine::getSku)
                .distinct()
                .count();

            // Find location for entire ASN
            String destLocation = findASNDestination(
                request.getFacility(), totalQty, uniqueSkus
            );

            if (destLocation == null) {
                destLocation = findAnyAvailable(request.getFacility());
            }

            if (destLocation != null) {
                ReceiptLine firstLine = lines.get(0);

                // Create single task for entire ASN
                String taskId = createASNTask(request, lines, destLocation);
                taskIds.add(taskId);

                batches.add(BatchInfo.builder()
                    .sku("ASN-BATCH")
                    .fromLocation(firstLine.getFromLocation())
                    .toLocation(destLocation)
                    .quantity(totalQty)
                    .lineCount(lines.size())
                    .build());

                // Update all lines
                for (ReceiptLine line : lines) {
                    updateLineWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);
                }
            }

        } catch (Exception e) {
            log.error("ASN-level batch PA failed: {}", e.getMessage());
        }

        return BatchPAResult.builder()
            .success(true)
            .taskIds(taskIds)
            .batches(batches)
            .tasksCreated(taskIds.size())
            .variant(VARIANT_ASN_LEVEL)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 4: Pallet-Based Batch PA (ispBatPA04)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Pallet-based batch putaway.
     * Groups items by pallet/license plate for bulk movement.
     *
     * Logic:
     * 1. Group lines by license plate (ID)
     * 2. Calculate pallet dimensions/weight
     * 3. Find pallet-compatible locations
     * 4. Create one task per pallet
     */
    private BatchPAResult executePalletBasedBatchPA(BatchPARequest request) {
        log.debug("Executing pallet-based batch PA for {}", request.getReceiptKey());

        List<ReceiptLine> lines = getEligibleLines(request.getReceiptKey());
        if (lines.isEmpty()) {
            return BatchPAResult.success(Collections.emptyList(), "No eligible lines");
        }

        // Group by license plate (pallet)
        Map<String, List<ReceiptLine>> byPallet = lines.stream()
            .filter(l -> l.getLicensePlate() != null && !l.getLicensePlate().isEmpty())
            .collect(Collectors.groupingBy(ReceiptLine::getLicensePlate));

        List<String> taskIds = new ArrayList<>();
        List<BatchInfo> batches = new ArrayList<>();

        for (Map.Entry<String, List<ReceiptLine>> entry : byPallet.entrySet()) {
            String palletId = entry.getKey();
            List<ReceiptLine> palletLines = entry.getValue();

            try {
                // Calculate pallet totals
                BigDecimal totalQty = palletLines.stream()
                    .map(ReceiptLine::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalWeight = calculatePalletWeight(request.getStorerKey(), palletLines);
                BigDecimal totalCube = calculatePalletCube(request.getStorerKey(), palletLines);

                // Find pallet-compatible location
                String destLocation = findPalletLocation(
                    request.getFacility(), totalWeight, totalCube
                );

                if (destLocation == null) {
                    destLocation = findAnyPalletLocation(request.getFacility());
                }

                if (destLocation != null) {
                    ReceiptLine firstLine = palletLines.get(0);

                    String taskId = createPalletTask(request, palletId, firstLine.getFromLocation(),
                        destLocation, totalQty, palletLines);
                    taskIds.add(taskId);

                    batches.add(BatchInfo.builder()
                        .sku("PALLET-" + palletId)
                        .fromLocation(firstLine.getFromLocation())
                        .toLocation(destLocation)
                        .quantity(totalQty)
                        .lineCount(palletLines.size())
                        .palletId(palletId)
                        .weight(totalWeight)
                        .cube(totalCube)
                        .build());

                    // Update all lines on pallet
                    for (ReceiptLine line : palletLines) {
                        updateLineWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);
                    }
                }

            } catch (Exception e) {
                log.error("Pallet batch PA failed for pallet {}: {}", palletId, e.getMessage());
            }
        }

        return BatchPAResult.builder()
            .success(true)
            .taskIds(taskIds)
            .batches(batches)
            .tasksCreated(taskIds.size())
            .variant(VARIANT_PALLET_BASED)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Variant 6: SKU-Grouped Batch PA (ispBatPA06)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * SKU-grouped batch putaway.
     * Groups by SKU regardless of location for consolidation.
     *
     * Logic:
     * 1. Group all lines by SKU
     * 2. Consolidate to existing SKU locations
     * 3. Create minimum tasks needed
     * 4. Optimize for SKU-level inventory management
     */
    private BatchPAResult executeSkuGroupedBatchPA(BatchPARequest request) {
        log.debug("Executing SKU-grouped batch PA for {}", request.getReceiptKey());

        List<ReceiptLine> lines = getEligibleLines(request.getReceiptKey());
        if (lines.isEmpty()) {
            return BatchPAResult.success(Collections.emptyList(), "No eligible lines");
        }

        // Group by SKU
        Map<String, List<ReceiptLine>> bySku = lines.stream()
            .collect(Collectors.groupingBy(ReceiptLine::getSku));

        List<String> taskIds = new ArrayList<>();
        List<BatchInfo> batches = new ArrayList<>();

        for (Map.Entry<String, List<ReceiptLine>> entry : bySku.entrySet()) {
            String sku = entry.getKey();
            List<ReceiptLine> skuLines = entry.getValue();

            try {
                BigDecimal totalQty = skuLines.stream()
                    .map(ReceiptLine::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Find existing location with same SKU for consolidation
                String destLocation = findConsolidationLocation(
                    request.getFacility(), request.getStorerKey(), sku, totalQty
                );

                // Fall back to new location
                if (destLocation == null) {
                    destLocation = findSkuOptimalLocation(
                        request.getFacility(), request.getStorerKey(), sku
                    );
                }

                if (destLocation == null) {
                    destLocation = findAnyAvailable(request.getFacility());
                }

                if (destLocation != null) {
                    ReceiptLine firstLine = skuLines.get(0);

                    // Group lines by source location for task creation
                    Map<String, List<ReceiptLine>> bySource = skuLines.stream()
                        .collect(Collectors.groupingBy(ReceiptLine::getFromLocation));

                    for (Map.Entry<String, List<ReceiptLine>> srcEntry : bySource.entrySet()) {
                        String fromLoc = srcEntry.getKey();
                        List<ReceiptLine> srcLines = srcEntry.getValue();

                        BigDecimal srcQty = srcLines.stream()
                            .map(ReceiptLine::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                        String taskId = createBatchTask(request, firstLine, fromLoc,
                            destLocation, srcQty, srcLines.size());
                        taskIds.add(taskId);

                        batches.add(BatchInfo.builder()
                            .sku(sku)
                            .fromLocation(fromLoc)
                            .toLocation(destLocation)
                            .quantity(srcQty)
                            .lineCount(srcLines.size())
                            .build());

                        for (ReceiptLine line : srcLines) {
                            updateLineWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("SKU-grouped batch PA failed for SKU {}: {}", sku, e.getMessage());
            }
        }

        return BatchPAResult.builder()
            .success(true)
            .taskIds(taskIds)
            .batches(batches)
            .tasksCreated(taskIds.size())
            .variant(VARIANT_SKU_GROUPED)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private List<ReceiptLine> getEligibleLines(String receiptKey) {
        try {
            return jdbcTemplate.query(
                """
                SELECT rd.receiptlinenumber, rd.sku, rd.qtyreceived, rd.packkey, rd.uom,
                       rd.toloc, rd.toid, rd.tolottable01, rd.lotxlocxidkey
                FROM dbo.RECEIPTDETAIL rd
                WHERE rd.receiptkey = ?
                AND rd.finalizeflag = 'Y'
                AND rd.qtyreceived > 0
                ORDER BY rd.sku, rd.toloc
                """,
                (rs, rowNum) -> ReceiptLine.builder()
                    .lineNumber(rs.getInt("receiptlinenumber"))
                    .sku(rs.getString("sku"))
                    .quantity(rs.getBigDecimal("qtyreceived"))
                    .packKey(rs.getString("packkey"))
                    .uom(rs.getString("uom"))
                    .fromLocation(rs.getString("toloc"))
                    .licensePlate(rs.getString("toid"))
                    .lottable01(rs.getString("tolottable01"))
                    .lotxlocxidKey(rs.getString("lotxlocxidkey"))
                    .build(),
                receiptKey
            );
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String findBatchDestination(String facility, String storerKey, String sku, BigDecimal qty) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 l.loc FROM dbo.LOC l
                LEFT JOIN dbo.LOTXLOCXID inv ON l.loc = inv.loc AND inv.sku = ?
                WHERE l.facility = ? AND l.status = '1'
                AND (l.maxqty = 0 OR l.currentqty + ? <= l.maxqty)
                ORDER BY CASE WHEN inv.sku IS NOT NULL THEN 0 ELSE 1 END, l.currentqty ASC
                """,
                String.class,
                sku, facility, qty
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findASNDestination(String facility, BigDecimal totalQty, int skuCount) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1'
                AND loctype IN ('STAGING', 'BULK', 'RESERVE')
                AND (maxqty = 0 OR currentqty + ? <= maxqty)
                ORDER BY currentqty ASC
                """,
                String.class,
                facility, totalQty
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findPalletLocation(String facility, BigDecimal weight, BigDecimal cube) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1'
                AND loctype IN ('PALLET', 'BULK', 'HIGHBAY')
                AND (maxweight = 0 OR currentweight + ? <= maxweight)
                AND (maxcube = 0 OR currentcube + ? <= maxcube)
                ORDER BY currentweight ASC
                """,
                String.class,
                facility, weight, cube
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findAnyPalletLocation(String facility) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1'
                AND loctype IN ('PALLET', 'BULK')
                ORDER BY currentqty ASC
                """,
                String.class,
                facility
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findConsolidationLocation(String facility, String storerKey, String sku, BigDecimal qty) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 l.loc FROM dbo.LOC l
                JOIN dbo.LOTXLOCXID inv ON l.loc = inv.loc
                WHERE l.facility = ? AND inv.storerkey = ? AND inv.sku = ?
                AND l.status = '1'
                AND (l.maxqty = 0 OR l.currentqty + ? <= l.maxqty)
                ORDER BY inv.qty DESC
                """,
                String.class,
                facility, storerKey, sku, qty
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findSkuOptimalLocation(String facility, String storerKey, String sku) {
        try {
            // Find location based on SKU's preferred zone
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 l.loc FROM dbo.LOC l
                JOIN dbo.SKU s ON l.putawayzone = s.putawayzone
                WHERE l.facility = ? AND s.storerkey = ? AND s.sku = ?
                AND l.status = '1' AND l.currentqty = 0
                ORDER BY l.loc
                """,
                String.class,
                facility, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String findAnyAvailable(String facility) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT TOP 1 loc FROM dbo.LOC
                WHERE facility = ? AND status = '1'
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

    private BigDecimal calculatePalletWeight(String storerKey, List<ReceiptLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptLine line : lines) {
            try {
                BigDecimal unitWeight = jdbcTemplate.queryForObject(
                    "SELECT stdgrosswgt FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                    BigDecimal.class, storerKey, line.getSku()
                );
                if (unitWeight != null) {
                    total = total.add(unitWeight.multiply(line.getQuantity()));
                }
            } catch (Exception e) {
                // Continue
            }
        }
        return total;
    }

    private BigDecimal calculatePalletCube(String storerKey, List<ReceiptLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptLine line : lines) {
            try {
                BigDecimal unitCube = jdbcTemplate.queryForObject(
                    "SELECT stdcube FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                    BigDecimal.class, storerKey, line.getSku()
                );
                if (unitCube != null) {
                    total = total.add(unitCube.multiply(line.getQuantity()));
                }
            } catch (Exception e) {
                // Continue
            }
        }
        return total;
    }

    private String createBatchTask(BatchPARequest request, ReceiptLine line,
                                    String fromLoc, String toLoc, BigDecimal qty, int lineCount) {
        String taskKey = keyGeneratorService.generateKey("TASKDETAIL");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.TASKDETAIL
            (taskdetailkey, whseid, storerkey, sku, lot, fromloc, fromid, toloc,
             qty, tasktype, status, priority, receiptkey, adddate, addwho, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PA', '0', 5, ?, GETDATE(), ?, ?)
            """,
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            line.getSku(),
            line.getLottable01(),
            fromLoc,
            line.getLicensePlate(),
            toLoc,
            qty,
            request.getReceiptKey(),
            request.getUserId(),
            "Batch PA - " + lineCount + " lines"
        );

        return taskKey;
    }

    private String createASNTask(BatchPARequest request, List<ReceiptLine> lines, String destLocation) {
        String taskKey = keyGeneratorService.generateKey("TASKDETAIL");
        ReceiptLine firstLine = lines.get(0);

        BigDecimal totalQty = lines.stream()
            .map(ReceiptLine::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        jdbcTemplate.update(
            """
            INSERT INTO dbo.TASKDETAIL
            (taskdetailkey, whseid, storerkey, sku, fromloc, fromid, toloc,
             qty, tasktype, status, priority, receiptkey, adddate, addwho, notes)
            VALUES (?, ?, ?, 'MIXED', ?, ?, ?, ?, 'PA', '0', 5, ?, GETDATE(), ?, ?)
            """,
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            firstLine.getFromLocation(),
            firstLine.getLicensePlate(),
            destLocation,
            totalQty,
            request.getReceiptKey(),
            request.getUserId(),
            "ASN Batch - " + lines.size() + " lines"
        );

        return taskKey;
    }

    private String createPalletTask(BatchPARequest request, String palletId, String fromLoc,
                                     String toLoc, BigDecimal qty, List<ReceiptLine> lines) {
        String taskKey = keyGeneratorService.generateKey("TASKDETAIL");

        int skuCount = (int) lines.stream().map(ReceiptLine::getSku).distinct().count();

        jdbcTemplate.update(
            """
            INSERT INTO dbo.TASKDETAIL
            (taskdetailkey, whseid, storerkey, sku, fromloc, fromid, toloc,
             qty, tasktype, status, priority, receiptkey, adddate, addwho, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PA', '0', 3, ?, GETDATE(), ?, ?)
            """,
            taskKey,
            request.getFacility(),
            request.getStorerKey(),
            skuCount == 1 ? lines.get(0).getSku() : "MIXED",
            fromLoc,
            palletId,
            toLoc,
            qty,
            request.getReceiptKey(),
            request.getUserId(),
            "Pallet PA - " + palletId
        );

        return taskKey;
    }

    private void updateLineWithTask(String receiptKey, int lineNumber, String taskId) {
        try {
            jdbcTemplate.update(
                """
                UPDATE dbo.RECEIPTDETAIL
                SET taskdetailkey = ?, editdate = GETDATE()
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                taskId, receiptKey, lineNumber
            );
        } catch (Exception e) {
            log.debug("Could not update receipt line: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class BatchPARequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
    }

    @lombok.Data
    @lombok.Builder
    public static class BatchPAResult {
        private boolean success;
        private List<String> taskIds;
        private List<BatchInfo> batches;
        private int tasksCreated;
        private String variant;
        private String message;

        public static BatchPAResult success(List<String> taskIds, String message) {
            return BatchPAResult.builder()
                .success(true)
                .taskIds(taskIds)
                .batches(Collections.emptyList())
                .tasksCreated(taskIds.size())
                .message(message)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class BatchInfo {
        private String sku;
        private String fromLocation;
        private String toLocation;
        private BigDecimal quantity;
        private int lineCount;
        private String palletId;
        private BigDecimal weight;
        private BigDecimal cube;
    }

    @lombok.Data
    @lombok.Builder
    private static class ReceiptLine {
        private int lineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String fromLocation;
        private String licensePlate;
        private String lottable01;
        private String lotxlocxidKey;
    }
}
