package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service for managing putaway tasks.
 *
 * Implements the logic from:
 * - lsp_ASNReleasePATask_Wrapper
 * - ispPARL* series (putaway release variants)
 * - nspPASTD (putaway strategy determination)
 *
 * Putaway task flow:
 * 1. Determine target location using strategy
 * 2. Create task in TASKDETAIL table
 * 3. Assign to user/equipment if configured
 * 4. Track completion status
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PutawayTaskService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;
    private final PutawayStrategyService putawayStrategyService;

    // Task type constants
    public static final String TASK_TYPE_PUTAWAY = "PA";
    public static final String TASK_TYPE_REPLENISH = "RP";
    public static final String TASK_TYPE_MOVE = "MV";

    // Task status constants
    public static final String STATUS_PENDING = "0";
    public static final String STATUS_ASSIGNED = "1";
    public static final String STATUS_IN_PROGRESS = "5";
    public static final String STATUS_COMPLETED = "9";
    public static final String STATUS_CANCELLED = "C";

    /**
     * Create a putaway task for an inventory record.
     *
     * @param request Task creation request
     * @return Created task key
     */
    @Transactional
    public String createPutawayTask(PutawayTaskRequest request) {
        log.debug("Creating putaway task: sku={}, from={}, qty={}",
            request.getSku(), request.getFromLocation(), request.getQuantity());

        // Determine target location using putaway strategy
        String targetLocation = determinePutawayLocation(request);

        // Generate task key
        String taskKey = keyGeneratorService.generateKey("TASKDETAIL");

        // Insert task into TASKDETAIL
        jdbcTemplate.update(
            """
            INSERT INTO dbo.taskdetail (
                taskdetailkey, tasktype, storerkey, sku,
                fromloc, fromid, toloc, toid,
                qty, status, priority, assignmenttype,
                receiptkey, receiptlinenumber, lotxlocxidkey,
                packkey, uom, startdate, duedate,
                adddate, addwho, editdate, editwho
            )
            VALUES (?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, 'MANUAL',
                    ?, ?, ?,
                    ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 DAY',
                    CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
            """,
            taskKey,
            TASK_TYPE_PUTAWAY,
            request.getStorerKey(),
            request.getSku(),
            request.getFromLocation(),
            request.getFromLicensePlate(),
            targetLocation,
            request.getToLicensePlate(),
            request.getQuantity(),
            STATUS_PENDING,
            request.getPriority(),
            request.getReceiptKey(),
            request.getReceiptLineNumber(),
            request.getLotxlocxidKey(),
            request.getPackKey(),
            request.getUom(),
            request.getUserId(),
            request.getUserId()
        );

        log.info("Created putaway task: key={}, from={}, to={}, sku={}, qty={}",
            taskKey, request.getFromLocation(), targetLocation, request.getSku(), request.getQuantity());

        return taskKey;
    }

    /**
     * Create putaway tasks in batch.
     *
     * @param requests List of task requests
     * @return List of created task keys
     */
    @Transactional
    public List<String> createPutawayTasksBatch(List<PutawayTaskRequest> requests) {
        log.info("Creating {} putaway tasks in batch", requests.size());

        List<String> taskKeys = new ArrayList<>();
        for (PutawayTaskRequest request : requests) {
            try {
                String taskKey = createPutawayTask(request);
                taskKeys.add(taskKey);
            } catch (Exception e) {
                log.error("Failed to create putaway task for sku {}: {}",
                    request.getSku(), e.getMessage());
            }
        }

        log.info("Created {} putaway tasks", taskKeys.size());
        return taskKeys;
    }

    /**
     * Cancel a putaway task.
     *
     * @param taskKey Task to cancel
     * @param userId User cancelling
     * @param reason Cancellation reason
     * @return true if cancelled
     */
    @Transactional
    public boolean cancelTask(String taskKey, String userId, String reason) {
        log.warn("Cancelling task: {} - {}", taskKey, reason);

        // Can only cancel pending or assigned tasks
        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.taskdetail
            SET status = ?,
                notes = COALESCE(notes, '') || ' CANCELLED: ' || ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE taskdetailkey = ?
            AND status IN (?, ?)
            """,
            STATUS_CANCELLED,
            reason,
            userId,
            taskKey,
            STATUS_PENDING, STATUS_ASSIGNED
        );

        if (updated > 0) {
            log.info("Task cancelled: {}", taskKey);
            return true;
        }

        log.warn("Task {} could not be cancelled (wrong status or not found)", taskKey);
        return false;
    }

    /**
     * Cancel multiple tasks (for compensation).
     *
     * @param taskKeys Tasks to cancel
     * @param userId User cancelling
     * @param reason Cancellation reason
     * @return Number of tasks cancelled
     */
    @Transactional
    public int cancelTasksBatch(List<String> taskKeys, String userId, String reason) {
        log.warn("COMPENSATION: Cancelling {} tasks", taskKeys.size());

        int cancelledCount = 0;
        for (String taskKey : taskKeys) {
            if (cancelTask(taskKey, userId, reason)) {
                cancelledCount++;
            }
        }

        log.info("COMPENSATION complete: Cancelled {}/{} tasks", cancelledCount, taskKeys.size());
        return cancelledCount;
    }

    /**
     * Assign a task to a user.
     *
     * @param taskKey Task to assign
     * @param assigneeId User to assign to
     * @param userId User making the assignment
     * @return true if assigned
     */
    @Transactional
    public boolean assignTask(String taskKey, String assigneeId, String userId) {
        log.info("Assigning task {} to {}", taskKey, assigneeId);

        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.taskdetail
            SET status = ?,
                userkey = ?,
                assignmenttype = 'USER',
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE taskdetailkey = ?
            AND status = ?
            """,
            STATUS_ASSIGNED,
            assigneeId,
            userId,
            taskKey,
            STATUS_PENDING
        );

        return updated > 0;
    }

    /**
     * Complete a task.
     *
     * @param taskKey Task to complete
     * @param userId User completing
     * @param actualToLocation Actual location where inventory was placed
     * @return true if completed
     */
    @Transactional
    public boolean completeTask(String taskKey, String userId, String actualToLocation) {
        log.info("Completing task {} at location {}", taskKey, actualToLocation);

        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.taskdetail
            SET status = ?,
                actualtoloc = ?,
                enddate = CURRENT_TIMESTAMP,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE taskdetailkey = ?
            AND status IN (?, ?)
            """,
            STATUS_COMPLETED,
            actualToLocation,
            userId,
            taskKey,
            STATUS_ASSIGNED, STATUS_IN_PROGRESS
        );

        return updated > 0;
    }

    /**
     * Get pending tasks for a receipt.
     *
     * @param receiptKey Receipt to get tasks for
     * @return List of pending tasks
     */
    @Transactional(readOnly = true)
    public List<TaskInfo> getPendingTasksForReceipt(String receiptKey) {
        return jdbcTemplate.query(
            """
            SELECT taskdetailkey, tasktype, storerkey, sku,
                   fromloc, fromid, toloc, toid,
                   qty, status, priority, userkey
            FROM dbo.taskdetail
            WHERE receiptkey = ?
            AND status IN (?, ?)
            ORDER BY priority, adddate
            """,
            (rs, rowNum) -> TaskInfo.builder()
                .taskKey(rs.getString("taskdetailkey"))
                .taskType(rs.getString("tasktype"))
                .storerKey(rs.getString("storerkey"))
                .sku(rs.getString("sku"))
                .fromLocation(rs.getString("fromloc"))
                .fromLicensePlate(rs.getString("fromid"))
                .toLocation(rs.getString("toloc"))
                .toLicensePlate(rs.getString("toid"))
                .quantity(rs.getBigDecimal("qty"))
                .status(rs.getString("status"))
                .priority(rs.getInt("priority"))
                .assignedUser(rs.getString("userkey"))
                .build(),
            receiptKey,
            STATUS_PENDING, STATUS_ASSIGNED
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Putaway Strategy Logic
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Determine the target putaway location using strategy rules.
     *
     * Delegates to PutawayStrategyService which implements the full
     * nspPASTD logic with 25+ PAType strategies.
     */
    private String determinePutawayLocation(PutawayTaskRequest request) {
        // 1. Check for directed putaway location (pre-configured on receipt)
        String directedLoc = getDirectedPutawayLocation(request);
        if (directedLoc != null) {
            log.debug("Using directed putaway location: {}", directedLoc);
            return directedLoc;
        }

        // 2. Use PutawayStrategyService for full strategy execution
        PutawayStrategyService.StrategyRequest strategyRequest = PutawayStrategyService.StrategyRequest.builder()
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .sku(request.getSku())
            .quantity(request.getQuantity())
            .packKey(request.getPackKey())
            .uom(request.getUom())
            .fromLocation(request.getFromLocation())
            .lottable01(request.getLottable01())
            .lottable02(request.getLottable02())
            .lottable03(request.getLottable03())
            .receiptKey(request.getReceiptKey())
            .receiptLineNumber(request.getReceiptLineNumber())
            .build();

        PutawayStrategyService.StrategyResult result = putawayStrategyService.determineLocation(strategyRequest);

        if (result.isSuccess()) {
            log.debug("Strategy {} returned location: {}", result.getPaType(), result.getLocation());
            return result.getLocation();
        }

        // 3. Fall back to staging location
        log.warn("Strategy failed, using staging location");
        return "STAGE";
    }

    private String getDirectedPutawayLocation(PutawayTaskRequest request) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT toloc FROM dbo.receiptdetail
                WHERE receiptkey = ? AND receiptlinenumber = ?
                AND toloc IS NOT NULL AND toloc != ''
                """,
                String.class,
                request.getReceiptKey(), request.getReceiptLineNumber()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PutawayTaskRequest {
        private String storerKey;
        private String facility;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String fromLocation;
        private String fromLicensePlate;
        private String toLocation;  // Optional: override strategy
        private String toLicensePlate;
        private String receiptKey;
        private int receiptLineNumber;
        private String lotxlocxidKey;
        private String userId;

        @lombok.Builder.Default
        private int priority = 5;

        // Lottables for strategy matching
        private String lottable01;
        private String lottable02;
        private String lottable03;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TaskInfo {
        private String taskKey;
        private String taskType;
        private String storerKey;
        private String sku;
        private String fromLocation;
        private String fromLicensePlate;
        private String toLocation;
        private String toLicensePlate;
        private BigDecimal quantity;
        private String status;
        private int priority;
        private String assignedUser;
    }
}
