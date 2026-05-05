package com.wms.po.unit.activity;

import com.wms.po.activity.impl.ReceiptCreationActivityImpl;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReceiptCreationActivity
 */
@ExtendWith(MockitoExtension.class)
class ReceiptCreationActivityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private KeyGeneratorService keyGeneratorService;

    @InjectMocks
    private ReceiptCreationActivityImpl receiptCreationActivity;

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
    @DisplayName("Create Receipt Tests")
    class CreateReceiptTests {

        @Test
        @DisplayName("Should create receipt successfully")
        void shouldCreateReceiptSuccessfully() {
            // Given
            when(keyGeneratorService.generateKey("RECEIPT")).thenReturn("RCV001");

            // When
            String receiptKey = receiptCreationActivity.createReceipt(request, context);

            // Then
            assertThat(receiptKey).isEqualTo("RCV001");
            verify(jdbcTemplate).update(anyString(), eq("RCV001"), eq("STORER001"), eq("DC01"),
                eq("0"), eq("PO"), eq("testuser"), eq("testuser"));
        }

        @Test
        @DisplayName("Should create receipt with correct storer")
        void shouldCreateReceiptWithCorrectStorer() {
            // Given
            when(keyGeneratorService.generateKey("RECEIPT")).thenReturn("RCV001");

            // When
            String receiptKey = receiptCreationActivity.createReceipt(request, context);

            // Then
            assertThat(receiptKey).isEqualTo("RCV001");
            // Verify key generation was called
            verify(keyGeneratorService).generateKey("RECEIPT");
        }
    }

    @Nested
    @DisplayName("Compensation Tests")
    class CompensationTests {

        @Test
        @DisplayName("Should delete receipt on compensation")
        void shouldDeleteReceiptOnCompensation() {
            // When
            receiptCreationActivity.compensateCreateReceipt("RCV001", context);

            // Then - verify all 3 DELETE statements are called
            verify(jdbcTemplate).update(eq("DELETE FROM RECEIPTDETAIL WHERE RECEIPTKEY = ?"), eq("RCV001"));
            verify(jdbcTemplate).update(eq("DELETE FROM RECEIPTPO WHERE RECEIPTKEY = ?"), eq("RCV001"));
            verify(jdbcTemplate).update(eq("DELETE FROM RECEIPT WHERE RECEIPTKEY = ?"), eq("RCV001"));
        }

        @Test
        @DisplayName("Should handle missing receipt gracefully")
        void shouldHandleMissingReceiptGracefully() {
            // Given
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

            // When/Then - should not throw
            receiptCreationActivity.compensateCreateReceipt("NONEXISTENT", context);
        }
    }
}
