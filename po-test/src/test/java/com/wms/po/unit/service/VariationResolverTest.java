package com.wms.po.unit.service;

import com.wms.po.domain.model.VariationContext;
import com.wms.po.variation.context.VariationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VariationResolverTest {

    private VariationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new VariationResolver();
        ReflectionTestUtils.setField(resolver, "dualWriteEnabled", false);
        ReflectionTestUtils.setField(resolver, "defaultVersion", "V2");
    }

    @Nested
    @DisplayName("Region resolution tests")
    class RegionResolutionTests {

        @ParameterizedTest
        @CsvSource({
            "KR01, ASIA-KR",
            "KR02, ASIA-KR",
            "SG01, ASIA-SG",
            "SG02, ASIA-SG",
            "TH01, ASIA-TH",
            "IN01, ASIA-IN",
            "MY01, ASIA-MY",
            "XX01, ASIA-DEFAULT"
        })
        @DisplayName("resolves region from facility prefix")
        void resolvesRegionFromFacility(String facility, String expectedRegion) {
            VariationContext context = resolver.resolve("TEST_STORER", facility);
            assertThat(context.getRegion()).isEqualTo(expectedRegion);
        }
    }

    @Nested
    @DisplayName("Client resolution tests")
    class ClientResolutionTests {

        @ParameterizedTest
        @CsvSource({
            "NIKE_001, NIKE",
            "NK_KOREA, NIKE",
            "HM_INDIA, HM",
            "H&M_001, HM",
            "ZARA_001, ZARA",
            "INDITEX_SG, ZARA",
            "UNIQLO_JP, UNIQLO",
            "UQ_001, UNIQLO",
            "ADIDAS_001, ADIDAS",
            "PUMA_001, PUMA",
            "GENERIC_001, STANDARD"
        })
        @DisplayName("resolves client from storer key pattern")
        void resolvesClientFromStorerKey(String storerKey, String expectedClient) {
            VariationContext context = resolver.resolve(storerKey, "KR01");
            assertThat(context.getClient()).isEqualTo(expectedClient);
        }
    }

    @Nested
    @DisplayName("Version resolution tests")
    class VersionResolutionTests {

        @Test
        @DisplayName("V0 prefix returns V0 version")
        void v0PrefixReturnsV0() {
            VariationContext context = resolver.resolve("V0_LEGACY_001", "KR01");
            assertThat(context.getVersion()).isEqualTo("V0");
        }

        @Test
        @DisplayName("V0 suffix returns V0 version")
        void v0SuffixReturnsV0() {
            VariationContext context = resolver.resolve("STORER_V0", "KR01");
            assertThat(context.getVersion()).isEqualTo("V0");
        }

        @Test
        @DisplayName("Regular storer returns default version")
        void regularStorerReturnsDefault() {
            VariationContext context = resolver.resolve("REGULAR_STORER", "KR01");
            assertThat(context.getVersion()).isEqualTo("V2");
        }
    }

    @Nested
    @DisplayName("Context helper methods")
    class ContextHelperTests {

        @Test
        @DisplayName("isKorea returns true for ASIA-KR")
        void isKoreaWorks() {
            VariationContext context = resolver.resolve("TEST", "KR01");
            assertThat(context.isKorea()).isTrue();
            assertThat(context.isSingapore()).isFalse();
        }

        @Test
        @DisplayName("isNike returns true for Nike storer")
        void isNikeWorks() {
            VariationContext context = resolver.resolve("NIKE_001", "KR01");
            assertThat(context.isNike()).isTrue();
            assertThat(context.isHM()).isFalse();
        }

        @Test
        @DisplayName("isV0 returns true for V0 version")
        void isV0Works() {
            VariationContext context = resolver.resolve("V0_STORER", "KR01");
            assertThat(context.isV0()).isTrue();
            assertThat(context.isV2()).isFalse();
        }
    }
}
