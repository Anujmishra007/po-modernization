package com.wms.po.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.PopulateResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * PO persistence service - handles database operations.
 *
 * Error codes:
 * - PO_001 (68800) - PO Not Found
 * - PO_018 (68818) - PO Field Mapping Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    /**
     * Create new PO in database.
     *
     * Error codes:
     * - PO_018 (68818) - PO Field Mapping Failed (database insert failure)
     */
    public String createPO(PopulateRequest request, VariationContext context) {
        String poKey = null;

        try {
            poKey = keyGeneratorService.generateKey("PO");
            log.info("Creating PO with key: {}", poKey);

            String sql = """
                INSERT INTO dbo.po (pokey, storerkey, facility, status, adddate, addwho, editdate, editwho)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
                """;

            jdbcTemplate.update(sql,
                poKey,
                request.getStorerKey(),
                request.getFacility(),
                "0", // Initial status
                request.getUserId(),
                request.getUserId()
            );

            return poKey;

        } catch (DataAccessException e) {
            log.error("Failed to create PO {}: {} (legacy error 68818)", poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Failed to create PO: " + e.getMessage(), e)
                .withDetail("poKey", poKey)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility());
        }
    }

    /**
     * Find PO by key.
     *
     * Error codes:
     * - PO_018 (68818) - Database query failed
     *
     * @param poKey PO key to find
     * @param context Variation context
     * @return Optional containing the PO result if found
     * @throws BusinessException if query fails
     */
    public Optional<PopulateResult> findByPoKey(String poKey, VariationContext context) {
        if (poKey == null || poKey.isBlank()) {
            log.debug("PO key is null or blank, returning empty result");
            return Optional.empty();
        }

        try {
            String sql = """
                SELECT pokey, storerkey, facility, status, adddate, editdate
                FROM dbo.po WHERE pokey = ?
                """;

            List<PopulateResult> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> PopulateResult.builder()
                    .receiptKey(rs.getString("pokey"))
                    .success(true)
                    .build(),
                poKey);

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

        } catch (DataAccessException e) {
            log.error("Database error finding PO {}: {} (legacy error 68818)",
                poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Failed to find PO: " + e.getMessage(), e)
                .withDetail("poKey", poKey);
        }
    }

    /**
     * Update PO.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - PO_018 (68818) - PO Field Mapping Failed (database update failure)
     */
    public void updatePO(String poKey, PopulateRequest request, VariationContext context) {
        log.info("Updating PO: {}", poKey);

        try {
            String sql = """
                UPDATE dbo.po SET storerkey = ?, facility = ?, editdate = CURRENT_TIMESTAMP, editwho = ?
                WHERE pokey = ?
                """;

            int updated = jdbcTemplate.update(sql,
                request.getStorerKey(),
                request.getFacility(),
                request.getUserId(),
                poKey
            );

            if (updated == 0) {
                log.error("PO not found for update: {} (legacy error 68800)", poKey);
                throw BusinessException.poNotFound(poKey);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to update PO {}: {} (legacy error 68818)", poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Failed to update PO: " + e.getMessage(), e)
                .withDetail("poKey", poKey)
                .withDetail("storerKey", request.getStorerKey());
        }
    }

    /**
     * Delete PO.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     */
    public void deletePO(String poKey, VariationContext context) {
        log.info("Deleting PO: {}", poKey);

        try {
            String sql = "DELETE FROM dbo.po WHERE pokey = ?";
            int deleted = jdbcTemplate.update(sql, poKey);

            if (deleted == 0) {
                log.error("PO not found for delete: {} (legacy error 68800)", poKey);
                throw BusinessException.poNotFound(poKey);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to delete PO {}: {} (legacy error 68800)", poKey, e.getMessage(), e);
            throw BusinessException.poNotFound(poKey);
        }
    }

    /**
     * Search POs.
     *
     * Error codes:
     * - PO_018 (68818) - Database query failed
     *
     * @param criteria Search criteria
     * @param context Variation context
     * @return List of matching PO results
     * @throws BusinessException if search fails
     */
    public List<PopulateResult> search(POSearchCriteria criteria, VariationContext context) {
        log.debug("Searching POs with criteria");

        if (criteria == null) {
            log.error("Search criteria is null (legacy error 68818)");
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Search criteria is required")
                .withDetail("criteria", "null");
        }

        try {
            StringBuilder sql = new StringBuilder("""
                SELECT pokey, storerkey, facility, status, adddate, editdate
                FROM dbo.po WHERE 1=1
                """);

            if (criteria.getStorerKey() != null) {
                sql.append(" AND storerkey = '").append(criteria.getStorerKey()).append("'");
            }
            if (criteria.getFacility() != null) {
                sql.append(" AND facility = '").append(criteria.getFacility()).append("'");
            }
            if (criteria.getStatus() != null) {
                sql.append(" AND status = '").append(criteria.getStatus()).append("'");
            }

            return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> PopulateResult.builder()
                    .receiptKey(rs.getString("pokey"))
                    .success(true)
                    .build());

        } catch (DataAccessException e) {
            log.error("Database error searching POs: {} (legacy error 68818)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Failed to search POs: " + e.getMessage(), e)
                .withDetail("storerKey", criteria.getStorerKey())
                .withDetail("facility", criteria.getFacility())
                .withDetail("status", criteria.getStatus());
        }
    }

    /**
     * Find POs by storer.
     *
     * Error codes:
     * - PO_018 (68818) - Database query failed
     *
     * @param storerKey Storer key to search for
     * @param context Variation context
     * @return List of PO results for the storer
     * @throws BusinessException if query fails
     */
    public List<PopulateResult> findByStorerKey(String storerKey, VariationContext context) {
        if (storerKey == null || storerKey.isBlank()) {
            log.error("Storer key is required for PO search (legacy error 68818)");
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Storer key is required for PO search")
                .withDetail("storerKey", "null or blank");
        }

        try {
            String sql = """
                SELECT pokey, storerkey, facility, status FROM dbo.po WHERE storerkey = ?
                """;

            return jdbcTemplate.query(sql,
                (rs, rowNum) -> PopulateResult.builder()
                    .receiptKey(rs.getString("pokey"))
                    .success(true)
                    .build(),
                storerKey);

        } catch (DataAccessException e) {
            log.error("Database error finding POs for storer {}: {} (legacy error 68818)",
                storerKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Failed to find POs for storer: " + e.getMessage(), e)
                .withDetail("storerKey", storerKey);
        }
    }

    /**
     * Find POs ready for population.
     *
     * Error codes:
     * - PO_018 (68818) - Database query failed
     *
     * @param facility Facility to search in
     * @param context Variation context
     * @return List of POs in status 0 (ready for population)
     * @throws BusinessException if query fails
     */
    public List<PopulateResult> findReadyForPopulation(String facility, VariationContext context) {
        if (facility == null || facility.isBlank()) {
            log.error("Facility is required for finding POs ready for population (legacy error 68818)");
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Facility is required for finding POs ready for population")
                .withDetail("facility", "null or blank");
        }

        try {
            String sql = """
                SELECT pokey, storerkey, facility, status FROM dbo.po
                WHERE facility = ? AND status = '0'
                """;

            return jdbcTemplate.query(sql,
                (rs, rowNum) -> PopulateResult.builder()
                    .receiptKey(rs.getString("pokey"))
                    .success(true)
                    .build(),
                facility);

        } catch (DataAccessException e) {
            log.error("Database error finding POs ready for population in facility {}: {} (legacy error 68818)",
                facility, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PO_MAPPING_FAILED,
                "Failed to find POs ready for population: " + e.getMessage(), e)
                .withDetail("facility", facility);
        }
    }
}
