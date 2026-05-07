package com.wms.po.unit.activity;

import com.wms.po.activity.impl.ValidationActivityImpl;
import com.wms.po.domain.entity.POEntity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.repository.PORepository;
import com.wms.po.variation.context.VariationResolver;
import com.wms.po.variation.rule.RuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationActivityTest {

    @Mock
    private VariationResolver variationResolver;

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private PORepository poRepository;

    @InjectMocks
    private ValidationActivityImpl validationActivity;

    private PopulateRequest validRequest;
    private VariationContext context;

    @BeforeEach
    void setUp() {
        validRequest = PopulateRequest.builder()
            .poKeys(List.of("PO-001"))
            .storerKey("TEST_STORER")
            .facility("KR01")
            .userId("test_user")
            .build();

        context = VariationContext.builder()
            .version("V2")
            .region("ASIA-KR")
            .client("STANDARD")
            .facility("KR01")
            .storerKey("TEST_STORER")
            .dualWriteEnabled(false)
            .build();
    }

    @Nested
    @DisplayName("resolveContext tests")
    class ResolveContextTests {

        @Test
        @DisplayName("resolves context from storer and facility")
        void resolvesContextCorrectly() {
            when(variationResolver.resolve("TEST_STORER", "KR01")).thenReturn(context);

            VariationContext result = validationActivity.resolveContext(validRequest);

            assertThat(result).isEqualTo(context);
            assertThat(result.getRegion()).isEqualTo("ASIA-KR");
        }
    }

    @Nested
    @DisplayName("validate tests")
    class ValidateTests {

        @Test
        @DisplayName("valid request passes validation")
        void validRequestPasses() {
            POEntity validPO = POEntity.builder()
                .poKey("PO-001")
                .storerKey("TEST_STORER")
                .status("0")
                .build();

            when(poRepository.findByPoKeyIn(List.of("PO-001"))).thenReturn(List.of(validPO));
            when(ruleEngine.validate(any(), any())).thenReturn(ValidationResult.success());

            ValidationResult result = validationActivity.validate(validRequest, context);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("missing PO key fails validation with BusinessException")
        void missingPoKeyFails() {
            PopulateRequest emptyRequest = PopulateRequest.builder()
                .poKeys(List.of())
                .storerKey("TEST_STORER")
                .facility("KR01")
                .build();

            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> validationActivity.validate(emptyRequest, context)
            );

            assertThat(exception.getMessage()).contains("At least one PO key is required");
        }

        @Test
        @DisplayName("missing storer key fails validation with BusinessException")
        void missingStorerKeyFails() {
            PopulateRequest noStorerRequest = PopulateRequest.builder()
                .poKeys(List.of("PO-001"))
                .facility("KR01")
                .build();

            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> validationActivity.validate(noStorerRequest, context)
            );

            assertThat(exception.getMessage()).contains("Storer key is required");
        }

        @Test
        @DisplayName("closed PO fails validation with BusinessException")
        void closedPoFails() {
            POEntity closedPO = POEntity.builder()
                .poKey("PO-001")
                .storerKey("TEST_STORER")
                .status("9") // Closed
                .build();

            when(poRepository.findByPoKeyIn(List.of("PO-001"))).thenReturn(List.of(closedPO));

            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> validationActivity.validate(validRequest, context)
            );

            assertThat(exception.getMessage()).contains("closed");
        }

        @Test
        @DisplayName("cancelled PO fails validation with BusinessException")
        void cancelledPoFails() {
            POEntity cancelledPO = POEntity.builder()
                .poKey("PO-001")
                .storerKey("TEST_STORER")
                .status("8") // Cancelled
                .build();

            when(poRepository.findByPoKeyIn(List.of("PO-001"))).thenReturn(List.of(cancelledPO));

            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> validationActivity.validate(validRequest, context)
            );

            assertThat(exception.getMessage()).contains("cancelled");
        }

        @Test
        @DisplayName("non-existent PO fails validation with BusinessException")
        void nonExistentPoFails() {
            when(poRepository.findByPoKeyIn(List.of("PO-001"))).thenReturn(List.of());

            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> validationActivity.validate(validRequest, context)
            );

            assertThat(exception.getMessage()).contains("not found");
        }

        @Test
        @DisplayName("PO with wrong storer fails validation with BusinessException")
        void wrongStorerFails() {
            POEntity wrongStorerPO = POEntity.builder()
                .poKey("PO-001")
                .storerKey("DIFFERENT_STORER")
                .status("0")
                .build();

            when(poRepository.findByPoKeyIn(List.of("PO-001"))).thenReturn(List.of(wrongStorerPO));

            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> validationActivity.validate(validRequest, context)
            );

            assertThat(exception.getMessage()).containsIgnoringCase("storer");
        }
    }
}
