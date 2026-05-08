package com.wms.po.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.dto.POCreateRequest;
import com.wms.po.dto.POResponse;
import com.wms.po.variation.context.VariationResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Core PO service for business operations.
 *
 * Error codes:
 * - PO_001 (68800) - PO Not Found
 * - PO_008 (68808) - ASN Creation Failed
 * - PO_018 (68818) - PO Field Mapping Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POService {

    private final POValidationService validationService;
    private final POEnrichmentService enrichmentService;
    private final POPersistenceService persistenceService;
    private final VariationResolver variationResolver;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Create a new PO.
     *
     * Error codes:
     * - PO_019 (68819) - PO Validation Failed
     * - PO_018 (68818) - PO Field Mapping Failed
     * - PO_008 (68808) - ASN Creation Failed
     *
     * @param request PO creation request
     * @param userId User creating the PO
     * @return Created PO response
     * @throws BusinessException if creation fails
     */
    @Transactional
    public POResponse createPO(POCreateRequest request, String userId) {
        log.info("Creating PO for storer: {}, facility: {}, user: {}",
            request.getStorerKey(), request.getFacility(), userId);

        // Validate request
        if (request == null) {
            log.error("PO create request is null (legacy error 68808)");
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "PO create request is required")
                .withDetail("request", "null");
        }

        try {
            VariationContext context = variationResolver.resolve(
                request.getFacility(), request.getStorerKey());

            // Convert to domain model for validation
            PopulateRequest populateRequest = toPopulateRequest(request, userId);

            // Validate
            validationService.validateForCreate(populateRequest, context);

            // Enrich
            PopulateRequest enriched = enrichmentService.enrichRequest(populateRequest, context);

            // Persist and get the PO key
            String poKey = persistenceService.createPO(enriched, context);

            log.info("Created PO: {}", poKey);
            return getPO(poKey);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create PO for storer {}: {} (legacy error 68808)",
                request.getStorerKey(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to create PO: " + e.getMessage(), e)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility());
        }
    }

    /**
     * Get PO by key.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     */
    public POResponse getPO(String poKey) {
        log.debug("Getting PO: {}", poKey);

        try {
            String sql = """
                SELECT pokey, storerkey, externpokey, facility, supplierkey,
                       status, potype, expecteddate,
                       adddate, addwho, editdate, editwho
                FROM dbo.po WHERE pokey = ?
                """;

            List<POResponse> results = jdbcTemplate.query(sql, (rs, rowNum) ->
                POResponse.builder()
                    .poKey(rs.getString("pokey"))
                    .storerKey(rs.getString("storerkey"))
                    .externPoKey(rs.getString("externpokey"))
                    .facility(rs.getString("facility"))
                    .supplierKey(rs.getString("supplierkey"))
                    .status(rs.getString("status"))
                    .type(rs.getString("potype"))
                    .poDate(null)
                    .expectedReceiptDate(rs.getDate("expecteddate") != null ?
                        rs.getDate("expecteddate").toLocalDate() : null)
                    .addDate(rs.getTimestamp("adddate") != null ?
                        rs.getTimestamp("adddate").toLocalDateTime() : null)
                    .addWho(rs.getString("addwho"))
                    .editDate(rs.getTimestamp("editdate") != null ?
                        rs.getTimestamp("editdate").toLocalDateTime() : null)
                    .editWho(rs.getString("editwho"))
                    .build(),
                poKey);

            if (results.isEmpty()) {
                log.error("PO not found: {} (legacy error 68800)", poKey);
                throw BusinessException.poNotFound(poKey);
            }
            return results.get(0);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error getting PO {}: {} (legacy error 68800)", poKey, e.getMessage());
            throw BusinessException.poNotFound(poKey);
        }
    }

    /**
     * Get POs by storer and facility.
     *
     * Error codes:
     * - PO_008 (68808) - Database access failed
     *
     * @param storerKey Storer key (required)
     * @param facility Facility code (required)
     * @return List of POs matching criteria
     * @throws BusinessException if query fails
     */
    public List<POResponse> getPOsByStorerAndFacility(String storerKey, String facility) {
        log.debug("Getting POs for storer: {}, facility: {}", storerKey, facility);

        if (storerKey == null || storerKey.isBlank()) {
            log.error("Storer key is required for PO search (legacy error 68808)");
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Storer key is required for PO search")
                .withDetail("storerKey", "null or blank");
        }

        if (facility == null || facility.isBlank()) {
            log.error("Facility is required for PO search (legacy error 68808)");
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Facility is required for PO search")
                .withDetail("facility", "null or blank");
        }

        try {
            String sql = """
                SELECT pokey, storerkey, externpokey, facility, supplierkey,
                       status, potype, expecteddate,
                       adddate, addwho, editdate, editwho
                FROM dbo.po WHERE storerkey = ? AND facility = ?
                ORDER BY adddate DESC
                """;

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                POResponse.builder()
                    .poKey(rs.getString("pokey"))
                    .storerKey(rs.getString("storerkey"))
                    .externPoKey(rs.getString("externpokey"))
                    .facility(rs.getString("facility"))
                    .supplierKey(rs.getString("supplierkey"))
                    .status(rs.getString("status"))
                    .type(rs.getString("potype"))
                    .poDate(null)
                    .expectedReceiptDate(rs.getDate("expecteddate") != null ?
                        rs.getDate("expecteddate").toLocalDate() : null)
                    .addDate(rs.getTimestamp("adddate") != null ?
                        rs.getTimestamp("adddate").toLocalDateTime() : null)
                    .addWho(rs.getString("addwho"))
                    .editDate(rs.getTimestamp("editdate") != null ?
                        rs.getTimestamp("editdate").toLocalDateTime() : null)
                    .editWho(rs.getString("editwho"))
                    .build(),
                storerKey, facility);

        } catch (DataAccessException e) {
            log.error("Database error querying POs for storer {}, facility {}: {} (legacy error 68808)",
                storerKey, facility, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to query POs: " + e.getMessage(), e)
                .withDetail("storerKey", storerKey)
                .withDetail("facility", facility);
        }
    }

    /**
     * Update PO.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - PO_019 (68819) - PO Validation Failed
     * - PO_018 (68818) - PO Field Mapping Failed
     * - PO_008 (68808) - PO Update Failed
     *
     * @param poKey PO key to update
     * @param request Update request data
     * @param userId User performing the update
     * @return Updated PO response
     * @throws BusinessException if update fails
     */
    @Transactional
    public POResponse updatePO(String poKey, POCreateRequest request, String userId) {
        log.info("Updating PO: {}", poKey);

        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is required for update (legacy error 68800)");
            throw BusinessException.poNotFound("null or blank");
        }

        if (request == null) {
            log.error("PO update request is null (legacy error 68808)");
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "PO update request is required")
                .withDetail("poKey", poKey)
                .withDetail("request", "null");
        }

        try {
            VariationContext context = variationResolver.resolve(
                request.getFacility(), request.getStorerKey());

            PopulateRequest populateRequest = toPopulateRequest(request, userId);

            // Validate
            validationService.validateForUpdate(poKey, populateRequest, context);

            // Enrich
            PopulateRequest enriched = enrichmentService.enrichRequest(populateRequest, context);

            // Update
            persistenceService.updatePO(poKey, enriched, context);

            log.info("Updated PO: {}", poKey);
            return getPO(poKey);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update PO {}: {} (legacy error 68808)",
                poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to update PO: " + e.getMessage(), e)
                .withDetail("poKey", poKey)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility());
        }
    }

    /**
     * Delete PO.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - PO_019 (68819) - PO Validation Failed (cannot delete)
     * - PO_008 (68808) - PO Deletion Failed
     *
     * @param poKey PO key to delete
     * @throws BusinessException if deletion fails
     */
    @Transactional
    public void deletePO(String poKey) {
        log.info("Deleting PO: {}", poKey);

        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is required for delete (legacy error 68800)");
            throw BusinessException.poNotFound("null or blank");
        }

        try {
            // Get context for validation (will throw PO_001 if not found)
            POResponse po = getPO(poKey);
            VariationContext context = variationResolver.resolve(po.getFacility(), po.getStorerKey());

            // Validate can delete
            validationService.validateForDelete(poKey, context);

            // Delete details first
            int detailsDeleted = jdbcTemplate.update("DELETE FROM dbo.podetail WHERE pokey = ?", poKey);
            log.debug("Deleted {} detail records for PO: {}", detailsDeleted, poKey);

            // Delete header
            int headerDeleted = jdbcTemplate.update("DELETE FROM dbo.po WHERE pokey = ?", poKey);
            if (headerDeleted == 0) {
                log.error("PO header not found during delete: {} (legacy error 68800)", poKey);
                throw BusinessException.poNotFound(poKey);
            }

            log.info("Deleted PO: {} ({} details)", poKey, detailsDeleted);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error deleting PO {}: {} (legacy error 68808)",
                poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to delete PO: " + e.getMessage(), e)
                .withDetail("poKey", poKey);
        }
    }

    /**
     * Update PO status.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - PO_008 (68808) - Status Update Failed
     *
     * @param poKey PO key to update
     * @param status New status value
     * @param userId User performing the update
     * @throws BusinessException if status update fails
     */
    @Transactional
    public void updatePOStatus(String poKey, String status, String userId) {
        log.info("Updating PO {} status to {}", poKey, status);

        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is required for status update (legacy error 68800)");
            throw BusinessException.poNotFound("null or blank");
        }

        if (status == null || status.isBlank()) {
            log.error("Status is required for PO status update (legacy error 68808)");
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Status is required for PO status update")
                .withDetail("poKey", poKey)
                .withDetail("status", "null or blank");
        }

        try {
            String sql = """
                UPDATE dbo.po SET status = ?, editdate = CURRENT_TIMESTAMP, editwho = ?
                WHERE pokey = ?
                """;

            int rowsUpdated = jdbcTemplate.update(sql, status, userId, poKey);

            if (rowsUpdated == 0) {
                log.error("PO not found for status update: {} (legacy error 68800)", poKey);
                throw BusinessException.poNotFound(poKey);
            }

            log.info("Updated PO {} status to {}", poKey, status);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error updating PO {} status to {}: {} (legacy error 68808)",
                poKey, status, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to update PO status: " + e.getMessage(), e)
                .withDetail("poKey", poKey)
                .withDetail("status", status);
        }
    }

    private PopulateRequest toPopulateRequest(POCreateRequest request, String userId) {
        return PopulateRequest.builder()
            .poKeys(List.of()) // Will be generated
            .facility(request.getFacility())
            .storerKey(request.getStorerKey())
            .userId(userId)
            .build();
    }
}
