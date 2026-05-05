package com.wms.po.controller;

import com.wms.po.dto.PopulateRequest;
import com.wms.po.dto.PopulateResponse;
import com.wms.po.service.PopulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/population")
@RequiredArgsConstructor
@Slf4j
public class PopulationController {

    private final PopulationService populationService;

    @PostMapping("/populate")
    public ResponseEntity<PopulateResponse> populatePOs(@Valid @RequestBody PopulateRequest request) {
        log.info("Populating {} POs for storer: {}, facility: {}",
                request.getPoKeys().size(), request.getStorerKey(), request.getFacility());

        PopulateResponse response = populationService.populatePOs(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{workflowId}")
    public ResponseEntity<PopulateResponse> getPopulationStatus(@PathVariable String workflowId) {
        log.info("Fetching population status for workflow: {}", workflowId);
        PopulateResponse response = populationService.getWorkflowStatus(workflowId);
        return ResponseEntity.ok(response);
    }
}
