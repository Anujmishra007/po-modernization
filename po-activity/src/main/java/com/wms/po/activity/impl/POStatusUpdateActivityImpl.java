package com.wms.po.activity.impl;

import com.wms.po.activity.POStatusUpdateActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementation of POStatusUpdateActivity.
 * Updates PO status after successful population.
 *
 * Error codes:
 * - RCV_022 (68922) - PO Update Failed
 * - PO_001 (68800) - PO Not Found
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class POStatusUpdateActivityImpl implements POStatusUpdateActivity {

    private final JdbcTemplate jdbcTemplate;

    // PO Status codes
    public static final String STATUS_OPEN = "0";
    public static final String STATUS_IN_PROGRESS = "1";
    public static final String STATUS_PARTIALLY_RECEIVED = "5";
    public static final String STATUS_FULLY_RECEIVED = "9";
    public static final String STATUS_CLOSED = "9";

    /**
     * Update PO status.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - RCV_022 (68922) - PO Update Failed
     */
    @Override
    public void updatePOStatus(String receiptKey, PopulateRequest request,
                                VariationContext context, String targetStatus) {
        log.info("Updating PO status to {} for {} POs linked to receipt {}",
            targetStatus, request.getPoKeys().size(), receiptKey);

        String sql = """
            UPDATE PO SET
                STATUS = ?,
                EDITDATE = GETDATE(),
                EDITWHO = ?
            WHERE POKEY = ?
            """;

        for (String poKey : request.getPoKeys()) {
            try {
                int updated = jdbcTemplate.update(sql, targetStatus, request.getUserId(), poKey);
                if (updated > 0) {
                    log.debug("Updated PO {} status to {}", poKey, targetStatus);
                } else {
                    log.warn("PO {} not found for status update (legacy error 68800)", poKey);
                    throw BusinessException.poNotFound(poKey);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (DataAccessException e) {
                log.error("Failed to update PO {} status: {} (legacy error 68922)",
                    poKey, e.getMessage(), e);
                throw new BusinessException(ErrorCode.FINALIZE_PO_UPDATE_FAILED,
                    "Failed to update PO status: " + e.getMessage(), e)
                    .withDetail("poKey", poKey)
                    .withDetail("targetStatus", targetStatus);
            }
        }

        log.info("Successfully updated {} POs to status {}", request.getPoKeys().size(), targetStatus);
    }

    @Override
    public void compensateUpdatePOStatus(String receiptKey, PopulateRequest request,
                                          VariationContext context, String previousStatus) {
        log.warn("COMPENSATION: Reverting PO status to {} for {} POs",
            previousStatus, request.getPoKeys().size());

        String sql = """
            UPDATE PO SET
                STATUS = ?,
                EDITDATE = GETDATE(),
                EDITWHO = ?
            WHERE POKEY = ?
            """;

        for (String poKey : request.getPoKeys()) {
            try {
                jdbcTemplate.update(sql, previousStatus, request.getUserId(), poKey);
                log.debug("COMPENSATION: Reverted PO {} status to {}", poKey, previousStatus);
            } catch (Exception e) {
                log.error("COMPENSATION: Failed to revert PO {} status: {}", poKey, e.getMessage());
            }
        }

        log.info("COMPENSATION: Reverted {} POs to status {}", request.getPoKeys().size(), previousStatus);
    }
}
