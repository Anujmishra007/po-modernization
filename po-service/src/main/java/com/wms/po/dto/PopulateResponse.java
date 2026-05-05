package com.wms.po.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for populate operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulateResponse {

    private boolean success;
    private String receiptKey;
    private List<String> receiptKeys;
    private String workflowId;

    private List<String> poKeys;
    private int linesProcessed;
    private int linesCreated;

    private String status;
    private String message;
    private List<String> errors;
    private List<String> warnings;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;

    private Map<String, Object> metadata;

    public static PopulateResponse success(String receiptKey, String workflowId) {
        return PopulateResponse.builder()
            .success(true)
            .receiptKey(receiptKey)
            .workflowId(workflowId)
            .status("COMPLETED")
            .build();
    }

    public static PopulateResponse failure(String message, List<String> errors) {
        return PopulateResponse.builder()
            .success(false)
            .status("FAILED")
            .message(message)
            .errors(errors)
            .build();
    }

    public static PopulateResponse pending(String workflowId) {
        return PopulateResponse.builder()
            .success(true)
            .workflowId(workflowId)
            .status("PENDING")
            .message("Population workflow started")
            .build();
    }
}
