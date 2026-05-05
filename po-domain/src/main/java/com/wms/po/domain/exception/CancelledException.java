package com.wms.po.domain.exception;

/**
 * Exception thrown when workflow is cancelled
 */
public class CancelledException extends RuntimeException {

    public CancelledException() {
        super("Workflow cancelled");
    }

    public CancelledException(String message) {
        super(message);
    }
}
