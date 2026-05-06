package com.wms.po.test.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Database utility class for Karate E2E tests.
 * Enables dual-write validation by querying the database directly.
 *
 * <p>Usage in Karate:
 * <pre>
 * * def result = db.query("SELECT * FROM dbo.orders WHERE orderkey = 'PO-001'")
 * * match result[0].status == '5'
 * </pre>
 */
public class DbUtils {

    /**
     * Execute a SQL query and return results as a list of maps.
     *
     * @param dbConfig Map containing url, username, password, driverClassName
     * @param sql      SQL query to execute
     * @return List of maps, each map representing a row
     */
    public static List<Map<String, Object>> query(Map<String, Object> dbConfig, String sql) {
        return queryWithParams(dbConfig, sql, new Object[]{});
    }

    /**
     * Execute a parameterized SQL query.
     *
     * @param dbConfig Map containing url, username, password, driverClassName
     * @param sql      SQL query with ? placeholders
     * @param params   Array of parameter values
     * @return List of maps, each map representing a row
     */
    public static List<Map<String, Object>> queryWithParams(
            Map<String, Object> dbConfig,
            String sql,
            Object[] params) {

        List<Map<String, Object>> results = new ArrayList<>();

        if (dbConfig == null) {
            return results;
        }

        String url = (String) dbConfig.get("url");
        String username = (String) dbConfig.get("username");
        String password = (String) dbConfig.get("password");
        String driverClassName = (String) dbConfig.get("driverClassName");

        try {
            // Load driver
            if (driverClassName != null) {
                Class.forName(driverClassName);
            }

            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Set parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = meta.getColumnLabel(i).toLowerCase();
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        results.add(row);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Database driver not found: " + driverClassName, e);
        } catch (SQLException e) {
            throw new RuntimeException("SQL error executing query: " + sql, e);
        }

        return results;
    }

    /**
     * Execute an update/insert/delete statement.
     *
     * @param dbConfig Map containing url, username, password, driverClassName
     * @param sql      SQL statement to execute
     * @return Number of affected rows
     */
    public static int execute(Map<String, Object> dbConfig, String sql) {
        return executeWithParams(dbConfig, sql, new Object[]{});
    }

    /**
     * Execute a parameterized update/insert/delete statement.
     *
     * @param dbConfig Map containing url, username, password, driverClassName
     * @param sql      SQL statement with ? placeholders
     * @param params   Array of parameter values
     * @return Number of affected rows
     */
    public static int executeWithParams(
            Map<String, Object> dbConfig,
            String sql,
            Object[] params) {

        if (dbConfig == null) {
            return 0;
        }

        String url = (String) dbConfig.get("url");
        String username = (String) dbConfig.get("username");
        String password = (String) dbConfig.get("password");
        String driverClassName = (String) dbConfig.get("driverClassName");

        try {
            if (driverClassName != null) {
                Class.forName(driverClassName);
            }

            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                return stmt.executeUpdate();
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Database driver not found: " + driverClassName, e);
        } catch (SQLException e) {
            throw new RuntimeException("SQL error executing statement: " + sql, e);
        }
    }

    /**
     * Check if a record exists.
     *
     * @param dbConfig    Map containing url, username, password, driverClassName
     * @param table       Table name (without schema)
     * @param whereClause WHERE clause without the keyword
     * @return true if record exists
     */
    public static boolean exists(Map<String, Object> dbConfig, String table, String whereClause) {
        String sql = "SELECT 1 FROM dbo." + table + " WHERE " + whereClause + " LIMIT 1";
        List<Map<String, Object>> result = query(dbConfig, sql);
        return !result.isEmpty();
    }

    /**
     * Get a single value from the database.
     *
     * @param dbConfig Map containing url, username, password, driverClassName
     * @param sql      SQL query that returns a single value
     * @return The value or null
     */
    public static Object getValue(Map<String, Object> dbConfig, String sql) {
        List<Map<String, Object>> result = query(dbConfig, sql);
        if (!result.isEmpty()) {
            Map<String, Object> row = result.get(0);
            if (!row.isEmpty()) {
                return row.values().iterator().next();
            }
        }
        return null;
    }

    /**
     * Count records in a table with optional where clause.
     *
     * @param dbConfig    Map containing url, username, password, driverClassName
     * @param table       Table name
     * @param whereClause WHERE clause without keyword (can be null)
     * @return Record count
     */
    public static long count(Map<String, Object> dbConfig, String table, String whereClause) {
        String sql = "SELECT COUNT(*) FROM dbo." + table;
        if (whereClause != null && !whereClause.isEmpty()) {
            sql += " WHERE " + whereClause;
        }
        Object value = getValue(dbConfig, sql);
        return value != null ? ((Number) value).longValue() : 0;
    }
}
