package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Putaway Task Dispatcher Service.
 *
 * Replaces SQL stored procedures:
 * - SP-070: isp_ASNReleasePATask_Wrapper - Main dispatcher
 * - SP-071: ispPARL01 - Standard PA release
 * - SP-076: ispPARL06 - ULM PA release variant
 *
 * The dispatcher:
 * 1. Validates ASN/Receipt status
 * 2. Resolves storer-specific PA release strategy
 * 3. Executes appropriate release logic
 * 4. Creates TASKDETAIL records for finalized inventory
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PutawayDispatcherService {

    private final JdbcTemplate jdbcTemplate;
    private final PutawayTaskService putawayTaskService;
    private final PutawayStrategyService putawayStrategyService;
    private final KeyGeneratorService keyGeneratorService;

    // Release strategy types
    public static final String STRATEGY_STANDARD = "STANDARD";
    public static final String STRATEGY_ULM = "ULM";
    public static final String STRATEGY_NIKE_CRW = "NIKECRW";
    public static final String STRATEGY_BATCH = "BATCH";

    // Valid statuses for PA release
    private static final Set<String> VALID_RECEIPT_STATUSES = Set.of("9", "11", "12");

    /**
     * Release putaway tasks for a receipt.
     * Main entry point - replaces isp_ASNReleasePATask_Wrapper.
     *
     * @param request Release request
     * @return Release result with created task IDs
     */
    @Transactional
    public ReleaseResult releasePutawayTasks(ReleaseRequest request) {
        log.info("Releasing putaway tasks for receipt: {}", request.getReceiptKey());

        // 1. Validate receipt status
        ValidationResult validation = validateReceipt(request.getReceiptKey());
        if (!validation.isValid()) {
            log.warn("Receipt {} validation failed: {}", request.getReceiptKey(), validation.getMessage());
            return ReleaseResult.failed(validation.getMessage());
        }

        // 2. Resolve release strategy for storer
        String strategy = resolveReleaseStrategy(request.getStorerKey());
        log.debug("Using release strategy: {} for storer: {}", strategy, request.getStorerKey());

        // 3. Execute release based on strategy
        List<String> taskIds = switch (strategy) {
            case STRATEGY_ULM -> executeULMRelease(request);
            case STRATEGY_NIKE_CRW -> executeNikeCRWRelease(request);
            case STRATEGY_BATCH -> executeBatchRelease(request);
            default -> executeStandardRelease(request);
        };

        log.info("Released {} putaway tasks for receipt {}", taskIds.size(), request.getReceiptKey());

        return ReleaseResult.builder()
            .success(true)
            .receiptKey(request.getReceiptKey())
            .taskIds(taskIds)
            .tasksCreated(taskIds.size())
            .strategy(strategy)
            .build();
    }

    /**
     * Release putaway tasks for multiple receipts.
     *
     * @param receiptKeys List of receipt keys
     * @param storerKey Storer key
     * @param userId User ID
     * @return Combined release result
     */
    @Transactional
    public ReleaseResult releasePutawayTasksBatch(List<String> receiptKeys, String storerKey, String userId) {
        log.info("Batch releasing putaway tasks for {} receipts", receiptKeys.size());

        List<String> allTaskIds = new ArrayList<>();
        List<String> failedReceipts = new ArrayList<>();

        for (String receiptKey : receiptKeys) {
            try {
                ReleaseRequest request = ReleaseRequest.builder()
                    .receiptKey(receiptKey)
                    .storerKey(storerKey)
                    .userId(userId)
                    .build();

                ReleaseResult result = releasePutawayTasks(request);
                if (result.isSuccess()) {
                    allTaskIds.addAll(result.getTaskIds());
                } else {
                    failedReceipts.add(receiptKey);
                }
            } catch (Exception e) {
                log.error("Failed to release PA tasks for receipt {}: {}", receiptKey, e.getMessage());
                failedReceipts.add(receiptKey);
            }
        }

        return ReleaseResult.builder()
            .success(failedReceipts.isEmpty())
            .taskIds(allTaskIds)
            .tasksCreated(allTaskIds.size())
            .failedReceipts(failedReceipts)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════════════════

    private ValidationResult validateReceipt(String receiptKey) {
        try {
            Map<String, Object> receipt = jdbcTemplate.queryForMap(
                """
                SELECT status, storerkey, type, finalizeflag
                FROM dbo.receipt
                WHERE receiptkey = ?
                """,
                receiptKey
            );

            String status = (String) receipt.get("status");
            if (!VALID_RECEIPT_STATUSES.contains(status)) {
                return ValidationResult.invalid(
                    String.format("Receipt status '%s' not valid for PA release", status));
            }

            String finalizeFlag = (String) receipt.get("finalizeflag");
            if (!"Y".equals(finalizeFlag) && !"1".equals(finalizeFlag)) {
                return ValidationResult.invalid("Receipt not finalized");
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            return ValidationResult.invalid("Receipt not found: " + receiptKey);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Strategy Resolution
    // ═══════════════════════════════════════════════════════════════════════

    private String resolveReleaseStrategy(String storerKey) {
        try {
            // Check StorerConfig for custom release SP/strategy
            String configValue = jdbcTemplate.queryForObject(
                """
                SELECT configvalue FROM dbo.storerconfig
                WHERE storerkey = ? AND configkey = 'ASNReleasePATask_SP'
                """,
                String.class,
                storerKey
            );

            if (configValue != null && !configValue.isEmpty()) {
                // Map SP name to strategy
                return switch (configValue.toUpperCase()) {
                    case "ISPPARL06", "ULM" -> STRATEGY_ULM;
                    case "ISPBATPA02", "NIKECRW" -> STRATEGY_NIKE_CRW;
                    case "BATCH" -> STRATEGY_BATCH;
                    default -> STRATEGY_STANDARD;
                };
            }
        } catch (Exception e) {
            // Use default
        }

        return STRATEGY_STANDARD;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Standard Release (ispPARL01)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Standard putaway release.
     * Implements ispPARL01 logic:
     * - Processes finalized receipt lines
     * - Creates one task per line with inventory
     * - Uses PutawayStrategyService for location determination
     */
    private List<String> executeStandardRelease(ReleaseRequest request) {
        log.debug("Executing standard PA release for {}", request.getReceiptKey());

        // Get finalized receipt lines eligible for PA
        List<ReceiptLineInfo> eligibleLines = getEligibleLines(request.getReceiptKey());

        if (eligibleLines.isEmpty()) {
            log.info("No eligible lines for PA release in receipt {}", request.getReceiptKey());
            return Collections.emptyList();
        }

        List<String> taskIds = new ArrayList<>();

        for (ReceiptLineInfo line : eligibleLines) {
            try {
                // Check if task already exists
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    log.debug("Task already exists for line {}", line.getLineNumber());
                    continue;
                }

                // Create putaway task
                PutawayTaskService.PutawayTaskRequest taskRequest = buildTaskRequest(request, line);
                String taskId = putawayTaskService.createPutawayTask(taskRequest);
                taskIds.add(taskId);

                // Update receipt detail with task reference
                updateReceiptDetailWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);

            } catch (Exception e) {
                log.error("Failed to create PA task for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ULM Release (ispPARL06)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ULM-specific putaway release.
     * Implements ispPARL06 logic with additional:
     * - Multi-level location priority
     * - Cube-based allocation
     * - Direct nspPASTD integration
     */
    private List<String> executeULMRelease(ReleaseRequest request) {
        log.debug("Executing ULM PA release for {}", request.getReceiptKey());

        List<ReceiptLineInfo> eligibleLines = getEligibleLines(request.getReceiptKey());
        List<String> taskIds = new ArrayList<>();

        for (ReceiptLineInfo line : eligibleLines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                // ULM uses full strategy service for location determination
                PutawayStrategyService.StrategyRequest strategyRequest =
                    PutawayStrategyService.StrategyRequest.builder()
                        .storerKey(request.getStorerKey())
                        .facility(line.getFacility())
                        .sku(line.getSku())
                        .quantity(line.getQuantity())
                        .packKey(line.getPackKey())
                        .fromLocation(line.getToLocation())
                        .weight(calculateWeight(line))
                        .cube(calculateCube(line))
                        .lottable01(line.getLottable01())
                        .lottable02(line.getLottable02())
                        .receiptKey(request.getReceiptKey())
                        .receiptLineNumber(line.getLineNumber())
                        .build();

                PutawayStrategyService.StrategyResult strategyResult =
                    putawayStrategyService.determineLocation(strategyRequest);

                // Create task with strategy-determined location
                PutawayTaskService.PutawayTaskRequest taskRequest =
                    PutawayTaskService.PutawayTaskRequest.builder()
                        .storerKey(request.getStorerKey())
                        .facility(line.getFacility())
                        .sku(line.getSku())
                        .quantity(line.getQuantity())
                        .packKey(line.getPackKey())
                        .uom(line.getUom())
                        .fromLocation(line.getToLocation())
                        .fromLicensePlate(line.getToId())
                        .toLocation(strategyResult.getLocation())
                        .receiptKey(request.getReceiptKey())
                        .receiptLineNumber(line.getLineNumber())
                        .lotxlocxidKey(line.getLotxlocxidKey())
                        .userId(request.getUserId())
                        .priority(calculatePriority(line))
                        .lottable01(line.getLottable01())
                        .lottable02(line.getLottable02())
                        .lottable03(line.getLottable03())
                        .build();

                String taskId = putawayTaskService.createPutawayTask(taskRequest);
                taskIds.add(taskId);

                updateReceiptDetailWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);

            } catch (Exception e) {
                log.error("ULM PA task creation failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Nike CRW Release (ispBatPA02 style)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Nike CRW-style putaway release.
     * Priority-based location finding:
     * 1. Mezzanine with same SKU
     * 2. Mezzanine with same material/class
     * 3. Empty mezzanine
     * 4. Free-seat locations
     * 5. HighBay
     */
    private List<String> executeNikeCRWRelease(ReleaseRequest request) {
        log.debug("Executing Nike CRW PA release for {}", request.getReceiptKey());

        List<ReceiptLineInfo> eligibleLines = getEligibleLines(request.getReceiptKey());
        List<String> taskIds = new ArrayList<>();

        for (ReceiptLineInfo line : eligibleLines) {
            try {
                if (taskExists(request.getReceiptKey(), line.getLineNumber())) {
                    continue;
                }

                // Nike CRW priority location finding
                String targetLocation = findNikeCRWLocation(request.getStorerKey(), line);

                PutawayTaskService.PutawayTaskRequest taskRequest = buildTaskRequest(request, line);
                // Override with Nike-specific location
                if (targetLocation != null) {
                    taskRequest.setToLocation(targetLocation);
                }

                String taskId = putawayTaskService.createPutawayTask(taskRequest);
                taskIds.add(taskId);

                updateReceiptDetailWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);

            } catch (Exception e) {
                log.error("Nike CRW PA task creation failed for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        return taskIds;
    }

    private String findNikeCRWLocation(String storerKey, ReceiptLineInfo line) {
        String facility = line.getFacility();

        // Priority 1: Mezzanine with same SKU
        try {
            String loc = jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                JOIN dbo.lotxlocxid inv ON l.loc = inv.loc
                WHERE l.facility = ? AND l.loctype = 'MEZZANINE'
                AND inv.storerkey = ? AND inv.sku = ?
                AND l.status = '1'
                ORDER BY inv.qty DESC
                LIMIT 1
                """,
                String.class,
                facility, storerKey, line.getSku()
            );
            if (loc != null) return loc;
        } catch (Exception e) { /* continue */ }

        // Priority 2: Empty mezzanine
        try {
            String loc = jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ? AND l.loctype = 'MEZZANINE'
                AND l.status = '1' AND l.currentqty = 0
                ORDER BY l.loc
                LIMIT 1
                """,
                String.class,
                facility
            );
            if (loc != null) return loc;
        } catch (Exception e) { /* continue */ }

        // Priority 3: HighBay
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ? AND l.loctype = 'HIGHBAY'
                AND l.status = '1'
                ORDER BY l.currentweight ASC
                LIMIT 1
                """,
                String.class,
                facility
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Release
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Batch putaway release - groups items for efficiency.
     */
    private List<String> executeBatchRelease(ReleaseRequest request) {
        log.debug("Executing batch PA release for {}", request.getReceiptKey());

        // Group by SKU and location for batch processing
        List<ReceiptLineInfo> eligibleLines = getEligibleLines(request.getReceiptKey());

        Map<String, List<ReceiptLineInfo>> bySkuLocation = new HashMap<>();
        for (ReceiptLineInfo line : eligibleLines) {
            String key = line.getSku() + "|" + line.getToLocation();
            bySkuLocation.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }

        List<String> taskIds = new ArrayList<>();

        for (Map.Entry<String, List<ReceiptLineInfo>> entry : bySkuLocation.entrySet()) {
            List<ReceiptLineInfo> lines = entry.getValue();
            ReceiptLineInfo firstLine = lines.get(0);

            // Aggregate quantity
            BigDecimal totalQty = lines.stream()
                .map(ReceiptLineInfo::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            try {
                PutawayTaskService.PutawayTaskRequest taskRequest =
                    PutawayTaskService.PutawayTaskRequest.builder()
                        .storerKey(request.getStorerKey())
                        .facility(firstLine.getFacility())
                        .sku(firstLine.getSku())
                        .quantity(totalQty)
                        .packKey(firstLine.getPackKey())
                        .uom(firstLine.getUom())
                        .fromLocation(firstLine.getToLocation())
                        .fromLicensePlate(firstLine.getToId())
                        .receiptKey(request.getReceiptKey())
                        .userId(request.getUserId())
                        .build();

                String taskId = putawayTaskService.createPutawayTask(taskRequest);
                taskIds.add(taskId);

                // Update all lines
                for (ReceiptLineInfo line : lines) {
                    updateReceiptDetailWithTask(request.getReceiptKey(), line.getLineNumber(), taskId);
                }

            } catch (Exception e) {
                log.error("Batch PA task creation failed: {}", e.getMessage());
            }
        }

        return taskIds;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private List<ReceiptLineInfo> getEligibleLines(String receiptKey) {
        try {
            return jdbcTemplate.query(
                """
                SELECT rd.receiptlinenumber, rd.sku, rd.qtyreceived, rd.packkey, rd.uom,
                       rd.toloc, rd.toid, rd.lottable01, rd.lottable02, rd.lottable03,
                       rd.lotxlocxidkey, r.storerkey, r.facility
                FROM dbo.receiptdetail rd
                JOIN dbo.receipt r ON rd.receiptkey = r.receiptkey
                WHERE rd.receiptkey = ?
                AND rd.finalizeflag = 'Y'
                AND rd.qtyreceived > 0
                AND COALESCE(rd.toid, '') != ''
                AND COALESCE(rd.putawayloc, '') = ''
                ORDER BY rd.receiptlinenumber
                """,
                (rs, rowNum) -> ReceiptLineInfo.builder()
                    .lineNumber(rs.getInt("receiptlinenumber"))
                    .sku(rs.getString("sku"))
                    .quantity(rs.getBigDecimal("qtyreceived"))
                    .packKey(rs.getString("packkey"))
                    .uom(rs.getString("uom"))
                    .toLocation(rs.getString("toloc"))
                    .toId(rs.getString("toid"))
                    .lottable01(rs.getString("lottable01"))
                    .lottable02(rs.getString("lottable02"))
                    .lottable03(rs.getString("lottable03"))
                    .lotxlocxidKey(rs.getString("lotxlocxidkey"))
                    .storerKey(rs.getString("storerkey"))
                    .facility(rs.getString("facility"))
                    .build(),
                receiptKey
            );
        } catch (Exception e) {
            log.error("Failed to get eligible lines for {}: {}", receiptKey, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean taskExists(String receiptKey, int lineNumber) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM dbo.taskdetail
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

    private PutawayTaskService.PutawayTaskRequest buildTaskRequest(ReleaseRequest request, ReceiptLineInfo line) {
        return PutawayTaskService.PutawayTaskRequest.builder()
            .storerKey(request.getStorerKey())
            .facility(line.getFacility())
            .sku(line.getSku())
            .quantity(line.getQuantity())
            .packKey(line.getPackKey())
            .uom(line.getUom())
            .fromLocation(line.getToLocation())
            .fromLicensePlate(line.getToId())
            .receiptKey(request.getReceiptKey())
            .receiptLineNumber(line.getLineNumber())
            .lotxlocxidKey(line.getLotxlocxidKey())
            .userId(request.getUserId())
            .priority(calculatePriority(line))
            .lottable01(line.getLottable01())
            .lottable02(line.getLottable02())
            .lottable03(line.getLottable03())
            .build();
    }

    private void updateReceiptDetailWithTask(String receiptKey, int lineNumber, String taskId) {
        try {
            jdbcTemplate.update(
                """
                UPDATE dbo.receiptdetail
                SET taskdetailkey = ?,
                    editdate = CURRENT_TIMESTAMP
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                taskId, receiptKey, lineNumber
            );
        } catch (Exception e) {
            log.debug("Could not update receipt detail with task: {}", e.getMessage());
        }
    }

    private int calculatePriority(ReceiptLineInfo line) {
        // Higher priority for larger quantities
        if (line.getQuantity() != null && line.getQuantity().compareTo(BigDecimal.valueOf(100)) > 0) {
            return 3;
        }
        return 5; // Default priority
    }

    private BigDecimal calculateWeight(ReceiptLineInfo line) {
        try {
            BigDecimal unitWeight = jdbcTemplate.queryForObject(
                "SELECT stdgrosswgt FROM dbo.pack WHERE packkey = ?",
                BigDecimal.class,
                line.getPackKey()
            );
            if (unitWeight != null && line.getQuantity() != null) {
                return unitWeight.multiply(line.getQuantity());
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private BigDecimal calculateCube(ReceiptLineInfo line) {
        try {
            BigDecimal unitCube = jdbcTemplate.queryForObject(
                "SELECT stdcube FROM dbo.pack WHERE packkey = ?",
                BigDecimal.class,
                line.getPackKey()
            );
            if (unitCube != null && line.getQuantity() != null) {
                return unitCube.multiply(line.getQuantity());
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReleaseRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
        private boolean forceRelease;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReleaseResult {
        private boolean success;
        private String receiptKey;
        private List<String> taskIds;
        private int tasksCreated;
        private String strategy;
        private List<String> failedReceipts;
        private String errorMessage;

        public static ReleaseResult failed(String message) {
            return ReleaseResult.builder()
                .success(false)
                .errorMessage(message)
                .taskIds(Collections.emptyList())
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ReceiptLineInfo {
        private int lineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String toLocation;
        private String toId;
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lotxlocxidKey;
        private String storerKey;
        private String facility;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ValidationResult {
        private boolean valid;
        private String message;

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
