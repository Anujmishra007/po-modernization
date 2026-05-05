package com.wms.po.integration;

import com.wms.po.POModernizationApplication;
import com.wms.po.domain.entity.POEntity;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.PopulateResult;
import com.wms.po.domain.repository.PORepository;
import com.wms.po.domain.repository.ReceiptRepository;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests with Spring Boot and embedded H2.
 * Disabled: Requires full application context with Temporal configuration.
 */
@Disabled("Integration tests require Temporal server and full application context")
@SpringBootTest(
    classes = POModernizationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopulateIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PORepository poRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    private String baseUrl;

    @BeforeAll
    void setUpAll() {
        baseUrl = "http://localhost:" + port + "/api/v1";
    }

    @BeforeEach
    void setUp() {
        // Clean up test data
        receiptRepository.deleteAll();
    }

    @Test
    @DisplayName("Health endpoint returns UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            baseUrl + "/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    @DisplayName("Populate PO creates receipt")
    @Disabled("Requires Temporal to be running")
    void populatePoCreatesReceipt() {
        // Create test PO
        POEntity po = POEntity.builder()
            .poKey("INT-TEST-PO-001")
            .storerKey("TEST_STORER")
            .facility("KR01")
            .status("0")
            .build();
        poRepository.save(po);

        // Populate
        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-TEST-PO-001"))
            .storerKey("TEST_STORER")
            .facility("KR01")
            .userId("test_user")
            .build();

        ResponseEntity<PopulateResult> response = restTemplate.postForEntity(
            baseUrl + "/po/populate", request, PopulateResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getReceiptKey()).isNotNull();

        // Verify receipt created
        assertThat(receiptRepository.findByReceiptKey(response.getBody().getReceiptKey()))
            .isPresent();
    }

    @Test
    @DisplayName("Populate non-existent PO fails")
    @Disabled("Requires Temporal to be running")
    void populateNonExistentPoFails() {
        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("NON_EXISTENT_PO"))
            .storerKey("TEST_STORER")
            .facility("KR01")
            .userId("test_user")
            .build();

        ResponseEntity<PopulateResult> response = restTemplate.postForEntity(
            baseUrl + "/po/populate", request, PopulateResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }
}
