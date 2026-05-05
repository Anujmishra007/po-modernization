package com.wms.po.rules;

import com.wms.po.rules.model.LottableMapping;
import com.wms.po.rules.service.LottableMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LottableMappingService
 */
class LottableMappingServiceTest {

    private LottableMappingService service;

    @BeforeEach
    void setUp() {
        service = new LottableMappingService();
    }

    @Test
    @DisplayName("Should return default mapping for unknown region/client")
    void shouldReturnDefaultMapping() {
        LottableMapping mapping = service.getMapping("UNKNOWN", "UNKNOWN");

        assertThat(mapping).isNotNull();
        assertThat(mapping.getLottable01Field()).isEqualTo("LOT_NUMBER");
        assertThat(mapping.getLottable04Field()).isEqualTo("EXPIRY_DATE");
    }

    @Test
    @DisplayName("Should return Korea mapping for ASIA-KR region")
    void shouldReturnKoreaMapping() {
        LottableMapping mapping = service.getMapping("ASIA-KR", "GENERIC");

        assertThat(mapping).isNotNull();
        assertThat(mapping.getRegion()).isEqualTo("ASIA-KR");
        assertThat(mapping.getLottable02Field()).isEqualTo("KC_MARK");
        assertThat(mapping.getLottable03Field()).isEqualTo("CUSTOMS_REF");
        assertThat(mapping.isLottable03Required()).isTrue();
    }

    @Test
    @DisplayName("Should return India mapping for ASIA-IN region")
    void shouldReturnIndiaMapping() {
        LottableMapping mapping = service.getMapping("ASIA-IN", "GENERIC");

        assertThat(mapping).isNotNull();
        assertThat(mapping.getRegion()).isEqualTo("ASIA-IN");
        assertThat(mapping.getLottable02Field()).isEqualTo("GST_INVOICE");
        assertThat(mapping.getLottable03Field()).isEqualTo("HSN_CODE");
        assertThat(mapping.isLottable02Required()).isTrue();
    }

    @Test
    @DisplayName("Should return Nike mapping for Nike client")
    void shouldReturnNikeMapping() {
        LottableMapping mapping = service.getMapping("US", "NIKE");

        assertThat(mapping).isNotNull();
        assertThat(mapping.getClient()).isEqualTo("NIKE");
        assertThat(mapping.getLottable01Field()).isEqualTo("STYLE_COLOR");
        assertThat(mapping.getLottable02Field()).isEqualTo("SIZE_CODE");
        assertThat(mapping.getLottable01Pattern()).isEqualTo("[A-Z]{2}[0-9]{6}-[0-9]{3}");
    }

    @Test
    @DisplayName("Should return H&M mapping for HM client")
    void shouldReturnHMMapping() {
        LottableMapping mapping = service.getMapping("US", "HM");

        assertThat(mapping).isNotNull();
        assertThat(mapping.getClient()).isEqualTo("HM");
        assertThat(mapping.getLottable01Field()).isEqualTo("ARTICLE_NUMBER");
        assertThat(mapping.getLottable01Pattern()).isEqualTo("[0-9]{7}");
    }

    @Test
    @DisplayName("Client mapping should take precedence over region")
    void clientMappingShouldTakePrecedence() {
        // Nike client in Korea region should get Nike mapping
        LottableMapping mapping = service.getMapping("ASIA-KR", "NIKE");

        assertThat(mapping.getLottable01Field()).isEqualTo("STYLE_COLOR");
    }

    @Test
    @DisplayName("Should be able to register custom mapping")
    void shouldRegisterCustomMapping() {
        LottableMapping custom = LottableMapping.builder()
            .storerKey("CUSTOM")
            .region("*")
            .client("CUSTOM_CLIENT")
            .lottable01Field("CUSTOM_FIELD")
            .build();

        service.registerMapping("CLIENT-CUSTOM_CLIENT", custom);

        LottableMapping retrieved = service.getMapping("US", "CUSTOM_CLIENT");
        assertThat(retrieved.getLottable01Field()).isEqualTo("CUSTOM_FIELD");
    }

    @Test
    @DisplayName("Should get field mapping by number")
    void shouldGetFieldMappingByNumber() {
        LottableMapping mapping = service.getMapping("ASIA-KR", "GENERIC");

        assertThat(mapping.getFieldMapping(1)).isEqualTo("LOT_NUMBER");
        assertThat(mapping.getFieldMapping(2)).isEqualTo("KC_MARK");
        assertThat(mapping.getFieldMapping(3)).isEqualTo("CUSTOMS_REF");
        assertThat(mapping.getFieldMapping(11)).isNull(); // Out of range
    }
}
