package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configuration for EDI and external system integrations.
 *
 * Replaces StorerConfig/CODELKUP keys:
 * - CFG-070: EnableEDITransmit
 * - CFG-071: EDITransmitFormat
 * - CFG-072: ASNConfirmationEDI
 * - CFG-073: POAckEDI
 * - CFG-074: InventoryAdjustmentEDI
 * - CFG-075: ShipmentConfirmEDI
 * - CFG-076: EDIPartnerID
 * - CFG-077: EDIQualifier
 * - CFG-078: EDITransactionSet
 * - CFG-079: ExternalSystemURL
 * - CFG-080: ExternalSystemAPIKey
 * - CFG-081: WebhookEnabled
 * - CFG-082: WebhookURL
 * - CFG-083: KafkaTopicPrefix
 */
@Component
@Slf4j
public class IntegrationConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorerIntegrationSettings> storerCache = new HashMap<>();
    private final Map<String, EDIPartner> ediPartnerCache = new HashMap<>();
    private final Map<String, TransactionSet> transactionSetCache = new HashMap<>();

    public IntegrationConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EDI Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean enableEDITransmit(String storerKey) {
        return getStorerSettings(storerKey).enableEDITransmit;
    }

    public String getEDITransmitFormat(String storerKey) {
        StorerIntegrationSettings settings = getStorerSettings(storerKey);
        return settings.ediTransmitFormat != null ? settings.ediTransmitFormat : "X12";
    }

    public boolean sendASNConfirmationEDI(String storerKey) {
        return getStorerSettings(storerKey).asnConfirmationEDI;
    }

    public boolean sendPOAckEDI(String storerKey) {
        return getStorerSettings(storerKey).poAckEDI;
    }

    public boolean sendInventoryAdjustmentEDI(String storerKey) {
        return getStorerSettings(storerKey).inventoryAdjustmentEDI;
    }

    public boolean sendShipmentConfirmEDI(String storerKey) {
        return getStorerSettings(storerKey).shipmentConfirmEDI;
    }

    public String getEDIPartnerID(String storerKey) {
        return getStorerSettings(storerKey).ediPartnerID;
    }

    public String getEDIQualifier(String storerKey) {
        return getStorerSettings(storerKey).ediQualifier;
    }

    public String getEDITransactionSet(String storerKey, String transactionType) {
        return getStorerSettings(storerKey).transactionSets.get(transactionType);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // External System Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getExternalSystemURL(String storerKey) {
        return getStorerSettings(storerKey).externalSystemURL;
    }

    public String getExternalSystemAPIKey(String storerKey) {
        return getStorerSettings(storerKey).externalSystemAPIKey;
    }

    public boolean isWebhookEnabled(String storerKey) {
        return getStorerSettings(storerKey).webhookEnabled;
    }

    public String getWebhookURL(String storerKey) {
        return getStorerSettings(storerKey).webhookURL;
    }

    public List<String> getWebhookEvents(String storerKey) {
        return getStorerSettings(storerKey).webhookEvents;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Kafka Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getKafkaTopicPrefix(String storerKey) {
        StorerIntegrationSettings settings = getStorerSettings(storerKey);
        return settings.kafkaTopicPrefix != null ? settings.kafkaTopicPrefix : "wms";
    }

    public boolean isKafkaEnabled(String storerKey) {
        return getStorerSettings(storerKey).kafkaEnabled;
    }

    public String getKafkaTopic(String storerKey, String eventType) {
        String prefix = getKafkaTopicPrefix(storerKey);
        return prefix + "." + storerKey.toLowerCase() + "." + eventType.toLowerCase();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EDI Partner Lookup
    // ═══════════════════════════════════════════════════════════════════════

    public EDIPartner getEDIPartner(String partnerID) {
        return ediPartnerCache.computeIfAbsent(partnerID, this::loadEDIPartner);
    }

    public List<EDIPartner> getAllEDIPartners() {
        loadAllEDIPartners();
        return new ArrayList<>(ediPartnerCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Transaction Set Lookup
    // ═══════════════════════════════════════════════════════════════════════

    public TransactionSet getTransactionSet(String setCode) {
        return transactionSetCache.computeIfAbsent(setCode, this::loadTransactionSet);
    }

    public List<TransactionSet> getAllTransactionSets() {
        loadAllTransactionSets();
        return new ArrayList<>(transactionSetCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Transmit Log Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean createTransmitLog(String storerKey) {
        return getStorerSettings(storerKey).createTransmitLog;
    }

    public String getTransmitLogTable(String storerKey) {
        StorerIntegrationSettings settings = getStorerSettings(storerKey);
        return settings.transmitLogTable != null ? settings.transmitLogTable : "TRANSMITLOG3";
    }

    public Integer getTransmitRetryCount(String storerKey) {
        StorerIntegrationSettings settings = getStorerSettings(storerKey);
        return settings.transmitRetryCount != null ? settings.transmitRetryCount : 3;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════════

    public void refresh() {
        storerCache.clear();
        ediPartnerCache.clear();
        transactionSetCache.clear();
        log.info("Integration configuration cache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════

    private StorerIntegrationSettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerIntegrationSettings loadStorerSettings(String storerKey) {
        StorerIntegrationSettings settings = getDefaultSettings();

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
            log.debug("Could not load integration settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerIntegrationSettings settings, String key, String value) {
        switch (key) {
            case "EnableEDITransmit" -> settings.enableEDITransmit = isTrue(value);
            case "EDITransmitFormat" -> settings.ediTransmitFormat = value;
            case "ASNConfirmationEDI" -> settings.asnConfirmationEDI = isTrue(value);
            case "POAckEDI" -> settings.poAckEDI = isTrue(value);
            case "InventoryAdjustmentEDI" -> settings.inventoryAdjustmentEDI = isTrue(value);
            case "ShipmentConfirmEDI" -> settings.shipmentConfirmEDI = isTrue(value);
            case "EDIPartnerID" -> settings.ediPartnerID = value;
            case "EDIQualifier" -> settings.ediQualifier = value;
            case "ExternalSystemURL" -> settings.externalSystemURL = value;
            case "ExternalSystemAPIKey" -> settings.externalSystemAPIKey = value;
            case "WebhookEnabled" -> settings.webhookEnabled = isTrue(value);
            case "WebhookURL" -> settings.webhookURL = value;
            case "WebhookEvents" -> settings.webhookEvents = parseList(value);
            case "KafkaTopicPrefix" -> settings.kafkaTopicPrefix = value;
            case "KafkaEnabled" -> settings.kafkaEnabled = isTrue(value);
            case "CreateTransmitLog" -> settings.createTransmitLog = isTrue(value);
            case "TransmitLogTable" -> settings.transmitLogTable = value;
            case "TransmitRetryCount" -> settings.transmitRetryCount = parseInteger(value);
        }
    }

    private StorerIntegrationSettings getDefaultSettings() {
        StorerIntegrationSettings settings = new StorerIntegrationSettings();
        settings.enableEDITransmit = true;
        settings.ediTransmitFormat = "X12";
        settings.asnConfirmationEDI = true;
        settings.poAckEDI = true;
        settings.inventoryAdjustmentEDI = true;
        settings.shipmentConfirmEDI = true;
        settings.webhookEnabled = false;
        settings.kafkaTopicPrefix = "wms";
        settings.kafkaEnabled = true;
        settings.createTransmitLog = true;
        settings.transmitLogTable = "TRANSMITLOG3";
        settings.transmitRetryCount = 3;
        settings.transactionSets = new HashMap<>();
        settings.webhookEvents = new ArrayList<>();
        return settings;
    }

    private EDIPartner loadEDIPartner(String partnerID) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT partnerid, partnername, qualifier, format, protocol " +
                "FROM dbo.edipartner WHERE partnerid = ?",
                (rs, rowNum) -> new EDIPartner(
                    rs.getString("partnerid"),
                    rs.getString("partnername"),
                    rs.getString("qualifier"),
                    rs.getString("format"),
                    rs.getString("protocol")
                ),
                partnerID
            );
        } catch (Exception e) {
            log.debug("EDI partner {} not found", partnerID);
            return null;
        }
    }

    private void loadAllEDIPartners() {
        if (!ediPartnerCache.isEmpty()) return;

        try {
            List<EDIPartner> partners = jdbcTemplate.query(
                "SELECT partnerid, partnername, qualifier, format, protocol FROM dbo.edipartner",
                (rs, rowNum) -> new EDIPartner(
                    rs.getString("partnerid"),
                    rs.getString("partnername"),
                    rs.getString("qualifier"),
                    rs.getString("format"),
                    rs.getString("protocol")
                )
            );

            for (EDIPartner ep : partners) {
                ediPartnerCache.put(ep.partnerID(), ep);
            }
        } catch (Exception e) {
            log.debug("Could not load EDI partners: {}", e.getMessage());
        }
    }

    private TransactionSet loadTransactionSet(String setCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT code, description, transactiontype, direction " +
                "FROM dbo.codelkup WHERE listname = 'EDITRANSET' AND code = ?",
                (rs, rowNum) -> new TransactionSet(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("transactiontype"),
                    rs.getString("direction")
                ),
                setCode
            );
        } catch (Exception e) {
            log.debug("Transaction set {} not found", setCode);
            return null;
        }
    }

    private void loadAllTransactionSets() {
        if (!transactionSetCache.isEmpty()) return;

        try {
            List<TransactionSet> sets = jdbcTemplate.query(
                "SELECT code, description, transactiontype, direction " +
                "FROM dbo.codelkup WHERE listname = 'EDITRANSET'",
                (rs, rowNum) -> new TransactionSet(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("transactiontype"),
                    rs.getString("direction")
                )
            );

            for (TransactionSet ts : sets) {
                transactionSetCache.put(ts.code(), ts);
            }
        } catch (Exception e) {
            log.debug("Could not load transaction sets: {}", e.getMessage());
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

    private List<String> parseList(String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.split(",")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    private static class StorerIntegrationSettings {
        private boolean enableEDITransmit;
        private String ediTransmitFormat;
        private boolean asnConfirmationEDI;
        private boolean poAckEDI;
        private boolean inventoryAdjustmentEDI;
        private boolean shipmentConfirmEDI;
        private String ediPartnerID;
        private String ediQualifier;
        private Map<String, String> transactionSets;
        private String externalSystemURL;
        private String externalSystemAPIKey;
        private boolean webhookEnabled;
        private String webhookURL;
        private List<String> webhookEvents;
        private String kafkaTopicPrefix;
        private boolean kafkaEnabled;
        private boolean createTransmitLog;
        private String transmitLogTable;
        private Integer transmitRetryCount;
    }

    public record EDIPartner(
        String partnerID,
        String partnerName,
        String qualifier,
        String format,
        String protocol
    ) {}

    public record TransactionSet(
        String code,
        String description,
        String transactionType,
        String direction
    ) {}
}
