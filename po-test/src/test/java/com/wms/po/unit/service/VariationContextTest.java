package com.wms.po.unit.service;

import com.wms.po.domain.model.VariationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VariationContext Tests")
class VariationContextTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builds context with all fields")
        void buildsContextWithAllFields() {
            VariationContext context = VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .client("ADIDAS")
                .facility("KR01")
                .storerKey("ADIDAS_KR")
                .dualWriteEnabled(true)
                .build();

            assertThat(context.getVersion()).isEqualTo("V2");
            assertThat(context.getRegion()).isEqualTo("ASIA-KR");
            assertThat(context.getClient()).isEqualTo("ADIDAS");
            assertThat(context.getFacility()).isEqualTo("KR01");
            assertThat(context.getStorerKey()).isEqualTo("ADIDAS_KR");
            assertThat(context.isDualWriteEnabled()).isTrue();
        }

        @Test
        @DisplayName("builds context with defaults")
        void buildsContextWithDefaults() {
            VariationContext context = VariationContext.builder()
                .version("V2")
                .build();

            assertThat(context.getVersion()).isEqualTo("V2");
            assertThat(context.isDualWriteEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Version Detection Tests")
    class VersionDetectionTests {

        @Test
        @DisplayName("isV2 returns true for V2 version")
        void isV2ReturnsTrueForV2() {
            VariationContext context = VariationContext.builder()
                .version("V2")
                .build();

            assertThat(context.isV2()).isTrue();
        }

        @Test
        @DisplayName("isV2 returns false for V0 version")
        void isV2ReturnsFalseForV0() {
            VariationContext context = VariationContext.builder()
                .version("V0")
                .build();

            assertThat(context.isV2()).isFalse();
        }
    }

    @Nested
    @DisplayName("Region Detection Tests")
    class RegionDetectionTests {

        @Test
        @DisplayName("detects ASIA region")
        void detectsAsiaRegion() {
            VariationContext context = VariationContext.builder()
                .region("ASIA-KR")
                .build();

            assertThat(context.getRegion()).startsWith("ASIA");
        }

        @Test
        @DisplayName("detects EUROPE region")
        void detectsEuropeRegion() {
            VariationContext context = VariationContext.builder()
                .region("EUROPE-UK")
                .build();

            assertThat(context.getRegion()).startsWith("EUROPE");
        }

        @Test
        @DisplayName("detects AMERICAS region")
        void detectsAmericasRegion() {
            VariationContext context = VariationContext.builder()
                .region("AMERICAS-US")
                .build();

            assertThat(context.getRegion()).startsWith("AMERICAS");
        }
    }

    @Nested
    @DisplayName("Client Detection Tests")
    class ClientDetectionTests {

        @Test
        @DisplayName("identifies STANDARD client")
        void identifiesStandardClient() {
            VariationContext context = VariationContext.builder()
                .client("STANDARD")
                .build();

            assertThat(context.getClient()).isEqualTo("STANDARD");
        }

        @Test
        @DisplayName("identifies specific client")
        void identifiesSpecificClient() {
            VariationContext context = VariationContext.builder()
                .client("ADIDAS")
                .build();

            assertThat(context.getClient()).isEqualTo("ADIDAS");
        }
    }

    @Nested
    @DisplayName("Dual Write Tests")
    class DualWriteTests {

        @Test
        @DisplayName("dual write enabled when set to true")
        void dualWriteEnabledWhenTrue() {
            VariationContext context = VariationContext.builder()
                .dualWriteEnabled(true)
                .build();

            assertThat(context.isDualWriteEnabled()).isTrue();
        }

        @Test
        @DisplayName("dual write disabled by default")
        void dualWriteDisabledByDefault() {
            VariationContext context = VariationContext.builder()
                .build();

            assertThat(context.isDualWriteEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equal contexts are equal")
        void equalContextsAreEqual() {
            VariationContext context1 = VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .client("STANDARD")
                .facility("KR01")
                .storerKey("TEST")
                .dualWriteEnabled(false)
                .build();

            VariationContext context2 = VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .client("STANDARD")
                .facility("KR01")
                .storerKey("TEST")
                .dualWriteEnabled(false)
                .build();

            assertThat(context1).isEqualTo(context2);
        }

        @Test
        @DisplayName("different contexts are not equal")
        void differentContextsAreNotEqual() {
            VariationContext context1 = VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .build();

            VariationContext context2 = VariationContext.builder()
                .version("V0")
                .region("ASIA-KR")
                .build();

            assertThat(context1).isNotEqualTo(context2);
        }
    }
}
