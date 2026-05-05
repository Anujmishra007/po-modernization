package com.wms.po.integration;

import com.wms.po.POModernizationApplication;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.FinalizeResult;
import com.wms.po.domain.model.WorkflowStatus;
import com.wms.po.domain.repository.ReceiptRepository;
import com.wms.po.domain.repository.ReceiptDetailRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Receipt Finalization flow.
 *
 * Tests the complete finalization workflow including:
 * - Status validation and transitions
 * - Inventory posting
 * - Hold application
 * - PO quantity updates
 * - Putaway task release
 * - Plugin execution
 *
 * Disabled: Requires Temporal server and full application context.
 */
@Disabled("Integration tests require Temporal server and full application context")
@SpringBootTest(
    classes = POModernizationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinalizeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptDetailRepository receiptDetailRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl;

    private static final String TEST_RECEIPT_KEY = "INT-TEST-RCP-001";
    private static final String TEST_STORER_KEY = "TEST_STORER";
    private static final String TEST_FACILITY = "KR01";
    private static final String TEST_USER = "test_user";

    @BeforeAll
    void setUpAll() {
        baseUrl = "http://localhost:" + port + "/api/v1/receipts";
    }

    @BeforeEach
    void setUp() {
        // Clean up test data
        cleanupTestData();
        // Create test receipt with details
        createTestReceipt();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Happy Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Finalize receipt creates inventory records")
    void finalizeReceiptCreatesInventory() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(false)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getWorkflowStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(response.getBody().getInventoryRecordsCreated()).isGreaterThan(0);

        // Verify receipt status updated
        verifyReceiptStatus(TEST_RECEIPT_KEY, "9");
    }

    @Test
    @DisplayName("Finalize with auto-close closes receipt")
    void finalizeWithAutoCloseClosesReceipt() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .autoClose(true)
            .releasePutaway(false)
            .applyHolds(false)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();

        // Verify receipt is closed (status C or closedate set)
        verifyReceiptClosed(TEST_RECEIPT_KEY);
    }

    @Test
    @DisplayName("Finalize with holds applies inventory holds")
    void finalizeWithHoldsAppliesHolds() {
        // Arrange - Set up storer to require QC hold
        setupQCRequirement(TEST_STORER_KEY);

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(true)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getHoldsApplied()).isNotEmpty();

        // Verify holds created
        verifyHoldsExist(TEST_RECEIPT_KEY);
    }

    @Test
    @DisplayName("Finalize with putaway releases putaway tasks")
    void finalizeWithPutawayReleasesTasks() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .autoClose(false)
            .releasePutaway(true)
            .applyHolds(false)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getPutawayTasksReleased()).isGreaterThanOrEqualTo(0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Finalize non-existent receipt returns error")
    void finalizeNonExistentReceiptFails() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey("NON_EXISTENT_RECEIPT")
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/NON_EXISTENT_RECEIPT/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Finalize already finalized receipt fails")
    void finalizeAlreadyFinalizedReceiptFails() {
        // Arrange - First finalization
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .autoClose(false)
            .build();

        restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Act - Second finalization
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Async Finalization Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Async finalization returns workflow ID immediately")
    void asyncFinalizationReturnsWorkflowId() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .async(true)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/finalize/async",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getWorkflowId()).isNotNull();
        assertThat(response.getBody().getWorkflowStatus()).isIn(
            WorkflowStatus.STARTED,
            WorkflowStatus.RUNNING
        );
    }

    @Test
    @DisplayName("Get workflow status returns current state")
    void getWorkflowStatusReturnsCurrentState() {
        // Arrange - Start async finalization
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .async(true)
            .build();

        ResponseEntity<FinalizeResult> startResponse = restTemplate.postForEntity(
            baseUrl + "/finalize/async",
            request,
            FinalizeResult.class
        );

        String workflowId = startResponse.getBody().getWorkflowId();

        // Act
        ResponseEntity<String> statusResponse = restTemplate.getForEntity(
            baseUrl + "/status?workflowId=" + workflowId,
            String.class
        );

        // Assert
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PO Quantity Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Finalization updates PO received quantities")
    void finalizationUpdatesPOQuantities() {
        // Arrange
        createTestPOWithLines();

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(TEST_RECEIPT_KEY)
            .storerKey(TEST_STORER_KEY)
            .facility(TEST_FACILITY)
            .userId(TEST_USER)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(false)
            .build();

        // Act
        ResponseEntity<FinalizeResult> response = restTemplate.postForEntity(
            baseUrl + "/" + TEST_RECEIPT_KEY + "/finalize",
            request,
            FinalizeResult.class
        );

        // Assert
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getUpdatedPOLines()).isNotEmpty();

        // Verify PO quantities updated
        verifyPOQuantitiesUpdated();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private void cleanupTestData() {
        try {
            jdbcTemplate.update("DELETE FROM dbo.inventoryhold WHERE storerkey = ?", TEST_STORER_KEY);
            jdbcTemplate.update("DELETE FROM dbo.lotxlocxid WHERE storerkey = ?", TEST_STORER_KEY);
            jdbcTemplate.update("DELETE FROM dbo.receiptdetail WHERE receiptkey = ?", TEST_RECEIPT_KEY);
            jdbcTemplate.update("DELETE FROM dbo.receipt WHERE receiptkey = ?", TEST_RECEIPT_KEY);
            jdbcTemplate.update("DELETE FROM dbo.podetail WHERE storerkey = ?", TEST_STORER_KEY);
            jdbcTemplate.update("DELETE FROM dbo.po WHERE storerkey = ?", TEST_STORER_KEY);
            jdbcTemplate.update("DELETE FROM dbo.codelkup WHERE listname = 'QCREQUIRED' AND code = ?", TEST_STORER_KEY);
        } catch (Exception e) {
            // Tables may not exist in test environment
        }
    }

    private void createTestReceipt() {
        jdbcTemplate.update(
            """
            INSERT INTO dbo.receipt (receiptkey, storerkey, facility, status, adddate, addwho)
            VALUES (?, ?, ?, '5', CURRENT_TIMESTAMP, ?)
            """,
            TEST_RECEIPT_KEY, TEST_STORER_KEY, TEST_FACILITY, TEST_USER
        );

        // Create receipt details
        for (int i = 1; i <= 3; i++) {
            jdbcTemplate.update(
                """
                INSERT INTO dbo.receiptdetail (
                    receiptdetailkey, receiptkey, receiptlinenumber,
                    storerkey, sku, qtyexpected, qtyreceived, status, adddate, addwho
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, '5', CURRENT_TIMESTAMP, ?)
                """,
                TEST_RECEIPT_KEY + "-" + i,
                TEST_RECEIPT_KEY,
                i,
                TEST_STORER_KEY,
                "SKU00" + i,
                new BigDecimal("100"),
                new BigDecimal("100"),
                TEST_USER
            );
        }
    }

    private void createTestPOWithLines() {
        String poKey = "INT-TEST-PO-001";

        jdbcTemplate.update(
            """
            INSERT INTO dbo.po (pokey, storerkey, facility, status, adddate, addwho)
            VALUES (?, ?, ?, '1', CURRENT_TIMESTAMP, ?)
            """,
            poKey, TEST_STORER_KEY, TEST_FACILITY, TEST_USER
        );

        for (int i = 1; i <= 3; i++) {
            jdbcTemplate.update(
                """
                INSERT INTO dbo.podetail (
                    pokey, polinenumber, storerkey, sku,
                    qtyordered, qtyreceived, adddate, addwho
                )
                VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, ?)
                """,
                poKey, i, TEST_STORER_KEY, "SKU00" + i,
                new BigDecimal("100"), TEST_USER
            );
        }

        // Link receipt to PO
        jdbcTemplate.update(
            """
            UPDATE dbo.receiptdetail
            SET pokey = ?, polinenumber = receiptlinenumber
            WHERE receiptkey = ?
            """,
            poKey, TEST_RECEIPT_KEY
        );
    }

    private void setupQCRequirement(String storerKey) {
        jdbcTemplate.update(
            """
            INSERT INTO dbo.codelkup (listname, code, value1, status, adddate, addwho)
            VALUES ('QCREQUIRED', ?, '1', '1', CURRENT_TIMESTAMP, ?)
            """,
            storerKey, TEST_USER
        );
    }

    private void verifyReceiptStatus(String receiptKey, String expectedStatus) {
        String actualStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM dbo.receipt WHERE receiptkey = ?",
            String.class,
            receiptKey
        );
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }

    private void verifyReceiptClosed(String receiptKey) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.receipt
            WHERE receiptkey = ?
            AND (status = 'C' OR closedate IS NOT NULL)
            """,
            Integer.class,
            receiptKey
        );
        assertThat(count).isGreaterThan(0);
    }

    private void verifyHoldsExist(String receiptKey) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.inventoryhold h
            WHERE h.storerkey = ?
            AND h.status = '1'
            """,
            Integer.class,
            TEST_STORER_KEY
        );
        assertThat(count).isGreaterThan(0);
    }

    private void verifyPOQuantitiesUpdated() {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.podetail
            WHERE storerkey = ?
            AND qtyreceived > 0
            """,
            Integer.class,
            TEST_STORER_KEY
        );
        assertThat(count).isGreaterThan(0);
    }
}
