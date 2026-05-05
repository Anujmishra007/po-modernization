package com.wms.po.legacy.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Client for interacting with legacy WMS stored procedures
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyWMSClient {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Call legacy stored procedure
     */
    public Map<String, Object> callStoredProcedure(String procedureName, Map<String, Object> params) {
        log.info("Calling legacy SP: {} with params: {}", procedureName, params.keySet());

        // Build parameter list
        StringBuilder paramList = new StringBuilder();
        for (String key : params.keySet()) {
            if (paramList.length() > 0) paramList.append(", ");
            paramList.append("@").append(key).append(" = ?");
        }

        String sql = String.format("EXEC %s %s", procedureName, paramList);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.values().toArray());

        if (results.isEmpty()) {
            return Map.of("success", true);
        }

        return results.get(0);
    }

    /**
     * Call V0 populate procedure
     */
    public String callV0Populate(String storerKey, List<String> poKeys, String facility, String userId) {
        log.info("Calling V0 populate for {} POs", poKeys.size());

        String sql = """
            DECLARE @ReceiptKey NVARCHAR(20)
            EXEC nsp_PopulateReceiptFromPO
                @StorerKey = ?,
                @POKeys = ?,
                @Facility = ?,
                @UserId = ?,
                @ReceiptKey = @ReceiptKey OUTPUT
            SELECT @ReceiptKey as ReceiptKey
            """;

        String poKeysStr = String.join(",", poKeys);
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, storerKey, poKeysStr, facility, userId);

        return (String) result.get("ReceiptKey");
    }

    /**
     * Call V2 populate procedure
     */
    public String callV2Populate(String storerKey, List<String> poKeys, String facility, String userId) {
        log.info("Calling V2 populate for {} POs", poKeys.size());

        String sql = """
            DECLARE @ReceiptKey NVARCHAR(20)
            EXEC lsp_PopulateReceiptFromPO
                @StorerKey = ?,
                @POKeys = ?,
                @Facility = ?,
                @UserId = ?,
                @ReceiptKey = @ReceiptKey OUTPUT
            SELECT @ReceiptKey as ReceiptKey
            """;

        String poKeysStr = String.join(",", poKeys);
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, storerKey, poKeysStr, facility, userId);

        return (String) result.get("ReceiptKey");
    }

    /**
     * Verify PO exists
     */
    public boolean poExists(String poKey) {
        String sql = "SELECT COUNT(*) FROM PO WHERE POKEY = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, poKey);
        return count != null && count > 0;
    }

    /**
     * Get PO status
     */
    public String getPOStatus(String poKey) {
        String sql = "SELECT STATUS FROM PO WHERE POKEY = ?";
        return jdbcTemplate.queryForObject(sql, String.class, poKey);
    }

    /**
     * Get receipt status
     */
    public String getReceiptStatus(String receiptKey) {
        String sql = "SELECT STATUS FROM RECEIPT WHERE RECEIPTKEY = ?";
        return jdbcTemplate.queryForObject(sql, String.class, receiptKey);
    }
}
