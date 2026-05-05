package com.wms.po.rules;

import com.wms.po.rules.config.RulesConfig;
import com.wms.po.rules.model.POValidationFact;
import com.wms.po.rules.model.SKUEntity;
import com.wms.po.rules.service.RulesExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Drools rules execution
 */
class RulesExecutorTest {

    private RulesExecutor rulesExecutor;
    private static KieContainer kieContainer;

    @BeforeEach
    void setUp() throws Exception {
        // Disable MVEL jitting for faster test execution
        System.setProperty("drools.jittingThreshold", "-1");
        System.setProperty("drools.dialect.mvel.strict", "false");

        if (kieContainer == null) {
            RulesConfig config = new RulesConfig();
            KieServices kieServices = config.kieServices();
            kieContainer = config.kieContainer(kieServices);
        }
        rulesExecutor = new RulesExecutor(kieContainer);
    }

    @Nested
    @DisplayName("PO Validation Rules")
    class POValidationTests {

        @Test
        @DisplayName("Should fail validation when storer key is missing")
        void shouldFailWhenStorerKeyMissing() {
            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey(null)
                .facility("DC01")
                .region("US")
                .lines(new ArrayList<>())
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Storer key is required");
        }

        @Test
        @DisplayName("Should fail validation when facility is missing")
        void shouldFailWhenFacilityMissing() {
            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("")
                .region("US")
                .lines(new ArrayList<>())
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Facility is required");
        }

        @Test
        @DisplayName("Should fail when PO date is in future")
        void shouldFailWhenPODateInFuture() {
            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("DC01")
                .poDate(LocalDate.now().plusDays(10))
                .region("US")
                .lines(List.of(createValidLine()))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("PO date cannot be in the future");
        }

        @Test
        @DisplayName("Should fail when expected receipt before PO date")
        void shouldFailWhenExpectedReceiptBeforePODate() {
            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("DC01")
                .poDate(LocalDate.now())
                .expectedReceiptDate(LocalDate.now().minusDays(5))
                .region("US")
                .lines(List.of(createValidLine()))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Expected receipt date must be after PO date");
        }

        @Test
        @DisplayName("Should pass validation for valid PO")
        void shouldPassForValidPO() {
            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("DC01")
                .poDate(LocalDate.now().minusDays(1))
                .expectedReceiptDate(LocalDate.now().plusDays(5))
                .region("US")
                .lines(List.of(createValidLine()))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        private POValidationFact.POLineValidationFact createValidLine() {
            return POValidationFact.POLineValidationFact.builder()
                .poLineKey("LINE001")
                .sku("SKU001")
                .qtyOrdered(BigDecimal.TEN)
                .build();
        }
    }

    @Nested
    @DisplayName("Line Validation Rules")
    class LineValidationTests {

        @Test
        @DisplayName("Should fail when line has no SKU")
        void shouldFailWhenLineHasNoSKU() {
            POValidationFact.POLineValidationFact line = POValidationFact.POLineValidationFact.builder()
                .poLineKey("LINE001")
                .sku(null)
                .qtyOrdered(BigDecimal.TEN)
                .build();

            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("DC01")
                .region("US")
                .lines(List.of(line))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should fail when line quantity is zero")
        void shouldFailWhenLineQuantityZero() {
            POValidationFact.POLineValidationFact line = POValidationFact.POLineValidationFact.builder()
                .poLineKey("LINE001")
                .sku("SKU001")
                .qtyOrdered(BigDecimal.ZERO)
                .build();

            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("DC01")
                .region("US")
                .lines(List.of(line))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Region-Specific Rules")
    class RegionRulesTests {

        @Test
        @DisplayName("Korea region requires customs reference")
        void koreaRequiresCustomsRef() {
            POValidationFact.POLineValidationFact line = POValidationFact.POLineValidationFact.builder()
                .poLineKey("LINE001")
                .sku("SKU001")
                .qtyOrdered(BigDecimal.TEN)
                .lottables(Map.of()) // Missing CUSTOMS_REF
                .build();

            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("KR01")
                .region("ASIA-KR")
                .lines(List.of(line))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("India region requires GST invoice")
        void indiaRequiresGSTInvoice() {
            POValidationFact.POLineValidationFact line = POValidationFact.POLineValidationFact.builder()
                .poLineKey("LINE001")
                .sku("SKU001")
                .qtyOrdered(BigDecimal.TEN)
                .lottables(Map.of()) // Missing GST_INVOICE
                .build();

            POValidationFact fact = POValidationFact.builder()
                .poKey("PO001")
                .storerKey("STORER1")
                .facility("IN01")
                .region("ASIA-IN")
                .lines(List.of(line))
                .build();

            POValidationFact result = rulesExecutor.validatePO(fact);

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("SKU Validation Rules")
    class SKUValidationTests {

        @Test
        @DisplayName("Should fail when weight is negative")
        void shouldFailWhenWeightNegative() {
            SKUEntity sku = SKUEntity.builder()
                .sku("SKU001")
                .storerKey("STORER1")
                .weight(new BigDecimal("-1.5"))
                .build();

            SKUEntity result = rulesExecutor.validateSKU(sku);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getValidationError()).contains("Weight cannot be negative");
        }

        @Test
        @DisplayName("Should calculate cube from dimensions")
        void shouldCalculateCube() {
            SKUEntity sku = SKUEntity.builder()
                .sku("SKU001")
                .storerKey("STORER1")
                .length(new BigDecimal("10"))
                .width(new BigDecimal("5"))
                .height(new BigDecimal("2"))
                .build();

            SKUEntity result = rulesExecutor.validateSKU(sku);

            assertThat(result.getCube()).isEqualByComparingTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("Should set hazmat storage type")
        void shouldSetHazmatStorageType() {
            SKUEntity sku = SKUEntity.builder()
                .sku("SKU001")
                .storerKey("STORER1")
                .hazmatCode("HAZMAT-CLASS3")
                .build();

            SKUEntity result = rulesExecutor.validateSKU(sku);

            assertThat(result.getComputedStorageType()).isEqualTo("HAZMAT");
        }

        @Test
        @DisplayName("Should set standard storage type by default")
        void shouldSetStandardStorageByDefault() {
            SKUEntity sku = SKUEntity.builder()
                .sku("SKU001")
                .storerKey("STORER1")
                .build();

            SKUEntity result = rulesExecutor.validateSKU(sku);

            assertThat(result.getComputedStorageType()).isEqualTo("STANDARD");
        }
    }
}
