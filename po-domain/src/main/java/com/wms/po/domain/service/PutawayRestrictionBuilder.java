package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Putaway Restriction Builder.
 *
 * Replaces SQL function: FN-020 fnc_BuildPutawayRestriction (740 LOC)
 *
 * Builds location restriction criteria for putaway strategy based on:
 * - Location type/flag/category/handling
 * - Dimension restrictions (cube, length, width, height, weight)
 * - Quantity capacity
 * - Pallet capacity
 * - Stack factor
 * - Lot/SKU commingling rules
 * - Area exclusions
 * - Special location types (DRIVEIN, DOUBLEDEEP)
 *
 * Used by PutawayStrategyService to filter candidate locations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PutawayRestrictionBuilder {

    private final JdbcTemplate jdbcTemplate;

    // Restriction type constants
    public static final String RESTRICT_LOC_TYPE = "LOCTYPE";
    public static final String RESTRICT_LOC_FLAG = "LOCFLAG";
    public static final String RESTRICT_LOC_CATEGORY = "LOCCATEGORY";
    public static final String RESTRICT_HANDLING = "HANDLING";
    public static final String RESTRICT_ZONE = "ZONE";
    public static final String RESTRICT_AREA = "AREA";
    public static final String RESTRICT_DIMENSION = "DIMENSION";
    public static final String RESTRICT_WEIGHT = "WEIGHT";
    public static final String RESTRICT_CUBE = "CUBE";
    public static final String RESTRICT_QTY = "QTY";
    public static final String RESTRICT_PALLET = "PALLET";
    public static final String RESTRICT_COMMINGLING = "COMMINGLING";

    /**
     * Build restrictions for a putaway request.
     *
     * @param request The putaway request with SKU and quantity details
     * @return RestrictionSet containing all applicable restrictions
     */
    public RestrictionSet buildRestrictions(RestrictionRequest request) {
        log.debug("Building putaway restrictions for SKU={}, qty={}",
            request.getSku(), request.getQuantity());

        RestrictionSet restrictions = new RestrictionSet();

        // 1. Get SKU attributes for restriction building
        SkuAttributes skuAttrs = getSkuAttributes(request.getStorerKey(), request.getSku());

        // 2. Build location type restrictions
        buildLocationTypeRestrictions(restrictions, request, skuAttrs);

        // 3. Build dimension restrictions
        buildDimensionRestrictions(restrictions, request, skuAttrs);

        // 4. Build capacity restrictions
        buildCapacityRestrictions(restrictions, request, skuAttrs);

        // 5. Build commingling restrictions
        buildComminglingRestrictions(restrictions, request, skuAttrs);

        // 6. Build zone/area restrictions
        buildZoneAreaRestrictions(restrictions, request, skuAttrs);

        // 7. Build special location restrictions
        buildSpecialRestrictions(restrictions, request, skuAttrs);

        log.debug("Built {} restrictions for SKU={}", restrictions.getRestrictions().size(), request.getSku());

        return restrictions;
    }

    /**
     * Convert restrictions to SQL WHERE clause criteria.
     *
     * @param restrictions The restriction set
     * @return SQL criteria object with WHERE clause and parameters
     */
    public SqlCriteria toSqlCriteria(RestrictionSet restrictions) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (Restriction r : restrictions.getRestrictions()) {
            String clause = buildSqlClause(r, params);
            if (clause != null && !clause.isEmpty()) {
                if (where.length() > 0) {
                    where.append(" AND ");
                }
                where.append(clause);
            }
        }

        return new SqlCriteria(where.toString(), params);
    }

    /**
     * Check if a specific location meets all restrictions.
     *
     * @param location The location to check
     * @param facility The facility
     * @param restrictions The restrictions to apply
     * @return ValidationResult indicating if location is valid
     */
    public ValidationResult validateLocation(String location, String facility, RestrictionSet restrictions) {
        try {
            // Get location details
            Map<String, Object> locInfo = jdbcTemplate.queryForMap(
                """
                SELECT loc, loctype, locationflag, loccategory, handlingtype,
                       putawayzone, areakey, status,
                       qtycapacity, currentqty, maxweight, currentweight,
                       maxcube, currentcube, maxpallets, currentpallets,
                       loclength, locwidth, locheight, stackfactor,
                       commingle, skucommingle
                FROM dbo.loc
                WHERE loc = ? AND facility = ?
                """,
                location, facility
            );

            // Check each restriction
            for (Restriction r : restrictions.getRestrictions()) {
                if (!checkRestriction(r, locInfo)) {
                    return ValidationResult.invalid(r.getType(), r.getDescription());
                }
            }

            return ValidationResult.valid();

        } catch (Exception e) {
            return ValidationResult.invalid("LOCATION", "Location not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Restriction Building
    // ═══════════════════════════════════════════════════════════════════════

    private void buildLocationTypeRestrictions(RestrictionSet restrictions,
                                                RestrictionRequest request,
                                                SkuAttributes skuAttrs) {
        // Location type restriction
        if (request.getLocationTypes() != null && !request.getLocationTypes().isEmpty()) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_LOC_TYPE)
                .field("loctype")
                .operator("IN")
                .values(request.getLocationTypes())
                .description("Location type must be: " + String.join(", ", request.getLocationTypes()))
                .build());
        }

        // Location flag restriction
        if (request.getLocationFlag() != null) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_LOC_FLAG)
                .field("locationflag")
                .operator("=")
                .value(request.getLocationFlag())
                .description("Location flag must be: " + request.getLocationFlag())
                .build());
        }

        // Location category restriction
        if (request.getLocationCategory() != null) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_LOC_CATEGORY)
                .field("loccategory")
                .operator("=")
                .value(request.getLocationCategory())
                .description("Location category must be: " + request.getLocationCategory())
                .build());
        }

        // Handling type from SKU
        if (skuAttrs != null && skuAttrs.getHandlingType() != null) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_HANDLING)
                .field("handlingtype")
                .operator("=")
                .value(skuAttrs.getHandlingType())
                .description("Handling type must match SKU: " + skuAttrs.getHandlingType())
                .build());
        }
    }

    private void buildDimensionRestrictions(RestrictionSet restrictions,
                                             RestrictionRequest request,
                                             SkuAttributes skuAttrs) {
        // Length restriction
        if (request.getLength() != null && request.getLength().compareTo(BigDecimal.ZERO) > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_DIMENSION)
                .field("loclength")
                .operator(">=")
                .numericValue(request.getLength())
                .description("Location length must be >= " + request.getLength())
                .build());
        }

        // Width restriction
        if (request.getWidth() != null && request.getWidth().compareTo(BigDecimal.ZERO) > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_DIMENSION)
                .field("locwidth")
                .operator(">=")
                .numericValue(request.getWidth())
                .description("Location width must be >= " + request.getWidth())
                .build());
        }

        // Height restriction
        if (request.getHeight() != null && request.getHeight().compareTo(BigDecimal.ZERO) > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_DIMENSION)
                .field("locheight")
                .operator(">=")
                .numericValue(request.getHeight())
                .description("Location height must be >= " + request.getHeight())
                .build());
        }
    }

    private void buildCapacityRestrictions(RestrictionSet restrictions,
                                            RestrictionRequest request,
                                            SkuAttributes skuAttrs) {
        // Weight capacity restriction
        if (request.getWeight() != null && request.getWeight().compareTo(BigDecimal.ZERO) > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_WEIGHT)
                .field("maxweight")
                .operator("CAPACITY")
                .numericValue(request.getWeight())
                .capacityField("currentweight")
                .description("Available weight capacity must be >= " + request.getWeight())
                .build());
        }

        // Cube capacity restriction
        if (request.getCube() != null && request.getCube().compareTo(BigDecimal.ZERO) > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_CUBE)
                .field("maxcube")
                .operator("CAPACITY")
                .numericValue(request.getCube())
                .capacityField("currentcube")
                .description("Available cube capacity must be >= " + request.getCube())
                .build());
        }

        // Quantity capacity restriction
        if (request.getQuantity() != null && request.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_QTY)
                .field("qtycapacity")
                .operator("CAPACITY")
                .numericValue(request.getQuantity())
                .capacityField("currentqty")
                .description("Available quantity capacity must be >= " + request.getQuantity())
                .build());
        }

        // Pallet capacity restriction
        if (request.getPalletCount() != null && request.getPalletCount() > 0) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_PALLET)
                .field("maxpallets")
                .operator("PALLET_CAPACITY")
                .numericValue(BigDecimal.valueOf(request.getPalletCount()))
                .capacityField("currentpallets")
                .description("Available pallet slots must be >= " + request.getPalletCount())
                .build());
        }
    }

    private void buildComminglingRestrictions(RestrictionSet restrictions,
                                               RestrictionRequest request,
                                               SkuAttributes skuAttrs) {
        // Check if commingling is allowed
        boolean allowCommingling = request.isAllowCommingling();

        if (!allowCommingling) {
            // No commingling - location must be empty or have same SKU
            restrictions.add(Restriction.builder()
                .type(RESTRICT_COMMINGLING)
                .field("SKU_EXCLUSIVE")
                .operator("SKU_MATCH")
                .value(request.getSku())
                .storerKey(request.getStorerKey())
                .description("Location must be empty or contain only SKU: " + request.getSku())
                .build());
        }

        // Check storer commingling
        if (!request.isAllowStorerCommingling()) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_COMMINGLING)
                .field("STORER_EXCLUSIVE")
                .operator("STORER_MATCH")
                .storerKey(request.getStorerKey())
                .description("Location must be empty or contain only storer: " + request.getStorerKey())
                .build());
        }

        // Check lot commingling
        if (!request.isAllowLotCommingling() && request.getLottable01() != null) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_COMMINGLING)
                .field("LOT_EXCLUSIVE")
                .operator("LOT_MATCH")
                .value(request.getLottable01())
                .storerKey(request.getStorerKey())
                .description("Location must have matching lot: " + request.getLottable01())
                .build());
        }
    }

    private void buildZoneAreaRestrictions(RestrictionSet restrictions,
                                            RestrictionRequest request,
                                            SkuAttributes skuAttrs) {
        // Zone restriction
        if (request.getZone() != null && !request.getZone().isEmpty()) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_ZONE)
                .field("putawayzone")
                .operator("=")
                .value(request.getZone())
                .description("Location must be in zone: " + request.getZone())
                .build());
        } else if (skuAttrs != null && skuAttrs.getPutawayZone() != null) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_ZONE)
                .field("putawayzone")
                .operator("=")
                .value(skuAttrs.getPutawayZone())
                .description("Location must be in SKU's putaway zone: " + skuAttrs.getPutawayZone())
                .build());
        }

        // Area exclusions
        if (request.getExcludedAreas() != null && !request.getExcludedAreas().isEmpty()) {
            restrictions.add(Restriction.builder()
                .type(RESTRICT_AREA)
                .field("areakey")
                .operator("NOT IN")
                .values(request.getExcludedAreas())
                .description("Location must not be in areas: " + String.join(", ", request.getExcludedAreas()))
                .build());
        }
    }

    private void buildSpecialRestrictions(RestrictionSet restrictions,
                                           RestrictionRequest request,
                                           SkuAttributes skuAttrs) {
        // Active status required
        restrictions.add(Restriction.builder()
            .type("STATUS")
            .field("status")
            .operator("=")
            .value("1")
            .description("Location must be active")
            .build());

        // Drive-in location restriction
        if (request.isDriveInAllowed()) {
            // Include DRIVEIN locations - no additional restriction needed
        } else {
            restrictions.add(Restriction.builder()
                .type("SPECIAL")
                .field("loctype")
                .operator("!=")
                .value("DRIVEIN")
                .description("Drive-in locations not allowed")
                .build());
        }

        // Double-deep location restriction
        if (!request.isDoubleDeepAllowed()) {
            restrictions.add(Restriction.builder()
                .type("SPECIAL")
                .field("loctype")
                .operator("!=")
                .value("DOUBLEDEEP")
                .description("Double-deep locations not allowed")
                .build());
        }

        // Stack factor check
        if (skuAttrs != null && skuAttrs.getMaxStackFactor() != null) {
            restrictions.add(Restriction.builder()
                .type("SPECIAL")
                .field("stackfactor")
                .operator(">=")
                .numericValue(skuAttrs.getMaxStackFactor())
                .description("Stack factor must be >= " + skuAttrs.getMaxStackFactor())
                .build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SQL Generation
    // ═══════════════════════════════════════════════════════════════════════

    private String buildSqlClause(Restriction r, List<Object> params) {
        String field = "l." + r.getField();

        return switch (r.getOperator()) {
            case "=" -> {
                params.add(r.getValue());
                yield field + " = ?";
            }
            case "!=" -> {
                params.add(r.getValue());
                yield field + " != ?";
            }
            case ">=" -> {
                params.add(r.getNumericValue());
                yield field + " >= ?";
            }
            case "<=" -> {
                params.add(r.getNumericValue());
                yield field + " <= ?";
            }
            case "IN" -> {
                String placeholders = String.join(", ",
                    Collections.nCopies(r.getValues().size(), "?"));
                params.addAll(r.getValues());
                yield field + " IN (" + placeholders + ")";
            }
            case "NOT IN" -> {
                String placeholders = String.join(", ",
                    Collections.nCopies(r.getValues().size(), "?"));
                params.addAll(r.getValues());
                yield field + " NOT IN (" + placeholders + ")";
            }
            case "CAPACITY" -> {
                params.add(r.getNumericValue());
                yield "(" + field + " - COALESCE(l." + r.getCapacityField() + ", 0)) >= ?";
            }
            case "PALLET_CAPACITY" -> {
                params.add(r.getNumericValue().intValue());
                yield "(l.maxpallets - COALESCE(l.currentpallets, 0)) >= ?";
            }
            case "SKU_MATCH" -> {
                params.add(r.getStorerKey());
                params.add(r.getValue());
                yield """
                    (l.currentqty = 0 OR NOT EXISTS (
                        SELECT 1 FROM dbo.lotxlocxid inv
                        WHERE inv.loc = l.loc
                        AND (inv.storerkey != ? OR inv.sku != ?)
                        AND inv.qty > 0
                    ))
                    """;
            }
            case "STORER_MATCH" -> {
                params.add(r.getStorerKey());
                yield """
                    (l.currentqty = 0 OR NOT EXISTS (
                        SELECT 1 FROM dbo.lotxlocxid inv
                        WHERE inv.loc = l.loc AND inv.storerkey != ? AND inv.qty > 0
                    ))
                    """;
            }
            case "LOT_MATCH" -> {
                params.add(r.getStorerKey());
                params.add(r.getValue());
                yield """
                    (l.currentqty = 0 OR NOT EXISTS (
                        SELECT 1 FROM dbo.lotxlocxid inv
                        WHERE inv.loc = l.loc AND inv.storerkey = ?
                        AND inv.lottable01 != ? AND inv.qty > 0
                    ))
                    """;
            }
            default -> null;
        };
    }

    private boolean checkRestriction(Restriction r, Map<String, Object> locInfo) {
        Object fieldValue = locInfo.get(r.getField().toLowerCase());

        return switch (r.getOperator()) {
            case "=" -> r.getValue().equals(fieldValue);
            case "!=" -> !r.getValue().equals(fieldValue);
            case ">=" -> compareNumeric(fieldValue, r.getNumericValue()) >= 0;
            case "<=" -> compareNumeric(fieldValue, r.getNumericValue()) <= 0;
            case "IN" -> r.getValues().contains(fieldValue);
            case "NOT IN" -> !r.getValues().contains(fieldValue);
            case "CAPACITY" -> {
                BigDecimal max = toBigDecimal(locInfo.get(r.getField().toLowerCase()));
                BigDecimal current = toBigDecimal(locInfo.get(r.getCapacityField().toLowerCase()));
                yield max != null && current != null &&
                    max.subtract(current).compareTo(r.getNumericValue()) >= 0;
            }
            default -> true; // Complex checks handled separately
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private SkuAttributes getSkuAttributes(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT putawayzone, handlingtype, putawayloc, hazmatcode,
                       stdgrosswgt, stdcube, pallethi, palletty
                FROM dbo.sku
                WHERE storerkey = ? AND sku = ?
                """,
                (rs, rowNum) -> SkuAttributes.builder()
                    .putawayZone(rs.getString("putawayzone"))
                    .handlingType(rs.getString("handlingtype"))
                    .putawayLoc(rs.getString("putawayloc"))
                    .hazmatCode(rs.getString("hazmatcode"))
                    .unitWeight(rs.getBigDecimal("stdgrosswgt"))
                    .unitCube(rs.getBigDecimal("stdcube"))
                    .palletHi(rs.getInt("pallethi"))
                    .palletTi(rs.getInt("palletty"))
                    .build(),
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int compareNumeric(Object a, BigDecimal b) {
        BigDecimal aVal = toBigDecimal(a);
        if (aVal == null || b == null) return 0;
        return aVal.compareTo(b);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RestrictionRequest {
        private String storerKey;
        private String sku;
        private BigDecimal quantity;
        private BigDecimal weight;
        private BigDecimal cube;
        private BigDecimal length;
        private BigDecimal width;
        private BigDecimal height;
        private Integer palletCount;
        private String zone;
        private List<String> locationTypes;
        private String locationFlag;
        private String locationCategory;
        private List<String> excludedAreas;
        private String lottable01;
        private String lottable02;

        @lombok.Builder.Default
        private boolean allowCommingling = true;
        @lombok.Builder.Default
        private boolean allowStorerCommingling = true;
        @lombok.Builder.Default
        private boolean allowLotCommingling = true;
        @lombok.Builder.Default
        private boolean driveInAllowed = false;
        @lombok.Builder.Default
        private boolean doubleDeepAllowed = true;
    }

    @lombok.Data
    public static class RestrictionSet {
        private final List<Restriction> restrictions = new ArrayList<>();

        public void add(Restriction restriction) {
            restrictions.add(restriction);
        }

        public boolean isEmpty() {
            return restrictions.isEmpty();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Restriction {
        private String type;
        private String field;
        private String operator;
        private String value;
        private List<String> values;
        private BigDecimal numericValue;
        private String capacityField;
        private String storerKey;
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SkuAttributes {
        private String putawayZone;
        private String handlingType;
        private String putawayLoc;
        private String hazmatCode;
        private BigDecimal unitWeight;
        private BigDecimal unitCube;
        private int palletHi;
        private int palletTi;
        private BigDecimal maxStackFactor;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SqlCriteria {
        private String whereClause;
        private List<Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String failedRestriction;
        private String reason;

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(String restriction, String reason) {
            return new ValidationResult(false, restriction, reason);
        }
    }
}
