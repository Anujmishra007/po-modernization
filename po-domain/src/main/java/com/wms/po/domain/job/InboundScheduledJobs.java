package com.wms.po.domain.job;

import com.wms.po.domain.service.PutawayDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Inbound Scheduled Jobs.
 *
 * Replaces:
 * - JOB-001: AutoPopulatePOToASN (isp_NIKEKR_PopulatePOTOASN)
 * - JOB-002: AutoFinalizeASN (lsp_FinalizeReceipt_Wrapper)
 * - JOB-003: AutoReleasePATask (lsp_ASNReleasePATask_Wrapper)
 *
 * Runs automated background processing for inbound operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboundScheduledJobs {

    private final JdbcTemplate jdbcTemplate;
    private final PutawayDispatcherService putawayDispatcherService;

    @Value("${wms.jobs.auto-populate.enabled:true}")
    private boolean autoPopulateEnabled;

    @Value("${wms.jobs.auto-finalize.enabled:true}")
    private boolean autoFinalizeEnabled;

    @Value("${wms.jobs.auto-pa-release.enabled:true}")
    private boolean autoPAReleaseEnabled;

    @Value("${wms.jobs.auto-populate.storers:}")
    private List<String> autoPopulateStorers;

    @Value("${wms.jobs.auto-finalize.storers:}")
    private List<String> autoFinalizeStorers;

    // ═══════════════════════════════════════════════════════════════════════
    // JOB-001: Auto Populate PO to ASN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Auto-populate PO lines to ASN/Receipt.
     * Runs every 5 minutes.
     *
     * Replaces: isp_NIKEKR_PopulatePOTOASN
     *
     * Finds POs ready for receiving and creates ASN/Receipt records.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000) // 5 min
    public void autoPopulatePOToASN() {
        if (!autoPopulateEnabled) {
            return;
        }

        log.info("Starting AutoPopulatePOToASN job");
        int processed = 0;

        try {
            // Find POs ready for auto-populate
            List<Map<String, Object>> pendingPOs = findPendingPOsForPopulate();

            for (Map<String, Object> po : pendingPOs) {
                String poKey = (String) po.get("pokey");
                String storerKey = (String) po.get("storerkey");

                try {
                    // Check if storer is configured for auto-populate
                    if (!isAutoPopulateStorer(storerKey)) {
                        continue;
                    }

                    // Create ASN from PO
                    String receiptKey = createASNFromPO(poKey, storerKey);
                    if (receiptKey != null) {
                        log.info("Created ASN {} from PO {}", receiptKey, poKey);
                        processed++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to auto-populate PO {}: {}", poKey, e.getMessage());
                }
            }

            log.info("AutoPopulatePOToASN complete: {} POs processed", processed);

        } catch (Exception e) {
            log.error("AutoPopulatePOToASN job failed: {}", e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> findPendingPOsForPopulate() {
        return jdbcTemplate.queryForList(
            "SELECT p.pokey, p.storerkey, p.externpokey " +
            "FROM dbo.PO p " +
            "WHERE p.status IN ('0', '1') " + // Open or Approved
            "AND p.expectedreceiptdate <= DATEADD(day, 1, GETDATE()) " +
            "AND NOT EXISTS (SELECT 1 FROM dbo.RECEIPT r WHERE r.pokey = p.pokey) " +
            "AND EXISTS (SELECT 1 FROM dbo.STORERCONFIG sc " +
            "    WHERE sc.storerkey = p.storerkey " +
            "    AND sc.configkey = 'AUTOPOPULATEPO' AND sc.configvalue = '1') " +
            "ORDER BY p.expectedreceiptdate"
        );
    }

    private boolean isAutoPopulateStorer(String storerKey) {
        if (autoPopulateStorers == null || autoPopulateStorers.isEmpty()) {
            return true; // All storers if not configured
        }
        return autoPopulateStorers.contains(storerKey);
    }

    private String createASNFromPO(String poKey, String storerKey) {
        // Generate receipt key
        String receiptKey = generateReceiptKey();

        // Create receipt header from PO
        jdbcTemplate.update(
            "INSERT INTO dbo.RECEIPT (receiptkey, storerkey, pokey, externreceiptkey, " +
            "type, status, expectedreceiptdate, adddate, addwho) " +
            "SELECT ?, p.storerkey, p.pokey, p.externpokey, 'ASN', '0', " +
            "p.expectedreceiptdate, GETDATE(), 'AUTOJOB' " +
            "FROM dbo.PO p WHERE p.pokey = ?",
            receiptKey, poKey
        );

        // Create receipt detail from PO detail
        jdbcTemplate.update(
            "INSERT INTO dbo.RECEIPTDETAIL (receiptkey, receiptlinenumber, storerkey, " +
            "sku, pokey, polinenumber, qtyexpected, packkey, uom, status, adddate, addwho) " +
            "SELECT ?, pd.polinenumber, pd.storerkey, pd.sku, pd.pokey, pd.polinenumber, " +
            "pd.qtyordered, pd.packkey, pd.uom, '0', GETDATE(), 'AUTOJOB' " +
            "FROM dbo.PODETAIL pd WHERE pd.pokey = ?",
            receiptKey, poKey
        );

        // Update PO status to Allocated
        jdbcTemplate.update(
            "UPDATE dbo.PO SET status = '2' WHERE pokey = ?",
            poKey
        );

        return receiptKey;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JOB-002: Auto Finalize ASN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Auto-finalize receipts that are ready.
     * Runs every 5 minutes.
     *
     * Replaces: Calls to lsp_FinalizeReceipt_Wrapper
     *
     * Finds receipts with all lines received and triggers finalization.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 120000) // 5 min
    public void autoFinalizeASN() {
        if (!autoFinalizeEnabled) {
            return;
        }

        log.info("Starting AutoFinalizeASN job");
        int processed = 0;

        try {
            // Find receipts ready for auto-finalize
            List<Map<String, Object>> pendingReceipts = findReceiptsForAutoFinalize();

            for (Map<String, Object> receipt : pendingReceipts) {
                String receiptKey = (String) receipt.get("receiptkey");
                String storerKey = (String) receipt.get("storerkey");

                try {
                    // Check if storer is configured for auto-finalize
                    if (!isAutoFinalizeStorer(storerKey)) {
                        continue;
                    }

                    // Trigger finalization
                    boolean success = triggerFinalization(receiptKey, storerKey);
                    if (success) {
                        log.info("Auto-finalized receipt {}", receiptKey);
                        processed++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to auto-finalize receipt {}: {}", receiptKey, e.getMessage());
                }
            }

            log.info("AutoFinalizeASN complete: {} receipts finalized", processed);

        } catch (Exception e) {
            log.error("AutoFinalizeASN job failed: {}", e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> findReceiptsForAutoFinalize() {
        return jdbcTemplate.queryForList(
            "SELECT r.receiptkey, r.storerkey " +
            "FROM dbo.RECEIPT r " +
            "WHERE r.status IN ('3', '4') " + // Received or Verified
            "AND NOT EXISTS (SELECT 1 FROM dbo.RECEIPTDETAIL rd " +
            "    WHERE rd.receiptkey = r.receiptkey AND rd.status < '3') " +
            "AND EXISTS (SELECT 1 FROM dbo.STORERCONFIG sc " +
            "    WHERE sc.storerkey = r.storerkey " +
            "    AND sc.configkey = 'AUTOFINALIZE' AND sc.configvalue = '1') " +
            "ORDER BY r.receiptdate"
        );
    }

    private boolean isAutoFinalizeStorer(String storerKey) {
        if (autoFinalizeStorers == null || autoFinalizeStorers.isEmpty()) {
            return true;
        }
        return autoFinalizeStorers.contains(storerKey);
    }

    private boolean triggerFinalization(String receiptKey, String storerKey) {
        // Update receipt status to Finalized
        int updated = jdbcTemplate.update(
            "UPDATE dbo.RECEIPT SET status = '5', closedate = GETDATE(), editwho = 'AUTOJOB' " +
            "WHERE receiptkey = ? AND status IN ('3', '4')",
            receiptKey
        );

        if (updated > 0) {
            // Update all lines to finalized
            jdbcTemplate.update(
                "UPDATE dbo.RECEIPTDETAIL SET status = '5', editwho = 'AUTOJOB' " +
                "WHERE receiptkey = ?",
                receiptKey
            );

            // Update PO if all receipts finalized
            updatePOStatusIfComplete(receiptKey);
        }

        return updated > 0;
    }

    private void updatePOStatusIfComplete(String receiptKey) {
        try {
            jdbcTemplate.update(
                "UPDATE dbo.PO SET status = '9' " + // Closed
                "WHERE pokey = (SELECT pokey FROM dbo.RECEIPT WHERE receiptkey = ?) " +
                "AND NOT EXISTS (SELECT 1 FROM dbo.RECEIPT r " +
                "    WHERE r.pokey = PO.pokey AND r.status < '5')",
                receiptKey
            );
        } catch (Exception e) {
            log.debug("PO status update skipped: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JOB-003: Auto Release PA Tasks
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Auto-release putaway tasks for finalized receipts.
     * Runs every 5 minutes.
     *
     * Replaces: Calls to lsp_ASNReleasePATask_Wrapper
     */
    @Scheduled(fixedRate = 300000, initialDelay = 180000) // 5 min
    public void autoReleasePATask() {
        if (!autoPAReleaseEnabled) {
            return;
        }

        log.info("Starting AutoReleasePATask job");
        int processed = 0;

        try {
            // Find finalized receipts needing PA release
            List<Map<String, Object>> pendingReceipts = findReceiptsForPARelease();

            for (Map<String, Object> receipt : pendingReceipts) {
                String receiptKey = (String) receipt.get("receiptkey");
                String storerKey = (String) receipt.get("storerkey");
                String facility = (String) receipt.get("whseid");

                try {
                    // Use putaway dispatcher service
                    var request = PutawayDispatcherService.ReleaseRequest.builder()
                        .receiptKey(receiptKey)
                        .storerKey(storerKey)
                        .facility(facility)
                        .userId("AUTOJOB")
                        .build();

                    var result = putawayDispatcherService.releasePutawayTasks(request);

                    if (result.isSuccess()) {
                        log.info("Released {} PA tasks for receipt {}", result.getTasksCreated(), receiptKey);
                        processed++;
                    } else {
                        log.warn("PA release failed for receipt {}: {}", receiptKey, result.getErrorMessage());
                    }

                } catch (Exception e) {
                    log.warn("Failed to release PA for receipt {}: {}", receiptKey, e.getMessage());
                }
            }

            log.info("AutoReleasePATask complete: {} receipts processed", processed);

        } catch (Exception e) {
            log.error("AutoReleasePATask job failed: {}", e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> findReceiptsForPARelease() {
        return jdbcTemplate.queryForList(
            "SELECT r.receiptkey, r.storerkey, r.whseid " +
            "FROM dbo.RECEIPT r " +
            "WHERE r.status = '5' " + // Finalized
            "AND EXISTS (SELECT 1 FROM dbo.LOTXLOCXID lx " +
            "    WHERE lx.sourcekey = r.receiptkey " +
            "    AND lx.sourcetype = 'RECEIPT' " +
            "    AND lx.status = '0') " +  // Pending putaway
            "AND EXISTS (SELECT 1 FROM dbo.STORERCONFIG sc " +
            "    WHERE sc.storerkey = r.storerkey " +
            "    AND sc.configkey = 'AUTOPARELEASE' AND sc.configvalue = '1') " +
            "ORDER BY r.closedate"
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private String generateReceiptKey() {
        try {
            return jdbcTemplate.queryForObject(
                "EXEC dbo.nspg_GetKey @tablename = 'RECEIPT'",
                String.class
            );
        } catch (Exception e) {
            return "RCP" + System.currentTimeMillis();
        }
    }
}
