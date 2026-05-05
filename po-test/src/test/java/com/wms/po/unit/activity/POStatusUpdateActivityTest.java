package com.wms.po.unit.activity;

import com.wms.po.activity.impl.POStatusUpdateActivityImpl;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for POStatusUpdateActivity
 */
@ExtendWith(MockitoExtension.class)
class POStatusUpdateActivityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private POStatusUpdateActivityImpl poStatusUpdateActivity;

    private PopulateRequest request;
    private VariationContext context;

    @BeforeEach
    void setUp() {
        request = PopulateRequest.builder()
            .storerKey("STORER001")
            .facility("DC01")
            .poKeys(List.of("PO001", "PO002"))
            .userId("testuser")
            .build();

        context = VariationContext.builder()
            .region("US")
            .client("GENERIC")
            .version("V2")
            .build();
    }

    @Nested
    @DisplayName("Update Status Tests")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update PO status for all POs")
        void shouldUpdatePOStatusForAllPOs() {
            // Given
            String receiptKey = "RCV001";
            String targetStatus = "5"; // Populated status
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            // When
            poStatusUpdateActivity.updatePOStatus(receiptKey, request, context, targetStatus);

            // Then
            verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("Should use correct status value")
        void shouldUseCorrectStatusValue() {
            // Given
            String receiptKey = "RCV001";
            String targetStatus = "9"; // Completed status
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            // When
            poStatusUpdateActivity.updatePOStatus(receiptKey, request, context, targetStatus);

            // Then
            verify(jdbcTemplate, atLeastOnce()).update(contains("STATUS"), any(Object[].class));
        }
    }

    @Nested
    @DisplayName("Compensation Tests")
    class CompensationTests {

        @Test
        @DisplayName("Should revert PO status on compensation")
        void shouldRevertPOStatusOnCompensation() {
            // Given
            String receiptKey = "RCV001";
            String previousStatus = "0"; // Original status

            // When
            poStatusUpdateActivity.compensateUpdatePOStatus(receiptKey, request, context, previousStatus);

            // Then
            verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("Should handle missing PO gracefully")
        void shouldHandleMissingPOGracefully() {
            // Given
            String receiptKey = "RCV001";
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

            // When/Then - should not throw
            poStatusUpdateActivity.compensateUpdatePOStatus(receiptKey, request, context, "0");
        }
    }
}
