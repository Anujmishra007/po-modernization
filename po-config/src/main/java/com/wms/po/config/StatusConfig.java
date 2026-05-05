package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configuration for status transitions and status codes.
 *
 * Replaces CODELKUP entries:
 * - CFG-007: RECEIPTSTATUS - Receipt status transitions
 * - POSTATUS - PO status codes
 * - PODETAILSTATUS - PO detail status codes
 * - RECEIPTDETAILSTATUS - Receipt detail status codes
 */
@Component
@Slf4j
public class StatusConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StatusDefinition> statusCache = new HashMap<>();

    // PO Status Codes
    public static final String PO_STATUS_NEW = "0";
    public static final String PO_STATUS_IN_PROGRESS = "5";
    public static final String PO_STATUS_RECEIVED = "9";
    public static final String PO_STATUS_CLOSED = "9";
    public static final String PO_STATUS_VOIDED = "V";

    // PO Detail Status Codes
    public static final String PODETAIL_STATUS_OPEN = "0";
    public static final String PODETAIL_STATUS_PARTIAL = "5";
    public static final String PODETAIL_STATUS_RECEIVED = "9";
    public static final String PODETAIL_STATUS_CLOSED = "9";

    // Receipt Status Codes
    public static final String RECEIPT_STATUS_NEW = "0";
    public static final String RECEIPT_STATUS_IN_RECEIVING = "5";
    public static final String RECEIPT_STATUS_RECEIVED = "9";
    public static final String RECEIPT_STATUS_VERIFIED = "11";
    public static final String RECEIPT_STATUS_PUTAWAY = "12";
    public static final String RECEIPT_STATUS_CLOSED = "15";
    public static final String RECEIPT_STATUS_VOIDED = "V";

    // Receipt Detail Status Codes
    public static final String RECEIPTDETAIL_STATUS_NEW = "0";
    public static final String RECEIPTDETAIL_STATUS_RECEIVING = "5";
    public static final String RECEIPTDETAIL_STATUS_RECEIVED = "9";
    public static final String RECEIPTDETAIL_STATUS_VERIFIED = "11";
    public static final String RECEIPTDETAIL_STATUS_PUTAWAY = "12";
    public static final String RECEIPTDETAIL_STATUS_CLOSED = "15";

    // Inventory Status Codes
    public static final String INVENTORY_STATUS_OK = "OK";
    public static final String INVENTORY_STATUS_HOLD = "HOLD";
    public static final String INVENTORY_STATUS_DAMAGED = "DAMAGED";
    public static final String INVENTORY_STATUS_QC = "QC";
    public static final String INVENTORY_STATUS_EXPIRED = "EXPIRED";

    // Hold Codes
    public static final String HOLD_CODE_QUALITY = "QCHOLD";
    public static final String HOLD_CODE_DAMAGED = "DMGHOLD";
    public static final String HOLD_CODE_ADMIN = "ADMHOLD";
    public static final String HOLD_CODE_CUSTOMS = "CUSTHOLD";
    public static final String HOLD_CODE_RECALL = "RECALL";

    public StatusConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initializeDefaultTransitions();
    }

    public boolean isValidTransition(String entityType, String fromStatus, String toStatus) {
        StatusDefinition def = getStatusDefinition(entityType);
        if (def == null) return true;
        Set<String> allowedTargets = def.transitions.get(fromStatus);
        if (allowedTargets == null) return false;
        return allowedTargets.contains(toStatus);
    }

    public Set<String> getAllowedTransitions(String entityType, String fromStatus) {
        StatusDefinition def = getStatusDefinition(entityType);
        if (def == null) return Collections.emptySet();
        return def.transitions.getOrDefault(fromStatus, Collections.emptySet());
    }

    public String getStatusDescription(String entityType, String status) {
        StatusDefinition def = getStatusDefinition(entityType);
        if (def == null) return status;
        return def.descriptions.getOrDefault(status, status);
    }

    public boolean isTerminalStatus(String entityType, String status) {
        StatusDefinition def = getStatusDefinition(entityType);
        if (def == null) return false;
        return def.terminalStatuses.contains(status);
    }

    public boolean isEditable(String entityType, String status) {
        StatusDefinition def = getStatusDefinition(entityType);
        if (def == null) return true;
        return def.editableStatuses.contains(status);
    }

    public String getInitialStatus(String entityType) {
        StatusDefinition def = getStatusDefinition(entityType);
        return def != null ? def.initialStatus : "0";
    }

    public void refresh() {
        statusCache.clear();
        initializeDefaultTransitions();
        loadFromDatabase();
    }

    private StatusDefinition getStatusDefinition(String entityType) {
        return statusCache.get(entityType.toUpperCase());
    }

    private void initializeDefaultTransitions() {
        // PO status transitions
        StatusDefinition poStatus = new StatusDefinition("0");
        poStatus.addTransition("0", Set.of("5", "9", "V"));
        poStatus.addTransition("5", Set.of("9", "V"));
        poStatus.addTransition("9", Set.of());
        poStatus.addTransition("V", Set.of());
        poStatus.terminalStatuses.addAll(Set.of("9", "V"));
        poStatus.editableStatuses.addAll(Set.of("0", "5"));
        poStatus.descriptions.put("0", "New");
        poStatus.descriptions.put("5", "In Progress");
        poStatus.descriptions.put("9", "Closed");
        poStatus.descriptions.put("V", "Voided");
        statusCache.put("PO", poStatus);

        // PO Detail status transitions
        StatusDefinition podStatus = new StatusDefinition("0");
        podStatus.addTransition("0", Set.of("5", "9"));
        podStatus.addTransition("5", Set.of("9"));
        podStatus.addTransition("9", Set.of());
        podStatus.terminalStatuses.add("9");
        podStatus.editableStatuses.addAll(Set.of("0", "5"));
        podStatus.descriptions.put("0", "Open");
        podStatus.descriptions.put("5", "Partial");
        podStatus.descriptions.put("9", "Received");
        statusCache.put("PODETAIL", podStatus);

        // Receipt status transitions
        StatusDefinition receiptStatus = new StatusDefinition("0");
        receiptStatus.addTransition("0", Set.of("5", "9", "V"));
        receiptStatus.addTransition("5", Set.of("9", "11", "V"));
        receiptStatus.addTransition("9", Set.of("11", "12", "15"));
        receiptStatus.addTransition("11", Set.of("12", "15"));
        receiptStatus.addTransition("12", Set.of("15"));
        receiptStatus.addTransition("15", Set.of());
        receiptStatus.addTransition("V", Set.of());
        receiptStatus.terminalStatuses.addAll(Set.of("15", "V"));
        receiptStatus.editableStatuses.addAll(Set.of("0", "5"));
        receiptStatus.descriptions.put("0", "New");
        receiptStatus.descriptions.put("5", "Receiving");
        receiptStatus.descriptions.put("9", "Received");
        receiptStatus.descriptions.put("11", "Verified");
        receiptStatus.descriptions.put("12", "Putaway");
        receiptStatus.descriptions.put("15", "Closed");
        receiptStatus.descriptions.put("V", "Voided");
        statusCache.put("RECEIPT", receiptStatus);

        // Receipt Detail status transitions
        StatusDefinition recdtlStatus = new StatusDefinition("0");
        recdtlStatus.addTransition("0", Set.of("5", "9"));
        recdtlStatus.addTransition("5", Set.of("9", "11"));
        recdtlStatus.addTransition("9", Set.of("11", "12"));
        recdtlStatus.addTransition("11", Set.of("12", "15"));
        recdtlStatus.addTransition("12", Set.of("15"));
        recdtlStatus.addTransition("15", Set.of());
        recdtlStatus.terminalStatuses.add("15");
        recdtlStatus.editableStatuses.addAll(Set.of("0", "5"));
        recdtlStatus.descriptions.put("0", "New");
        recdtlStatus.descriptions.put("5", "Receiving");
        recdtlStatus.descriptions.put("9", "Received");
        recdtlStatus.descriptions.put("11", "Verified");
        recdtlStatus.descriptions.put("12", "Putaway");
        recdtlStatus.descriptions.put("15", "Closed");
        statusCache.put("RECEIPTDETAIL", recdtlStatus);

        // Inventory status transitions
        StatusDefinition invStatus = new StatusDefinition("OK");
        invStatus.addTransition("OK", Set.of("HOLD", "DAMAGED", "QC", "EXPIRED"));
        invStatus.addTransition("HOLD", Set.of("OK", "DAMAGED", "EXPIRED"));
        invStatus.addTransition("DAMAGED", Set.of("OK", "HOLD"));
        invStatus.addTransition("QC", Set.of("OK", "HOLD", "DAMAGED"));
        invStatus.addTransition("EXPIRED", Set.of());
        invStatus.terminalStatuses.add("EXPIRED");
        invStatus.descriptions.put("OK", "Available");
        invStatus.descriptions.put("HOLD", "On Hold");
        invStatus.descriptions.put("DAMAGED", "Damaged");
        invStatus.descriptions.put("QC", "Quality Check");
        invStatus.descriptions.put("EXPIRED", "Expired");
        statusCache.put("INVENTORY", invStatus);
    }

    private void loadFromDatabase() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT listname, code, description FROM dbo.codelkup " +
                "WHERE listname IN ('RECEIPTSTATUS', 'POSTATUS', 'PODETAILSTATUS') ORDER BY listname, code"
            );

            for (Map<String, Object> row : rows) {
                String listName = (String) row.get("listname");
                String code = (String) row.get("code");
                String desc = (String) row.get("description");

                String entityType = switch (listName) {
                    case "RECEIPTSTATUS" -> "RECEIPT";
                    case "POSTATUS" -> "PO";
                    case "PODETAILSTATUS" -> "PODETAIL";
                    default -> null;
                };

                if (entityType != null) {
                    StatusDefinition def = statusCache.get(entityType);
                    if (def != null && desc != null) {
                        def.descriptions.put(code, desc);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not load status config from database: {}", e.getMessage());
        }
    }

    @Data
    private static class StatusDefinition {
        private final String initialStatus;
        private final Map<String, Set<String>> transitions = new HashMap<>();
        private final Set<String> terminalStatuses = new HashSet<>();
        private final Set<String> editableStatuses = new HashSet<>();
        private final Map<String, String> descriptions = new HashMap<>();

        void addTransition(String from, Set<String> to) {
            transitions.put(from, new HashSet<>(to));
        }
    }
}
