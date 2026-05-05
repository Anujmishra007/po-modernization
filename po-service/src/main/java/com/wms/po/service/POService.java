package com.wms.po.service;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.dto.POCreateRequest;
import com.wms.po.dto.POResponse;
import com.wms.po.variation.context.VariationResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Core PO service for business operations
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
     * Create a new PO
     */
    @Transactional
    public POResponse createPO(POCreateRequest request, String userId) {
        log.info("Creating PO for storer: {}, facility: {}, user: {}",
            request.getStorerKey(), request.getFacility(), userId);

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
    }

    /**
     * Get PO by key
     */
    public POResponse getPO(String poKey) {
        log.debug("Getting PO: {}", poKey);

        String sql = """
            SELECT POKEY, STORERKEY, EXTERNPOKEY, FACILITY, SUPPLIERKEY,
                   STATUS, TYPE, PODATE, EXPECTEDRECEIPTDATE,
                   ADDDATE, ADDWHO, EDITDATE, EDITWHO
            FROM ORDERS WHERE POKEY = ?
            """;

        List<POResponse> results = jdbcTemplate.query(sql, (rs, rowNum) ->
            POResponse.builder()
                .poKey(rs.getString("POKEY"))
                .storerKey(rs.getString("STORERKEY"))
                .externPoKey(rs.getString("EXTERNPOKEY"))
                .facility(rs.getString("FACILITY"))
                .supplierKey(rs.getString("SUPPLIERKEY"))
                .status(rs.getString("STATUS"))
                .type(rs.getString("TYPE"))
                .poDate(rs.getDate("PODATE") != null ?
                    rs.getDate("PODATE").toLocalDate() : null)
                .expectedReceiptDate(rs.getDate("EXPECTEDRECEIPTDATE") != null ?
                    rs.getDate("EXPECTEDRECEIPTDATE").toLocalDate() : null)
                .addDate(rs.getTimestamp("ADDDATE") != null ?
                    rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                .addWho(rs.getString("ADDWHO"))
                .editDate(rs.getTimestamp("EDITDATE") != null ?
                    rs.getTimestamp("EDITDATE").toLocalDateTime() : null)
                .editWho(rs.getString("EDITWHO"))
                .build(),
            poKey);

        if (results.isEmpty()) {
            throw new RuntimeException("PO not found: " + poKey);
        }
        return results.get(0);
    }

    /**
     * Get POs by storer and facility
     */
    public List<POResponse> getPOsByStorerAndFacility(String storerKey, String facility) {
        log.debug("Getting POs for storer: {}, facility: {}", storerKey, facility);

        String sql = """
            SELECT POKEY, STORERKEY, EXTERNPOKEY, FACILITY, SUPPLIERKEY,
                   STATUS, TYPE, PODATE, EXPECTEDRECEIPTDATE,
                   ADDDATE, ADDWHO, EDITDATE, EDITWHO
            FROM ORDERS WHERE STORERKEY = ? AND FACILITY = ?
            ORDER BY ADDDATE DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
            POResponse.builder()
                .poKey(rs.getString("POKEY"))
                .storerKey(rs.getString("STORERKEY"))
                .externPoKey(rs.getString("EXTERNPOKEY"))
                .facility(rs.getString("FACILITY"))
                .supplierKey(rs.getString("SUPPLIERKEY"))
                .status(rs.getString("STATUS"))
                .type(rs.getString("TYPE"))
                .poDate(rs.getDate("PODATE") != null ?
                    rs.getDate("PODATE").toLocalDate() : null)
                .expectedReceiptDate(rs.getDate("EXPECTEDRECEIPTDATE") != null ?
                    rs.getDate("EXPECTEDRECEIPTDATE").toLocalDate() : null)
                .addDate(rs.getTimestamp("ADDDATE") != null ?
                    rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                .addWho(rs.getString("ADDWHO"))
                .editDate(rs.getTimestamp("EDITDATE") != null ?
                    rs.getTimestamp("EDITDATE").toLocalDateTime() : null)
                .editWho(rs.getString("EDITWHO"))
                .build(),
            storerKey, facility);
    }

    /**
     * Update PO
     */
    @Transactional
    public POResponse updatePO(String poKey, POCreateRequest request, String userId) {
        log.info("Updating PO: {}", poKey);

        VariationContext context = variationResolver.resolve(
            request.getFacility(), request.getStorerKey());

        PopulateRequest populateRequest = toPopulateRequest(request, userId);

        // Validate
        validationService.validateForUpdate(poKey, populateRequest, context);

        // Enrich
        PopulateRequest enriched = enrichmentService.enrichRequest(populateRequest, context);

        // Update
        persistenceService.updatePO(poKey, enriched, context);

        return getPO(poKey);
    }

    /**
     * Delete PO
     */
    @Transactional
    public void deletePO(String poKey) {
        log.info("Deleting PO: {}", poKey);

        // Get context for validation
        POResponse po = getPO(poKey);
        VariationContext context = variationResolver.resolve(po.getFacility(), po.getStorerKey());

        // Validate can delete
        validationService.validateForDelete(poKey, context);

        // Delete details first
        jdbcTemplate.update("DELETE FROM ORDERDETAIL WHERE POKEY = ?", poKey);

        // Delete header
        jdbcTemplate.update("DELETE FROM ORDERS WHERE POKEY = ?", poKey);
    }

    /**
     * Update PO status
     */
    @Transactional
    public void updatePOStatus(String poKey, String status, String userId) {
        log.info("Updating PO {} status to {}", poKey, status);

        String sql = """
            UPDATE ORDERS SET STATUS = ?, EDITDATE = GETDATE(), EDITWHO = ?
            WHERE POKEY = ?
            """;

        jdbcTemplate.update(sql, status, userId, poKey);
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
