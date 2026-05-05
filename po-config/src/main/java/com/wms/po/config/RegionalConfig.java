package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Configuration for regional and country-specific settings.
 *
 * Replaces StorerConfig/CODELKUP keys:
 * - CFG-130: CountryCode
 * - CFG-131: DefaultCurrency
 * - CFG-132: DateFormat
 * - CFG-133: TimeZone
 * - CFG-134: LanguageCode
 * - CFG-135: CustomsEnabled
 * - CFG-136: CustomsDeclarationType
 * - CFG-137: TaxCalculation
 * - CFG-138: GSTEnabled
 * - CFG-139: VATRate
 * - CFG-140: DutyCalculation
 * - CFG-141: BOIEnabled (Thailand)
 * - CFG-142: FTAEnabled
 * - CFG-143: ImportLicenseRequired
 * - CFG-144: ExportDocRequired
 */
@Component
@Slf4j
public class RegionalConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CountrySettings> countryCache = new HashMap<>();
    private final Map<String, TaxRate> taxRateCache = new HashMap<>();
    private final Map<String, CustomsCode> customsCodeCache = new HashMap<>();

    public RegionalConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Basic Regional Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getCountryCode(String storerKey) {
        return getCountrySettings(storerKey).countryCode;
    }

    public String getDefaultCurrency(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.defaultCurrency != null ? settings.defaultCurrency : "USD";
    }

    public String getDateFormat(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.dateFormat != null ? settings.dateFormat : "yyyy-MM-dd";
    }

    public String getTimeZone(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.timeZone != null ? settings.timeZone : "UTC";
    }

    public String getLanguageCode(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.languageCode != null ? settings.languageCode : "EN";
    }

    public String getWeightUnit(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.weightUnit != null ? settings.weightUnit : "KG";
    }

    public String getDimensionUnit(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.dimensionUnit != null ? settings.dimensionUnit : "CM";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Customs Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean isCustomsEnabled(String countryCode) {
        return getCountrySettings(countryCode).customsEnabled;
    }

    public String getCustomsDeclarationType(String countryCode) {
        return getCountrySettings(countryCode).customsDeclarationType;
    }

    public boolean requireCustomsClearance(String countryCode) {
        return getCountrySettings(countryCode).requireCustomsClearance;
    }

    public CustomsCode getCustomsCode(String hsCode) {
        return customsCodeCache.computeIfAbsent(hsCode, this::loadCustomsCode);
    }

    public List<CustomsCode> getAllCustomsCodes() {
        loadAllCustomsCodes();
        return new ArrayList<>(customsCodeCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Tax Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getTaxCalculation(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.taxCalculation != null ? settings.taxCalculation : "NONE";
    }

    public boolean isGSTEnabled(String countryCode) {
        return getCountrySettings(countryCode).gstEnabled;
    }

    public BigDecimal getVATRate(String countryCode) {
        return getCountrySettings(countryCode).vatRate;
    }

    public BigDecimal getGSTRate(String countryCode, String productCategory) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.gstRatesByCategory.getOrDefault(productCategory, settings.defaultGSTRate);
    }

    public TaxRate getTaxRate(String taxCode) {
        return taxRateCache.computeIfAbsent(taxCode, this::loadTaxRate);
    }

    public List<TaxRate> getTaxRatesForCountry(String countryCode) {
        loadAllTaxRates();
        return taxRateCache.values().stream()
            .filter(t -> countryCode.equals(t.countryCode))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Duty Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getDutyCalculation(String countryCode) {
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.dutyCalculation != null ? settings.dutyCalculation : "CIF";
    }

    public BigDecimal getDefaultDutyRate(String countryCode) {
        return getCountrySettings(countryCode).defaultDutyRate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Country-Specific Features
    // ═══════════════════════════════════════════════════════════════════════

    // Thailand BOI
    public boolean isBOIEnabled(String countryCode) {
        return "TH".equals(countryCode) && getCountrySettings(countryCode).boiEnabled;
    }

    public String getBOIZone(String countryCode) {
        return getCountrySettings(countryCode).boiZone;
    }

    // FTA (Free Trade Agreement)
    public boolean isFTAEnabled(String countryCode) {
        return getCountrySettings(countryCode).ftaEnabled;
    }

    public List<String> getFTASchemes(String countryCode) {
        return getCountrySettings(countryCode).ftaSchemes;
    }

    // Import/Export
    public boolean isImportLicenseRequired(String countryCode) {
        return getCountrySettings(countryCode).importLicenseRequired;
    }

    public boolean isExportDocRequired(String countryCode) {
        return getCountrySettings(countryCode).exportDocRequired;
    }

    // India GST
    public boolean requireGSTIN(String countryCode) {
        return "IN".equals(countryCode) && getCountrySettings(countryCode).requireGSTIN;
    }

    public boolean requireEWayBill(String countryCode, BigDecimal value) {
        if (!"IN".equals(countryCode)) return false;
        CountrySettings settings = getCountrySettings(countryCode);
        return settings.eWayBillThreshold != null && value.compareTo(settings.eWayBillThreshold) > 0;
    }

    // Singapore
    public boolean requireTradeNetDeclaration(String countryCode) {
        return "SG".equals(countryCode) && getCountrySettings(countryCode).tradeNetRequired;
    }

    // Taiwan
    public boolean requireBSMI(String countryCode) {
        return "TW".equals(countryCode) && getCountrySettings(countryCode).bsmiRequired;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════════

    public void refresh() {
        countryCache.clear();
        taxRateCache.clear();
        customsCodeCache.clear();
        log.info("Regional configuration cache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════

    private CountrySettings getCountrySettings(String countryCode) {
        if (countryCode == null) return getDefaultSettings();
        return countryCache.computeIfAbsent(countryCode, this::loadCountrySettings);
    }

    private CountrySettings loadCountrySettings(String countryCode) {
        CountrySettings settings = getDefaultSettings();
        settings.countryCode = countryCode;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT configkey, configvalue FROM dbo.countryconfig WHERE countrycode = ?",
                countryCode
            );

            for (Map<String, Object> row : rows) {
                String key = (String) row.get("configkey");
                String value = (String) row.get("configvalue");
                if (key != null && value != null) {
                    applyConfig(settings, key, value);
                }
            }
        } catch (Exception e) {
            log.debug("Could not load country settings for {}: {}", countryCode, e.getMessage());
        }

        // Apply country-specific defaults
        applyCountryDefaults(settings, countryCode);

        return settings;
    }

    private void applyConfig(CountrySettings settings, String key, String value) {
        switch (key) {
            case "DefaultCurrency" -> settings.defaultCurrency = value;
            case "DateFormat" -> settings.dateFormat = value;
            case "TimeZone" -> settings.timeZone = value;
            case "LanguageCode" -> settings.languageCode = value;
            case "WeightUnit" -> settings.weightUnit = value;
            case "DimensionUnit" -> settings.dimensionUnit = value;
            case "CustomsEnabled" -> settings.customsEnabled = isTrue(value);
            case "CustomsDeclarationType" -> settings.customsDeclarationType = value;
            case "RequireCustomsClearance" -> settings.requireCustomsClearance = isTrue(value);
            case "TaxCalculation" -> settings.taxCalculation = value;
            case "GSTEnabled" -> settings.gstEnabled = isTrue(value);
            case "VATRate" -> settings.vatRate = parseBigDecimal(value);
            case "DefaultGSTRate" -> settings.defaultGSTRate = parseBigDecimal(value);
            case "DutyCalculation" -> settings.dutyCalculation = value;
            case "DefaultDutyRate" -> settings.defaultDutyRate = parseBigDecimal(value);
            case "BOIEnabled" -> settings.boiEnabled = isTrue(value);
            case "BOIZone" -> settings.boiZone = value;
            case "FTAEnabled" -> settings.ftaEnabled = isTrue(value);
            case "FTASchemes" -> settings.ftaSchemes = parseList(value);
            case "ImportLicenseRequired" -> settings.importLicenseRequired = isTrue(value);
            case "ExportDocRequired" -> settings.exportDocRequired = isTrue(value);
            case "RequireGSTIN" -> settings.requireGSTIN = isTrue(value);
            case "EWayBillThreshold" -> settings.eWayBillThreshold = parseBigDecimal(value);
            case "TradeNetRequired" -> settings.tradeNetRequired = isTrue(value);
            case "BSMIRequired" -> settings.bsmiRequired = isTrue(value);
        }
    }

    private void applyCountryDefaults(CountrySettings settings, String countryCode) {
        switch (countryCode) {
            case "TH" -> {
                if (settings.defaultCurrency == null) settings.defaultCurrency = "THB";
                if (settings.timeZone == null) settings.timeZone = "Asia/Bangkok";
            }
            case "IN" -> {
                if (settings.defaultCurrency == null) settings.defaultCurrency = "INR";
                if (settings.timeZone == null) settings.timeZone = "Asia/Kolkata";
                if (settings.gstEnabled) settings.requireGSTIN = true;
            }
            case "SG" -> {
                if (settings.defaultCurrency == null) settings.defaultCurrency = "SGD";
                if (settings.timeZone == null) settings.timeZone = "Asia/Singapore";
            }
            case "TW" -> {
                if (settings.defaultCurrency == null) settings.defaultCurrency = "TWD";
                if (settings.timeZone == null) settings.timeZone = "Asia/Taipei";
            }
            case "MY" -> {
                if (settings.defaultCurrency == null) settings.defaultCurrency = "MYR";
                if (settings.timeZone == null) settings.timeZone = "Asia/Kuala_Lumpur";
            }
            case "CN" -> {
                if (settings.defaultCurrency == null) settings.defaultCurrency = "CNY";
                if (settings.timeZone == null) settings.timeZone = "Asia/Shanghai";
            }
        }
    }

    private CountrySettings getDefaultSettings() {
        CountrySettings settings = new CountrySettings();
        settings.defaultCurrency = "USD";
        settings.dateFormat = "yyyy-MM-dd";
        settings.timeZone = "UTC";
        settings.languageCode = "EN";
        settings.weightUnit = "KG";
        settings.dimensionUnit = "CM";
        settings.customsEnabled = false;
        settings.requireCustomsClearance = false;
        settings.taxCalculation = "NONE";
        settings.gstEnabled = false;
        settings.ftaEnabled = false;
        settings.ftaSchemes = new ArrayList<>();
        settings.gstRatesByCategory = new HashMap<>();
        return settings;
    }

    private TaxRate loadTaxRate(String taxCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT taxcode, description, countrycode, taxtype, rate " +
                "FROM dbo.taxrate WHERE taxcode = ?",
                (rs, rowNum) -> new TaxRate(
                    rs.getString("taxcode"),
                    rs.getString("description"),
                    rs.getString("countrycode"),
                    rs.getString("taxtype"),
                    rs.getBigDecimal("rate")
                ),
                taxCode
            );
        } catch (Exception e) {
            log.debug("Tax rate {} not found", taxCode);
            return null;
        }
    }

    private void loadAllTaxRates() {
        if (!taxRateCache.isEmpty()) return;

        try {
            List<TaxRate> rates = jdbcTemplate.query(
                "SELECT taxcode, description, countrycode, taxtype, rate FROM dbo.taxrate",
                (rs, rowNum) -> new TaxRate(
                    rs.getString("taxcode"),
                    rs.getString("description"),
                    rs.getString("countrycode"),
                    rs.getString("taxtype"),
                    rs.getBigDecimal("rate")
                )
            );

            for (TaxRate tr : rates) {
                taxRateCache.put(tr.taxCode(), tr);
            }
        } catch (Exception e) {
            log.debug("Could not load tax rates: {}", e.getMessage());
        }
    }

    private CustomsCode loadCustomsCode(String hsCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT hscode, description, dutyrate, restricted " +
                "FROM dbo.customscode WHERE hscode = ?",
                (rs, rowNum) -> new CustomsCode(
                    rs.getString("hscode"),
                    rs.getString("description"),
                    rs.getBigDecimal("dutyrate"),
                    "1".equals(rs.getString("restricted"))
                ),
                hsCode
            );
        } catch (Exception e) {
            log.debug("Customs code {} not found", hsCode);
            return null;
        }
    }

    private void loadAllCustomsCodes() {
        if (!customsCodeCache.isEmpty()) return;

        try {
            List<CustomsCode> codes = jdbcTemplate.query(
                "SELECT hscode, description, dutyrate, restricted FROM dbo.customscode",
                (rs, rowNum) -> new CustomsCode(
                    rs.getString("hscode"),
                    rs.getString("description"),
                    rs.getBigDecimal("dutyrate"),
                    "1".equals(rs.getString("restricted"))
                )
            );

            for (CustomsCode cc : codes) {
                customsCodeCache.put(cc.hsCode(), cc);
            }
        } catch (Exception e) {
            log.debug("Could not load customs codes: {}", e.getMessage());
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

    private List<String> parseList(String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.split(",")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    private static class CountrySettings {
        private String countryCode;
        private String defaultCurrency;
        private String dateFormat;
        private String timeZone;
        private String languageCode;
        private String weightUnit;
        private String dimensionUnit;
        private boolean customsEnabled;
        private String customsDeclarationType;
        private boolean requireCustomsClearance;
        private String taxCalculation;
        private boolean gstEnabled;
        private BigDecimal vatRate;
        private BigDecimal defaultGSTRate;
        private Map<String, BigDecimal> gstRatesByCategory;
        private String dutyCalculation;
        private BigDecimal defaultDutyRate;
        private boolean boiEnabled;
        private String boiZone;
        private boolean ftaEnabled;
        private List<String> ftaSchemes;
        private boolean importLicenseRequired;
        private boolean exportDocRequired;
        private boolean requireGSTIN;
        private BigDecimal eWayBillThreshold;
        private boolean tradeNetRequired;
        private boolean bsmiRequired;
    }

    public record TaxRate(
        String taxCode,
        String description,
        String countryCode,
        String taxType,
        BigDecimal rate
    ) {}

    public record CustomsCode(
        String hsCode,
        String description,
        BigDecimal dutyRate,
        boolean restricted
    ) {}
}
