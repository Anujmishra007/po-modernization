package com.wms.po.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order Configuration Properties.
 *
 * Replaces: CFG-005 ORDTYP2ASN (Order Type to ASN mapping configuration)
 *
 * Configuration for order type mappings, ASN creation rules, and
 * trade return processing settings.
 */
@Configuration
@ConfigurationProperties(prefix = "wms.order")
@Data
public class OrderConfig {

    /**
     * Order type to ASN type mappings.
     * Key: Order type code (SO, TR, XD, etc.)
     * Value: ASN type to create
     */
    private Map<String, String> orderTypeToAsnMap = new HashMap<>();

    /**
     * Order types that should auto-create ASN on receipt.
     */
    private List<String> autoCreateAsnTypes = List.of("TR", "XD");

    /**
     * Trade return configuration.
     */
    private TradeReturnConfig tradeReturn = new TradeReturnConfig();

    /**
     * XDock configuration.
     */
    private XDockConfig xdock = new XDockConfig();

    /**
     * Order validation settings.
     */
    private ValidationSettings validation = new ValidationSettings();

    /**
     * Default order priority (1=High, 5=Low).
     */
    private int defaultPriority = 3;

    /**
     * Whether to allow partial shipments.
     */
    private boolean allowPartialShipment = true;

    /**
     * Whether to auto-release orders after creation.
     */
    private boolean autoRelease = false;

    /**
     * Get ASN type for order type.
     */
    public String getAsnTypeForOrder(String orderType) {
        return orderTypeToAsnMap.getOrDefault(orderType, "ASN");
    }

    /**
     * Check if order type should auto-create ASN.
     */
    public boolean shouldAutoCreateAsn(String orderType) {
        return autoCreateAsnTypes.contains(orderType);
    }

    /**
     * Trade return specific configuration.
     */
    @Data
    public static class TradeReturnConfig {
        private String defaultReturnType = "TR";
        private boolean requireQualityCheck = true;
        private boolean autoRelease = false;
        private List<String> validReasonCodes = List.of(
            "DAMAGE", "WRONG", "QUALITY", "OVERSTOCK", "RECALL", "OTHER"
        );
        private int returnWindowDays = 30;

        public boolean isValidReasonCode(String code) {
            return code == null || validReasonCodes.isEmpty() ||
                   validReasonCodes.contains(code);
        }
    }

    /**
     * XDock specific configuration.
     */
    @Data
    public static class XDockConfig {
        private boolean enabled = true;
        private boolean autoAllocate = true;
        private List<String> xdockOrderTypes = List.of("XD", "FT");
        private int tolerancePercent = 5;
        private int priorityBoost = 2;

        public boolean isXDockOrderType(String orderType) {
            return xdockOrderTypes.contains(orderType);
        }
    }

    /**
     * Order validation settings.
     */
    @Data
    public static class ValidationSettings {
        private boolean requireCustomer = true;
        private boolean requireCarrier = false;
        private boolean validateSkuExists = true;
        private boolean allowZeroQty = false;
        private int maxLinesPerOrder = 9999;
        private List<String> modifiableStatuses = List.of("0", "1");

        public boolean isModifiable(String status) {
            return modifiableStatuses.contains(status);
        }
    }

    public OrderConfig() {
        orderTypeToAsnMap.put("SO", "ASN");
        orderTypeToAsnMap.put("TR", "RMA");
        orderTypeToAsnMap.put("XD", "XDOCK");
        orderTypeToAsnMap.put("FT", "FLOWTHRU");
        orderTypeToAsnMap.put("WO", "WORK");
    }
}
