package com.wms.po.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Configuration for putaway operations.
 *
 * Replaces StorerConfig/CODELKUP keys:
 * - CFG-050: PutawayStrategy
 * - CFG-051: DefaultPutawayZone
 * - CFG-052: AllowDirectedPutaway
 * - CFG-053: AllowRandomPutaway
 * - CFG-054: PutawayTaskReleaseMode
 * - CFG-055: AutoReleasePutaway
 * - CFG-056: MaxPutawayQtyPerTask
 * - CFG-057: PutawayPriority
 * - CFG-058: UsePutawayRestrictions
 * - CFG-059: PutawayRestrictionTable
 * - CFG-060: AllowPutawayToPickLoc
 * - CFG-061: AllowPutawayToReserveLoc
 * - CFG-062: RequireLocationConfirmation
 */
@Component
@Slf4j
public class PutawayConfig {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, StorerPutawaySettings> storerCache = new HashMap<>();
    private final Map<String, PutawayStrategy> strategyCache = new HashMap<>();
    private final Map<String, PutawayZone> zoneCache = new HashMap<>();

    public PutawayConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Strategy Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getPutawayStrategy(String storerKey) {
        StorerPutawaySettings settings = getStorerSettings(storerKey);
        return settings.putawayStrategy != null ? settings.putawayStrategy : "STANDARD";
    }

    public String getDefaultPutawayZone(String storerKey) {
        return getStorerSettings(storerKey).defaultPutawayZone;
    }

    public boolean allowDirectedPutaway(String storerKey) {
        return getStorerSettings(storerKey).allowDirectedPutaway;
    }

    public boolean allowRandomPutaway(String storerKey) {
        return getStorerSettings(storerKey).allowRandomPutaway;
    }

    public PutawayStrategy getStrategy(String strategyCode) {
        return strategyCache.computeIfAbsent(strategyCode, this::loadStrategy);
    }

    public List<PutawayStrategy> getAllStrategies() {
        loadAllStrategies();
        return new ArrayList<>(strategyCache.values());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Task Release Settings
    // ═══════════════════════════════════════════════════════════════════════

    public String getPutawayTaskReleaseMode(String storerKey) {
        StorerPutawaySettings settings = getStorerSettings(storerKey);
        return settings.taskReleaseMode != null ? settings.taskReleaseMode : "AUTO";
    }

    public boolean autoReleasePutaway(String storerKey) {
        return getStorerSettings(storerKey).autoReleasePutaway;
    }

    public BigDecimal getMaxPutawayQtyPerTask(String storerKey) {
        return getStorerSettings(storerKey).maxPutawayQtyPerTask;
    }

    public Integer getPutawayPriority(String storerKey) {
        StorerPutawaySettings settings = getStorerSettings(storerKey);
        return settings.putawayPriority != null ? settings.putawayPriority : 5;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Restriction Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean usePutawayRestrictions(String storerKey) {
        return getStorerSettings(storerKey).usePutawayRestrictions;
    }

    public String getPutawayRestrictionTable(String storerKey) {
        return getStorerSettings(storerKey).putawayRestrictionTable;
    }

    public boolean allowPutawayToPickLoc(String storerKey) {
        return getStorerSettings(storerKey).allowPutawayToPickLoc;
    }

    public boolean allowPutawayToReserveLoc(String storerKey) {
        return getStorerSettings(storerKey).allowPutawayToReserveLoc;
    }

    public boolean requireLocationConfirmation(String storerKey) {
        return getStorerSettings(storerKey).requireLocationConfirmation;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Consolidation Settings
    // ═══════════════════════════════════════════════════════════════════════

    public boolean allowConsolidation(String storerKey) {
        return getStorerSettings(storerKey).allowConsolidation;
    }

    public String getConsolidationLevel(String storerKey) {
        StorerPutawaySettings settings = getStorerSettings(storerKey);
        return settings.consolidationLevel != null ? settings.consolidationLevel : "LOT";
    }

    public boolean prioritizeExistingLocations(String storerKey) {
        return getStorerSettings(storerKey).prioritizeExistingLocations;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Zone Settings
    // ═══════════════════════════════════════════════════════════════════════

    public PutawayZone getZone(String zoneCode) {
        return zoneCache.computeIfAbsent(zoneCode, this::loadZone);
    }

    public List<PutawayZone> getZonesForStorer(String storerKey) {
        loadAllZones();
        return zoneCache.values().stream()
            .filter(z -> z.storers == null || z.storers.isEmpty() || z.storers.contains(storerKey))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Special Handling
    // ═══════════════════════════════════════════════════════════════════════

    public boolean requireQCBeforePutaway(String storerKey) {
        return getStorerSettings(storerKey).requireQCBeforePutaway;
    }

    public boolean allowPartialPutaway(String storerKey) {
        return getStorerSettings(storerKey).allowPartialPutaway;
    }

    public boolean createReplenishmentOnPutaway(String storerKey) {
        return getStorerSettings(storerKey).createReplenishmentOnPutaway;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════════════════════════════════

    public void refresh() {
        storerCache.clear();
        strategyCache.clear();
        zoneCache.clear();
        log.info("Putaway configuration cache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════

    private StorerPutawaySettings getStorerSettings(String storerKey) {
        if (storerKey == null) return getDefaultSettings();
        return storerCache.computeIfAbsent(storerKey, this::loadStorerSettings);
    }

    private StorerPutawaySettings loadStorerSettings(String storerKey) {
        StorerPutawaySettings settings = getDefaultSettings();

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
            log.debug("Could not load putaway settings for {}: {}", storerKey, e.getMessage());
        }

        return settings;
    }

    private void applyConfig(StorerPutawaySettings settings, String key, String value) {
        switch (key) {
            case "PutawayStrategy" -> settings.putawayStrategy = value;
            case "DefaultPutawayZone" -> settings.defaultPutawayZone = value;
            case "AllowDirectedPutaway" -> settings.allowDirectedPutaway = isTrue(value);
            case "AllowRandomPutaway" -> settings.allowRandomPutaway = isTrue(value);
            case "PutawayTaskReleaseMode" -> settings.taskReleaseMode = value;
            case "AutoReleasePutaway" -> settings.autoReleasePutaway = isTrue(value);
            case "MaxPutawayQtyPerTask" -> settings.maxPutawayQtyPerTask = parseBigDecimal(value);
            case "PutawayPriority" -> settings.putawayPriority = parseInteger(value);
            case "UsePutawayRestrictions" -> settings.usePutawayRestrictions = isTrue(value);
            case "PutawayRestrictionTable" -> settings.putawayRestrictionTable = value;
            case "AllowPutawayToPickLoc" -> settings.allowPutawayToPickLoc = isTrue(value);
            case "AllowPutawayToReserveLoc" -> settings.allowPutawayToReserveLoc = isTrue(value);
            case "RequireLocationConfirmation" -> settings.requireLocationConfirmation = isTrue(value);
            case "AllowConsolidation" -> settings.allowConsolidation = isTrue(value);
            case "ConsolidationLevel" -> settings.consolidationLevel = value;
            case "PrioritizeExistingLocations" -> settings.prioritizeExistingLocations = isTrue(value);
            case "RequireQCBeforePutaway" -> settings.requireQCBeforePutaway = isTrue(value);
            case "AllowPartialPutaway" -> settings.allowPartialPutaway = isTrue(value);
            case "CreateReplenishmentOnPutaway" -> settings.createReplenishmentOnPutaway = isTrue(value);
        }
    }

    private StorerPutawaySettings getDefaultSettings() {
        StorerPutawaySettings settings = new StorerPutawaySettings();
        settings.putawayStrategy = "STANDARD";
        settings.allowDirectedPutaway = true;
        settings.allowRandomPutaway = false;
        settings.taskReleaseMode = "AUTO";
        settings.autoReleasePutaway = true;
        settings.putawayPriority = 5;
        settings.usePutawayRestrictions = false;
        settings.allowPutawayToPickLoc = false;
        settings.allowPutawayToReserveLoc = true;
        settings.requireLocationConfirmation = true;
        settings.allowConsolidation = true;
        settings.consolidationLevel = "LOT";
        settings.prioritizeExistingLocations = true;
        settings.requireQCBeforePutaway = false;
        settings.allowPartialPutaway = true;
        settings.createReplenishmentOnPutaway = false;
        return settings;
    }

    private PutawayStrategy loadStrategy(String strategyCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT code, description, strategytype, priority " +
                "FROM dbo.codelkup WHERE listname = 'PASTRATEGY' AND code = ?",
                (rs, rowNum) -> new PutawayStrategy(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("strategytype"),
                    rs.getInt("priority")
                ),
                strategyCode
            );
        } catch (Exception e) {
            log.debug("Putaway strategy {} not found", strategyCode);
            return null;
        }
    }

    private void loadAllStrategies() {
        if (!strategyCache.isEmpty()) return;

        try {
            List<PutawayStrategy> strategies = jdbcTemplate.query(
                "SELECT code, description, strategytype, priority " +
                "FROM dbo.codelkup WHERE listname = 'PASTRATEGY'",
                (rs, rowNum) -> new PutawayStrategy(
                    rs.getString("code"),
                    rs.getString("description"),
                    rs.getString("strategytype"),
                    rs.getInt("priority")
                )
            );

            for (PutawayStrategy ps : strategies) {
                strategyCache.put(ps.code(), ps);
            }
        } catch (Exception e) {
            log.debug("Could not load putaway strategies: {}", e.getMessage());
        }
    }

    private PutawayZone loadZone(String zoneCode) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT putawayzone, description, locationtype, maxheight, maxweight " +
                "FROM dbo.putawayzone WHERE putawayzone = ?",
                (rs, rowNum) -> new PutawayZone(
                    rs.getString("putawayzone"),
                    rs.getString("description"),
                    rs.getString("locationtype"),
                    rs.getBigDecimal("maxheight"),
                    rs.getBigDecimal("maxweight"),
                    new HashSet<>()
                ),
                zoneCode
            );
        } catch (Exception e) {
            log.debug("Putaway zone {} not found", zoneCode);
            return null;
        }
    }

    private void loadAllZones() {
        if (!zoneCache.isEmpty()) return;

        try {
            List<PutawayZone> zones = jdbcTemplate.query(
                "SELECT putawayzone, description, locationtype, maxheight, maxweight " +
                "FROM dbo.putawayzone",
                (rs, rowNum) -> new PutawayZone(
                    rs.getString("putawayzone"),
                    rs.getString("description"),
                    rs.getString("locationtype"),
                    rs.getBigDecimal("maxheight"),
                    rs.getBigDecimal("maxweight"),
                    new HashSet<>()
                )
            );

            for (PutawayZone pz : zones) {
                zoneCache.put(pz.code(), pz);
            }
        } catch (Exception e) {
            log.debug("Could not load putaway zones: {}", e.getMessage());
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
    private static class StorerPutawaySettings {
        private String putawayStrategy;
        private String defaultPutawayZone;
        private boolean allowDirectedPutaway;
        private boolean allowRandomPutaway;
        private String taskReleaseMode;
        private boolean autoReleasePutaway;
        private BigDecimal maxPutawayQtyPerTask;
        private Integer putawayPriority;
        private boolean usePutawayRestrictions;
        private String putawayRestrictionTable;
        private boolean allowPutawayToPickLoc;
        private boolean allowPutawayToReserveLoc;
        private boolean requireLocationConfirmation;
        private boolean allowConsolidation;
        private String consolidationLevel;
        private boolean prioritizeExistingLocations;
        private boolean requireQCBeforePutaway;
        private boolean allowPartialPutaway;
        private boolean createReplenishmentOnPutaway;
    }

    public record PutawayStrategy(
        String code,
        String description,
        String strategyType,
        int priority
    ) {}

    public record PutawayZone(
        String code,
        String description,
        String locationType,
        BigDecimal maxHeight,
        BigDecimal maxWeight,
        Set<String> storers
    ) {}
}
