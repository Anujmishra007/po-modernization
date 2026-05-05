package com.wms.po.api.controller;

import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final WorkflowClient workflowClient;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();

        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("service", "po-modernization");

        // Check Temporal connection
        try {
            workflowClient.getWorkflowServiceStubs().healthCheck();
            health.put("temporal", "UP");
        } catch (Exception e) {
            health.put("temporal", "DOWN");
            health.put("temporalError", e.getMessage());
            log.warn("Temporal health check failed: {}", e.getMessage());
        }

        return ResponseEntity.ok(health);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> ready = new HashMap<>();

        ready.put("status", "READY");
        ready.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(ready);
    }
}
