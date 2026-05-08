package com.wms.po.controller;

import com.wms.po.dto.*;
import com.wms.po.service.POService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PO Controller - disabled during E2E tests where mock controller handles requests.
 */
@RestController
@RequestMapping("/api/v1/po")
@RequiredArgsConstructor
@Slf4j
@Profile("!test & !e2e-test")
public class POController {

    private final POService poService;

    @PostMapping
    public ResponseEntity<POResponse> createPO(
            @Valid @RequestBody POCreateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {

        log.info("Creating PO for storer: {}, facility: {}, user: {}",
                request.getStorerKey(), request.getFacility(), userId);

        POResponse response = poService.createPO(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{poKey}")
    public ResponseEntity<POResponse> getPO(@PathVariable String poKey) {
        log.info("Fetching PO: {}", poKey);
        POResponse response = poService.getPO(poKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<POResponse>> getPOs(
            @RequestParam String storerKey,
            @RequestParam String facility) {

        log.info("Fetching POs for storer: {}, facility: {}", storerKey, facility);
        List<POResponse> response = poService.getPOsByStorerAndFacility(storerKey, facility);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{poKey}")
    public ResponseEntity<POResponse> updatePO(
            @PathVariable String poKey,
            @Valid @RequestBody POCreateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {

        log.info("Updating PO: {}", poKey);
        POResponse response = poService.updatePO(poKey, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{poKey}")
    public ResponseEntity<Void> deletePO(@PathVariable String poKey) {
        log.info("Deleting PO: {}", poKey);
        poService.deletePO(poKey);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{poKey}/status")
    public ResponseEntity<Void> updatePOStatus(
            @PathVariable String poKey,
            @RequestParam String status,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {

        log.info("Updating PO {} status to {}", poKey, status);
        poService.updatePOStatus(poKey, status, userId);
        return ResponseEntity.ok().build();
    }
}
