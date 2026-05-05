package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Configuration for receipt finalization behavior.
 *
 * Replaces CODELKUP entries and StorerConfig keys:
 * - CFG-006: CLOSEASNSTATUS
 * - CFG-023: CloseASNStatus
 * - CFG-024: CloseASNUponFinalize
 */
@Component
@Slf4j
public class FinalizeConfig {

    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_CLOSE_STATUS = "9";
    private static final String DEFAULT_VERIFIED_STATUS = "11";
    private static final String DEFAULT_FINALIZED_STATUS = "15";

    private final Map<String, StorerFinalizeSettings> storerCache = new HashMap<>();

    public FinalizeConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getCloseASNStatus(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.closeStatus != null ? settings.closeStatus : DEFAULT_CLOSE_STATUS;
    }

    public boolean shouldCloseASNUponFinalize(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.closeUponFinalize;
    }

    public String getFinalizedStatus(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.finalizedStatus != null ? settings.finalizedStatus : DEFAULT_FINALIZED_STATUS;
    }

    public String getVerifiedStatus(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.verifiedStatus != null ? settings.verifiedStatus : DEFAULT_VERIFIED_STATUS;
    }

    public boolean isAutoPutawayEnabled(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.autoPutawayRelease;
    }

    public String getPutawayReleaseSP(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.putawayReleaseSP;
    }

    public boolean isAutoVerifyEnabled(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.autoVerify;
    }

    public boolean isUCCGenerationEnabled(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.generateUCC;
    }

    public String getUCCType(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.uccType != null ? settings.uccType : "LPN";
    }

    public BigDecimal getVarianceTolerance(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.varianceTolerance != null ? settings.varianceTolerance : BigDecimal.ZERO;
    }

    public boolean isVarianceAllowed(String storerKey, BigDecimal variancePercent) {
        BigDecimal tolerance = getVarianceTolerance(storerKey);
        if (variancePercent == null) return true;
        return variancePercent.abs().compareTo(tolerance) <= 0;
    }

    public String getPostFinalizeSP(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.postFinalizeSP;
    }

    public String getPreFinalizeSP(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.preFinalizeSP;
    }

    public boolean isLineSplitAllowed(String storerKey) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.allowLineSplit;
    }

    public String getDefaultReceivingLocation(String storerKey, String facility) {
        StorerFinalizeSettings settings = getStorerSettings(storerKey);
        return settings.defaultReceivingLoc != null ? settings.defaultReceivingLoc : "RECV";
    }

    public void refresh() {
        storerCache.clear();
        log.info("Finalize configuration cache cleared");
    }

    private StorerFinalizeSettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerFinalizeSettings loadStorerSettings(String storerKey) {
        StorerFinalizeSettings settings = new StorerFinalizeSettings();

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT configkey, configvalue FROM dbo.storerconfig WHERE storerkey = ? " +
                "AND configkey IN ('CloseASNStatus', 'CloseASNUponFinalize', 'AutoPutawayRelease', " +
                "'ASNReleasePATask_SP', 'AutoVerify', 'GenerateUCC', 'UCCType', 'ChkASNVarianceTolerance', " +
                "'PostFinalizeSP', 'PreFinalizeSP', 'AllowLineSplit', 'DefaultRcptLOC', 'FinalizedStatus', 'VerifiedStatus')",
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
            log.debug("Could not load storer settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerFinalizeSettings settings, String key, String value) {
        switch (key) {
            case "CloseASNStatus" -> settings.closeStatus = value;
            case "CloseASNUponFinalize" -> settings.closeUponFinalize = isTrue(value);
            case "AutoPutawayRelease" -> settings.autoPutawayRelease = isTrue(value);
            case "ASNReleasePATask_SP" -> settings.putawayReleaseSP = value;
            case "AutoVerify" -> settings.autoVerify = isTrue(value);
            case "GenerateUCC" -> settings.generateUCC = isTrue(value);
            case "UCCType" -> settings.uccType = value;
            case "ChkASNVarianceTolerance" -> settings.varianceTolerance = parseBigDecimal(value);
            case "PostFinalizeSP" -> settings.postFinalizeSP = value;
            case "PreFinalizeSP" -> settings.preFinalizeSP = value;
            case "AllowLineSplit" -> settings.allowLineSplit = isTrue(value);
            case "DefaultRcptLOC" -> settings.defaultReceivingLoc = value;
            case "FinalizedStatus" -> settings.finalizedStatus = value;
            case "VerifiedStatus" -> settings.verifiedStatus = value;
        }
    }

    private StorerFinalizeSettings getDefaultSettings() {
        StorerFinalizeSettings settings = new StorerFinalizeSettings();
        settings.closeStatus = DEFAULT_CLOSE_STATUS;
        settings.finalizedStatus = DEFAULT_FINALIZED_STATUS;
        settings.verifiedStatus = DEFAULT_VERIFIED_STATUS;
        settings.closeUponFinalize = true;
        settings.autoPutawayRelease = false;
        settings.autoVerify = false;
        settings.generateUCC = false;
        settings.uccType = "LPN";
        settings.varianceTolerance = BigDecimal.ZERO;
        settings.allowLineSplit = true;
        settings.defaultReceivingLoc = "RECV";
        return settings;
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

    @Data
    private static class StorerFinalizeSettings {
        private String closeStatus;
        private String finalizedStatus;
        private String verifiedStatus;
        private boolean closeUponFinalize = true;
        private boolean autoPutawayRelease = false;
        private String putawayReleaseSP;
        private boolean autoVerify = false;
        private boolean generateUCC = false;
        private String uccType;
        private BigDecimal varianceTolerance;
        private String postFinalizeSP;
        private String preFinalizeSP;
        private boolean allowLineSplit = true;
        private String defaultReceivingLoc;
    }
}
