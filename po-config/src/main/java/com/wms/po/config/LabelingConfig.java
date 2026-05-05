package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configuration for labeling and barcode generation.
 *
 * Replaces StorerConfig/CODELKUP keys:
 * - CFG-090: LabelFormat
 * - CFG-091: LabelPrinterType
 * - CFG-092: DefaultLabelTemplate
 * - CFG-093: BarcodeFormat
 * - CFG-094: UCCLabelRequired
 * - CFG-095: UCCLabelFormat
 * - CFG-096: GS1CompanyPrefix
 * - CFG-097: SSCCPrefix
 * - CFG-098: GenerateSSCCOnReceipt
 * - CFG-099: LabelCopies
 * - CFG-100: AutoPrintOnFinalize
 * - CFG-101: PrinterIP
 */
@Component
@Slf4j
public class LabelingConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorerLabelSettings> storerCache = new HashMap<>();
    private final Map<String, LabelTemplate> templateCache = new HashMap<>();
    private final Map<String, BarcodeFormat> barcodeFormatCache = new HashMap<>();

    public LabelingConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Label Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getLabelFormat(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.labelFormat != null ? settings.labelFormat : "ZPL";
    }

    public String getLabelPrinterType(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.labelPrinterType != null ? settings.labelPrinterType : "ZEBRA";
    }

    public String getDefaultLabelTemplate(String storerKey, String labelType) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.labelTemplates.getOrDefault(labelType, settings.defaultLabelTemplate);
    }

    public Integer getLabelCopies(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.labelCopies != null ? settings.labelCopies : 1;
    }

    public boolean autoPrintOnFinalize(String storerKey) {
        return getStorerSettings(storerKey).autoPrintOnFinalize;
    }

    public String getPrinterIP(String storerKey) {
        return getStorerSettings(storerKey).printerIP;
    }

    public String getPrinterPort(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.printerPort != null ? settings.printerPort : "9100";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Barcode Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getBarcodeFormat(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.barcodeFormat != null ? settings.barcodeFormat : "CODE128";
    }

    public BarcodeFormat getBarcodeFormatDetails(String formatCode) {
        return barcodeFormatCache.computeIfAbsent(formatCode, this::loadBarcodeFormat);
    }

    public List<BarcodeFormat> getAllBarcodeFormats() {
        loadAllBarcodeFormats();
        return new ArrayList<>(barcodeFormatCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UCC/GS1 Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean isUCCLabelRequired(String storerKey) {
        return getStorerSettings(storerKey).uccLabelRequired;
    }

    public String getUCCLabelFormat(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.uccLabelFormat != null ? settings.uccLabelFormat : "GS1-128";
    }

    public String getGS1CompanyPrefix(String storerKey) {
        return getStorerSettings(storerKey).gs1CompanyPrefix;
    }

    public String getSSCCPrefix(String storerKey) {
        return getStorerSettings(storerKey).ssccPrefix;
    }

    public boolean generateSSCCOnReceipt(String storerKey) {
        return getStorerSettings(storerKey).generateSSCCOnReceipt;
    }

    public String getGTINFormat(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.gtinFormat != null ? settings.gtinFormat : "GTIN-14";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Label Template
    // ═══════════════════════════════════════════════════════════════════════

    public LabelTemplate getLabelTemplate(String templateCode) {
        return templateCache.computeIfAbsent(templateCode, this::loadLabelTemplate);
    }

    public List<LabelTemplate> getAllLabelTemplates() {
        loadAllLabelTemplates();
        return new ArrayList<>(templateCache.values());
    }

    public List<LabelTemplate> getLabelTemplatesForType(String labelType) {
        loadAllLabelTemplates();
        return templateCache.values().stream()
            .filter(t -> labelType.equals(t.labelType))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Print Queue Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean usePrintQueue(String storerKey) {
        return getStorerSettings(storerKey).usePrintQueue;
    }

    public String getPrintQueueName(String storerKey) {
        return getStorerSettings(storerKey).printQueueName;
    }

    public Integer getPrintRetryCount(String storerKey) {
        StorerLabelSettings settings = getStorerSettings(storerKey);
        return settings.printRetryCount != null ? settings.printRetryCount : 3;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════════

    public void refresh() {
        storerCache.clear();
        templateCache.clear();
        barcodeFormatCache.clear();
        log.info("Labeling configuration cache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════

    private StorerLabelSettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerLabelSettings loadStorerSettings(String storerKey) {
        StorerLabelSettings settings = getDefaultSettings();

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
            log.debug("Could not load label settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerLabelSettings settings, String key, String value) {
        switch (key) {
            case "LabelFormat" -> settings.labelFormat = value;
            case "LabelPrinterType" -> settings.labelPrinterType = value;
            case "DefaultLabelTemplate" -> settings.defaultLabelTemplate = value;
            case "BarcodeFormat" -> settings.barcodeFormat = value;
            case "UCCLabelRequired" -> settings.uccLabelRequired = isTrue(value);
            case "UCCLabelFormat" -> settings.uccLabelFormat = value;
            case "GS1CompanyPrefix" -> settings.gs1CompanyPrefix = value;
            case "SSCCPrefix" -> settings.ssccPrefix = value;
            case "GenerateSSCCOnReceipt" -> settings.generateSSCCOnReceipt = isTrue(value);
            case "LabelCopies" -> settings.labelCopies = parseInteger(value);
            case "AutoPrintOnFinalize" -> settings.autoPrintOnFinalize = isTrue(value);
            case "PrinterIP" -> settings.printerIP = value;
            case "PrinterPort" -> settings.printerPort = value;
            case "GTINFormat" -> settings.gtinFormat = value;
            case "UsePrintQueue" -> settings.usePrintQueue = isTrue(value);
            case "PrintQueueName" -> settings.printQueueName = value;
            case "PrintRetryCount" -> settings.printRetryCount = parseInteger(value);
        }
    }

    private StorerLabelSettings getDefaultSettings() {
        StorerLabelSettings settings = new StorerLabelSettings();
        settings.labelFormat = "ZPL";
        settings.labelPrinterType = "ZEBRA";
        settings.barcodeFormat = "CODE128";
        settings.uccLabelRequired = false;
        settings.uccLabelFormat = "GS1-128";
        settings.generateSSCCOnReceipt = false;
        settings.labelCopies = 1;
        settings.autoPrintOnFinalize = false;
        settings.printerPort = "9100";
        settings.gtinFormat = "GTIN-14";
        settings.usePrintQueue = false;
        settings.printRetryCount = 3;
        settings.labelTemplates = new HashMap<>();
        return settings;
    }

    private LabelTemplate loadLabelTemplate(String templateCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT templatecode, description, labeltype, printertype, template " +
                "FROM dbo.labeltemplate WHERE templatecode = ?",
                (rs, rowNum) -> new LabelTemplate(
                    rs.getString("templatecode"),
                    rs.getString("description"),
                    rs.getString("labeltype"),
                    rs.getString("printertype"),
                    rs.getString("template")
                ),
                templateCode
            );
        } catch (Exception e) {
            log.debug("Label template {} not found", templateCode);
            return null;
        }
    }

    private void loadAllLabelTemplates() {
        if (!templateCache.isEmpty()) return;

        try {
            List<LabelTemplate> templates = jdbcTemplate.query(
                "SELECT templatecode, description, labeltype, printertype, template FROM dbo.labeltemplate",
                (rs, rowNum) -> new LabelTemplate(
                    rs.getString("templatecode"),
                    rs.getString("description"),
                    rs.getString("labeltype"),
                    rs.getString("printertype"),
                    rs.getString("template")
                )
            );

            for (LabelTemplate lt : templates) {
                templateCache.put(lt.templateCode(), lt);
            }
        } catch (Exception e) {
            log.debug("Could not load label templates: {}", e.getMessage());
        }
    }

    private BarcodeFormat loadBarcodeFormat(String formatCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT code, description, barcodetype, minlength, maxlength " +
                "FROM dbo.codelkup WHERE listname = 'BARCODEFMT' AND code = ?",
                (rs, rowNum) -> new BarcodeFormat(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("barcodetype"),
                    rs.getInt("minlength"),
                    rs.getInt("maxlength")
                ),
                formatCode
            );
        } catch (Exception e) {
            log.debug("Barcode format {} not found", formatCode);
            return null;
        }
    }

    private void loadAllBarcodeFormats() {
        if (!barcodeFormatCache.isEmpty()) return;

        try {
            List<BarcodeFormat> formats = jdbcTemplate.query(
                "SELECT code, description, barcodetype, minlength, maxlength " +
                "FROM dbo.codelkup WHERE listname = 'BARCODEFMT'",
                (rs, rowNum) -> new BarcodeFormat(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("barcodetype"),
                    rs.getInt("minlength"),
                    rs.getInt("maxlength")
                )
            );

            for (BarcodeFormat bf : formats) {
                barcodeFormatCache.put(bf.code(), bf);
            }
        } catch (Exception e) {
            log.debug("Could not load barcode formats: {}", e.getMessage());
        }
    }

    private boolean isTrue(String value) {
        return "1".equals(value) || "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
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
    private static class StorerLabelSettings {
        private String labelFormat;
        private String labelPrinterType;
        private String defaultLabelTemplate;
        private Map<String, String> labelTemplates;
        private String barcodeFormat;
        private boolean uccLabelRequired;
        private String uccLabelFormat;
        private String gs1CompanyPrefix;
        private String ssccPrefix;
        private boolean generateSSCCOnReceipt;
        private Integer labelCopies;
        private boolean autoPrintOnFinalize;
        private String printerIP;
        private String printerPort;
        private String gtinFormat;
        private boolean usePrintQueue;
        private String printQueueName;
        private Integer printRetryCount;
    }

    public record LabelTemplate(
        String templateCode,
        String description,
        String labelType,
        String printerType,
        String template
    ) {}

    public record BarcodeFormat(
        String code,
        String description,
        String barcodeType,
        int minLength,
        int maxLength
    ) {}
}
