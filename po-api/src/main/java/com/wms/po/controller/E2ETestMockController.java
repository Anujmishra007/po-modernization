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

    // Track created PO external keys to detect duplicates
    private final Set<String> createdPoExternalKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, String> externalKeyToPoKey = new ConcurrentHashMap<>();

    // Additional error trigger patterns
    private static final Set<String> INVALID_SUPPLIERS = Set.of("INVALID-SUPPLIER-999", "INVALID_SUPPLIER", "UNKNOWN-SUPPLIER");
    private static final Set<String> ERROR_STORERS = Set.of("TEST_STORER_ERR", "STORER_INACTIVE", "STORER_DISABLED");

    // ==================== PO CRUD Operations ====================
    // These replace POController during E2E tests

    @PostMapping(value = "/po", consumes = {"application/json", "text/plain", "text/xml", "*/*"})
    public ResponseEntity<Map<String, Object>> createPO(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Facility", required = false) String facilityHeader,
            @RequestHeader(value = "X-Test-Simulate-DB-Timeout", required = false) String dbTimeout,
            @RequestHeader(value = "X-Test-Simulate-Service-Down", required = false) String serviceDown,
            @RequestHeader(value = "X-Test-Simulate-Slow-Processing", required = false) String slowProcessing,
            @RequestHeader(value = "X-Test-Simulate-Internal-Error", required = false) String internalError) {

        log.info("[E2E Mock] Create PO: {}, contentType={}, dbTimeout={}, serviceDown={}, slowProcessing={}",
                request, contentType, dbTimeout, serviceDown, slowProcessing);

        // Check for DB timeout simulation (503)
        if ("true".equalsIgnoreCase(dbTimeout)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "errorCode", "INT_022",
                "message", "Database connection timeout",
                "retryable", true
            ));
        }

        // Check for service down simulation (503)
        if ("true".equalsIgnoreCase(serviceDown)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "errorCode", "INT_023",
                "message", "Service unavailable - please retry later",
                "retryAfter", 30
            ));
        }

        // Check for slow processing timeout simulation (504)
        if ("true".equalsIgnoreCase(slowProcessing)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "errorCode", "INT_003",
                "message", "Gateway timeout - request processing took too long"
            ));
        }

        // Check for internal error simulation (500)
        if ("true".equalsIgnoreCase(internalError)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "errorCode", "INT_500",
                "message", "Internal server error during PO creation",
                "retryable", true
            ));
        }

        // Check authentication
        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Authorization header is required"
            ));
        }
        if (authHeader.contains("invalid") || authHeader.contains("expired")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "errorCode", "AUTH_001",
                "message", "Invalid or expired token"
            ));
        }
        // Check for read-only token (403)
        if (authHeader.contains("readonly") || authHeader.contains("read-only")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "errorCode", "AUTH_002",
                "message", "Insufficient privileges to create PO",
                "requiredPrivilege", "PO_CREATE"
            ));
        }

        // Check content type (415) - if no Content-Type header at all
        if (contentType == null || contentType.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of(
                "errorCode", "MEDIA_001",
                "message", "Content-Type header is required"
            ));
        }
        if (!contentType.contains("application/json")) {
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
        String supplierKey = request.get("supplierKey") != null ? request.get("supplierKey").toString() : null;
        // Support both externPoKey and externalOrderKey field names
        String externPoKey = request.get("externPoKey") != null ? request.get("externPoKey").toString() :
                             request.get("externalOrderKey") != null ? request.get("externalOrderKey").toString() : null;

        // Validation checks (400) - Required fields first
        if (storerKey == null || storerKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "VAL_001",
                "message", "storerKey is required",
                "field", "storerKey"
            ));
        }
        if (facility == null || facility.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "VAL_001",
                "message", "facility is required",
                "field", "facility"
            ));
        }

        // Check for duplicate PO external key (409) - check tracking
        if (externPoKey != null) {
            if (DUPLICATE_PO_KEYS.contains(externPoKey) || createdPoExternalKeys.contains(externPoKey)) {
                String existingPoKey = externalKeyToPoKey.getOrDefault(externPoKey, "PO-EXISTING");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "errorCode", "VAL_003",
                    "message", "Duplicate PO - external key already exists: " + externPoKey,
                    "existingPoKey", existingPoKey
                ));
            }
        }

        // Check for service down triggers in data (503)
        if (SERVICE_DOWN_TRIGGERS.contains(storerKey) || SERVICE_DOWN_TRIGGERS.contains(facility)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "errorCode", "INT_023",
                "message", "Service temporarily unavailable",
                "retryAfter", 60
            ));
        }

        // Check for timeout triggers in data (504)
        if (TIMEOUT_TRIGGERS.contains(storerKey) || (externPoKey != null && externPoKey.contains("TIMEOUT"))) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "errorCode", "INT_003",
                "message", "Gateway timeout - request took too long"
            ));
        }

        // Check authorization for facility (403)
        if (INVALID_FACILITIES.contains(facility)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "errorCode", "AUTH_003",
                "message", "Not authorized for facility: " + facility
            ));
        }

        // Check for inactive/error storer (422)
        if (ERROR_STORERS.contains(storerKey) || INVALID_STORERS.contains(storerKey)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "VAL_002",
                "message", "Storer is inactive or invalid: " + storerKey
            ));
        }

        // Check for invalid supplier (422)
        if (supplierKey != null && (INVALID_SUPPLIERS.contains(supplierKey) || supplierKey.startsWith("INVALID-SUPPLIER"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "PO_012",
                "message", "Invalid or unknown Supplier: " + supplierKey
            ));
        }

        // Check for invalid SKU in lines (422)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) request.get("lines");
        if (lines != null) {
            for (Map<String, Object> line : lines) {
                String sku = line.get("sku") != null ? line.get("sku").toString() : null;
                if (sku != null && (sku.startsWith("INVALID-SKU") || sku.startsWith("TEST-SKU-ERR"))) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "errorCode", "PO_013",
                        "message", "Invalid or unknown SKU: " + sku,
                        "details", Map.of("sku", sku)
                    ));
                }
            }
        }

        // Check for non-existent storer pattern (422)
        if (storerKey != null && (storerKey.startsWith("NON_EXISTENT") || storerKey.startsWith("NONEXISTENT"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "VAL_006",
                "message", "Storer does not exist: " + storerKey
            ));
        }

        // Check for internal server error patterns (500)
        if ((storerKey != null && (storerKey.contains("INTERNAL_ERROR") || storerKey.contains("SERVER_ERROR") ||
             storerKey.contains("DB_ERROR") || storerKey.contains("CRITICAL"))) ||
            (externPoKey != null && (externPoKey.contains("INTERNAL_ERROR") || externPoKey.contains("SERVER_ERROR") ||
             externPoKey.contains("FAIL_500") || externPoKey.contains("DB_ERROR")))) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "errorCode", "INT_500",
                "legacyCode", 68899,
                "message", "Internal server error during PO creation",
                "retryable", true
            ));
        }

        // Check for too-long external key (400)
        if (externPoKey != null && externPoKey.length() > 50) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "VAL_007",
                "message", "External PO key exceeds maximum length of 50 characters"
            ));
        }

        // Check for specific validation error patterns (lines already extracted above)
        if (lines != null) {
            for (Map<String, Object> line : lines) {
                Object qty = line.get("qtyOrdered");
                if (qty != null && qty instanceof Number) {
                    int qtyValue = ((Number) qty).intValue();
                    // Zero quantity (400)
                    if (qtyValue == 0) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "errorCode", "VAL_008",
                            "message", "Quantity cannot be zero"
                        ));
                    }
                    // Negative quantity (400)
                    if (qtyValue < 0) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "errorCode", "VAL_004",
                            "message", "Quantity cannot be negative"
                        ));
                    }
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

        // Check for past expected date (400)
        Object expectedDateObj = request.get("expectedDate");
        if (expectedDateObj != null) {
            String expectedDateStr = expectedDateObj.toString();
            // Simple check: if it contains a date before 2026, it's in the past
            if (expectedDateStr.startsWith("202") && expectedDateStr.compareTo("2026-05-08") < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "errorCode", "VAL_009",
                    "message", "Expected date cannot be in the past"
                ));
            }
        }

        // Success - create PO
        String poKey = "PO-" + System.currentTimeMillis();

        // Track the external key for duplicate detection
        if (externPoKey != null) {
            createdPoExternalKeys.add(externPoKey);
            externalKeyToPoKey.put(externPoKey, poKey);
        }

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poKey", poKey);
        response.put("externalOrderKey", externPoKey);
        response.put("storerKey", storerKey);
        response.put("facility", facility);
        response.put("status", "0");
        response.put("lineCount", lines != null ? lines.size() : 0);
        response.put("createdAt", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

        // 404 - Not Found scenarios
        if (NOTFOUND_RECEIPTS.contains(receiptKey) || receiptKey.startsWith("NOTFOUND-") ||
            receiptKey.contains("DOES-NOT-EXIST") || receiptKey.contains("NOT-EXIST")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_001",
                "legacyCode", 69001,
                "message", "Receipt not found: " + receiptKey
            ));
        }

        // 422 - Validation/Business rule errors
        if (receiptKey.startsWith("RCV-ERR-") || receiptKey.contains("-ERR-") || receiptKey.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_010",
                "legacyCode", 69010,
                "message", "Receipt cannot be finalized: validation failed for " + receiptKey,
                "receiptKey", receiptKey,
                "retryable", false
            ));
        }

        // 422 - Already finalized (RCV-HAPPY-* re-finalization attempts)
        if (receiptKey.startsWith("RCV-HAPPY-")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_011",
                "legacyCode", 69011,
                "message", "Receipt already finalized: " + receiptKey,
                "receiptKey", receiptKey,
                "currentStatus", "FINALIZED",
                "retryable", false
            ));
        }

        // 504 - Timeout scenarios
        if (receiptKey.startsWith("RCV-TIMEOUT-") || receiptKey.contains("TIMEOUT")) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "errorCode", "RCV_020",
                "legacyCode", 69020,
                "message", "Finalization timed out for receipt: " + receiptKey,
                "receiptKey", receiptKey,
                "retryable", true
            ));
        }

        // 500 - Database error scenarios
        if (receiptKey.startsWith("RCV-DBERR-") || receiptKey.contains("DBERR")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "errorCode", "RCV_030",
                "legacyCode", 69030,
                "message", "Database error during finalization: " + receiptKey,
                "receiptKey", receiptKey,
                "retryable", true
            ));
        }

        // 409 - Concurrent modification conflict
        if (receiptKey.startsWith("RCV-CONC-") || receiptKey.contains("CONC")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "errorCode", "RCV_040",
                "legacyCode", 69040,
                "message", "Concurrent modification detected for receipt: " + receiptKey,
                "receiptKey", receiptKey,
                "retryable", true
            ));
        }

        // 202 - Async/Temporal workflow processing
        if (receiptKey.startsWith("RCV-TEMPORAL-") || receiptKey.startsWith("RCV-ASYNC-") || receiptKey.contains("TEMPORAL")) {
            String workflowId = "WF-" + System.currentTimeMillis();
            return ResponseEntity.accepted().body(Map.of(
                "receiptKey", receiptKey,
                "workflowId", workflowId,
                "status", "PROCESSING",
                "async", true,
                "statusUrl", "/api/v1/receipts/" + receiptKey + "/finalize/" + workflowId + "/status"
            ));
        }

        // Extract options from request
        boolean createPutawayTasks = request != null && Boolean.TRUE.equals(request.get("createPutawayTasks"));
        boolean closePoIfComplete = request != null && Boolean.TRUE.equals(request.get("closePoIfComplete"));
        boolean qualityHold = request != null && Boolean.TRUE.equals(request.get("qualityHold"));
        String targetLocation = request != null ? (String) request.get("targetLocation") : null;

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
    public ResponseEntity<Map<String, Object>> bulkLottableUpdate(
            @RequestBody(required = false) Map<String, Object> request) {
        log.info("[E2E Mock] Bulk lottable update: {}", request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updates = request != null ?
            (List<Map<String, Object>>) request.get("updates") : null;

        int updatedCount = updates != null ? updates.size() : 0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("updated", true);
        response.put("updatedCount", updatedCount);
        response.put("updatedAt", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
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

    @GetMapping("/rdt/tasks/next")
    public ResponseEntity<Map<String, Object>> rdtGetNextTask(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String facility) {
        log.info("[E2E Mock] RDT Get next task: taskType={}, facility={}", taskType, facility);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskKey", "TASK-" + System.currentTimeMillis());
        response.put("taskType", taskType != null ? taskType : "PUTAWAY");
        response.put("fromLocation", "RECV-01");
        response.put("toLocation", "A-01-01");
        if (facility != null) {
            response.put("facility", facility);
        }
        return ResponseEntity.ok(response);
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
    public ResponseEntity<Map<String, Object>> rdtCompleteTask(
            @PathVariable String taskKey,
            @RequestBody(required = false) Map<String, Object> request) {
        log.info("[E2E Mock] RDT Complete task: {}", taskKey);

        // Simulate validation errors for specific task keys
        if (taskKey.contains("PUTAWAY-003") || taskKey.contains("PUTAWAY-004")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "TASK_ERR_001",
                "message", "Task cannot be completed: invalid location state",
                "taskKey", taskKey
            ));
        }
        // Check for error patterns but exclude OVERRIDE which contains "ERR"
        if ((taskKey.contains("-ERR-") || taskKey.startsWith("ERR-") || taskKey.endsWith("-ERR") ||
             taskKey.contains("INVALID")) && !taskKey.contains("OVERRIDE")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "TASK_ERR_002",
                "message", "Task completion failed: " + taskKey,
                "taskKey", taskKey
            ));
        }

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
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalTasks", 100);
        metrics.put("completed", 80);
        metrics.put("pending", 15);
        metrics.put("inProgress", 5);
        metrics.put("avgCompletionTime", 125.5);
        metrics.put("throughputPerHour", 45);
        return ResponseEntity.ok(metrics);
    }

    // ==================== Cross-Dock Utilities ====================

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
    public ResponseEntity<Map<String, Object>> splitInventory(
            @RequestBody(required = false) Map<String, Object> request) {
        log.info("[E2E Mock] Split inventory: {}", request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("split", true);
        response.put("newLpn", "LPN-" + System.currentTimeMillis());
        if (request != null && request.get("qty") != null) {
            response.put("qty", request.get("qty"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/inventory/consolidate")
    public ResponseEntity<Map<String, Object>> consolidateInventory(@RequestBody Map<String, Object> request) {
        log.info("[E2E Mock] Consolidate inventory: {}", request);
        return ResponseEntity.ok(Map.of(
            "consolidated", true
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
                "legacyCode", 70001,
                "message", "EDI content is required"
            ));
        }

        // Check for various error patterns in EDI content
        if (ediContent.contains("MALFORMED") || ediContent.contains("INVALID-EDI") ||
            ediContent.contains("BAD_FORMAT") || ediContent.contains("ERROR") ||
            ediContent.contains("PARSE_FAIL") || ediContent.contains("<xml>") ||
            !ediContent.contains("ISA") || ediContent.length() < 10) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "EDI_002",
                "legacyCode", 70002,
                "message", "Malformed EDI content: invalid format or structure",
                "retryable", false
            ));
        }

        // Check for validation errors in EDI content
        if (ediContent.contains("INVALID_STORER") || ediContent.contains("INVALID_FACILITY") ||
            ediContent.contains("MISSING_SEGMENT")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "EDI_003",
                "legacyCode", 70003,
                "message", "EDI validation failed: missing required segments or invalid values",
                "retryable", false
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

    // ==================== Cross-Dock Allocation ====================

    @PostMapping("/xdock/allocate")
    public ResponseEntity<Map<String, Object>> xdockAllocate(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cross-dock allocate: {}", request);

        String receiptKey = request != null ? (String) request.get("receiptKey") : null;
        String orderKey = request != null ? (String) request.get("orderKey") : null;
        String sku = request != null ? (String) request.get("sku") : null;
        String storerKey = request != null ? (String) request.get("storerKey") : null;
        String facility = request != null ? (String) request.get("facility") : null;
        Object qtyObj = request != null ? request.get("qty") : null;
        int qty = qtyObj instanceof Number ? ((Number) qtyObj).intValue() : 0;

        // 422 - Validation errors for various patterns
        if (receiptKey != null && (receiptKey.contains("-ERR-") || receiptKey.contains("INVALID") ||
            receiptKey.contains("NOT_ELIGIBLE") || receiptKey.contains("ALREADY_ALLOCATED"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "XDOCK_001",
                "message", "Receipt not eligible for cross-dock: " + receiptKey
            ));
        }
        if (orderKey != null && (orderKey.contains("-ERR-") || orderKey.contains("INVALID") ||
            orderKey.contains("CLOSED") || orderKey.contains("CANCELLED"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "XDOCK_004",
                "message", "Order not eligible for allocation: " + orderKey
            ));
        }
        if (sku != null && (sku.contains("INVALID") || sku.contains("-ERR-") ||
            sku.contains("HAZMAT") || sku.contains("RESTRICTED"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "XDOCK_005",
                "message", "SKU not eligible for cross-dock: " + sku
            ));
        }
        if (storerKey != null && (storerKey.contains("INVALID") || storerKey.contains("INACTIVE"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "XDOCK_006",
                "message", "Storer not eligible for cross-dock: " + storerKey
            ));
        }

        // 404 - Not found scenarios
        if (orderKey != null && (orderKey.contains("NOTFOUND") || orderKey.contains("NOT_EXIST"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "XDOCK_002",
                "message", "Order not found: " + orderKey
            ));
        }
        if (receiptKey != null && (receiptKey.contains("NOTFOUND") || receiptKey.contains("NOT_EXIST"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "XDOCK_007",
                "message", "Receipt not found: " + receiptKey
            ));
        }

        // 409 - Conflict scenarios
        if (receiptKey != null && (receiptKey.contains("CONCURRENT") || receiptKey.contains("CONFLICT") ||
            receiptKey.contains("LOCKED"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "errorCode", "XDOCK_008",
                "message", "Concurrent allocation conflict for receipt: " + receiptKey
            ));
        }

        // 400 - Bad request
        if (qty <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "XDOCK_003",
                "message", "Quantity must be positive"
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("allocationKey", "ALLOC-" + System.currentTimeMillis());
        if (receiptKey != null) response.put("receiptKey", receiptKey);
        if (orderKey != null) response.put("orderKey", orderKey);
        response.put("sku", sku != null ? sku : "SKU-001");
        response.put("qty", qty > 0 ? qty : 100);
        response.put("status", "ALLOCATED");
        response.put("allocType", "XDOCK");
        response.put("allocatedAt", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/xdock/allocate-full")
    public ResponseEntity<Map<String, Object>> xdockAllocateFull(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cross-dock allocate full: {}", request);

        String receiptKey = (String) request.get("receiptKey");
        String orderKey = (String) request.get("orderKey");

        if (receiptKey != null && receiptKey.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "XDOCK_001",
                "message", "Receipt not eligible for cross-dock"
            ));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "receiptKey", receiptKey,
            "orderKey", orderKey,
            "totalQtyAllocated", 100,
            "allocations", List.of(
                Map.of("sku", "SKU-001", "qty", 50, "status", "ALLOCATED"),
                Map.of("sku", "SKU-002", "qty", 50, "status", "ALLOCATED")
            )
        ));
    }

    @PostMapping("/xdock/allocate-multi")
    public ResponseEntity<Map<String, Object>> xdockAllocateMulti(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cross-dock allocate multi: {}", request);

        String receiptKey = (String) request.get("receiptKey");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) request.get("allocations");

        if (receiptKey != null && receiptKey.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "XDOCK_001",
                "message", "Receipt not eligible for cross-dock"
            ));
        }

        List<Map<String, Object>> resultAllocations = new ArrayList<>();
        if (allocations != null) {
            for (Map<String, Object> alloc : allocations) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("orderKey", alloc.get("orderKey"));
                result.put("sku", alloc.get("sku"));
                result.put("qty", alloc.get("qty"));
                result.put("status", "ALLOCATED");
                result.put("allocationKey", "ALLOC-" + System.currentTimeMillis());
                resultAllocations.add(result);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "receiptKey", receiptKey,
            "allocations", resultAllocations,
            "totalAllocated", resultAllocations.size()
        ));
    }

    @PostMapping("/xdock/allocate-batch")
    public ResponseEntity<Map<String, Object>> xdockAllocateBatch(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Cross-dock allocate batch: {}", request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) request.get("allocations");
        int count = allocations != null ? allocations.size() : 0;

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "batchId", "BATCH-" + System.currentTimeMillis(),
            "totalProcessed", count,
            "successful", count,
            "failed", 0,
            "status", "COMPLETED"
        ));
    }

    // ==================== ASN Population ====================

    @PostMapping("/asn/populate")
    public ResponseEntity<Map<String, Object>> populateAsn(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Populate ASN: {}", request);

        String poKey = (String) request.get("poKey");
        String asnKey = (String) request.get("asnKey");

        if (poKey != null && poKey.contains("NOTFOUND")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "ASN_001",
                "message", "PO not found: " + poKey
            ));
        }
        if (poKey != null && poKey.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "ASN_002",
                "message", "PO not eligible for ASN population"
            ));
        }

        String receiptKey = "RCV-" + System.currentTimeMillis();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "poKey", poKey,
            "asnKey", asnKey != null ? asnKey : "ASN-" + System.currentTimeMillis(),
            "receiptKey", receiptKey,
            "status", "POPULATED",
            "linesPopulated", 3,
            "populatedAt", LocalDateTime.now().toString()
        ));
    }

    // ==================== Receipts (Create) ====================

    @PostMapping("/receipts")
    public ResponseEntity<Map<String, Object>> createReceipt(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Create receipt: {}", request);

        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "RCV_000",
                "message", "Request body is required"
            ));
        }

        String poKey = (String) request.get("poKey");
        String storerKey = (String) request.get("storerKey");
        String facility = (String) request.get("facility");
        String supplierKey = (String) request.get("supplierKey");

        // Validation - required fields
        if (storerKey == null || storerKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "RCV_001",
                "message", "Storer key is required"
            ));
        }

        // 404 - Not found
        if (poKey != null && (poKey.contains("NOTFOUND") || poKey.contains("NOT_EXIST"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "RCV_002",
                "message", "PO not found: " + poKey
            ));
        }

        // 422 - Unprocessable entity (various error patterns)
        if (poKey != null && (poKey.contains("-ERR-") || poKey.startsWith("ERR-") || poKey.endsWith("-ERR") ||
            poKey.contains("INVALID") || poKey.contains("CLOSED") || poKey.contains("CANCELLED"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_003",
                "message", "Cannot create receipt for PO: " + poKey
            ));
        }
        if (storerKey.contains("INVALID") || storerKey.contains("INACTIVE") || storerKey.contains("-ERR-")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_004",
                "message", "Invalid or inactive storer: " + storerKey
            ));
        }
        if (facility != null && (facility.contains("INVALID") || facility.contains("CLOSED"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_005",
                "message", "Invalid or closed facility: " + facility
            ));
        }
        if (supplierKey != null && (supplierKey.contains("INVALID") || supplierKey.contains("-ERR-"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "RCV_006",
                "message", "Invalid supplier: " + supplierKey
            ));
        }

        String receiptKey = "RCV-" + System.currentTimeMillis();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("receiptKey", receiptKey);
        response.put("poKey", poKey != null ? poKey : "PO-AUTO");
        response.put("storerKey", storerKey);
        response.put("facility", facility != null ? facility : "TEST01");
        response.put("status", "0");
        response.put("createdAt", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==================== Tasks ====================

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Create task: {}", request);

        String taskType = (String) request.get("taskType");
        String receiptKey = (String) request.get("receiptKey");
        String facility = (String) request.get("facility");

        if (taskType == null || taskType.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "errorCode", "TASK_001",
                "message", "Task type is required"
            ));
        }
        if (receiptKey != null && receiptKey.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "TASK_002",
                "message", "Cannot create task for receipt: " + receiptKey
            ));
        }

        String taskKey = "TASK-" + System.currentTimeMillis();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskKey", taskKey);
        response.put("taskType", taskType);
        if (receiptKey != null) {
            response.put("receiptKey", receiptKey);
        }
        response.put("facility", facility != null ? facility : "TEST01");
        response.put("status", "PENDING");
        response.put("createdAt", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tasks/{taskKey}")
    public ResponseEntity<Map<String, Object>> getTask(
            @PathVariable String taskKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get task: {}", taskKey);

        if (taskKey.contains("NOTFOUND")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TASK_003",
                "message", "Task not found: " + taskKey
            ));
        }

        return ResponseEntity.ok(Map.of(
            "taskKey", taskKey,
            "taskType", "PUTAWAY",
            "status", "PENDING",
            "facility", "TEST01"
        ));
    }

    @PostMapping("/tasks/{taskKey}/reassign")
    public ResponseEntity<Map<String, Object>> reassignTask(
            @PathVariable String taskKey,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Reassign task: {}", taskKey);

        if (taskKey.contains("NOTFOUND")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errorCode", "TASK_003",
                "message", "Task not found: " + taskKey
            ));
        }

        // Safe extraction of userId with default
        String newUserId = "USER-001";
        if (request != null && request.get("userId") != null) {
            newUserId = request.get("userId").toString();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskKey", taskKey);
        response.put("assignedTo", newUserId);
        response.put("status", "REASSIGNED");
        response.put("reassignedAt", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/interleaved-assignment")
    public ResponseEntity<Map<String, Object>> getInterleavedAssignment(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String facility,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Get interleaved assignment: userId={}, facility={}", userId, facility);

        return ResponseEntity.ok(Map.of(
            "taskKey", "TASK-INTERLEAVED-" + System.currentTimeMillis(),
            "taskType", "PUTAWAY",
            "userId", userId != null ? userId : "USER-001",
            "facility", facility != null ? facility : "TEST01",
            "status", "ASSIGNED"
        ));
    }

    // ==================== Putaway ====================

    @GetMapping("/putaway/suggest-location")
    public ResponseEntity<Map<String, Object>> suggestPutawayLocation(
            @RequestParam(required = false) Integer qty,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String facility,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[E2E Mock] Suggest putaway location: qty={}, sku={}, facility={}", qty, sku, facility);

        if (sku != null && sku.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "PUT_001",
                "message", "Cannot suggest location for SKU: " + sku
            ));
        }

        return ResponseEntity.ok(Map.of(
            "suggestedLocation", "A-01-01-01",
            "locationType", "STORAGE",
            "sku", sku != null ? sku : "SKU-001",
            "maxQty", 1000,
            "currentQty", 0,
            "available", true
        ));
    }

    // ==================== Saga ====================

    @PostMapping("/saga/po-to-inventory")
    public ResponseEntity<Map<String, Object>> sagaPoToInventory(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Test-Fail-At-Step", required = false) String failAtStep) {
        log.info("[E2E Mock] Saga PO to inventory: {}, failAtStep={}", request, failAtStep);

        String poKey = (String) request.get("poKey");

        // Simulate failure at step
        if (failAtStep != null && !failAtStep.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "SAGA_001",
                "message", "Saga failed at step: " + failAtStep,
                "compensated", true,
                "failedStep", failAtStep
            ));
        }

        if (poKey != null && poKey.contains("ERR")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "errorCode", "SAGA_002",
                "message", "Saga failed for PO: " + poKey
            ));
        }

        return ResponseEntity.ok(Map.of(
            "sagaId", "SAGA-" + System.currentTimeMillis(),
            "poKey", poKey,
            "status", "COMPLETED",
            "steps", List.of("VALIDATE", "CREATE_RECEIPT", "UPDATE_INVENTORY", "NOTIFY"),
            "completedAt", LocalDateTime.now().toString()
        ));
    }
}
