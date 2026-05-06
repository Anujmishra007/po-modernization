package com.wms.po.service;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.dto.POCreateRequest;
import com.wms.po.dto.POResponse;
import com.wms.po.variation.context.VariationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for POService
 */
@ExtendWith(MockitoExtension.class)
class POServiceTest {

    @Mock
    private POValidationService validationService;

    @Mock
    private POEnrichmentService enrichmentService;

    @Mock
    private POPersistenceService persistenceService;

    @Mock
    private VariationResolver variationResolver;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private POService poService;

    private POCreateRequest createRequest;
    private VariationContext context;

    @BeforeEach
    void setUp() {
        poService = new POService(validationService, enrichmentService, persistenceService, variationResolver, jdbcTemplate);

        createRequest = POCreateRequest.builder()
            .storerKey("STORER001")
            .facility("DC01")
            .supplierKey("SUPPLIER001")
            .build();

        context = VariationContext.builder()
            .region("US")
            .client("GENERIC")
            .version("V2")
            .build();
    }

    @Nested
    @DisplayName("Create PO Tests")
    class CreatePOTests {

        @Test
        @DisplayName("Should create PO successfully")
        void shouldCreatePOSuccessfully() {
            // Given
            when(variationResolver.resolve(anyString(), anyString())).thenReturn(context);
            when(enrichmentService.enrichRequest(any(), any())).thenReturn(
                PopulateRequest.builder().facility("DC01").storerKey("STORER001").build()
            );
            when(persistenceService.createPO(any(), any())).thenReturn("PO-NEW-001");

            POResponse mockResponse = POResponse.builder()
                .poKey("PO-NEW-001")
                .storerKey("STORER001")
                .facility("DC01")
                .status("0")
                .build();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(mockResponse));

            // When
            POResponse response = poService.createPO(createRequest, "testuser");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getPoKey()).isEqualTo("PO-NEW-001");
            verify(validationService).validateForCreate(any(), eq(context));
            verify(enrichmentService).enrichRequest(any(), eq(context));
            verify(persistenceService).createPO(any(), eq(context));
        }

        @Test
        @DisplayName("Should call validation before persistence")
        void shouldValidateBeforePersist() {
            // Given
            when(variationResolver.resolve(anyString(), anyString())).thenReturn(context);
            when(enrichmentService.enrichRequest(any(), any())).thenReturn(
                PopulateRequest.builder().facility("DC01").storerKey("STORER001").build()
            );
            when(persistenceService.createPO(any(), any())).thenReturn("PO-001");

            POResponse mockResponse = POResponse.builder()
                .poKey("PO-001")
                .build();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(mockResponse));

            // When
            poService.createPO(createRequest, "testuser");

            // Then - verify order
            var inOrder = inOrder(validationService, enrichmentService, persistenceService);
            inOrder.verify(validationService).validateForCreate(any(), any());
            inOrder.verify(enrichmentService).enrichRequest(any(), any());
            inOrder.verify(persistenceService).createPO(any(), any());
        }
    }

    @Nested
    @DisplayName("Get PO Tests")
    class GetPOTests {

        @Test
        @DisplayName("Should return PO when exists")
        void shouldReturnPOWhenExists() {
            // Given
            POResponse expected = POResponse.builder()
                .poKey("PO001")
                .storerKey("STORER001")
                .status("0")
                .build();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("PO001")))
                .thenReturn(List.of(expected));

            // When
            POResponse result = poService.getPO("PO001");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getPoKey()).isEqualTo("PO001");
        }

        @Test
        @DisplayName("Should throw when PO not found")
        void shouldThrowWhenNotFound() {
            // Given
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of());

            // When/Then
            assertThatThrownBy(() -> poService.getPO("NONEXISTENT"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PO not found");
        }
    }

    @Nested
    @DisplayName("Update PO Tests")
    class UpdatePOTests {

        @Test
        @DisplayName("Should update PO successfully")
        void shouldUpdatePOSuccessfully() {
            // Given
            when(variationResolver.resolve(anyString(), anyString())).thenReturn(context);
            when(enrichmentService.enrichRequest(any(), any())).thenReturn(
                PopulateRequest.builder().facility("DC01").storerKey("STORER001").build()
            );

            POResponse mockResponse = POResponse.builder()
                .poKey("PO001")
                .build();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(mockResponse));

            // When
            POResponse result = poService.updatePO("PO001", createRequest, "testuser");

            // Then
            assertThat(result).isNotNull();
            verify(validationService).validateForUpdate(eq("PO001"), any(), eq(context));
            verify(persistenceService).updatePO(eq("PO001"), any(), eq(context));
        }
    }

    @Nested
    @DisplayName("Delete PO Tests")
    class DeletePOTests {

        @Test
        @DisplayName("Should delete PO successfully")
        void shouldDeletePOSuccessfully() {
            // Given
            POResponse po = POResponse.builder()
                .poKey("PO001")
                .facility("DC01")
                .storerKey("STORER001")
                .build();

            // Mock getPO query
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("PO001")))
                .thenReturn(List.of(po));
            when(variationResolver.resolve("DC01", "STORER001")).thenReturn(context);

            // Mock delete operations to return rows affected
            when(jdbcTemplate.update(contains("DELETE FROM ORDERDETAIL"), eq("PO001")))
                .thenReturn(3); // 3 details deleted
            when(jdbcTemplate.update(contains("DELETE FROM ORDERS"), eq("PO001")))
                .thenReturn(1); // 1 header deleted

            // When
            poService.deletePO("PO001");

            // Then
            verify(validationService).validateForDelete("PO001", context);
            verify(jdbcTemplate).update(contains("DELETE FROM ORDERDETAIL"), eq("PO001"));
            verify(jdbcTemplate).update(contains("DELETE FROM ORDERS"), eq("PO001"));
        }
    }

    @Nested
    @DisplayName("Get POs By Storer and Facility Tests")
    class GetPOsByStorerAndFacilityTests {

        @Test
        @DisplayName("Should return POs for storer and facility")
        void shouldReturnPOsForStorerAndFacility() {
            // Given
            List<POResponse> expected = List.of(
                POResponse.builder().poKey("PO001").build(),
                POResponse.builder().poKey("PO002").build()
            );
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("STORER001"), eq("DC01")))
                .thenReturn(expected);

            // When
            List<POResponse> results = poService.getPOsByStorerAndFacility("STORER001", "DC01");

            // Then
            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Update PO Status Tests")
    class UpdatePOStatusTests {

        @Test
        @DisplayName("Should update PO status successfully")
        void shouldUpdatePOStatusSuccessfully() {
            // Given - mock the update to return 1 row affected
            when(jdbcTemplate.update(contains("UPDATE ORDERS SET STATUS"), eq("5"), eq("testuser"), eq("PO001")))
                .thenReturn(1);

            // When
            poService.updatePOStatus("PO001", "5", "testuser");

            // Then
            verify(jdbcTemplate).update(contains("UPDATE ORDERS SET STATUS"), eq("5"), eq("testuser"), eq("PO001"));
        }
    }
}
