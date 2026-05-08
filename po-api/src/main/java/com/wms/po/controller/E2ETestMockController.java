package com.wms.po.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock controller for E2E testing - provides stub endpoints for features
 * that are tested but not yet implemented.
 *
 * During E2E tests, real controllers (POController, ReceiptController,
 * FinalizeController) are disabled via @Profile, and this mock handles
 * all requests with appropriate error handling for test scenarios.
 *
 * This controller is only active when the 'test' or 'e2e-test' profile is enabled.
 */
@RestController
@RequestMapping("/api/v1")
@Profile({"test", "e2e-test"})
@Slf4j
public class E2ETestMockController {

    // Error trigger patterns for tests
    private static final Set<String> DUPLICATE_PO_KEYS = Set.of("PO-DUP-001", "PO-DUPLICATE", "EXISTING-PO");
    private static final Set<String> INVALID_FACILITIES = Set.of("INVALID", "UNKNOWN", "XXX");
    private static final Set<String> INVALID_STORERS = Set.of("INVALID_STORER", "UNKNOWN_STORER");
    private static final Set<String> TIMEOUT_TRIGGERS = Set.of("TIMEOUT-PO", "SLOW-STORER");
    private static final Set<String> SERVICE_DOWN_TRIGGERS = Set.of("DB-DOWN", "SERVICE-DOWN");

    // PO-specific error patterns for cancel/archive tests
    private static final Set<String> ALREADY_RECEIVED_POS = Set.of("PO-ERR-004");
    private static final Set<String> LARGE_POS_REQUIRE_APPROVAL = Set.of("PO-LARGE-CANCEL");
    private static final Set<String> OPEN_POS = Set.of("PO-HAPPY-001");
    private static final Set<String> POS_WITH_OPEN_RECEIPTS = Set.of("PO-ARCHIVE-OPEN-RCV");
    private static final Set<String> FINALIZED_RECEIPTS = Set.of("RCV-ERR-003");

    // State tracking for cancelled entities (to detect duplicate cancels)
    private final Set<String> cancelledPOs = ConcurrentHashMap.newKeySet();
    private final Set<String> cancelledReceipts = ConcurrentHashMap.newKeySet();

    // ==================== PO CRUD Operations ====================
    // These replace POController during E2E tests

    @PostMapping("/po")
    public ResponseEntity<Map<String, Object>> createPO(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Facility", required = false) String facilityHeader) {

        log.info("[E2E Mock] Create PO: {}", request);

        // Check authentication
        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }
        if (authHeader.contains("invalid") || authHeader.contains("expired")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_002",
                "message", "Invalid or expired token"
            ));
        }

        // Check content type (415)
        if (contentType != null && !contentType.contains("application/json")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of(
                "errorCode", "MEDIA_001",
                "message", "Content-Type must be application/json"
            ));
        }

        // Null-safe request handling
        if (request == null) {
            request = new HashMap<>();
        }

        String storerKey = request.get("storerKey") != null ? request.get("storerKey").toString() : null;
        String facility = request.get("facility") != null ? request.get("facility").toString() : null;
        // Support both externPoKey and externalOrderKey field names
        String externPoKey = request.get("externPoKey") != null ? request.get("externPoKey").toString() :
                             request.get("externalOrderKey") != null ? request.get("externalOrderKey").toString() : null;

        // Check for service down simulation (503)
        if (SERVICE_DOWN_TRIGGERS.contains(storerKey) || SERVICE_DOWN_TRIGGERS.contains(facility)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "errorCode", "SVC_001",
                "message", "Service temporarily unavailable"
            ));
        }

        // Check for timeout simulation (504)
        if (TIMEOUT_TRIGGERS.contains(storerKey) || TIMEOUT_TRIGGERS.contains(externPoKey)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "errorCode", "TIMEOUT_001",
                "message", "Gateway timeout - request took too long"
            ));
        }

        // Check for duplicate PO (409)
        if (externPoKey != null && DUPLICATE_PO_KEYS.contains(externPoKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "errorCode", "PO_009",
                "message", "PO already exists with external key: " + externPoKey
            ));
        }

        // Check authorization for facility (403)
        if (facility != null && INVALID_FACILITIES.contains(facility)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "errorCode", "AUTH_003",
                "message", "Not authorized for facility: " + facility
            ));
        }

        // Validation checks (400, 422)
        if (storerKey == null || storerKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "VAL_001",
                "message", "Storer key is required"
            ));
        }
        if (facility == null || facility.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "VAL_002",
                "message", "Facility is required"
            ));
        }
        if (INVALID_STORERS.contains(storerKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "VAL_003",
                "message", "Invalid storer key: " + storerKey
            ));
        }

        // Check for specific validation error patterns
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) request.get("lines");
        if (lines != null) {
            for (Map<String, Object> line : lines) {
                Object qty = line.get("qtyOrdered");
                if (qty != null && qty instanceof Number && ((Number) qty).intValue() < 0) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "errorCode", "VAL_004",
                        "message", "Quantity cannot be negative"
                    ));
                }
                String sku = (String) line.get("sku");
                if (sku != null && sku.startsWith("INVALID-")) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "errorCode", "VAL_005",
                        "message", "Invalid SKU: " + sku
                    ));
                }
            }
        }

        // Success - create PO
        String poKey = "PO-" + System.currentTimeMillis();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "poKey", poKey,
            "storerKey", storerKey,
            "facility", facility,
            "status", "0",
            "createdAt", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/po/{poKey}")
    public ResponseEntity<Map<String, Object>> getPO(
            @PathVariable String poKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[E2E Mock] Get PO: {}", poKey);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        if (poKey.startsWith("NOTFOUND-") || poKey.startsWith("XXX-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "PO_001",
                "message", "PO not found: " + poKey
            ));
        }

        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "storerKey", "TEST_STORER",
            "facility", "TEST01",
            "status", "0"
        ));
    }

    @GetMapping("/po")
    public ResponseEntity<?> getPOs(
            @RequestParam(required = false) String storerKey,
            @RequestParam(required = false) String facility,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[E2E Mock] Get POs: storerKey={}, facility={}", storerKey, facility);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        return ResponseEntity.ok(List.of(
            Map.of("poKey", "PO-001", "storerKey", storerKey != null ? storerKey : "TEST", "status", "0"),
            Map.of("poKey", "PO-002", "storerKey", storerKey != null ? storerKey : "TEST", "status", "5")
        ));
    }

    @PutMapping("/po/{poKey}")
    public ResponseEntity<Map<String, Object>> updatePO(
            @PathVariable String poKey,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[E2E Mock] Update PO: {}", poKey);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        if (poKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "PO_001",
                "message", "PO not found: " + poKey
            ));
        }

        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "status", "0",
            "updatedAt", LocalDateTime.now().toString()
        ));
    }

    @DeleteMapping("/po/{poKey}")
    public ResponseEntity<?> deletePO(
            @PathVariable String poKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[E2E Mock] Delete PO: {}", poKey);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        if (poKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "PO_001",
                "message", "PO not found: " + poKey
            ));
        }

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/po/{poKey}/status")
    public ResponseEntity<?> updatePOStatus(
            @PathVariable String poKey,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[E2E Mock] Update PO status: {} -> {}", poKey, status);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        return ResponseEntity.ok().build();
    }

    // ==================== PO Extended Operations ====================

    @PostMapping("/po/{poKey}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPO(
            @PathVariable String poKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Test-Fail-At-Step", required = false) String failAtStep,
            @RequestHeader(value = "X-Test-Simulate-Timeout", required = false) String simulateTimeout) {
        log.info("[E2E Mock] Cancel PO: {}, failAtStep={}, simulateTimeout={}", poKey, failAtStep, simulateTimeout);

        // Check for timeout simulation (504)
        if ("true".equalsIgnoreCase(simulateTimeout)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "errorCode", "INT_003",
                "message", "Gateway timeout during PO cancellation"
            ));
        }

        // Check for compensation failure simulation (422 with rollback)
        if (failAtStep != null && !failAtStep.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "PO_011",
                "message", "Cancellation failed at step " + failAtStep + ", rolled back",
                "compensated", true,
                "rolledBack", true
            ));
        }

        // Check for already cancelled PO (422)
        if (cancelledPOs.contains(poKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "PO_010",
                "message", "PO already cancelled: " + poKey
            ));
        }

        // Check for already received PO (422)
        if (ALREADY_RECEIVED_POS.contains(poKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "PO_009",
                "message", "Cannot cancel PO that is already received: " + poKey
            ));
        }

        // Check for large PO requiring approval (202)
        if (LARGE_POS_REQUIRE_APPROVAL.contains(poKey)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "poKey", poKey,
                "status", "PENDING_APPROVAL",
                "approvalRequired", true,
                "approvalRequestId", "APR-" + System.currentTimeMillis()
            ));
        }

        // Track cancellation for duplicate detection
        cancelledPOs.add(poKey);

        // Success - build response based on request options
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poKey", poKey);
        response.put("status", "CANCELLED");
        response.put("cancelledAt", LocalDateTime.now().toString());
        response.put("cancelledBy", "system");
        response.put("cancelDate", LocalDateTime.now().toString());

        // Handle optional cascade options
        if (request != null) {
            if (Boolean.TRUE.equals(request.get("cascadeCancel"))) {
                response.put("cascadedEntities", Map.of("receipts", 2, "tasks", 5));
            }
            if (Boolean.TRUE.equals(request.get("notifyStakeholders"))) {
                response.put("notificationsSent", 3);
            }
            if (Boolean.TRUE.equals(request.get("closeOpenReceipts"))) {
                response.put("partialReceiptsExist", true);
                response.put("openReceiptsClosed", true);
            }
            if (Boolean.TRUE.equals(request.get("cancelPendingAsn"))) {
                response.put("asnsCancelled", 2);
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/po/{poKey}/archive")
    public ResponseEntity<Map<String, Object>> archivePO(
            @PathVariable String poKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Archive PO: {}", poKey);

        // Check for open PO (422)
        if (OPEN_POS.contains(poKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "ARCH_001",
                "message", "Cannot archive PO that is not closed: " + poKey
            ));
        }

        // Check for PO with open receipts (422)
        if (POS_WITH_OPEN_RECEIPTS.contains(poKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "ARCH_002",
                "message", "Cannot archive PO with open receipts: " + poKey,
                "openReceiptKeys", List.of("RCV-OPEN-001", "RCV-OPEN-002")
            ));
        }

        // Success response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poKey", poKey);
        response.put("archived", true);
        response.put("archiveDate", LocalDateTime.now().toString());
        response.put("archivedAt", LocalDateTime.now().toString());
        response.put("retentionPolicy", "7_YEARS");
        response.put("purgeDate", LocalDateTime.now().plusYears(7).toString());

        // Handle archiveRelated option
        if (request != null && Boolean.TRUE.equals(request.get("archiveRelated"))) {
            response.put("archivedEntities", Map.of(
                "po", 1,
                "receipts", 3,
                "receiptDetails", 15
            ));
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/po/{poKey}/revert-cancel")
    public ResponseEntity<Map<String, Object>> revertCancelPO(
            @PathVariable String poKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Revert cancel PO: {}", poKey);

        // Remove from cancelled set so it can be cancelled again
        cancelledPOs.remove(poKey);

        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "status", "OPEN",
            "revertedFrom", "CANCELLED",
            "revertedAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/po/{poKey}/compensate")
    public ResponseEntity<Map<String, Object>> compensatePO(@PathVariable String poKey) {
        log.info("[E2E Mock] Compensate PO: {}", poKey);
        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "compensated", true,
            "compensatedAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/po/{poKey}/populate")
    public ResponseEntity<Map<String, Object>> populatePO(
            @PathVariable String poKey,
            @RequestBody(required = false) Map<String, Object> request) {
        log.info("[E2E Mock] Populate PO: {}", poKey);
        String receiptKey = "RCV-" + System.currentTimeMillis();

        // Calculate line count from request if present
        int lineCount = 1;
        if (request != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> details = (List<Map<String, Object>>) request.get("receiptDetails");
            if (details != null && !details.isEmpty()) {
                lineCount = details.size();
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "poKey", poKey,
            "receiptKey", receiptKey,
            "status", "POPULATED",
            "lineCount", lineCount,
            "populatedAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/po/{poKey}/lines/{lineNumber}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPOLine(
            @PathVariable String poKey,
            @PathVariable String lineNumber,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cancel PO line: {}/{}", poKey, lineNumber);
        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "lineNumber", lineNumber,
            "lineStatus", "CANCELLED",
            "poStatus", "OPEN",
            "cancelledAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/po/bulk-cancel")
    public ResponseEntity<Map<String, Object>> bulkCancelPO(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Bulk cancel POs: {}", request);

        @SuppressWarnings("unchecked")
        List<String> poKeys = (List<String>) request.getOrDefault("poKeys", List.of());
        int cancelledCount = poKeys.size();

        return ResponseEntity.ok(Map.of(
            "cancelledCount", cancelledCount,
            "failedCount", 0,
            "cancelled", poKeys,
            "cancelledAt", LocalDateTime.now().toString()
        ));
    }

    // ==================== PO History ====================

    @GetMapping("/po-history/{poKey}")
    public ResponseEntity<Map<String, Object>> getPOHistory(@PathVariable String poKey) {
        log.info("[E2E Mock] Get PO history: {}", poKey);
        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "archived", true,
            "archivedAt", LocalDateTime.now().minusDays(30).toString(),
            "history", List.of(
                Map.of("action", "CREATED", "timestamp", LocalDateTime.now().minusDays(60).toString()),
                Map.of("action", "RECEIVED", "timestamp", LocalDateTime.now().minusDays(45).toString()),
                Map.of("action", "ARCHIVED", "timestamp", LocalDateTime.now().minusDays(30).toString())
            )
        ));
    }

    @GetMapping("/po-history/search")
    public ResponseEntity<Map<String, Object>> searchPOHistory(
            @RequestParam(required = false) String storerKey,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Search PO history: storerKey={}, dateFrom={}, dateTo={}", storerKey, dateFrom, dateTo);

        List<Map<String, Object>> results = List.of(
            Map.of("poKey", "PO-HIST-001", "storerKey", storerKey != null ? storerKey : "TEST", "archivedAt", LocalDateTime.now().minusDays(30).toString()),
            Map.of("poKey", "PO-HIST-002", "storerKey", storerKey != null ? storerKey : "TEST", "archivedAt", LocalDateTime.now().minusDays(60).toString())
        );

        return ResponseEntity.ok(Map.of(
            "results", results,
            "totalCount", results.size()
        ));
    }

    @PostMapping("/po-history/{poKey}/unarchive")
    public ResponseEntity<Map<String, Object>> unarchivePO(
            @PathVariable String poKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Unarchive PO: {}", poKey);
        return ResponseEntity.ok(Map.of(
            "poKey", poKey,
            "restored", true,
            "unarchivedAt", LocalDateTime.now().toString()
        ));
    }

    // ==================== Receipt Operations ====================
    // These replace ReceiptController and FinalizeController during E2E tests
    private static final Set<String> NOTFOUND_RECEIPTS = Set.of("RCV-NOTFOUND", "RCV-XXX", "RCV-999");

    @GetMapping("/receipts/{receiptKey}")
    public ResponseEntity<Map<String, Object>> getMockReceipt(
            @PathVariable String receiptKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get receipt: {}", receiptKey);

        if (NOTFOUND_RECEIPTS.contains(receiptKey) || receiptKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_001",
                "message", "Receipt not found: " + receiptKey
            ));
        }

        // Return mock receipt with expected fields for tests
        // Extract poKey from receiptKey (e.g., RCV-HAPPY-001 becomes PO-HAPPY-001)
        String poKey = receiptKey.replace("RCV-", "PO-");
        return ResponseEntity.ok(Map.of(
            "receiptKey", receiptKey,
            "storerKey", "TEST_STORER_001",
            "facility", "TEST01",
            "status", "5", // Ready for finalization
            "poKey", poKey,
            "lineCount", 1
        ));
    }

    @GetMapping("/receipt/{receiptKey}")
    public ResponseEntity<Map<String, Object>> getReceipt(
            @PathVariable String receiptKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Delegate to receipts endpoint
        return getMockReceipt(receiptKey, authHeader);
    }

    @GetMapping("/receipt")
    public ResponseEntity<List<Map<String, Object>>> getReceiptList(
            @RequestParam String storerKey,
            @RequestParam String facility,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get receipts: storerKey={}, facility={}", storerKey, facility);
        return ResponseEntity.ok(List.of(
            Map.of("receiptKey", "RCV-001", "storerKey", storerKey, "facility", facility, "status", "0"),
            Map.of("receiptKey", "RCV-002", "storerKey", storerKey, "facility", facility, "status", "5")
        ));
    }

    @GetMapping("/receipt/by-po/{poKey}")
    public ResponseEntity<List<Map<String, Object>>> getReceiptsByPO(
            @PathVariable String poKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get receipts by PO: {}", poKey);
        return ResponseEntity.ok(List.of(
            Map.of("receiptKey", "RCV-001", "poKey", poKey, "status", "5")
        ));
    }

    @PatchMapping("/receipt/{receiptKey}/status")
    public ResponseEntity<?> updateReceiptStatus(
            @PathVariable String receiptKey,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Update receipt status: {} -> {}", receiptKey, status);

        if (NOTFOUND_RECEIPTS.contains(receiptKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_001",
                "message", "Receipt not found: " + receiptKey
            ));
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/receipts/{receiptKey}/finalize")
    public ResponseEntity<Map<String, Object>> finalizeReceipt(
            @PathVariable String receiptKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("[E2E Mock] Finalize receipt: {}", receiptKey);

        if (NOTFOUND_RECEIPTS.contains(receiptKey) || receiptKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_001",
                "message", "Receipt not found: " + receiptKey
            ));
        }

        // Extract options from request
        boolean createPutawayTasks = request != null && Boolean.TRUE.equals(request.get("createPutawayTasks"));
        boolean closePoIfComplete = request != null && Boolean.TRUE.equals(request.get("closePoIfComplete"));
        boolean qualityHold = request != null && Boolean.TRUE.equals(request.get("qualityHold"));
        String targetLocation = request != null ? (String) request.get("targetLocation") : null;
        String holdCode = request != null ? (String) request.get("holdCode") : null;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("receiptKey", receiptKey);
        response.put("status", "FINALIZED");
        response.put("workflowId", "WF-" + System.currentTimeMillis());
        response.put("linesFinalized", 3);
        response.put("finalizedBy", userId != null ? userId : "system");

        if (createPutawayTasks) {
            response.put("putawayTasksCreated", 2);
        }
        if (closePoIfComplete) {
            response.put("poClosedAutomatically", true);
        }
        if (qualityHold) {
            response.put("holdApplied", true);
        }
        if (targetLocation != null) {
            response.put("targetLocation", targetLocation);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/receipts/{receiptKey}/finalize/async")
    public ResponseEntity<Map<String, Object>> finalizeReceiptAsync(
            @PathVariable String receiptKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Finalize receipt async: {}", receiptKey);

        if (NOTFOUND_RECEIPTS.contains(receiptKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_001",
                "message", "Receipt not found: " + receiptKey
            ));
        }

        String workflowId = "WF-" + System.currentTimeMillis();
        return ResponseEntity.accepted().body(Map.of(
            "workflowId", workflowId,
            "statusUrl", "/api/v1/receipts/" + receiptKey + "/finalize/" + workflowId + "/status"
        ));
    }

    @GetMapping("/receipts/{receiptKey}/finalize/{workflowId}/status")
    public ResponseEntity<Map<String, Object>> getFinalizeStatus(
            @PathVariable String receiptKey,
            @PathVariable String workflowId) {
        log.info("[E2E Mock] Get finalize status: {}/{}", receiptKey, workflowId);
        return ResponseEntity.ok(Map.of(
            "workflowId", workflowId,
            "status", "COMPLETED",
            "currentStep", "DONE",
            "progress", 100,
            "canCancel", false
        ));
    }

    @PostMapping("/receipts/{receiptKey}/cancel")
    public ResponseEntity<Map<String, Object>> cancelReceipt(
            @PathVariable String receiptKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cancel receipt: {}", receiptKey);

        // Check for finalized receipt (422)
        if (FINALIZED_RECEIPTS.contains(receiptKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_009",
                "message", "Cannot cancel receipt that is already finalized: " + receiptKey
            ));
        }

        // Check for already cancelled receipt (422)
        if (cancelledReceipts.contains(receiptKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_010",
                "message", "Receipt already cancelled: " + receiptKey
            ));
        }

        // Check for not found
        if (NOTFOUND_RECEIPTS.contains(receiptKey) || receiptKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_001",
                "message", "Receipt not found: " + receiptKey
            ));
        }

        // Track cancellation
        cancelledReceipts.add(receiptKey);

        return ResponseEntity.ok(Map.of(
            "receiptKey", receiptKey,
            "status", "CANCELLED",
            "cancelledAt", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/receipts/{receiptKey}/details")
    public ResponseEntity<List<Map<String, Object>>> getReceiptDetails(@PathVariable String receiptKey) {
        log.info("[E2E Mock] Get receipt details: {}", receiptKey);
        return ResponseEntity.ok(List.of(
            Map.of("lineNumber", "00001", "sku", "SKU-001", "qtyExpected", 100, "qtyReceived", 100),
            Map.of("lineNumber", "00002", "sku", "SKU-002", "qtyExpected", 50, "qtyReceived", 50)
        ));
    }

    @GetMapping("/receipts/{receiptKey}/available")
    public ResponseEntity<Map<String, Object>> getReceiptAvailable(@PathVariable String receiptKey) {
        log.info("[E2E Mock] Get receipt available: {}", receiptKey);
        return ResponseEntity.ok(Map.of(
            "receiptKey", receiptKey,
            "available", true
        ));
    }

    @PostMapping("/receipts/bulk-lottable-update")
    public ResponseEntity<Map<String, Object>> bulkLottableUpdate(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Bulk lottable update: {}", request);
        return ResponseEntity.ok(Map.of(
            "updated", true,
            "updatedAt", LocalDateTime.now().toString()
        ));
    }

    // ==================== Trade Returns ====================
    private static final Set<String> NOTFOUND_RETURNS = Set.of("TR-NOTFOUND", "TR-XXX", "TR-999");
    private static final Set<String> INVALID_RETURN_DATA = Set.of("INVALID-RETURN", "BAD-DATA");
    private static final Set<String> NOTFOUND_ORIGINAL_ORDERS = Set.of("SO-DOES-NOT-EXIST", "SO-NOTFOUND", "SO-XXX");
    private static final int MAX_RETURN_QTY = 10000; // Quantities above this trigger "exceeds original" error

    @PostMapping("/trade-returns")
    public ResponseEntity<Map<String, Object>> createTradeReturn(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Create trade return: {}", request);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        String storerKey = (String) request.get("storerKey");
        String returnType = (String) request.get("returnType");
        String originalOrderKey = (String) request.get("originalOrderKey");

        // Check for not found original order FIRST (404) - before validation
        if (originalOrderKey != null && NOTFOUND_ORIGINAL_ORDERS.contains(originalOrderKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Original order not found: " + originalOrderKey
            ));
        }

        // Also check poKey for backward compatibility
        String poKey = (String) request.get("poKey");
        if (poKey != null && (poKey.startsWith("NOTFOUND-") || poKey.startsWith("XXX-"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Related PO not found: " + poKey
            ));
        }

        // Check for quantity exceeding original (422)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) request.get("lines");
        if (lines != null) {
            for (Map<String, Object> line : lines) {
                Object qtyReturned = line.get("qtyReturned");
                if (qtyReturned instanceof Number) {
                    int qty = ((Number) qtyReturned).intValue();
                    if (qty > MAX_RETURN_QTY) {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                            "errorCode", "TR_002",
                            "message", "Return quantity exceeds original order quantity"
                        ));
                    }
                }
            }
        }

        // Validation errors (422) - only check required fields if not already returning 404
        if (storerKey == null || storerKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "VAL_001",
                "message", "Storer key is required for trade return"
            ));
        }

        String returnKey = "TR-" + System.currentTimeMillis();

        // Build success response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("returnKey", returnKey);
        response.put("returnType", returnType != null ? returnType : "TRADE_RETURN");
        response.put("status", "0");
        response.put("storerKey", storerKey);
        response.put("createdAt", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/trade-returns")
    public ResponseEntity<List<Map<String, Object>>> getTradeReturns(
            @RequestParam(required = false) String storerKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get trade returns: storerKey={}", storerKey);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        return ResponseEntity.ok(List.of(
            Map.of("returnKey", "TR-001", "storerKey", storerKey != null ? storerKey : "TEST", "status", "0", "totalValue", 150.00),
            Map.of("returnKey", "TR-002", "storerKey", storerKey != null ? storerKey : "TEST", "status", "5", "totalValue", 250.00)
        ));
    }

    @GetMapping("/trade-returns/{returnKey}")
    public ResponseEntity<Map<String, Object>> getTradeReturn(
            @PathVariable String returnKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get trade return: {}", returnKey);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        return ResponseEntity.ok(Map.of(
            "returnKey", returnKey,
            "status", "0",
            "storerKey", "TEST_STORER",
            "totalValue", 100.00
        ));
    }

    @PostMapping("/trade-returns/{returnKey}/inspect")
    public ResponseEntity<Map<String, Object>> inspectTradeReturn(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Inspect trade return: {}, request: {}", returnKey, request);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        // Extract inspection details from request
        String inspectionResult = request != null ? (String) request.get("inspectionResult") : "PASS";
        String disposition = request != null ? (String) request.get("disposition") : "RETURN_TO_STOCK";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("returnKey", returnKey);
        response.put("disposition", disposition);

        // Determine qcStatus based on inspection result
        if ("FAIL".equalsIgnoreCase(inspectionResult)) {
            response.put("qcStatus", "FAIL");
            if ("SCRAP".equalsIgnoreCase(disposition)) {
                response.put("scrapTaskCreated", true);
            }
        } else if ("CONDITIONAL".equalsIgnoreCase(inspectionResult)) {
            response.put("qcStatus", "CONDITIONAL");
            if ("REFURBISH".equalsIgnoreCase(disposition)) {
                response.put("workOrderCreated", true);
                String workOrder = request != null ? (String) request.get("refurbWorkOrder") : null;
                if (workOrder != null) {
                    response.put("workOrder", workOrder);
                }
            }
        } else {
            response.put("qcStatus", "PASS");
        }

        response.put("inspectedAt", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/trade-returns/{returnKey}/inspect-batch")
    public ResponseEntity<Map<String, Object>> inspectBatchTradeReturn(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Inspect batch trade return: {}", returnKey);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        // Count inspections from request
        int processedCount = 0;
        if (request != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inspections = (List<Map<String, Object>>) request.get("inspections");
            if (inspections != null) {
                processedCount = inspections.size();
            }
        }

        return ResponseEntity.ok(Map.of(
            "returnKey", returnKey,
            "processedCount", processedCount > 0 ? processedCount : 3,
            "inspected", true,
            "batchProcessed", true
        ));
    }

    @PostMapping("/trade-returns/{returnKey}/finalize")
    public ResponseEntity<Map<String, Object>> finalizeTradeReturn(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Finalize trade return: {}", returnKey);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("returnKey", returnKey);
        response.put("status", "9");
        response.put("finalizedAt", LocalDateTime.now().toString());

        // Handle restock option
        if (request != null && Boolean.TRUE.equals(request.get("restockApproved"))) {
            response.put("restocked", true);
        }

        // Handle credit memo generation
        if (request != null && Boolean.TRUE.equals(request.get("generateCreditMemo"))) {
            response.put("creditMemoKey", "CM-" + System.currentTimeMillis());
            response.put("creditAmount", 150.00);
        }

        return ResponseEntity.ok(response);
    }

    @PutMapping("/trade-returns/{returnKey}/hold")
    public ResponseEntity<Map<String, Object>> holdTradeReturn(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Hold trade return: {}", returnKey);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        // Extract hold details from request
        String holdCode = request != null ? (String) request.get("holdCode") : "HOLD";
        String reason = request != null ? (String) request.get("reason") : "";

        return ResponseEntity.ok(Map.of(
            "returnKey", returnKey,
            "holdApplied", true,
            "holdCode", holdCode,
            "status", "H",
            "heldAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/trade-returns/{returnKey}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTradeReturn(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cancel trade return: {}", returnKey);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        return ResponseEntity.ok(Map.of(
            "returnKey", returnKey,
            "status", "CANCELLED",
            "cancelledAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping(value = "/trade-returns/{returnKey}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTradeReturnPhotosMultipart(
            @PathVariable String returnKey,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "lineNumber", required = false) String lineNumber,
            @RequestParam(value = "description", required = false) String description,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Upload trade return photos (multipart): {}, lineNumber={}", returnKey, lineNumber);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        String photoKey = "PHOTO-" + System.currentTimeMillis();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("returnKey", returnKey);
        response.put("photoKey", photoKey);
        response.put("lineNumber", lineNumber != null ? lineNumber : "00001");
        if (description != null) {
            response.put("description", description);
        }
        response.put("uploadedAt", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/trade-returns/{returnKey}/photos", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTradeReturnPhotosJson(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Upload trade return photos (json): {}", returnKey);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        String photoKey = "PHOTO-" + System.currentTimeMillis();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "returnKey", returnKey,
            "photoKey", photoKey,
            "uploadedAt", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/trade-returns/report")
    public ResponseEntity<Map<String, Object>> getTradeReturnReport(
            @RequestParam(required = false) String storerKey,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get trade return report");
        return ResponseEntity.ok(Map.of(
            "totalReturns", 25,
            "totalValue", 5000.00,
            "dispositionBreakdown", Map.of(
                "RETURN_TO_STOCK", 15,
                "SCRAP", 5,
                "REFURBISH", 3,
                "HOLD", 2
            ),
            "inspected", 20,
            "finalized", 15,
            "cancelled", 5
        ));
    }

    // ==================== RDT Operations ====================
    // RDT endpoints validate device, user, and facility authorization based on headers

    // Valid RDT devices and users for testing
    private static final Set<String> VALID_RDT_DEVICES = Set.of(
        "RDT-KR01-001", "RDT-KR01-002", "RDT-DEV-001", "RDT-DEV-002"
    );
    private static final Set<String> VALID_RDT_USERS = Set.of(
        "RDT-OPR-001", "RDT-OPR-002", "RDT-OP-001", "RDT-OP-002"
    );
    // User to authorized facilities mapping
    private static final Map<String, Set<String>> USER_FACILITY_AUTH = Map.of(
        "RDT-OPR-001", Set.of("KR01", "KR02"),
        "RDT-OPR-002", Set.of("IN01", "IN02"),
        "RDT-OP-001", Set.of("KR01", "KR02"),
        "RDT-OP-002", Set.of("IN01", "IN02")
    );
    private static final Set<String> EXPIRED_SESSIONS = Set.of("EXPIRED-SESSION-XXX");

    @PostMapping("/rdt/po")
    public ResponseEntity<Map<String, Object>> rdtCreatePO(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-RDT-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-RDT-User-Id", required = false) String userId,
            @RequestHeader(value = "X-RDT-Session-Id", required = false) String sessionId) {
        log.info("[E2E Mock] RDT Create PO: device={}, user={}, session={}", deviceId, userId, sessionId);

        // Check for expired session first
        if (sessionId != null && EXPIRED_SESSIONS.contains(sessionId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "RDT_004",
                "message", "Session expired or invalid"
            ));
        }

        // Validate device
        if (deviceId != null && !VALID_RDT_DEVICES.contains(deviceId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "RDT_001",
                "message", "Device not registered: " + deviceId
            ));
        }

        // Validate user
        if (userId != null && !VALID_RDT_USERS.contains(userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "RDT_002",
                "message", "Operator not found: " + userId
            ));
        }

        // Validate facility authorization
        String facility = (String) request.get("facility");
        if (userId != null && facility != null) {
            Set<String> authorizedFacilities = USER_FACILITY_AUTH.getOrDefault(userId, Set.of());
            if (!authorizedFacilities.contains(facility)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "errorCode", "RDT_003",
                    "message", "Not authorized for facility: " + facility
                ));
            }
        }

        // Success case
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "poKey", "PO-RDT-" + System.currentTimeMillis(),
            "status", "0",
            "createdBy", userId != null ? userId : "system",
            "device", deviceId != null ? deviceId : "unknown"
        ));
    }

    @PostMapping("/rdt/po/from-scan")
    public ResponseEntity<Map<String, Object>> rdtCreatePOFromScan(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-RDT-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-RDT-User-Id", required = false) String userId) {
        log.info("[E2E Mock] RDT Create PO from scan: {}", request);

        // Validate device
        if (deviceId != null && !VALID_RDT_DEVICES.contains(deviceId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "RDT_001",
                "message", "Device not registered: " + deviceId
            ));
        }

        // Count scanned items for response
        @SuppressWarnings("unchecked")
        List<Object> scannedItems = (List<Object>) request.getOrDefault("scannedItems", List.of());
        int resolvedSkus = scannedItems.size();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "poKey", "PO-SCAN-" + System.currentTimeMillis(),
            "status", "0",
            "resolvedSkus", resolvedSkus
        ));
    }

    @PostMapping("/rdt/po/offline-sync")
    public ResponseEntity<Map<String, Object>> rdtOfflineSync(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-RDT-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-RDT-User-Id", required = false) String userId) {
        log.info("[E2E Mock] RDT Offline sync: {}", request);
        // Offline sync returns 202 Accepted for async processing
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "queuedForProcessing", true,
            "syncId", "SYNC-" + System.currentTimeMillis(),
            "syncedAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/rdt/receipts/{receiptKey}/finalize")
    public ResponseEntity<Map<String, Object>> rdtFinalizeReceipt(
            @PathVariable String receiptKey,
            @RequestHeader(value = "X-RDT-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-RDT-User-Id", required = false) String userId,
            @RequestBody(required = false) Map<String, Object> request) {
        log.info("[E2E Mock] RDT Finalize receipt: {}", receiptKey);
        return ResponseEntity.ok(Map.of(
            "receiptKey", receiptKey,
            "status", "FINALIZED",
            "finalizedBy", userId != null ? userId : "system",
            "device", deviceId != null ? deviceId : "unknown",
            "finalizedAt", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/rdt/receipts/{receiptKey}/finalize-with-scan")
    public ResponseEntity<Map<String, Object>> rdtFinalizeReceiptWithScan(
            @PathVariable String receiptKey,
            @RequestHeader(value = "X-RDT-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-RDT-User-Id", required = false) String userId) {
        log.info("[E2E Mock] RDT Finalize receipt with scan: {}", receiptKey);
        return ResponseEntity.ok(Map.of(
            "receiptKey", receiptKey,
            "status", "FINALIZED",
            "finalizedBy", userId != null ? userId : "system",
            "device", deviceId != null ? deviceId : "unknown",
            "scanned", true
        ));
    }

    @PutMapping("/rdt/receipts/{receiptKey}/lines/{lineNumber}/lottables")
    public ResponseEntity<Map<String, Object>> rdtUpdateLottables(
            @PathVariable String receiptKey, @PathVariable String lineNumber,
            @RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] RDT Update lottables: {}/{}", receiptKey, lineNumber);
        return ResponseEntity.ok(Map.of(
            "receiptKey", receiptKey,
            "lineNumber", lineNumber,
            "updated", true
        ));
    }

    @PostMapping("/rdt/trade-returns/{returnKey}/receive")
    public ResponseEntity<Map<String, Object>> rdtReceiveTradeReturn(
            @PathVariable String returnKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "X-RDT-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-RDT-User-Id", required = false) String userId) {
        log.info("[E2E Mock] RDT Receive trade return: {}", returnKey);

        if (NOTFOUND_RETURNS.contains(returnKey) || returnKey.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TR_001",
                "message", "Trade return not found: " + returnKey
            ));
        }

        // Extract qty from request or default
        int qtyReceived = 10;
        if (request != null && request.get("qtyReceived") instanceof Number) {
            qtyReceived = ((Number) request.get("qtyReceived")).intValue();
        }

        // Calculate pending qty (simulate partial receiving)
        int qtyPending = qtyReceived < 10 ? 10 - qtyReceived : 0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("returnKey", returnKey);
        response.put("qtyReceived", qtyReceived);
        response.put("lineStatus", "RECEIVED");
        if (qtyPending > 0) {
            response.put("qtyPending", qtyPending);
        }
        response.put("receivedAt", LocalDateTime.now().toString());
        response.put("receivedBy", userId != null ? userId : "system");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/rdt/tasks/next")
    public ResponseEntity<Map<String, Object>> rdtGetNextTask() {
        log.info("[E2E Mock] RDT Get next task");
        return ResponseEntity.ok(Map.of(
            "taskKey", "TASK-" + System.currentTimeMillis(),
            "taskType", "PUTAWAY",
            "fromLocation", "RECV-01",
            "toLocation", "A-01-01"
        ));
    }

    @GetMapping("/rdt/tasks/queue")
    public ResponseEntity<List<Map<String, Object>>> rdtGetTaskQueue() {
        log.info("[E2E Mock] RDT Get task queue");
        return ResponseEntity.ok(List.of(
            Map.of("taskKey", "TASK-001", "taskType", "PUTAWAY", "priority", 1),
            Map.of("taskKey", "TASK-002", "taskType", "PICK", "priority", 2)
        ));
    }

    @PostMapping("/rdt/tasks/{taskKey}/assign")
    public ResponseEntity<Map<String, Object>> rdtAssignTask(@PathVariable String taskKey) {
        log.info("[E2E Mock] RDT Assign task: {}", taskKey);
        return ResponseEntity.ok(Map.of(
            "taskKey", taskKey,
            "assigned", true
        ));
    }

    @PostMapping("/rdt/tasks/{taskKey}/complete")
    public ResponseEntity<Map<String, Object>> rdtCompleteTask(@PathVariable String taskKey) {
        log.info("[E2E Mock] RDT Complete task: {}", taskKey);
        return ResponseEntity.ok(Map.of(
            "taskKey", taskKey,
            "completed", true,
            "completedAt", LocalDateTime.now().toString()
        ));
    }

    // ==================== Tasks ====================

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> getTasks() {
        log.info("[E2E Mock] Get tasks");
        return ResponseEntity.ok(List.of(
            Map.of("taskKey", "TASK-001", "status", "PENDING"),
            Map.of("taskKey", "TASK-002", "status", "IN_PROGRESS")
        ));
    }

    @PostMapping("/tasks/{taskKey}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskKey) {
        log.info("[E2E Mock] Cancel task: {}", taskKey);
        return ResponseEntity.ok(Map.of(
            "taskKey", taskKey,
            "status", "CANCELLED"
        ));
    }

    @PostMapping("/tasks/{taskKey}/reassign")
    public ResponseEntity<Map<String, Object>> reassignTask(@PathVariable String taskKey) {
        log.info("[E2E Mock] Reassign task: {}", taskKey);
        return ResponseEntity.ok(Map.of(
            "taskKey", taskKey,
            "reassigned", true
        ));
    }

    @PostMapping("/tasks/{taskKey}/timeout")
    public ResponseEntity<Map<String, Object>> timeoutTask(@PathVariable String taskKey) {
        log.info("[E2E Mock] Timeout task: {}", taskKey);
        return ResponseEntity.ok(Map.of(
            "taskKey", taskKey,
            "timedOut", true
        ));
    }

    @GetMapping("/tasks/{taskKey}/audit")
    public ResponseEntity<List<Map<String, Object>>> getTaskAudit(@PathVariable String taskKey) {
        log.info("[E2E Mock] Get task audit: {}", taskKey);
        return ResponseEntity.ok(List.of(
            Map.of("action", "CREATED", "timestamp", LocalDateTime.now().minusHours(2).toString()),
            Map.of("action", "ASSIGNED", "timestamp", LocalDateTime.now().minusHours(1).toString())
        ));
    }

    @PostMapping("/tasks/batch-complete")
    public ResponseEntity<Map<String, Object>> batchCompleteTasks(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Batch complete tasks: {}", request);
        return ResponseEntity.ok(Map.of(
            "completed", true,
            "count", 5
        ));
    }

    @PostMapping("/tasks/consolidate")
    public ResponseEntity<Map<String, Object>> consolidateTasks(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Consolidate tasks: {}", request);
        return ResponseEntity.ok(Map.of(
            "consolidated", true
        ));
    }

    @PostMapping("/tasks/interleaved-assignment")
    public ResponseEntity<Map<String, Object>> interleavedAssignment(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Interleaved assignment: {}", request);
        return ResponseEntity.ok(Map.of(
            "assigned", true
        ));
    }

    @GetMapping("/tasks/metrics")
    public ResponseEntity<Map<String, Object>> getTaskMetrics() {
        log.info("[E2E Mock] Get task metrics");
        return ResponseEntity.ok(Map.of(
            "totalTasks", 100,
            "completed", 80,
            "pending", 15,
            "inProgress", 5
        ));
    }

    // ==================== Cross-Dock ====================

    @PostMapping("/xdock/allocate")
    public ResponseEntity<Map<String, Object>> xdockAllocate(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] X-Dock allocate: {}", request);
        return ResponseEntity.ok(Map.of(
            "allocated", true,
            "allocationKey", "XDOCK-" + System.currentTimeMillis()
        ));
    }

    @PostMapping("/xdock/allocate-batch")
    public ResponseEntity<Map<String, Object>> xdockAllocateBatch(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] X-Dock allocate batch: {}", request);
        return ResponseEntity.ok(Map.of(
            "allocated", true,
            "batchId", "BATCH-" + System.currentTimeMillis()
        ));
    }

    @PostMapping("/xdock/allocate-full")
    public ResponseEntity<Map<String, Object>> xdockAllocateFull(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] X-Dock allocate full: {}", request);
        return ResponseEntity.ok(Map.of(
            "allocated", true,
            "fullAllocation", true
        ));
    }

    @PostMapping("/xdock/allocate-multi")
    public ResponseEntity<Map<String, Object>> xdockAllocateMulti(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] X-Dock allocate multi: {}", request);
        return ResponseEntity.ok(Map.of(
            "allocated", true,
            "multiAllocation", true
        ));
    }

    @PostMapping("/xdock/auto-allocate")
    public ResponseEntity<Map<String, Object>> xdockAutoAllocate(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] X-Dock auto allocate: {}", request);
        return ResponseEntity.ok(Map.of(
            "allocated", true,
            "autoAllocation", true
        ));
    }

    @PostMapping("/xdock/process-linkage")
    public ResponseEntity<Map<String, Object>> xdockProcessLinkage(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] X-Dock process linkage: {}", request);
        return ResponseEntity.ok(Map.of(
            "processed", true
        ));
    }

    // ==================== Workflows ====================

    @GetMapping("/workflows/{workflowId}/status")
    public ResponseEntity<Map<String, Object>> getWorkflowStatus(@PathVariable String workflowId) {
        log.info("[E2E Mock] Get workflow status: {}", workflowId);
        return ResponseEntity.ok(Map.of(
            "workflowId", workflowId,
            "status", "COMPLETED",
            "completedAt", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/workflows/{workflowId}/history")
    public ResponseEntity<List<Map<String, Object>>> getWorkflowHistory(@PathVariable String workflowId) {
        log.info("[E2E Mock] Get workflow history: {}", workflowId);
        return ResponseEntity.ok(List.of(
            Map.of("event", "STARTED", "timestamp", LocalDateTime.now().minusMinutes(10).toString()),
            Map.of("event", "ACTIVITY_COMPLETED", "timestamp", LocalDateTime.now().minusMinutes(5).toString()),
            Map.of("event", "COMPLETED", "timestamp", LocalDateTime.now().toString())
        ));
    }

    @PostMapping("/workflows/{workflowId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelWorkflow(@PathVariable String workflowId) {
        log.info("[E2E Mock] Cancel workflow: {}", workflowId);
        return ResponseEntity.ok(Map.of(
            "workflowId", workflowId,
            "cancelled", true
        ));
    }

    // ==================== Jobs ====================

    @PostMapping("/jobs/generic-inbound-po/trigger")
    public ResponseEntity<Map<String, Object>> triggerGenericInboundJob() {
        log.info("[E2E Mock] Trigger generic inbound PO job");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "jobExecutionId", "JOB-" + System.currentTimeMillis(),
            "status", "RUNNING"
        ));
    }

    @PostMapping("/jobs/auto-finalize/trigger")
    public ResponseEntity<Map<String, Object>> triggerAutoFinalizeJob() {
        log.info("[E2E Mock] Trigger auto finalize job");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "jobExecutionId", "JOB-" + System.currentTimeMillis(),
            "status", "RUNNING"
        ));
    }

    @PostMapping("/jobs/po-archive/trigger")
    public ResponseEntity<Map<String, Object>> triggerArchiveJob(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Trigger PO archive job: {}", request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "jobExecutionId", "JOB-" + System.currentTimeMillis(),
            "status", "RUNNING",
            "estimatedRecords", 150
        ));
    }

    @PostMapping("/jobs/po-expire-cancel/trigger")
    public ResponseEntity<Map<String, Object>> triggerExpireCancelJob() {
        log.info("[E2E Mock] Trigger PO expire cancel job");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "jobExecutionId", "JOB-" + System.currentTimeMillis(),
            "status", "RUNNING"
        ));
    }

    @PostMapping("/jobs/po-purge/trigger")
    public ResponseEntity<Map<String, Object>> triggerPurgeJob() {
        log.info("[E2E Mock] Trigger PO purge job");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "jobExecutionId", "JOB-" + System.currentTimeMillis(),
            "status", "RUNNING"
        ));
    }

    @GetMapping("/jobs/executions/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobExecution(
            @PathVariable String jobId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get job execution: {}", jobId);
        return ResponseEntity.ok(Map.of(
            "jobExecutionId", jobId,
            "status", "COMPLETED",
            "recordsProcessed", 42,
            "completedAt", LocalDateTime.now().toString()
        ));
    }

    // ==================== Inventory ====================

    @GetMapping("/inventory/search")
    public ResponseEntity<List<Map<String, Object>>> searchInventory(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String location) {
        log.info("[E2E Mock] Search inventory: sku={}, location={}", sku, location);
        return ResponseEntity.ok(List.of(
            Map.of("sku", sku != null ? sku : "SKU-001", "location", "A-01-01", "qty", 100),
            Map.of("sku", sku != null ? sku : "SKU-001", "location", "A-01-02", "qty", 50)
        ));
    }

    @PostMapping("/inventory/split")
    public ResponseEntity<Map<String, Object>> splitInventory(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Split inventory: {}", request);
        return ResponseEntity.ok(Map.of(
            "split", true,
            "newLpn", "LPN-" + System.currentTimeMillis()
        ));
    }

    @PostMapping("/inventory/consolidate")
    public ResponseEntity<Map<String, Object>> consolidateInventory(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Consolidate inventory: {}", request);
        return ResponseEntity.ok(Map.of(
            "consolidated", true
        ));
    }

    // ==================== ASN ====================

    @PostMapping("/asn/populate")
    public ResponseEntity<Map<String, Object>> populateASN(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Populate ASN: {}", request);
        String receiptKey = "RCV-" + System.currentTimeMillis();
        String poKey = (String) request.get("poKey");

        // Calculate carton count and total qty from request
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cartons = (List<Map<String, Object>>) request.get("cartons");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) request.get("lines");

        int cartonCount = cartons != null ? cartons.size() : 0;
        int totalQty = 0;
        boolean isPartialShipment = Boolean.TRUE.equals(request.get("isPartialShipment"));
        boolean allowOverReceipt = Boolean.TRUE.equals(request.get("allowOverReceipt"));
        int overReceiptQty = 0;

        // Calculate total qty from lines or cartons
        if (cartons != null) {
            for (Map<String, Object> carton : cartons) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cartonLines = (List<Map<String, Object>>) carton.get("lines");
                if (cartonLines != null) {
                    for (Map<String, Object> line : cartonLines) {
                        Object qty = line.get("qtyShipped");
                        if (qty instanceof Number) {
                            totalQty += ((Number) qty).intValue();
                        }
                    }
                }
            }
        } else if (lines != null) {
            for (Map<String, Object> line : lines) {
                Object qtyShipped = line.get("qtyShipped");
                Object qtyOrdered = line.get("qtyOrdered");
                if (qtyShipped instanceof Number) {
                    int shipped = ((Number) qtyShipped).intValue();
                    totalQty += shipped;
                    if (qtyOrdered instanceof Number && allowOverReceipt) {
                        int ordered = ((Number) qtyOrdered).intValue();
                        if (shipped > ordered) {
                            overReceiptQty += (shipped - ordered);
                        }
                    }
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("receiptKey", receiptKey);
        response.put("asnKey", "ASN-" + System.currentTimeMillis());
        response.put("status", "POPULATED");
        if (poKey != null) {
            response.put("linkedPoKey", poKey);
        }
        if (cartonCount > 0) {
            response.put("cartonCount", cartonCount);
        }
        if (totalQty > 0) {
            response.put("totalQty", totalQty);
        }
        if (isPartialShipment) {
            response.put("partialShipment", true);
        }
        if (overReceiptQty > 0) {
            response.put("overReceiptWarning", true);
            response.put("overReceiptQty", overReceiptQty);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==================== Saga ====================

    @PostMapping("/saga/po-to-inventory")
    public ResponseEntity<Map<String, Object>> sagaPOToInventory(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Saga PO to inventory: {}", request);
        return ResponseEntity.ok(Map.of(
            "sagaId", "SAGA-" + System.currentTimeMillis(),
            "status", "COMPLETED"
        ));
    }

    // ==================== Putaway ====================

    @PostMapping("/putaway/suggest-location")
    public ResponseEntity<Map<String, Object>> suggestPutawayLocation(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Suggest putaway location: {}", request);
        return ResponseEntity.ok(Map.of(
            "suggestedLocation", "A-01-01",
            "alternateLocations", List.of("A-01-02", "A-01-03")
        ));
    }

    // ==================== Reports ====================

    @GetMapping("/reports/archival")
    public ResponseEntity<Map<String, Object>> getArchivalReport(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get archival report");
        return ResponseEntity.ok(Map.of(
            "totalArchived", 150,
            "totalPurged", 25,
            "storageRecovered", "2.5GB",
            "thisMonth", 25
        ));
    }

    @GetMapping("/reports/cancellations")
    public ResponseEntity<Map<String, Object>> getCancellationsReport(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get cancellations report");
        return ResponseEntity.ok(Map.of(
            "totalCancelled", 50,
            "reasonBreakdown", Map.of(
                "VENDOR_ISSUE", 20,
                "CUSTOMER_REQUEST", 15,
                "BUDGET_REDUCTION", 10,
                "OTHER", 5
            ),
            "thisMonth", 10
        ));
    }

    @GetMapping("/reports/lottable-summary")
    public ResponseEntity<Map<String, Object>> getLottableSummaryReport() {
        log.info("[E2E Mock] Get lottable summary report");
        return ResponseEntity.ok(Map.of(
            "totalRecords", 1000,
            "withLottables", 800
        ));
    }

    // ==================== EDI ====================

    @PostMapping("/edi/inbound")
    public ResponseEntity<Map<String, Object>> processEDIInbound(
            @RequestBody String ediContent,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Process EDI inbound: {} chars", ediContent != null ? ediContent.length() : 0);

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }

        // Check for malformed EDI content (400)
        if (ediContent == null || ediContent.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "EDI_001",
                "message", "EDI content is required"
            ));
        }
        if (ediContent.contains("MALFORMED") || ediContent.contains("INVALID-EDI")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "EDI_002",
                "message", "Malformed EDI content"
            ));
        }

        // Determine transaction type from content (simplified)
        String transactionType = "850"; // Default to PO
        if (ediContent.contains("856")) {
            transactionType = "856"; // ASN
        }
        // EDI inbound returns 202 Accepted for async processing
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "messageId", "EDI-" + System.currentTimeMillis(),
            "status", "ACCEPTED",
            "transactionType", transactionType
        ));
    }

    @GetMapping("/edi/messages/{messageId}/parsed")
    public ResponseEntity<Map<String, Object>> getEDIParsedMessage(
            @PathVariable String messageId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get EDI parsed message: {}", messageId);

        if (messageId.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "EDI_003",
                "message", "EDI message not found: " + messageId
            ));
        }

        return ResponseEntity.ok(Map.of(
            "messageId", messageId,
            "type", "850",
            "parsed", true
        ));
    }

    @GetMapping("/edi/messages/{messageId}/status")
    public ResponseEntity<Map<String, Object>> getEDIMessageStatus(
            @PathVariable String messageId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get EDI message status: {}", messageId);

        if (messageId.startsWith("NOTFOUND-")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "EDI_003",
                "message", "EDI message not found: " + messageId
            ));
        }

        return ResponseEntity.ok(Map.of(
            "messageId", messageId,
            "status", "PROCESSED",
            "processingStatus", "COMPLETED"
        ));
    }

    // ==================== Audit ====================

    @GetMapping("/audit/lottable-changes")
    public ResponseEntity<List<Map<String, Object>>> getLottableChangesAudit() {
        log.info("[E2E Mock] Get lottable changes audit");
        return ResponseEntity.ok(List.of(
            Map.of("changeId", "CHG-001", "field", "lottable01", "oldValue", "OLD", "newValue", "NEW"),
            Map.of("changeId", "CHG-002", "field", "lottable02", "oldValue", "A", "newValue", "B")
        ));
    }
}
