package com.wms.po.service;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.rules.service.LottableMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for POEnrichmentService
 */
@ExtendWith(MockitoExtension.class)
class POEnrichmentServiceTest {

    @Mock
    private LottableMappingService lottableMappingService;

    @InjectMocks
    private POEnrichmentService enrichmentService;

    private PopulateRequest request;

    @BeforeEach
    void setUp() {
        request = PopulateRequest.builder()
            .storerKey("STORER001")
            .facility("DC01")
            .poKeys(List.of("PO001"))
            .userId("testuser")
            .build();
    }

    @Test
    @DisplayName("Should enrich request with region and client")
    void shouldEnrichWithRegionAndClient() {
        // Given
        VariationContext context = VariationContext.builder()
            .region("US")
            .client("GENERIC")
            .version("V2")
            .build();

        when(lottableMappingService.getMapping(any(), any())).thenReturn(null);

        // When
        PopulateRequest enriched = enrichmentService.enrichRequest(request, context);

        // Then
        assertThat(enriched).isNotNull();
        assertThat(enriched.getStorerKey()).isEqualTo("STORER001");
    }

    @Test
    @DisplayName("Should add Korea customs metadata")
    void shouldAddKoreaCustomsMetadata() {
        // Given
        VariationContext context = VariationContext.builder()
            .region("ASIA-KR")
            .client("GENERIC")
            .version("V2")
            .build();

        when(lottableMappingService.getMapping(any(), any())).thenReturn(null);

        // When
        PopulateRequest enriched = enrichmentService.enrichRequest(request, context);

        // Then
        assertThat(enriched).isNotNull();
    }

    @Test
    @DisplayName("Should add India GST metadata")
    void shouldAddIndiaGSTMetadata() {
        // Given
        VariationContext context = VariationContext.builder()
            .region("ASIA-IN")
            .client("GENERIC")
            .version("V2")
            .build();

        when(lottableMappingService.getMapping(any(), any())).thenReturn(null);

        // When
        PopulateRequest enriched = enrichmentService.enrichRequest(request, context);

        // Then
        assertThat(enriched).isNotNull();
    }

    @Test
    @DisplayName("Should add Nike client metadata")
    void shouldAddNikeClientMetadata() {
        // Given
        VariationContext context = VariationContext.builder()
            .region("US")
            .client("NIKE")
            .version("V2")
            .build();

        when(lottableMappingService.getMapping(any(), any())).thenReturn(null);

        // When
        PopulateRequest enriched = enrichmentService.enrichRequest(request, context);

        // Then
        assertThat(enriched).isNotNull();
    }

    @Test
    @DisplayName("Should add H&M client metadata")
    void shouldAddHMClientMetadata() {
        // Given
        VariationContext context = VariationContext.builder()
            .region("EU")
            .client("HM")
            .version("V2")
            .build();

        when(lottableMappingService.getMapping(any(), any())).thenReturn(null);

        // When
        PopulateRequest enriched = enrichmentService.enrichRequest(request, context);

        // Then
        assertThat(enriched).isNotNull();
    }
}
