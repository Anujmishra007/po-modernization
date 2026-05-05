package com.wms.po.service;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.PopulateResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * PO persistence service - handles database operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    /**
     * Create new PO in database
     */
    public String createPO(PopulateRequest request, VariationContext context) {
        String poKey = keyGeneratorService.generateKey("PO");
        log.info("Creating PO with key: {}", poKey);

        String sql = """
            INSERT INTO ORDERS (POKEY, STORERKEY, FACILITY, STATUS, ADDDATE, ADDWHO, EDITDATE, EDITWHO)
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
    }

    /**
     * Find PO by key
     */
    public Optional<PopulateResult> findByPoKey(String poKey, VariationContext context) {
        String sql = """
            SELECT POKEY, STORERKEY, FACILITY, STATUS, ADDDATE, EDITDATE
            FROM ORDERS WHERE POKEY = ?
            """;

        List<PopulateResult> results = jdbcTemplate.query(sql,
            (rs, rowNum) -> PopulateResult.builder()
                .receiptKey(rs.getString("POKEY"))
                .success(true)
                .build(),
            poKey);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Update PO
     */
    public void updatePO(String poKey, PopulateRequest request, VariationContext context) {
        log.info("Updating PO: {}", poKey);

        String sql = """
            UPDATE ORDERS SET STORERKEY = ?, FACILITY = ?, EDITDATE = CURRENT_TIMESTAMP, EDITWHO = ?
            WHERE POKEY = ?
            """;

        int updated = jdbcTemplate.update(sql,
            request.getStorerKey(),
            request.getFacility(),
            request.getUserId(),
            poKey
        );

        if (updated == 0) {
            throw new RuntimeException("PO not found: " + poKey);
        }
    }

    /**
     * Delete PO
     */
    public void deletePO(String poKey, VariationContext context) {
        log.info("Deleting PO: {}", poKey);

        String sql = "DELETE FROM ORDERS WHERE POKEY = ?";
        int deleted = jdbcTemplate.update(sql, poKey);

        if (deleted == 0) {
            throw new RuntimeException("PO not found: " + poKey);
        }
    }

    /**
     * Search POs
     */
    public List<PopulateResult> search(POSearchCriteria criteria, VariationContext context) {
        log.debug("Searching POs with criteria");

        StringBuilder sql = new StringBuilder("""
            SELECT POKEY, STORERKEY, FACILITY, STATUS, ADDDATE, EDITDATE
            FROM ORDERS WHERE 1=1
            """);

        if (criteria.getStorerKey() != null) {
            sql.append(" AND STORERKEY = '").append(criteria.getStorerKey()).append("'");
        }
        if (criteria.getFacility() != null) {
            sql.append(" AND FACILITY = '").append(criteria.getFacility()).append("'");
        }
        if (criteria.getStatus() != null) {
            sql.append(" AND STATUS = '").append(criteria.getStatus()).append("'");
        }

        return jdbcTemplate.query(sql.toString(),
            (rs, rowNum) -> PopulateResult.builder()
                .receiptKey(rs.getString("POKEY"))
                .success(true)
                .build());
    }

    /**
     * Find POs by storer
     */
    public List<PopulateResult> findByStorerKey(String storerKey, VariationContext context) {
        String sql = """
            SELECT POKEY, STORERKEY, FACILITY, STATUS FROM ORDERS WHERE STORERKEY = ?
            """;

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> PopulateResult.builder()
                .receiptKey(rs.getString("POKEY"))
                .success(true)
                .build(),
            storerKey);
    }

    /**
     * Find POs ready for population
     */
    public List<PopulateResult> findReadyForPopulation(String facility, VariationContext context) {
        String sql = """
            SELECT POKEY, STORERKEY, FACILITY, STATUS FROM ORDERS
            WHERE FACILITY = ? AND STATUS = '0'
            """;

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> PopulateResult.builder()
                .receiptKey(rs.getString("POKEY"))
                .success(true)
                .build(),
            facility);
    }
}
