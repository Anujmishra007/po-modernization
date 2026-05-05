package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Configuration for inventory management settings.
 *
 * Replaces StorerConfig/CODELKUP keys:
 * - CFG-030: AllowNegativeInventory
 * - CFG-031: DefaultInventoryStatus
 * - CFG-032: RequireLotTracking
 * - CFG-033: RequireSerialTracking
 * - CFG-034: AllowMixedLots
 * - CFG-035: AllowMixedSKU
 * - CFG-036: FIFOMethod
 * - CFG-037: DefaultHoldCode
 * - CFG-038: AutoHoldOnReceipt
 * - CFG-039: QualityHoldDays
 * - CFG-040: ExpiryWarningDays
 * - CFG-041: MinShelfLifePercent
 */
@Component
@Slf4j
public class InventoryConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorerInventorySettings> storerCache = new HashMap<>();
    private final Map<String, HoldCode> holdCodeCache = new HashMap<>();
    private final Map<String, InventoryStatus> statusCache = new HashMap<>();

    public InventoryConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inventory Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean allowNegativeInventory(String storerKey) {
        return getStorerSettings(storerKey).allowNegativeInventory;
    }

    public String getDefaultInventoryStatus(String storerKey) {
        StorerInventorySettings settings = getStorerSettings(storerKey);
        return settings.defaultInventoryStatus != null ? settings.defaultInventoryStatus : "OK";
    }

    public boolean requireLotTracking(String storerKey) {
        return getStorerSettings(storerKey).requireLotTracking;
    }

    public boolean requireSerialTracking(String storerKey) {
        return getStorerSettings(storerKey).requireSerialTracking;
    }

    public boolean allowMixedLots(String storerKey) {
        return getStorerSettings(storerKey).allowMixedLots;
    }

    public boolean allowMixedSKU(String storerKey) {
        return getStorerSettings(storerKey).allowMixedSKU;
    }

    public String getFIFOMethod(String storerKey) {
        StorerInventorySettings settings = getStorerSettings(storerKey);
        return settings.fifoMethod != null ? settings.fifoMethod : "RECEIPT_DATE";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Hold Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getDefaultHoldCode(String storerKey) {
        return getStorerSettings(storerKey).defaultHoldCode;
    }

    public boolean autoHoldOnReceipt(String storerKey) {
        return getStorerSettings(storerKey).autoHoldOnReceipt;
    }

    public Integer getQualityHoldDays(String storerKey) {
        return getStorerSettings(storerKey).qualityHoldDays;
    }

    public HoldCode getHoldCode(String holdCode) {
        return holdCodeCache.computeIfAbsent(holdCode, this::loadHoldCode);
    }

    public List<HoldCode> getAllHoldCodes() {
        loadAllHoldCodes();
        return new ArrayList<>(holdCodeCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Expiry/Shelf Life Settings
    // ═══════════════════════════════════════════════════════════════════════

    public Integer getExpiryWarningDays(String storerKey) {
        StorerInventorySettings settings = getStorerSettings(storerKey);
        return settings.expiryWarningDays != null ? settings.expiryWarningDays : 30;
    }

    public BigDecimal getMinShelfLifePercent(String storerKey) {
        StorerInventorySettings settings = getStorerSettings(storerKey);
        return settings.minShelfLifePercent != null ? settings.minShelfLifePercent : BigDecimal.valueOf(50);
    }

    public boolean requireExpiryValidation(String storerKey) {
        return getStorerSettings(storerKey).requireExpiryValidation;
    }

    public boolean allowExpiredReceipt(String storerKey) {
        return getStorerSettings(storerKey).allowExpiredReceipt;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Location Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean allowMultipleLocations(String storerKey) {
        return getStorerSettings(storerKey).allowMultipleLocations;
    }

    public String getDefaultZone(String storerKey) {
        return getStorerSettings(storerKey).defaultZone;
    }

    public boolean validateLocationCapacity(String storerKey) {
        return getStorerSettings(storerKey).validateLocationCapacity;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inventory Status
    // ═══════════════════════════════════════════════════════════════════════

    public InventoryStatus getInventoryStatus(String statusCode) {
        return statusCache.computeIfAbsent(statusCode, this::loadInventoryStatus);
    }

    public List<InventoryStatus> getAllInventoryStatuses() {
        loadAllInventoryStatuses();
        return new ArrayList<>(statusCache.values());
    }

    public boolean isAllocatable(String statusCode) {
        InventoryStatus status = getInventoryStatus(statusCode);
        return status != null && status.allocatable;
    }

    public boolean isPickable(String statusCode) {
        InventoryStatus status = getInventoryStatus(statusCode);
        return status != null && status.pickable;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════════

    public void refresh() {
        storerCache.clear();
        holdCodeCache.clear();
        statusCache.clear();
        log.info("Inventory configuration cache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════

    private StorerInventorySettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerInventorySettings loadStorerSettings(String storerKey) {
        StorerInventorySettings settings = getDefaultSettings();

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT configkey, configvalue FROM dbo.storerconfig WHERE storerkey = ?",
                storerKey
            );

            for (Map<String, Object> row : rows) {
                String key = (String) row.get("configkey");
                String value = (String) row.get("configvalue");
                if (key != null && value != null) {
                    applyConfig(settings, key, value);
                }
            }
        } catch (Exception e) {
            log.debug("Could not load inventory settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerInventorySettings settings, String key, String value) {
        switch (key) {
            case "AllowNegativeInventory" -> settings.allowNegativeInventory = isTrue(value);
            case "DefaultInventoryStatus" -> settings.defaultInventoryStatus = value;
            case "RequireLotTracking" -> settings.requireLotTracking = isTrue(value);
            case "RequireSerialTracking" -> settings.requireSerialTracking = isTrue(value);
            case "AllowMixedLots" -> settings.allowMixedLots = isTrue(value);
            case "AllowMixedSKU" -> settings.allowMixedSKU = isTrue(value);
            case "FIFOMethod" -> settings.fifoMethod = value;
            case "DefaultHoldCode" -> settings.defaultHoldCode = value;
            case "AutoHoldOnReceipt" -> settings.autoHoldOnReceipt = isTrue(value);
            case "QualityHoldDays" -> settings.qualityHoldDays = parseInteger(value);
            case "ExpiryWarningDays" -> settings.expiryWarningDays = parseInteger(value);
            case "MinShelfLifePercent" -> settings.minShelfLifePercent = parseBigDecimal(value);
            case "RequireExpiryValidation" -> settings.requireExpiryValidation = isTrue(value);
            case "AllowExpiredReceipt" -> settings.allowExpiredReceipt = isTrue(value);
            case "AllowMultipleLocations" -> settings.allowMultipleLocations = isTrue(value);
            case "DefaultZone" -> settings.defaultZone = value;
            case "ValidateLocationCapacity" -> settings.validateLocationCapacity = isTrue(value);
        }
    }

    private StorerInventorySettings getDefaultSettings() {
        StorerInventorySettings settings = new StorerInventorySettings();
        settings.allowNegativeInventory = false;
        settings.defaultInventoryStatus = "OK";
        settings.requireLotTracking = false;
        settings.requireSerialTracking = false;
        settings.allowMixedLots = false;
        settings.allowMixedSKU = false;
        settings.fifoMethod = "RECEIPT_DATE";
        settings.autoHoldOnReceipt = false;
        settings.expiryWarningDays = 30;
        settings.minShelfLifePercent = BigDecimal.valueOf(50);
        settings.requireExpiryValidation = false;
        settings.allowExpiredReceipt = false;
        settings.allowMultipleLocations = true;
        settings.validateLocationCapacity = true;
        return settings;
    }

    private HoldCode loadHoldCode(String holdCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT code, description, holdtype, autorelease, releasedays " +
                "FROM dbo.codelkup WHERE listname = 'HOLDCODE' AND code = ?",
                (rs, rowNum) -> new HoldCode(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("holdtype"),
                    "1".equals(rs.getString("autorelease")),
                    rs.getInt("releasedays")
                ),
                holdCode
            );
        } catch (Exception e) {
            log.debug("Hold code {} not found", holdCode);
            return null;
        }
    }

    private void loadAllHoldCodes() {
        if (!holdCodeCache.isEmpty()) return;

        try {
            List<HoldCode> codes = jdbcTemplate.query(
                "SELECT code, description, holdtype, autorelease, releasedays " +
                "FROM dbo.codelkup WHERE listname = 'HOLDCODE'",
                (rs, rowNum) -> new HoldCode(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("holdtype"),
                    "1".equals(rs.getString("autorelease")),
                    rs.getInt("releasedays")
                )
            );

            for (HoldCode hc : codes) {
                holdCodeCache.put(hc.code(), hc);
            }
        } catch (Exception e) {
            log.debug("Could not load hold codes: {}", e.getMessage());
        }
    }

    private InventoryStatus loadInventoryStatus(String statusCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT code, description, allocatable, pickable, shippable " +
                "FROM dbo.codelkup WHERE listname = 'INVSTATUS' AND code = ?",
                (rs, rowNum) -> new InventoryStatus(
                    rs.getString("code"),
                    rs.getString("description"),
                    "1".equals(rs.getString("allocatable")),
                    "1".equals(rs.getString("pickable")),
                    "1".equals(rs.getString("shippable"))
                ),
                statusCode
            );
        } catch (Exception e) {
            log.debug("Inventory status {} not found", statusCode);
            return null;
        }
    }

    private void loadAllInventoryStatuses() {
        if (!statusCache.isEmpty()) return;

        try {
            List<InventoryStatus> statuses = jdbcTemplate.query(
                "SELECT code, description, allocatable, pickable, shippable " +
                "FROM dbo.codelkup WHERE listname = 'INVSTATUS'",
                (rs, rowNum) -> new InventoryStatus(
                    rs.getString("code"),
                    rs.getString("description"),
                    "1".equals(rs.getString("allocatable")),
                    "1".equals(rs.getString("pickable")),
                    "1".equals(rs.getString("shippable"))
                )
            );

            for (InventoryStatus is : statuses) {
                statusCache.put(is.code(), is);
            }
        } catch (Exception e) {
            log.debug("Could not load inventory statuses: {}", e.getMessage());
        }
    }

    private boolean isTrue(String value) {
        return "1".equals(value) || "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    private static class StorerInventorySettings {
        private boolean allowNegativeInventory;
        private String defaultInventoryStatus;
        private boolean requireLotTracking;
        private boolean requireSerialTracking;
        private boolean allowMixedLots;
        private boolean allowMixedSKU;
        private String fifoMethod;
        private String defaultHoldCode;
        private boolean autoHoldOnReceipt;
        private Integer qualityHoldDays;
        private Integer expiryWarningDays;
        private BigDecimal minShelfLifePercent;
        private boolean requireExpiryValidation;
        private boolean allowExpiredReceipt;
        private boolean allowMultipleLocations;
        private String defaultZone;
        private boolean validateLocationCapacity;
    }

    public record HoldCode(
        String code,
        String description,
        String holdType,
        boolean autoRelease,
        int releaseDays
    ) {}

    public record InventoryStatus(
        String code,
        String description,
        boolean allocatable,
        boolean pickable,
        boolean shippable
    ) {}
}
