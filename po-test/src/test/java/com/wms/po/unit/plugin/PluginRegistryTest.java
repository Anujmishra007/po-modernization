package com.wms.po.unit.plugin;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.plugin.api.PostFinalizePlugin;
import com.wms.po.plugin.api.PreFinalizePlugin;
import com.wms.po.variation.plugin.PluginRegistry;
import com.wms.po.variation.plugin.PostPopulatePlugin;
import com.wms.po.variation.plugin.PrePopulatePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PluginRegistry Tests")
class PluginRegistryTest {

    @Nested
    @DisplayName("Pre-Finalize Plugin Tests")
    class PreFinalizePluginTests {

        @Test
        @DisplayName("returns empty list when no plugins registered")
        void returnsEmptyListWhenNoPlugins() {
            PluginRegistry registry = new PluginRegistry(null, null, null, null);

            List<PreFinalizePlugin> result = registry.getPreFinalizePlugins("TEST_STORER", "ASIA");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns STANDARD plugins for any storer")
        void returnsStandardPluginsForAnyStorer() {
            PreFinalizePlugin standardPlugin = createPreFinalizePlugin("STANDARD", "ALL", 1);
            PluginRegistry registry = new PluginRegistry(
                List.of(standardPlugin), null, null, null);

            List<PreFinalizePlugin> result = registry.getPreFinalizePlugins("ANY_STORER", "ASIA");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getClientCode()).isEqualTo("STANDARD");
        }

        @Test
        @DisplayName("returns client-specific plugins for matching storer")
        void returnsClientSpecificPluginsForMatchingStorer() {
            PreFinalizePlugin adidasPlugin = createPreFinalizePlugin("ADIDAS", "ALL", 1);
            PluginRegistry registry = new PluginRegistry(
                List.of(adidasPlugin), null, null, null);

            List<PreFinalizePlugin> result = registry.getPreFinalizePlugins("ADIDAS_US", "ASIA");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getClientCode()).isEqualTo("ADIDAS");
        }

        @Test
        @DisplayName("filters by region when specified")
        void filtersByRegion() {
            PreFinalizePlugin asiaPlugin = createPreFinalizePlugin("STANDARD", "ASIA", 1);
            PreFinalizePlugin europePlugin = createPreFinalizePlugin("STANDARD", "EUROPE", 2);
            PluginRegistry registry = new PluginRegistry(
                List.of(asiaPlugin, europePlugin), null, null, null);

            List<PreFinalizePlugin> result = registry.getPreFinalizePlugins("TEST_STORER", "ASIA");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRegionCode()).isEqualTo("ASIA");
        }

        @Test
        @DisplayName("returns ALL region plugins for any region")
        void returnsAllRegionPluginsForAnyRegion() {
            PreFinalizePlugin allRegionPlugin = createPreFinalizePlugin("STANDARD", "ALL", 1);
            PluginRegistry registry = new PluginRegistry(
                List.of(allRegionPlugin), null, null, null);

            List<PreFinalizePlugin> result = registry.getPreFinalizePlugins("TEST_STORER", "EUROPE");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("sorts plugins by order")
        void sortsPluginsByOrder() {
            PreFinalizePlugin plugin3 = createPreFinalizePlugin("STANDARD", "ALL", 3);
            PreFinalizePlugin plugin1 = createPreFinalizePlugin("STANDARD", "ALL", 1);
            PreFinalizePlugin plugin2 = createPreFinalizePlugin("STANDARD", "ALL", 2);
            PluginRegistry registry = new PluginRegistry(
                List.of(plugin3, plugin1, plugin2), null, null, null);

            List<PreFinalizePlugin> result = registry.getPreFinalizePlugins("TEST_STORER", "ALL");

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getOrder()).isEqualTo(1);
            assertThat(result.get(1).getOrder()).isEqualTo(2);
            assertThat(result.get(2).getOrder()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Post-Finalize Plugin Tests")
    class PostFinalizePluginTests {

        @Test
        @DisplayName("returns empty list when no plugins registered")
        void returnsEmptyListWhenNoPlugins() {
            PluginRegistry registry = new PluginRegistry(null, null, null, null);

            List<PostFinalizePlugin> result = registry.getPostFinalizePlugins("TEST_STORER", "ASIA");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns matching post-finalize plugins")
        void returnsMatchingPostFinalizePlugins() {
            PostFinalizePlugin plugin = createPostFinalizePlugin("STANDARD", "ALL", 1);
            PluginRegistry registry = new PluginRegistry(
                null, List.of(plugin), null, null);

            List<PostFinalizePlugin> result = registry.getPostFinalizePlugins("TEST_STORER", "ASIA");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Pre-Populate Plugin Tests")
    class PrePopulatePluginTests {

        @Test
        @DisplayName("returns empty list when no plugins registered")
        void returnsEmptyListWhenNoPlugins() {
            PluginRegistry registry = new PluginRegistry(null, null, null, null);

            List<PrePopulatePlugin> result = registry.getPrePopulatePlugins("TEST_CLIENT");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns all pre-populate plugins")
        void returnsAllPrePopulatePlugins() {
            PrePopulatePlugin plugin1 = createPrePopulatePlugin();
            PrePopulatePlugin plugin2 = createPrePopulatePlugin();
            PluginRegistry registry = new PluginRegistry(
                null, null, List.of(plugin1, plugin2), null);

            List<PrePopulatePlugin> result = registry.getPrePopulatePlugins("TEST_CLIENT");

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Post-Populate Plugin Tests")
    class PostPopulatePluginTests {

        @Test
        @DisplayName("returns empty list when no plugins registered")
        void returnsEmptyListWhenNoPlugins() {
            PluginRegistry registry = new PluginRegistry(null, null, null, null);

            List<PostPopulatePlugin> result = registry.getPostPopulatePlugins("TEST_CLIENT");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns all post-populate plugins")
        void returnsAllPostPopulatePlugins() {
            PostPopulatePlugin plugin1 = createPostPopulatePlugin();
            PostPopulatePlugin plugin2 = createPostPopulatePlugin();
            PluginRegistry registry = new PluginRegistry(
                null, null, null, List.of(plugin1, plugin2));

            List<PostPopulatePlugin> result = registry.getPostPopulatePlugins("TEST_CLIENT");

            assertThat(result).hasSize(2);
        }
    }

    // Helper methods to create test plugins
    private PreFinalizePlugin createPreFinalizePlugin(String clientCode, String regionCode, int order) {
        return new PreFinalizePlugin() {
            @Override
            public String getPluginId() { return "test-pre-" + clientCode + "-" + regionCode; }
            @Override
            public String getClientCode() { return clientCode; }
            @Override
            public String getRegionCode() { return regionCode; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean shouldExecute(com.wms.po.domain.model.FinalizeRequest request,
                                        VariationContext context) { return true; }
            @Override
            public PluginResult execute(com.wms.po.domain.model.FinalizeRequest request,
                                       VariationContext context) {
                return PluginResult.success();
            }
        };
    }

    private PostFinalizePlugin createPostFinalizePlugin(String clientCode, String regionCode, int order) {
        return new PostFinalizePlugin() {
            @Override
            public String getPluginId() { return "test-post-" + clientCode + "-" + regionCode; }
            @Override
            public String getClientCode() { return clientCode; }
            @Override
            public String getRegionCode() { return regionCode; }
            @Override
            public int getOrder() { return order; }
            @Override
            public boolean shouldExecute(String receiptKey,
                                        com.wms.po.domain.model.FinalizeRequest request,
                                        VariationContext context) { return true; }
            @Override
            public void execute(String receiptKey,
                               com.wms.po.domain.model.FinalizeRequest request,
                               VariationContext context,
                               java.util.Map<String, Object> finalizationData) {
                // no-op for test
            }
        };
    }

    private PrePopulatePlugin createPrePopulatePlugin() {
        return (request, context) -> PluginResult.success();
    }

    private PostPopulatePlugin createPostPopulatePlugin() {
        return (receiptKey, request, context) -> PluginResult.success();
    }
}
