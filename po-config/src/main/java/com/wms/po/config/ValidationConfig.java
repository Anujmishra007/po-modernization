package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Configuration for validation rules.
 *
 * Replaces StorerConfig keys:
 * - CFG-021: AllowPopulateSamePOLine
 * - CFG-022: DefaultRcptLOC
 * - CFG-025: ChkASNVarianceTolerance
 * - CFG-026: AllowOneASNPerPO
 * - CFG-004: CTNTYPETAB (Carton Type validation)
 */
@Component
@Slf4j
public class ValidationConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorerValidationSettings> storerCache = new HashMap<>();
    private final Map<String, CartonType> cartonTypeCache = new HashMap<>();

    public ValidationConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean allowPopulateSamePOLine(String storerKey) {
        return getStorerSettings(storerKey).allowPopulateSamePOLine;
    }

    public boolean allowOneASNPerPO(String storerKey) {
        return getStorerSettings(storerKey).allowOneASNPerPO;
    }

    public BigDecimal getVarianceTolerance(String storerKey) {
        StorerValidationSettings settings = getStorerSettings(storerKey);
        return settings.varianceTolerance != null ? settings.varianceTolerance : BigDecimal.ZERO;
    }

    public String getDefaultReceivingLocation(String storerKey, String facility) {
        StorerValidationSettings settings = getStorerSettings(storerKey);
        return settings.defaultReceivingLoc != null ? settings.defaultReceivingLoc : "RECV";
    }

    public boolean allowOverReceipt(String storerKey) {
        return getStorerSettings(storerKey).allowOverReceipt;
    }

    public BigDecimal getOverReceiptTolerance(String storerKey) {
        StorerValidationSettings settings = getStorerSettings(storerKey);
        return settings.overReceiptTolerance != null ? settings.overReceiptTolerance : BigDecimal.ZERO;
    }

    public boolean allowUnderReceipt(String storerKey) {
        return getStorerSettings(storerKey).allowUnderReceipt;
    }

    public boolean allowZeroQuantity(String storerKey) {
        return getStorerSettings(storerKey).allowZeroQuantity;
    }

    public BigDecimal getMinQuantity(String storerKey) {
        StorerValidationSettings settings = getStorerSettings(storerKey);
        return settings.minQuantity != null ? settings.minQuantity : BigDecimal.ONE;
    }

    public BigDecimal getMaxQuantity(String storerKey) {
        return getStorerSettings(storerKey).maxQuantity;
    }

    public boolean requireValidSKU(String storerKey) {
        return getStorerSettings(storerKey).requireValidSKU;
    }

    public boolean allowAutoCreateSKU(String storerKey) {
        return getStorerSettings(storerKey).allowAutoCreateSKU;
    }

    public boolean allowSubstituteSKU(String storerKey) {
        return getStorerSettings(storerKey).allowSubstituteSKU;
    }

    public boolean isValidCartonType(String cartonType) {
        if (cartonType == null || cartonType.isEmpty()) return false;
        return getCartonType(cartonType) != null;
    }

    public CartonType getCartonType(String cartonType) {
        return cartonTypeCache.computeIfAbsent(cartonType, this::loadCartonType);
    }

    public List<String> getValidCartonTypes() {
        loadAllCartonTypes();
        return new ArrayList<>(cartonTypeCache.keySet());
    }

    public boolean isLottableRequired(String storerKey, String lottableField) {
        return getStorerSettings(storerKey).requiredLottables.contains(lottableField);
    }

    public Set<String> getRequiredLottables(String storerKey) {
        return Collections.unmodifiableSet(getStorerSettings(storerKey).requiredLottables);
    }

    public boolean requireExpiryDate(String storerKey) {
        return getStorerSettings(storerKey).requireExpiryDate;
    }

    public boolean requireManufactureDate(String storerKey) {
        return getStorerSettings(storerKey).requireManufactureDate;
    }

    public Integer getMinShelfLifeDays(String storerKey) {
        return getStorerSettings(storerKey).minShelfLifeDays;
    }

    public void refresh() {
        storerCache.clear();
        cartonTypeCache.clear();
        log.info("Validation configuration cache cleared");
    }

    private StorerValidationSettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerValidationSettings loadStorerSettings(String storerKey) {
        StorerValidationSettings settings = getDefaultSettings();

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
            log.debug("Could not load validation settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerValidationSettings settings, String key, String value) {
        switch (key) {
            case "AllowPopulateSamePOLine" -> settings.allowPopulateSamePOLine = isTrue(value);
            case "AllowOneASNPerPO" -> settings.allowOneASNPerPO = isTrue(value);
            case "ChkASNVarianceTolerance" -> settings.varianceTolerance = parseBigDecimal(value);
            case "DefaultRcptLOC" -> settings.defaultReceivingLoc = value;
            case "AllowOverReceipt" -> settings.allowOverReceipt = isTrue(value);
            case "OverReceiptTolerance" -> settings.overReceiptTolerance = parseBigDecimal(value);
            case "AllowUnderReceipt" -> settings.allowUnderReceipt = isTrue(value);
            case "AllowZeroQuantity" -> settings.allowZeroQuantity = isTrue(value);
            case "MinQuantity" -> settings.minQuantity = parseBigDecimal(value);
            case "MaxQuantity" -> settings.maxQuantity = parseBigDecimal(value);
            case "RequireValidSKU" -> settings.requireValidSKU = isTrue(value);
            case "AllowAutoCreateSKU" -> settings.allowAutoCreateSKU = isTrue(value);
            case "AllowSubstituteSKU" -> settings.allowSubstituteSKU = isTrue(value);
            case "RequiredLottables" -> settings.requiredLottables.addAll(parseList(value));
            case "RequireExpiryDate" -> settings.requireExpiryDate = isTrue(value);
            case "RequireManufactureDate" -> settings.requireManufactureDate = isTrue(value);
            case "MinShelfLifeDays" -> settings.minShelfLifeDays = parseInteger(value);
        }
    }

    private StorerValidationSettings getDefaultSettings() {
        StorerValidationSettings settings = new StorerValidationSettings();
        settings.allowPopulateSamePOLine = false;
        settings.allowOneASNPerPO = false;
        settings.varianceTolerance = BigDecimal.ZERO;
        settings.defaultReceivingLoc = "RECV";
        settings.allowOverReceipt = false;
        settings.overReceiptTolerance = BigDecimal.ZERO;
        settings.allowUnderReceipt = true;
        settings.allowZeroQuantity = false;
        settings.minQuantity = BigDecimal.ONE;
        settings.maxQuantity = null;
        settings.requireValidSKU = true;
        settings.allowAutoCreateSKU = false;
        settings.allowSubstituteSKU = false;
        settings.requireExpiryDate = false;
        settings.requireManufactureDate = false;
        return settings;
    }

    private CartonType loadCartonType(String cartonType) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT code, description, length, width, height, maxweight, tareweight " +
                "FROM dbo.codelkup WHERE listname = 'CTNTYPETAB' AND code = ?",
                (rs, rowNum) -> new CartonType(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getBigDecimal("length"),
                    rs.getBigDecimal("width"),
                    rs.getBigDecimal("height"),
                    rs.getBigDecimal("maxweight"),
                    rs.getBigDecimal("tareweight")
                ),
                cartonType
            );
        } catch (Exception e) {
            log.debug("Carton type {} not found", cartonType);
            return null;
        }
    }

    private void loadAllCartonTypes() {
        if (!cartonTypeCache.isEmpty()) return;

        try {
            List<CartonType> types = jdbcTemplate.query(
                "SELECT code, description, length, width, height, maxweight, tareweight " +
                "FROM dbo.codelkup WHERE listname = 'CTNTYPETAB'",
                (rs, rowNum) -> new CartonType(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getBigDecimal("length"),
                    rs.getBigDecimal("width"),
                    rs.getBigDecimal("height"),
                    rs.getBigDecimal("maxweight"),
                    rs.getBigDecimal("tareweight")
                )
            );

            for (CartonType ct : types) {
                cartonTypeCache.put(ct.code(), ct);
            }
        } catch (Exception e) {
            log.debug("Could not load carton types: {}", e.getMessage());
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

    private Set<String> parseList(String value) {
        if (value == null || value.isEmpty()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(value.split(",")));
    }

    @Data
    private static class StorerValidationSettings {
        private boolean allowPopulateSamePOLine;
        private boolean allowOneASNPerPO;
        private BigDecimal varianceTolerance;
        private String defaultReceivingLoc;
        private boolean allowOverReceipt;
        private BigDecimal overReceiptTolerance;
        private boolean allowUnderReceipt;
        private boolean allowZeroQuantity;
        private BigDecimal minQuantity;
        private BigDecimal maxQuantity;
        private boolean requireValidSKU;
        private boolean allowAutoCreateSKU;
        private boolean allowSubstituteSKU;
        private Set<String> requiredLottables = new HashSet<>();
        private boolean requireExpiryDate;
        private boolean requireManufactureDate;
        private Integer minShelfLifeDays;
    }

    public record CartonType(
        String code,
        String description,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal maxWeight,
        BigDecimal tareWeight
    ) {
        public BigDecimal getCube() {
            if (length == null || width == null || height == null) return null;
            return length.multiply(width).multiply(height);
        }
    }
}
