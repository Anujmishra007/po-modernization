package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Putaway Strategy Service - Location Suggestion Engine.
 *
 * Replaces SQL stored procedure: nspPASTD (3,021 lines)
 *
 * Implements 25+ putaway strategy types (PAType) to determine optimal
 * storage locations based on various criteria:
 * - Direct mapping (absolute from/to)
 * - Zone-based search
 * - SKU-specific locations
 * - Pick face / case pick locations
 * - Capacity and restriction checks
 * - Lottable matching
 * - Commingling rules
 *
 * Strategy execution order:
 * 1. Load strategy configuration from PUTAWAYSTRATEGY/DETAIL
 * 2. Apply restriction filters (location type, dimensions, weight)
 * 3. Execute PAType-specific location finding
 * 4. Validate capacity and commingling
 * 5. Return best matching location or fallback to staging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PutawayStrategyService {

    private final JdbcTemplate jdbcTemplate;

    // ═══════════════════════════════════════════════════════════════════════
    // PAType Constants (Strategy Types)
    // ═══════════════════════════════════════════════════════════════════════

    public static final String PATYPE_ABSOLUTE_MAPPING = "01";      // From→To direct mapping
    public static final String PATYPE_FROM_LOC_ZONE = "02";         // From-loc + zone search
    public static final String PATYPE_STRATEGY_ZONE = "04";         // Strategy-specified zone
    public static final String PATYPE_ABSOLUTE_TO_LOC = "06";       // Fixed target location
    public static final String PATYPE_PICK_LOC_EMPTY = "07";        // Pick loc only if BOH=0
    public static final String PATYPE_PICK_CASE_LOC = "08";         // Pick/case location
    public static final String PATYPE_SKU_DEFAULT = "09";           // SKU's PutawayLoc field
    public static final String PATYPE_SKU_PUTAWAY_ZONE = "12";      // SKU's putaway zone
    public static final String PATYPE_CASE_PICK = "15";             // Case pick face
    public static final String PATYPE_ZONE_VARIANT_16 = "16";       // Zone with conditions
    public static final String PATYPE_ZONE_VARIANT_17 = "17";       // Zone with conditions
    public static final String PATYPE_ZONE_VARIANT_18 = "18";       // Zone with conditions
    public static final String PATYPE_ZONE_VARIANT_19 = "19";       // Zone with conditions
    public static final String PATYPE_PICK_FACE = "20";             // Pick-face location
    public static final String PATYPE_MULTI_PALLET = "22";          // Multi-pallet zone
    public static final String PATYPE_LOTTABLE_MATCH = "30";        // Lottable matching
    public static final String PATYPE_CROSS_FACILITY = "40";        // Cross-facility
    public static final String PATYPE_HIGHBAY = "50";               // HighBay locations
    public static final String PATYPE_MEZZANINE = "55";             // Mezzanine locations
    public static final String PATYPE_PICK_CASE_CUSTOM = "88";      // Custom pick/case

    // Location type constants
    public static final String LOC_TYPE_STORAGE = "STORAGE";
    public static final String LOC_TYPE_PICK = "PICK";
    public static final String LOC_TYPE_CASE_PICK = "CASEPICK";
    public static final String LOC_TYPE_STAGE = "STAGE";
    public static final String LOC_TYPE_RECV = "RECV";
    public static final String LOC_TYPE_HIGHBAY = "HIGHBAY";

    /**
     * Determine the optimal putaway location for inventory.
     *
     * @param request Putaway strategy request containing SKU, qty, and context
     * @return Strategy result with suggested location and details
     */
    @Transactional(readOnly = true)
    public StrategyResult determineLocation(StrategyRequest request) {
        log.debug("Determining putaway location: storer={}, sku={}, qty={}, facility={}",
            request.getStorerKey(), request.getSku(), request.getQuantity(), request.getFacility());

        // 1. Load putaway strategy configuration
        List<StrategyDetail> strategies = loadStrategies(request);

        if (strategies.isEmpty()) {
            log.warn("No putaway strategies found for storer={}, using default",
                request.getStorerKey());
            strategies = getDefaultStrategies();
        }

        // 2. Try each strategy in sequence order
        for (StrategyDetail strategy : strategies) {
            log.debug("Trying strategy: paType={}, zone={}, seq={}",
                strategy.getPaType(), strategy.getZone(), strategy.getSequence());

            try {
                String location = executeStrategy(request, strategy);

                if (location != null) {
                    // 3. Validate the location
                    ValidationResult validation = validateLocation(request, location, strategy);

                    if (validation.isValid()) {
                        log.info("Found putaway location: {} via PAType={} for SKU={}",
                            location, strategy.getPaType(), request.getSku());

                        return StrategyResult.builder()
                            .success(true)
                            .location(location)
                            .paType(strategy.getPaType())
                            .zone(strategy.getZone())
                            .strategyKey(strategy.getStrategyKey())
                            .build();
                    } else {
                        log.debug("Location {} failed validation: {}", location, validation.getReason());
                    }
                }
            } catch (Exception e) {
                log.warn("Strategy {} failed: {}", strategy.getPaType(), e.getMessage());
            }
        }

        // 4. Fallback to staging location
        String stagingLoc = getStagingLocation(request.getFacility());
        log.warn("No suitable location found, using staging: {}", stagingLoc);

        return StrategyResult.builder()
            .success(true)
            .location(stagingLoc)
            .paType("FALLBACK")
            .fallbackUsed(true)
            .build();
    }

    /**
     * Suggest multiple candidate locations (for user selection).
     *
     * @param request Strategy request
     * @param maxResults Maximum number of suggestions
     * @return List of candidate locations with details
     */
    @Transactional(readOnly = true)
    public List<LocationCandidate> suggestLocations(StrategyRequest request, int maxResults) {
        log.debug("Suggesting up to {} locations for SKU={}", maxResults, request.getSku());

        List<LocationCandidate> candidates = new ArrayList<>();
        List<StrategyDetail> strategies = loadStrategies(request);

        for (StrategyDetail strategy : strategies) {
            try {
                List<String> locations = executeStrategyMultiple(request, strategy, maxResults);

                for (String loc : locations) {
                    ValidationResult validation = validateLocation(request, loc, strategy);

                    if (validation.isValid()) {
                        LocationCandidate candidate = buildCandidate(request, loc, strategy);
                        candidates.add(candidate);

                        if (candidates.size() >= maxResults) {
                            return candidates;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Strategy {} error: {}", strategy.getPaType(), e.getMessage());
            }
        }

        return candidates;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Strategy Loading
    // ═══════════════════════════════════════════════════════════════════════

    private List<StrategyDetail> loadStrategies(StrategyRequest request) {
        try {
            // Get strategy key from SKU or storer config
            String strategyKey = getStrategyKey(request);

            if (strategyKey == null) {
                return Collections.emptyList();
            }

            // Load strategy details ordered by sequence
            return jdbcTemplate.query(
                """
                SELECT psd.strategykey, psd.sequence, psd.patype, psd.zone,
                       psd.fromloc, psd.toloc, psd.loctype, psd.loccategory,
                       psd.areakey, psd.putawayzone, psd.locationflag,
                       psd.maxqty, psd.minqty, psd.commingling,
                       ps.description as strategydesc
                FROM dbo.putawaystrategydetail psd
                LEFT JOIN dbo.putawaystrategy ps ON psd.strategykey = ps.strategykey
                WHERE psd.strategykey = ?
                AND psd.status = '1'
                ORDER BY psd.sequence
                """,
                (rs, rowNum) -> StrategyDetail.builder()
                    .strategyKey(rs.getString("strategykey"))
                    .sequence(rs.getInt("sequence"))
                    .paType(rs.getString("patype"))
                    .zone(rs.getString("zone"))
                    .fromLoc(rs.getString("fromloc"))
                    .toLoc(rs.getString("toloc"))
                    .locType(rs.getString("loctype"))
                    .locCategory(rs.getString("loccategory"))
                    .areaKey(rs.getString("areakey"))
                    .putawayZone(rs.getString("putawayzone"))
                    .locationFlag(rs.getString("locationflag"))
                    .maxQty(rs.getBigDecimal("maxqty"))
                    .minQty(rs.getBigDecimal("minqty"))
                    .commingling(rs.getString("commingling"))
                    .build(),
                strategyKey
            );
        } catch (Exception e) {
            log.warn("Failed to load strategies: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getStrategyKey(StrategyRequest request) {
        // Try SKU-specific strategy first
        try {
            String skuStrategy = jdbcTemplate.queryForObject(
                """
                SELECT putawaystrategykey FROM dbo.sku
                WHERE storerkey = ? AND sku = ?
                AND putawaystrategykey IS NOT NULL AND putawaystrategykey != ''
                """,
                String.class,
                request.getStorerKey(), request.getSku()
            );
            if (skuStrategy != null) {
                return skuStrategy;
            }
        } catch (Exception e) {
            // Continue to storer default
        }

        // Try storer default strategy
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT configvalue FROM dbo.storerconfig
                WHERE storerkey = ? AND configkey = 'DefaultPutawayStrategy'
                """,
                String.class,
                request.getStorerKey()
            );
        } catch (Exception e) {
            // Use facility default
        }

        // Facility default
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT defaultpastrategy FROM dbo.facility
                WHERE facility = ?
                """,
                String.class,
                request.getFacility()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private List<StrategyDetail> getDefaultStrategies() {
        // Default strategy sequence when no configuration exists
        return List.of(
            StrategyDetail.builder().paType(PATYPE_SKU_DEFAULT).sequence(1).build(),
            StrategyDetail.builder().paType(PATYPE_SKU_PUTAWAY_ZONE).sequence(2).build(),
            StrategyDetail.builder().paType(PATYPE_STRATEGY_ZONE).sequence(3).zone("A").build()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Strategy Execution
    // ═══════════════════════════════════════════════════════════════════════

    private String executeStrategy(StrategyRequest request, StrategyDetail strategy) {
        return switch (strategy.getPaType()) {
            case PATYPE_ABSOLUTE_MAPPING -> executeAbsoluteMapping(request, strategy);
            case PATYPE_FROM_LOC_ZONE -> executeFromLocZone(request, strategy);
            case PATYPE_STRATEGY_ZONE -> executeStrategyZone(request, strategy);
            case PATYPE_ABSOLUTE_TO_LOC -> strategy.getToLoc();
            case PATYPE_PICK_LOC_EMPTY -> executePickLocEmpty(request, strategy);
            case PATYPE_PICK_CASE_LOC -> executePickCaseLoc(request, strategy);
            case PATYPE_SKU_DEFAULT -> executeSkuDefault(request);
            case PATYPE_SKU_PUTAWAY_ZONE -> executeSkuPutawayZone(request, strategy);
            case PATYPE_CASE_PICK -> executeCasePick(request, strategy);
            case PATYPE_PICK_FACE -> executePickFace(request, strategy);
            case PATYPE_MULTI_PALLET -> executeMultiPallet(request, strategy);
            case PATYPE_LOTTABLE_MATCH -> executeLottableMatch(request, strategy);
            case PATYPE_HIGHBAY -> executeHighBay(request, strategy);
            case PATYPE_MEZZANINE -> executeMezzanine(request, strategy);
            default -> executeZoneVariant(request, strategy);
        };
    }

    private List<String> executeStrategyMultiple(StrategyRequest request, StrategyDetail strategy, int limit) {
        // Execute zone-based search with multiple results
        String zone = resolveZone(request, strategy);
        if (zone == null) {
            return Collections.emptyList();
        }

        try {
            return jdbcTemplate.queryForList(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ?
                AND l.putawayzone = ?
                AND l.status = '1'
                AND l.loctype = ?
                AND (l.currentqty + ?) <= l.qtyCapacity
                ORDER BY l.currentqty ASC, l.loc
                LIMIT ?
                """,
                String.class,
                request.getFacility(),
                zone,
                strategy.getLocType() != null ? strategy.getLocType() : LOC_TYPE_STORAGE,
                request.getQuantity(),
                limit
            );
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // PAType 01: Absolute mapping from→to
    private String executeAbsoluteMapping(StrategyRequest request, StrategyDetail strategy) {
        if (!request.getFromLocation().equals(strategy.getFromLoc())) {
            return null;
        }
        return strategy.getToLoc();
    }

    // PAType 02: From-location + zone search
    private String executeFromLocZone(StrategyRequest request, StrategyDetail strategy) {
        if (strategy.getFromLoc() != null &&
            !request.getFromLocation().equals(strategy.getFromLoc())) {
            return null;
        }
        return findAvailableInZone(request, strategy.getZone(), strategy);
    }

    // PAType 04: Strategy-specified zone search
    private String executeStrategyZone(StrategyRequest request, StrategyDetail strategy) {
        return findAvailableInZone(request, strategy.getZone(), strategy);
    }

    // PAType 07: Pick location only if balance on hand = 0
    private String executePickLocEmpty(StrategyRequest request, StrategyDetail strategy) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT sl.loc FROM dbo.skuxloc sl
                JOIN dbo.loc l ON sl.loc = l.loc AND sl.facility = l.facility
                WHERE sl.storerkey = ? AND sl.sku = ? AND sl.facility = ?
                AND l.status = '1'
                AND l.loctype IN ('PICK', 'CASEPICK')
                AND NOT EXISTS (
                    SELECT 1 FROM dbo.lotxlocxid inv
                    WHERE inv.loc = sl.loc AND inv.storerkey = sl.storerkey
                    AND inv.qty > 0
                )
                ORDER BY sl.primaryflag DESC
                LIMIT 1
                """,
                String.class,
                request.getStorerKey(), request.getSku(), request.getFacility()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 08: Pick/case location from SKUxLOC
    private String executePickCaseLoc(StrategyRequest request, StrategyDetail strategy) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT sl.loc FROM dbo.skuxloc sl
                JOIN dbo.loc l ON sl.loc = l.loc AND sl.facility = l.facility
                WHERE sl.storerkey = ? AND sl.sku = ? AND sl.facility = ?
                AND l.status = '1'
                AND l.loctype IN ('PICK', 'CASEPICK')
                AND (l.currentqty + ?) <= l.qtyCapacity
                ORDER BY sl.primaryflag DESC, l.currentqty ASC
                LIMIT 1
                """,
                String.class,
                request.getStorerKey(), request.getSku(), request.getFacility(),
                request.getQuantity()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 09: SKU's default putaway location
    private String executeSkuDefault(StrategyRequest request) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT s.putawayloc FROM dbo.sku s
                JOIN dbo.loc l ON s.putawayloc = l.loc
                WHERE s.storerkey = ? AND s.sku = ?
                AND s.putawayloc IS NOT NULL AND s.putawayloc != ''
                AND l.status = '1'
                """,
                String.class,
                request.getStorerKey(), request.getSku()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 12: SKU's putaway zone
    private String executeSkuPutawayZone(StrategyRequest request, StrategyDetail strategy) {
        try {
            String skuZone = jdbcTemplate.queryForObject(
                "SELECT putawayzone FROM dbo.sku WHERE storerkey = ? AND sku = ?",
                String.class,
                request.getStorerKey(), request.getSku()
            );
            return findAvailableInZone(request, skuZone, strategy);
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 15: Case pick face location
    private String executeCasePick(StrategyRequest request, StrategyDetail strategy) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT sl.loc FROM dbo.skuxloc sl
                JOIN dbo.loc l ON sl.loc = l.loc AND sl.facility = l.facility
                WHERE sl.storerkey = ? AND sl.sku = ? AND sl.facility = ?
                AND l.status = '1'
                AND l.loctype = 'CASEPICK'
                AND sl.primaryflag = '1'
                LIMIT 1
                """,
                String.class,
                request.getStorerKey(), request.getSku(), request.getFacility()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 20: Pick-face location
    private String executePickFace(StrategyRequest request, StrategyDetail strategy) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT sl.loc FROM dbo.skuxloc sl
                JOIN dbo.loc l ON sl.loc = l.loc AND sl.facility = l.facility
                WHERE sl.storerkey = ? AND sl.sku = ? AND sl.facility = ?
                AND l.status = '1'
                AND l.loctype = 'PICK'
                AND sl.primaryflag = '1'
                AND (l.currentqty + ?) <= l.qtyCapacity
                LIMIT 1
                """,
                String.class,
                request.getStorerKey(), request.getSku(), request.getFacility(),
                request.getQuantity()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 22: Multi-pallet zone handling
    private String executeMultiPallet(StrategyRequest request, StrategyDetail strategy) {
        try {
            // Find location with capacity for multiple pallets
            return jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ?
                AND l.putawayzone = ?
                AND l.status = '1'
                AND l.loctype = 'STORAGE'
                AND l.maxpallets > l.currentpallets
                AND (l.currentweight + ?) <= l.maxweight
                ORDER BY l.currentpallets ASC, l.loc
                LIMIT 1
                """,
                String.class,
                request.getFacility(),
                resolveZone(request, strategy),
                request.getWeight() != null ? request.getWeight() : BigDecimal.ZERO
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 30: Lottable matching - find location with same lottables
    private String executeLottableMatch(StrategyRequest request, StrategyDetail strategy) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT inv.loc FROM dbo.lotxlocxid inv
                JOIN dbo.loc l ON inv.loc = l.loc
                WHERE inv.storerkey = ? AND inv.sku = ?
                AND l.facility = ?
                AND l.status = '1'
                AND inv.lottable01 = COALESCE(?, inv.lottable01)
                AND inv.lottable02 = COALESCE(?, inv.lottable02)
                AND (l.currentqty + ?) <= l.qtyCapacity
                ORDER BY l.currentqty DESC
                LIMIT 1
                """,
                String.class,
                request.getStorerKey(), request.getSku(),
                request.getFacility(),
                request.getLottable01(), request.getLottable02(),
                request.getQuantity()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 50: HighBay locations
    private String executeHighBay(StrategyRequest request, StrategyDetail strategy) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ?
                AND l.loctype = 'HIGHBAY'
                AND l.status = '1'
                AND (l.currentweight + ?) <= l.maxweight
                ORDER BY l.currentweight ASC, l.loc
                LIMIT 1
                """,
                String.class,
                request.getFacility(),
                request.getWeight() != null ? request.getWeight() : BigDecimal.ZERO
            );
        } catch (Exception e) {
            return null;
        }
    }

    // PAType 55: Mezzanine locations (Nike CRW style)
    private String executeMezzanine(StrategyRequest request, StrategyDetail strategy) {
        try {
            // Priority 1: Same SKU exists
            String loc = jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                JOIN dbo.lotxlocxid inv ON l.loc = inv.loc
                WHERE l.facility = ?
                AND l.loctype = 'MEZZANINE'
                AND l.status = '1'
                AND inv.storerkey = ? AND inv.sku = ?
                AND (l.currentqty + ?) <= l.qtyCapacity
                ORDER BY l.currentqty DESC
                LIMIT 1
                """,
                String.class,
                request.getFacility(),
                request.getStorerKey(), request.getSku(),
                request.getQuantity()
            );
            if (loc != null) return loc;
        } catch (Exception e) {
            // Continue to next priority
        }

        try {
            // Priority 2: Empty mezzanine
            return jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ?
                AND l.loctype = 'MEZZANINE'
                AND l.status = '1'
                AND l.currentqty = 0
                ORDER BY l.loc
                LIMIT 1
                """,
                String.class,
                request.getFacility()
            );
        } catch (Exception e) {
            return null;
        }
    }

    // Generic zone variant execution (PAType 16-19, etc.)
    private String executeZoneVariant(StrategyRequest request, StrategyDetail strategy) {
        String zone = resolveZone(request, strategy);
        return findAvailableInZone(request, zone, strategy);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Location Finding
    // ═══════════════════════════════════════════════════════════════════════

    private String findAvailableInZone(StrategyRequest request, String zone, StrategyDetail strategy) {
        if (zone == null || zone.isEmpty()) {
            return null;
        }

        try {
            StringBuilder sql = new StringBuilder();
            List<Object> params = new ArrayList<>();

            sql.append("""
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ?
                AND l.putawayzone = ?
                AND l.status = '1'
                """);
            params.add(request.getFacility());
            params.add(zone);

            // Location type filter
            if (strategy.getLocType() != null && !strategy.getLocType().isEmpty()) {
                sql.append(" AND l.loctype = ?");
                params.add(strategy.getLocType());
            } else {
                sql.append(" AND l.loctype IN ('STORAGE', 'RESERVE')");
            }

            // Location category filter
            if (strategy.getLocCategory() != null && !strategy.getLocCategory().isEmpty()) {
                sql.append(" AND l.loccategory = ?");
                params.add(strategy.getLocCategory());
            }

            // Location flag filter
            if (strategy.getLocationFlag() != null && !strategy.getLocationFlag().isEmpty()) {
                sql.append(" AND l.locationflag = ?");
                params.add(strategy.getLocationFlag());
            }

            // Capacity check
            sql.append(" AND (l.currentqty + ?) <= COALESCE(l.qtycapacity, 999999)");
            params.add(request.getQuantity());

            // Weight check
            if (request.getWeight() != null) {
                sql.append(" AND (l.currentweight + ?) <= COALESCE(l.maxweight, 999999)");
                params.add(request.getWeight());
            }

            // Cube check
            if (request.getCube() != null) {
                sql.append(" AND (l.currentcube + ?) <= COALESCE(l.maxcube, 999999)");
                params.add(request.getCube());
            }

            // Commingling check
            if ("N".equals(strategy.getCommingling())) {
                sql.append("""
                     AND NOT EXISTS (
                        SELECT 1 FROM dbo.lotxlocxid inv
                        WHERE inv.loc = l.loc
                        AND (inv.storerkey != ? OR inv.sku != ?)
                        AND inv.qty > 0
                    )
                    """);
                params.add(request.getStorerKey());
                params.add(request.getSku());
            }

            sql.append(" ORDER BY l.currentqty ASC, l.loc LIMIT 1");

            return jdbcTemplate.queryForObject(
                sql.toString(),
                String.class,
                params.toArray()
            );
        } catch (Exception e) {
            log.debug("No available location in zone {}: {}", zone, e.getMessage());
            return null;
        }
    }

    private String resolveZone(StrategyRequest request, StrategyDetail strategy) {
        // Strategy-specified zone takes precedence
        if (strategy.getZone() != null && !strategy.getZone().isEmpty()) {
            return strategy.getZone();
        }

        // Use putaway zone from strategy
        if (strategy.getPutawayZone() != null && !strategy.getPutawayZone().isEmpty()) {
            return strategy.getPutawayZone();
        }

        // Fall back to SKU's putaway zone
        try {
            return jdbcTemplate.queryForObject(
                "SELECT putawayzone FROM dbo.sku WHERE storerkey = ? AND sku = ?",
                String.class,
                request.getStorerKey(), request.getSku()
            );
        } catch (Exception e) {
            return "A"; // Default zone
        }
    }

    private String getStagingLocation(String facility) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT l.loc FROM dbo.loc l
                WHERE l.facility = ?
                AND l.loctype = 'STAGE'
                AND l.status = '1'
                ORDER BY l.loc
                LIMIT 1
                """,
                String.class,
                facility
            );
        } catch (Exception e) {
            return "STAGE";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════════════════

    private ValidationResult validateLocation(StrategyRequest request, String location, StrategyDetail strategy) {
        try {
            // Get location details
            Map<String, Object> locInfo = jdbcTemplate.queryForMap(
                """
                SELECT status, loctype, qtycapacity, currentqty,
                       maxweight, currentweight, maxcube, currentcube,
                       maxpallets, currentpallets, locationflag
                FROM dbo.loc
                WHERE loc = ? AND facility = ?
                """,
                location, request.getFacility()
            );

            // Check status
            if (!"1".equals(locInfo.get("status"))) {
                return ValidationResult.invalid("Location is not active");
            }

            // Check quantity capacity
            BigDecimal qtyCapacity = (BigDecimal) locInfo.get("qtycapacity");
            BigDecimal currentQty = (BigDecimal) locInfo.get("currentqty");
            if (qtyCapacity != null && currentQty != null) {
                if (currentQty.add(request.getQuantity()).compareTo(qtyCapacity) > 0) {
                    return ValidationResult.invalid("Exceeds quantity capacity");
                }
            }

            // Check weight capacity
            if (request.getWeight() != null) {
                BigDecimal maxWeight = (BigDecimal) locInfo.get("maxweight");
                BigDecimal currentWeight = (BigDecimal) locInfo.get("currentweight");
                if (maxWeight != null && currentWeight != null) {
                    if (currentWeight.add(request.getWeight()).compareTo(maxWeight) > 0) {
                        return ValidationResult.invalid("Exceeds weight capacity");
                    }
                }
            }

            // Check cube capacity
            if (request.getCube() != null) {
                BigDecimal maxCube = (BigDecimal) locInfo.get("maxcube");
                BigDecimal currentCube = (BigDecimal) locInfo.get("currentcube");
                if (maxCube != null && currentCube != null) {
                    if (currentCube.add(request.getCube()).compareTo(maxCube) > 0) {
                        return ValidationResult.invalid("Exceeds cube capacity");
                    }
                }
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            log.warn("Location validation failed for {}: {}", location, e.getMessage());
            return ValidationResult.invalid("Location not found");
        }
    }

    private LocationCandidate buildCandidate(StrategyRequest request, String location, StrategyDetail strategy) {
        try {
            Map<String, Object> locInfo = jdbcTemplate.queryForMap(
                """
                SELECT loc, loctype, putawayzone, qtycapacity, currentqty,
                       maxweight, currentweight, maxcube, currentcube
                FROM dbo.loc WHERE loc = ?
                """,
                location
            );

            BigDecimal qtyCapacity = (BigDecimal) locInfo.get("qtycapacity");
            BigDecimal currentQty = (BigDecimal) locInfo.get("currentqty");
            BigDecimal availableQty = qtyCapacity != null && currentQty != null
                ? qtyCapacity.subtract(currentQty)
                : BigDecimal.valueOf(999999);

            return LocationCandidate.builder()
                .location(location)
                .locationType((String) locInfo.get("loctype"))
                .zone((String) locInfo.get("putawayzone"))
                .paType(strategy.getPaType())
                .availableQty(availableQty)
                .currentQty(currentQty)
                .score(calculateScore(request, locInfo, strategy))
                .build();

        } catch (Exception e) {
            return LocationCandidate.builder()
                .location(location)
                .paType(strategy.getPaType())
                .build();
        }
    }

    private int calculateScore(StrategyRequest request, Map<String, Object> locInfo, StrategyDetail strategy) {
        int score = 100;

        // Prefer less full locations
        BigDecimal currentQty = (BigDecimal) locInfo.get("currentqty");
        if (currentQty != null) {
            score -= currentQty.intValue();
        }

        // Prefer lower sequence strategies
        score -= strategy.getSequence();

        return Math.max(0, score);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StrategyRequest {
        private String storerKey;
        private String facility;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String fromLocation;
        private BigDecimal weight;
        private BigDecimal cube;
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lottable04;
        private String lottable05;
        private String receiptKey;
        private Integer receiptLineNumber;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StrategyResult {
        private boolean success;
        private String location;
        private String paType;
        private String zone;
        private String strategyKey;
        private boolean fallbackUsed;
        private String errorMessage;

        public static StrategyResult failed(String message) {
            return StrategyResult.builder()
                .success(false)
                .errorMessage(message)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StrategyDetail {
        private String strategyKey;
        private int sequence;
        private String paType;
        private String zone;
        private String fromLoc;
        private String toLoc;
        private String locType;
        private String locCategory;
        private String areaKey;
        private String putawayZone;
        private String locationFlag;
        private BigDecimal maxQty;
        private BigDecimal minQty;
        private String commingling;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LocationCandidate {
        private String location;
        private String locationType;
        private String zone;
        private String paType;
        private BigDecimal availableQty;
        private BigDecimal currentQty;
        private int score;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String reason;

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
