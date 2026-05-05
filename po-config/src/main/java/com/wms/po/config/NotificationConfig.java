package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Configuration for notifications and alerts.
 *
 * Replaces StorerConfig/CODELKUP keys:
 * - CFG-110: EnableEmailNotification
 * - CFG-111: EmailRecipients
 * - CFG-112: EmailOnReceiptFinalize
 * - CFG-113: EmailOnException
 * - CFG-114: EmailOnShortage
 * - CFG-115: EnableSMSNotification
 * - CFG-116: SMSRecipients
 * - CFG-117: AlertThresholdDays
 * - CFG-118: AlertEmailTemplate
 * - CFG-119: EscalationEnabled
 * - CFG-120: EscalationHours
 */
@Component
@Slf4j
public class NotificationConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorerNotificationSettings> storerCache = new HashMap<>();
    private final Map<String, EmailTemplate> emailTemplateCache = new HashMap<>();
    private final Map<String, AlertRule> alertRuleCache = new HashMap<>();

    public NotificationConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Email Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean enableEmailNotification(String storerKey) {
        return getStorerSettings(storerKey).enableEmailNotification;
    }

    public List<String> getEmailRecipients(String storerKey) {
        return getStorerSettings(storerKey).emailRecipients;
    }

    public List<String> getEmailRecipientsForEvent(String storerKey, String eventType) {
        StorerNotificationSettings settings = getStorerSettings(storerKey);
        return settings.eventEmailRecipients.getOrDefault(eventType, settings.emailRecipients);
    }

    public boolean emailOnReceiptFinalize(String storerKey) {
        return getStorerSettings(storerKey).emailOnReceiptFinalize;
    }

    public boolean emailOnException(String storerKey) {
        return getStorerSettings(storerKey).emailOnException;
    }

    public boolean emailOnShortage(String storerKey) {
        return getStorerSettings(storerKey).emailOnShortage;
    }

    public boolean emailOnOverage(String storerKey) {
        return getStorerSettings(storerKey).emailOnOverage;
    }

    public String getEmailFromAddress(String storerKey) {
        StorerNotificationSettings settings = getStorerSettings(storerKey);
        return settings.emailFromAddress != null ? settings.emailFromAddress : "noreply@wms.com";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SMS Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean enableSMSNotification(String storerKey) {
        return getStorerSettings(storerKey).enableSMSNotification;
    }

    public List<String> getSMSRecipients(String storerKey) {
        return getStorerSettings(storerKey).smsRecipients;
    }

    public boolean smsOnCriticalAlert(String storerKey) {
        return getStorerSettings(storerKey).smsOnCriticalAlert;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Alert Settings
    // ═══════════════════════════════════════════════════════════════════════

    public Integer getAlertThresholdDays(String storerKey) {
        StorerNotificationSettings settings = getStorerSettings(storerKey);
        return settings.alertThresholdDays != null ? settings.alertThresholdDays : 7;
    }

    public String getAlertEmailTemplate(String storerKey, String alertType) {
        StorerNotificationSettings settings = getStorerSettings(storerKey);
        return settings.alertEmailTemplates.getOrDefault(alertType, "DEFAULT_ALERT");
    }

    public boolean isEscalationEnabled(String storerKey) {
        return getStorerSettings(storerKey).escalationEnabled;
    }

    public Integer getEscalationHours(String storerKey) {
        StorerNotificationSettings settings = getStorerSettings(storerKey);
        return settings.escalationHours != null ? settings.escalationHours : 24;
    }

    public List<String> getEscalationRecipients(String storerKey) {
        return getStorerSettings(storerKey).escalationRecipients;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Email Templates
    // ═══════════════════════════════════════════════════════════════════════

    public EmailTemplate getEmailTemplate(String templateCode) {
        return emailTemplateCache.computeIfAbsent(templateCode, this::loadEmailTemplate);
    }

    public List<EmailTemplate> getAllEmailTemplates() {
        loadAllEmailTemplates();
        return new ArrayList<>(emailTemplateCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Alert Rules
    // ═══════════════════════════════════════════════════════════════════════

    public AlertRule getAlertRule(String ruleCode) {
        return alertRuleCache.computeIfAbsent(ruleCode, this::loadAlertRule);
    }

    public List<AlertRule> getAllAlertRules() {
        loadAllAlertRules();
        return new ArrayList<>(alertRuleCache.values());
    }

    public List<AlertRule> getAlertRulesForStorer(String storerKey) {
        loadAllAlertRules();
        return alertRuleCache.values().stream()
            .filter(r -> r.storers == null || r.storers.isEmpty() || r.storers.contains(storerKey))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Webhook Notification Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean enableWebhookNotification(String storerKey) {
        return getStorerSettings(storerKey).enableWebhookNotification;
    }

    public String getWebhookNotificationURL(String storerKey) {
        return getStorerSettings(storerKey).webhookNotificationURL;
    }

    public List<String> getWebhookNotificationEvents(String storerKey) {
        return getStorerSettings(storerKey).webhookNotificationEvents;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Digest Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean enableDailyDigest(String storerKey) {
        return getStorerSettings(storerKey).enableDailyDigest;
    }

    public String getDailyDigestTime(String storerKey) {
        StorerNotificationSettings settings = getStorerSettings(storerKey);
        return settings.dailyDigestTime != null ? settings.dailyDigestTime : "08:00";
    }

    public List<String> getDailyDigestRecipients(String storerKey) {
        return getStorerSettings(storerKey).dailyDigestRecipients;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════════

    public void refresh() {
        storerCache.clear();
        emailTemplateCache.clear();
        alertRuleCache.clear();
        log.info("Notification configuration cache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════

    private StorerNotificationSettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerNotificationSettings loadStorerSettings(String storerKey) {
        StorerNotificationSettings settings = getDefaultSettings();

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
            log.debug("Could not load notification settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerNotificationSettings settings, String key, String value) {
        switch (key) {
            case "EnableEmailNotification" -> settings.enableEmailNotification = isTrue(value);
            case "EmailRecipients" -> settings.emailRecipients = parseList(value);
            case "EmailOnReceiptFinalize" -> settings.emailOnReceiptFinalize = isTrue(value);
            case "EmailOnException" -> settings.emailOnException = isTrue(value);
            case "EmailOnShortage" -> settings.emailOnShortage = isTrue(value);
            case "EmailOnOverage" -> settings.emailOnOverage = isTrue(value);
            case "EmailFromAddress" -> settings.emailFromAddress = value;
            case "EnableSMSNotification" -> settings.enableSMSNotification = isTrue(value);
            case "SMSRecipients" -> settings.smsRecipients = parseList(value);
            case "SMSOnCriticalAlert" -> settings.smsOnCriticalAlert = isTrue(value);
            case "AlertThresholdDays" -> settings.alertThresholdDays = parseInteger(value);
            case "EscalationEnabled" -> settings.escalationEnabled = isTrue(value);
            case "EscalationHours" -> settings.escalationHours = parseInteger(value);
            case "EscalationRecipients" -> settings.escalationRecipients = parseList(value);
            case "EnableWebhookNotification" -> settings.enableWebhookNotification = isTrue(value);
            case "WebhookNotificationURL" -> settings.webhookNotificationURL = value;
            case "WebhookNotificationEvents" -> settings.webhookNotificationEvents = parseList(value);
            case "EnableDailyDigest" -> settings.enableDailyDigest = isTrue(value);
            case "DailyDigestTime" -> settings.dailyDigestTime = value;
            case "DailyDigestRecipients" -> settings.dailyDigestRecipients = parseList(value);
        }
    }

    private StorerNotificationSettings getDefaultSettings() {
        StorerNotificationSettings settings = new StorerNotificationSettings();
        settings.enableEmailNotification = true;
        settings.emailRecipients = new ArrayList<>();
        settings.emailOnReceiptFinalize = false;
        settings.emailOnException = true;
        settings.emailOnShortage = true;
        settings.emailOnOverage = false;
        settings.emailFromAddress = "noreply@wms.com";
        settings.enableSMSNotification = false;
        settings.smsRecipients = new ArrayList<>();
        settings.smsOnCriticalAlert = false;
        settings.alertThresholdDays = 7;
        settings.escalationEnabled = false;
        settings.escalationHours = 24;
        settings.escalationRecipients = new ArrayList<>();
        settings.enableWebhookNotification = false;
        settings.webhookNotificationEvents = new ArrayList<>();
        settings.enableDailyDigest = false;
        settings.dailyDigestTime = "08:00";
        settings.dailyDigestRecipients = new ArrayList<>();
        settings.eventEmailRecipients = new HashMap<>();
        settings.alertEmailTemplates = new HashMap<>();
        return settings;
    }

    private EmailTemplate loadEmailTemplate(String templateCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT templatecode, description, subject, body, contenttype " +
                "FROM dbo.emailtemplate WHERE templatecode = ?",
                (rs, rowNum) -> new EmailTemplate(
                    rs.getString("templatecode"),
                    rs.getString("description"),
                    rs.getString("subject"),
                    rs.getString("body"),
                    rs.getString("contenttype")
                ),
                templateCode
            );
        } catch (Exception e) {
            log.debug("Email template {} not found", templateCode);
            return null;
        }
    }

    private void loadAllEmailTemplates() {
        if (!emailTemplateCache.isEmpty()) return;

        try {
            List<EmailTemplate> templates = jdbcTemplate.query(
                "SELECT templatecode, description, subject, body, contenttype FROM dbo.emailtemplate",
                (rs, rowNum) -> new EmailTemplate(
                    rs.getString("templatecode"),
                    rs.getString("description"),
                    rs.getString("subject"),
                    rs.getString("body"),
                    rs.getString("contenttype")
                )
            );

            for (EmailTemplate et : templates) {
                emailTemplateCache.put(et.templateCode(), et);
            }
        } catch (Exception e) {
            log.debug("Could not load email templates: {}", e.getMessage());
        }
    }

    private AlertRule loadAlertRule(String ruleCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT rulecode, description, alerttype, severity, threshold, enabled " +
                "FROM dbo.alertrule WHERE rulecode = ?",
                (rs, rowNum) -> new AlertRule(
                    rs.getString("rulecode"),
                    rs.getString("description"),
                    rs.getString("alerttype"),
                    rs.getString("severity"),
                    rs.getString("threshold"),
                    "1".equals(rs.getString("enabled")),
                    new HashSet<>()
                ),
                ruleCode
            );
        } catch (Exception e) {
            log.debug("Alert rule {} not found", ruleCode);
            return null;
        }
    }

    private void loadAllAlertRules() {
        if (!alertRuleCache.isEmpty()) return;

        try {
            List<AlertRule> rules = jdbcTemplate.query(
                "SELECT rulecode, description, alerttype, severity, threshold, enabled FROM dbo.alertrule",
                (rs, rowNum) -> new AlertRule(
                    rs.getString("rulecode"),
                    rs.getString("description"),
                    rs.getString("alerttype"),
                    rs.getString("severity"),
                    rs.getString("threshold"),
                    "1".equals(rs.getString("enabled")),
                    new HashSet<>()
                )
            );

            for (AlertRule ar : rules) {
                alertRuleCache.put(ar.ruleCode(), ar);
            }
        } catch (Exception e) {
            log.debug("Could not load alert rules: {}", e.getMessage());
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
        return new ArrayList<>(Arrays.asList(value.split("[,;]")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    private static class StorerNotificationSettings {
        private boolean enableEmailNotification;
        private List<String> emailRecipients;
        private Map<String, List<String>> eventEmailRecipients;
        private boolean emailOnReceiptFinalize;
        private boolean emailOnException;
        private boolean emailOnShortage;
        private boolean emailOnOverage;
        private String emailFromAddress;
        private boolean enableSMSNotification;
        private List<String> smsRecipients;
        private boolean smsOnCriticalAlert;
        private Integer alertThresholdDays;
        private Map<String, String> alertEmailTemplates;
        private boolean escalationEnabled;
        private Integer escalationHours;
        private List<String> escalationRecipients;
        private boolean enableWebhookNotification;
        private String webhookNotificationURL;
        private List<String> webhookNotificationEvents;
        private boolean enableDailyDigest;
        private String dailyDigestTime;
        private List<String> dailyDigestRecipients;
    }

    public record EmailTemplate(
        String templateCode,
        String description,
        String subject,
        String body,
        String contentType
    ) {}

    public record AlertRule(
        String ruleCode,
        String description,
        String alertType,
        String severity,
        String threshold,
        boolean enabled,
        Set<String> storers
    ) {}
}
