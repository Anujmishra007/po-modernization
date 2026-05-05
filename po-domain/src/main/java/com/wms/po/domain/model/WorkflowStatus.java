package com.wms.po.domain.model;

/**
 * Workflow execution status
 */
public enum WorkflowStatus {
    STARTED,
    RUNNING,
    COMPENSATING,
    COMPLETED,
    FAILED,
    CANCELLED
}
