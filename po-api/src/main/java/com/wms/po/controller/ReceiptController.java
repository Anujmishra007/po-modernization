package com.wms.po.controller;

import com.wms.po.dto.ReceiptResponse;
import com.wms.po.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Receipt Controller - disabled during E2E tests where mock controller handles requests.
 */
@RestController
@RequestMapping("/api/v1/receipt")
@RequiredArgsConstructor
@Slf4j
@Profile("!test & !e2e-test")
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/{receiptKey}")
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable String receiptKey) {
        log.info("Fetching Receipt: {}", receiptKey);
        ReceiptResponse response = receiptService.getReceipt(receiptKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ReceiptResponse>> getReceipts(
            @RequestParam String storerKey,
            @RequestParam String facility) {

        log.info("Fetching Receipts for storer: {}, facility: {}", storerKey, facility);
        List<ReceiptResponse> response = receiptService.getReceiptsByStorerAndFacility(storerKey, facility);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-po/{poKey}")
    public ResponseEntity<List<ReceiptResponse>> getReceiptsByPO(@PathVariable String poKey) {
        log.info("Fetching Receipts for PO: {}", poKey);
        List<ReceiptResponse> response = receiptService.getReceiptsByPO(poKey);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{receiptKey}/status")
    public ResponseEntity<Void> updateReceiptStatus(
            @PathVariable String receiptKey,
            @RequestParam String status,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {

        log.info("Updating Receipt {} status to {}", receiptKey, status);
        receiptService.updateReceiptStatus(receiptKey, status, userId);
        return ResponseEntity.ok().build();
    }
}
