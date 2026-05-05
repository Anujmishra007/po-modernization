package com.wms.po.api.controller;

import com.wms.po.api.service.POPopulationService;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.PopulateResult;
import com.wms.po.domain.model.WorkflowStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for PO Population operations
 */
@RestController
@RequestMapping("/api/v1/populate")
@RequiredArgsConstructor
@Slf4j
public class PopulateController {

    private final POPopulationService populationService;

    /**
     * Synchronous population - waits for completion
     */
    @PostMapping("/populate")
    public ResponseEntity<PopulateResult> populate(@RequestBody @Valid PopulateRequest request) {
        log.info("Populate request: poKeys={}, storer={}, facility={}",
            request.getPoKeys(), request.getStorerKey(), request.getFacility());

        PopulateResult result = populationService.populate(request);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Asynchronous population - returns immediately with workflow ID
     */
    @PostMapping("/populate/async")
    public ResponseEntity<AsyncResponse> populateAsync(@RequestBody @Valid PopulateRequest request) {
        log.info("Async populate request: poKeys={}", request.getPoKeys());

        String workflowId = populationService.populateAsync(request);

        return ResponseEntity.accepted().body(new AsyncResponse(
            workflowId,
            "/api/v1/po/populate/" + workflowId + "/status"
        ));
    }

    /**
     * Query workflow status
     */
    @GetMapping("/populate/{workflowId}/status")
    public ResponseEntity<WorkflowStatusResponse> getStatus(@PathVariable String workflowId) {
        WorkflowStatusResponse status = populationService.getStatus(workflowId);
        return ResponseEntity.ok(status);
    }

    /**
     * Cancel running workflow
     */
    @PostMapping("/populate/{workflowId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String workflowId) {
        populationService.cancel(workflowId);
        return ResponseEntity.accepted().build();
    }

    // Response DTOs
    public record AsyncResponse(String workflowId, String statusUrl) {}

    public record WorkflowStatusResponse(
        String workflowId,
        WorkflowStatus status,
        String currentStep,
        List<String> completedSteps,
        int progress
    ) {}
}
