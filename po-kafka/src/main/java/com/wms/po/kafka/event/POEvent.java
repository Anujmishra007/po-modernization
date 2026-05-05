package com.wms.po.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Base event class for all PO-related events.
 * Supports event sourcing and saga coordination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POEvent {

    private String eventId;
    private String eventType;
    private String aggregateId;      // PO Key or Receipt Key
    private String aggregateType;    // PO, RECEIPT, INVENTORY
    private int version;
    private Instant timestamp;
    private String correlationId;    // Saga/Workflow ID
    private String causationId;      // Parent event ID
    private String source;           // Service that generated the event
    private String userId;
    private String facility;
    private String storerKey;
    private String region;
    private String dbVersion;        // V0 or V2
    private Map<String, Object> payload;
    private Map<String, String> metadata;

    // Event types
    public static final String PO_CREATED = "PO_CREATED";
    public static final String PO_VALIDATED = "PO_VALIDATED";
    public static final String PO_VALIDATION_FAILED = "PO_VALIDATION_FAILED";
    public static final String PO_MAPPED = "PO_MAPPED";
    public static final String PO_POPULATED = "PO_POPULATED";
    public static final String PO_POPULATION_FAILED = "PO_POPULATION_FAILED";
    public static final String PO_STATUS_UPDATED = "PO_STATUS_UPDATED";
    public static final String PO_CANCELLED = "PO_CANCELLED";

    public static final String RECEIPT_CREATED = "RECEIPT_CREATED";
    public static final String RECEIPT_DETAIL_CREATED = "RECEIPT_DETAIL_CREATED";
    public static final String RECEIPT_FINALIZED = "RECEIPT_FINALIZED";
    public static final String RECEIPT_ROLLED_BACK = "RECEIPT_ROLLED_BACK";

    public static final String INVENTORY_RESERVED = "INVENTORY_RESERVED";
    public static final String INVENTORY_RELEASED = "INVENTORY_RELEASED";
    public static final String INVENTORY_ALLOCATED = "INVENTORY_ALLOCATED";

    public static final String SAGA_STARTED = "SAGA_STARTED";
    public static final String SAGA_STEP_COMPLETED = "SAGA_STEP_COMPLETED";
    public static final String SAGA_STEP_FAILED = "SAGA_STEP_FAILED";
    public static final String SAGA_COMPENSATING = "SAGA_COMPENSATING";
    public static final String SAGA_COMPLETED = "SAGA_COMPLETED";
    public static final String SAGA_FAILED = "SAGA_FAILED";

    public static final String LEGACY_SYNC_STARTED = "LEGACY_SYNC_STARTED";
    public static final String LEGACY_SYNC_COMPLETED = "LEGACY_SYNC_COMPLETED";
    public static final String LEGACY_SYNC_FAILED = "LEGACY_SYNC_FAILED";
    public static final String LEGACY_ROLLBACK_COMPLETED = "LEGACY_ROLLBACK_COMPLETED";
}
