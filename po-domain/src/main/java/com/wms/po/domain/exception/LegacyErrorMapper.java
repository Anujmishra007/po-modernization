package com.wms.po.domain.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps legacy SQL Server SP error codes to Java exceptions.
 *
 * When calling legacy stored procedures (during dual-write mode or bridge calls),
 * this mapper converts RAISERROR codes to proper Java exceptions.
 *
 * SQL Server RAISERROR format:
 *   RAISERROR(68800, 16, 1, 'PO not found')
 *   - 68800 = error number
 *   - 16 = severity
 *   - 1 = state
 *   - 'PO not found' = message
 */
@Component
@Slf4j
public class LegacyErrorMapper {

    // Pattern to extract error code from SQL Server error messages
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("Msg\\s+(\\d+)|Error\\s+(\\d+)|#(\\d{5})");

    /**
     * Convert SQLException to BusinessException
     */
    public BusinessException mapSqlException(SQLException ex) {
        int errorCode = ex.getErrorCode();

        // Try to extract from message if error code is generic
        if (errorCode == 0 || errorCode == 50000) {
            errorCode = extractErrorCodeFromMessage(ex.getMessage());
        }

        ErrorCode mappedCode = ErrorCode.fromLegacyCode(errorCode);

        log.debug("Mapped SQL error code {} to {}", errorCode, mappedCode);

        return new BusinessException(mappedCode, ex.getMessage(), ex)
            .withDetail("sqlState", ex.getSQLState())
            .withDetail("originalErrorCode", ex.getErrorCode());
    }

    /**
     * Convert a legacy error code from SP result to BusinessException
     */
    public BusinessException mapErrorCode(int legacyCode, String message) {
        ErrorCode mappedCode = ErrorCode.fromLegacyCode(legacyCode);

        return new BusinessException(mappedCode, message != null ? message : mappedCode.getDescription())
            .withDetail("legacyCode", legacyCode);
    }

    /**
     * Extract error code from error message text
     */
    private int extractErrorCodeFromMessage(String message) {
        if (message == null) {
            return 99999;
        }

        Matcher matcher = ERROR_CODE_PATTERN.matcher(message);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    try {
                        return Integer.parseInt(group);
                    } catch (NumberFormatException e) {
                        // Continue to next group
                    }
                }
            }
        }

        // Common patterns in error messages
        if (message.contains("not found") || message.contains("does not exist")) {
            return ErrorCode.PO_NOT_FOUND.getLegacyCode();
        }
        if (message.contains("already finalized") || message.contains("already processed")) {
            return ErrorCode.RECEIPT_ALREADY_FINALIZED.getLegacyCode();
        }
        if (message.contains("invalid status")) {
            return ErrorCode.RECEIPT_INVALID_STATUS.getLegacyCode();
        }
        if (message.contains("deadlock")) {
            return ErrorCode.DEADLOCK_DETECTED.getLegacyCode();
        }
        if (message.contains("timeout")) {
            return ErrorCode.TIMEOUT_ERROR.getLegacyCode();
        }

        return ErrorCode.UNKNOWN_ERROR.getLegacyCode();
    }

    /**
     * Check if an error code indicates a retryable error
     */
    public boolean isRetryable(int legacyCode) {
        return ErrorCode.fromLegacyCode(legacyCode).isRetryable();
    }

    /**
     * Get HTTP status for a legacy error code
     */
    public int getHttpStatus(int legacyCode) {
        return ErrorCode.fromLegacyCode(legacyCode).getHttpStatus();
    }

    // ═══════════════════════════════════════════════════════════════
    // Legacy SP Error Code Reference (from SQL Server)
    // ═══════════════════════════════════════════════════════════════
    //
    // Task/Receipt Processing (68675-68687):
    //   68675 = Invalid TaskDetail Key
    //   68676 = Invalid From Location
    //   68677 = Invalid To ID
    //   68678 = Invalid To Location
    //   68679 = Item Already Processed
    //   68680-68682 = Invalid Reason Code
    //   68683 = TaskDetail Update Failed
    //   68684 = Insert Task Failed
    //   68685 = Log Alert Failed
    //   68686-68687 = Get Key Failed
    //
    // PO/ASN Errors (68800-68899):
    //   68800 = PO Not Found
    //   68801 = PO Already Closed
    //   68802 = PO Cancelled
    //   68803 = PO Line Not Found
    //   68804 = PO Line Fully Received
    //   68805 = ASN Not Found
    //   68806 = ASN Already Finalized
    //   68807 = ASN Invalid Status
    //
    // Receipt/Finalization (68900-68999):
    //   68900 = Receipt Not Found
    //   68901 = Receipt Already Finalized
    //   68902 = Receipt Invalid Status
    //   68903 = Receipt Detail Not Found
    //   68904 = Receipt Header Creation Failed
    //   68905 = Receipt Detail Creation Failed
    //   68910 = Receipt Quantity Mismatch
    //   68911 = Over-receive Not Allowed
    //   68920 = Finalize Validation Failed
    //   68921 = Inventory Posting Failed
    //   68922 = PO Update Failed
    //   68923 = Hold Application Failed
    //   68924 = Putaway Release Failed
    //
    // Inventory Errors (68700-68799):
    //   68700 = Inventory Not Found
    //   68701 = Insufficient Inventory
    //   68702 = Inventory Already Allocated
    //   68703 = Inventory On Hold
    //   68710 = Location Not Found
    //   68711 = Location Full
    //   68720 = License Plate Not Found
    //   68721 = License Plate Already Exists
    //   68730 = Lot Not Found
    //   68731 = Lot Expired
    // ═══════════════════════════════════════════════════════════════
}
