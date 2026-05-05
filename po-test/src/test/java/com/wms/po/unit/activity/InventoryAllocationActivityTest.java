package com.wms.po.unit.activity;

import com.wms.po.activity.impl.InventoryAllocationActivityImpl;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryAllocationActivity
 */
@ExtendWith(MockitoExtension.class)
class InventoryAllocationActivityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private InventoryAllocationActivityImpl inventoryAllocationActivity;

    private PopulateRequest request;
    private VariationContext context;

    @BeforeEach
    void setUp() {
        request = PopulateRequest.builder()
            .storerKey("STORER001")
            .facility("DC01")
            .poKeys(List.of("PO001"))
            .userId("testuser")
            .build();

        context = VariationContext.builder()
            .region("US")
            .client("GENERIC")
            .version("V2")
            .build();
    }

    @Nested
    @DisplayName("Allocate Inventory Tests")
    class AllocateInventoryTests {

        @Test
        @DisplayName("Should allocate inventory for receipt")
        void shouldAllocateInventoryForReceipt() {
            // Given
            String receiptKey = "RCV001";
            List<Map<String, Object>> mockDetails = List.of(
                Map.of("SKU", "SKU001", "LOT", "LOT001", "LOC", "LOC001",
                       "ID", "ID001", "QTYEXPECTED", BigDecimal.TEN)
            );
            when(jdbcTemplate.queryForList(anyString(), eq(receiptKey))).thenReturn(mockDetails);

            // When
            inventoryAllocationActivity.allocateInventory(receiptKey, request, context);

            // Then - verify queryForList called first, then update for allocation
            verify(jdbcTemplate).queryForList(anyString(), eq(receiptKey));
            verify(jdbcTemplate).update(contains("INSERT INTO LOTxLOCxID"),
                any(), eq("SKU001"), eq("LOT001"), eq("LOC001"), eq("ID001"),
                eq(BigDecimal.TEN), eq(receiptKey), eq("testuser"), eq("testuser"));
        }

        @Test
        @DisplayName("Should handle empty receipt details")
        void shouldHandleEmptyReceiptDetails() {
            // Given
            String receiptKey = "RCV001";
            when(jdbcTemplate.queryForList(anyString(), eq(receiptKey))).thenReturn(List.of());

            // When
            inventoryAllocationActivity.allocateInventory(receiptKey, request, context);

            // Then - should query but not insert
            verify(jdbcTemplate).queryForList(anyString(), eq(receiptKey));
            verify(jdbcTemplate, never()).update(contains("INSERT"),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Compensation Tests")
    class CompensationTests {

        @Test
        @DisplayName("Should deallocate inventory on compensation")
        void shouldDeallocateInventoryOnCompensation() {
            // Given
            String receiptKey = "RCV001";

            // When
            inventoryAllocationActivity.compensateAllocateInventory(receiptKey, context);

            // Then
            verify(jdbcTemplate).update(contains("DELETE"), eq(receiptKey));
        }
    }
}
