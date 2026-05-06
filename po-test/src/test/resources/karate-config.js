function fn() {
  var env = karate.env; // get system property 'karate.env'
  karate.log('karate.env system property was:', env);

  if (!env) {
    env = 'local';
  }

  // ═══════════════════════════════════════════════════════════
  // Base Configuration
  // ═══════════════════════════════════════════════════════════
  var config = {
    env: env,
    baseUrl: 'http://localhost:8080',
    apiPath: '/api/v1',
    temporalNamespace: 'default',

    // Test data defaults
    testStorerKey: 'TEST_STORER_001',
    testFacility: 'TEST01',
    testUserId: 'test_user',

    // Timeouts
    connectTimeout: 5000,
    readTimeout: 30000,

    // Retry configuration
    retryCount: 3,
    retryInterval: 1000,

    // Database configuration (for Layer 1 dual-write validation)
    dbConfig: {
      url: 'jdbc:postgresql://localhost:5433/po_test',
      username: 'wms',
      password: 'wms123',
      driverClassName: 'org.postgresql.Driver'
    }
  };

  // ═══════════════════════════════════════════════════════════
  // Environment-Specific Configuration
  // ═══════════════════════════════════════════════════════════
  if (env == 'local') {
    config.baseUrl = 'http://localhost:8080';
    config.dbConfig.url = 'jdbc:postgresql://localhost:5433/po_test';
  } else if (env == 'docker') {
    config.baseUrl = 'http://po-api:8080';
    config.dbConfig.url = 'jdbc:postgresql://postgres:5432/po_test';
  } else if (env == 'staging') {
    config.baseUrl = 'https://po-modernization-staging.example.com';
    config.dbConfig.url = 'jdbc:postgresql://pg-staging.example.com:5432/po_staging';
  } else if (env == 'prod') {
    config.baseUrl = 'https://po-modernization.example.com';
    // No direct DB access in prod - API validation only
    config.dbConfig = null;
  }

  // ═══════════════════════════════════════════════════════════
  // Helper Functions
  // ═══════════════════════════════════════════════════════════

  // Sleep for specified milliseconds
  config.sleep = function(ms) {
    java.lang.Thread.sleep(ms);
  };

  // Generate UUID
  config.uuid = function() {
    return java.util.UUID.randomUUID() + '';
  };

  // Generate timestamp
  config.timestamp = function() {
    return java.lang.System.currentTimeMillis() + '';
  };

  // Generate unique PO key for testing
  config.generatePoKey = function() {
    return 'PO-TEST-' + java.lang.System.currentTimeMillis();
  };

  // Generate unique Receipt key for testing
  config.generateReceiptKey = function() {
    return 'RCV-TEST-' + java.lang.System.currentTimeMillis();
  };

  // Wait for async workflow completion (polling)
  config.waitForWorkflow = function(workflowId, maxWaitSeconds) {
    var maxWait = maxWaitSeconds || 60;
    var pollInterval = 2000; // 2 seconds
    var elapsed = 0;

    while (elapsed < maxWait * 1000) {
      java.lang.Thread.sleep(pollInterval);
      elapsed += pollInterval;
      // Caller should check workflow status via API
      karate.log('Waiting for workflow:', workflowId, 'Elapsed:', elapsed / 1000, 's');
    }
  };

  // ═══════════════════════════════════════════════════════════
  // Database Query Helper (for dual-write validation)
  // ═══════════════════════════════════════════════════════════
  config.db = {
    // Execute query and return results
    query: function(sql) {
      if (!config.dbConfig) {
        karate.log('DB validation disabled in', env, 'environment');
        return null;
      }
      var DbUtils = Java.type('com.wms.po.test.util.DbUtils');
      return DbUtils.query(config.dbConfig, sql);
    },

    // Execute query with parameters
    queryWithParams: function(sql, params) {
      if (!config.dbConfig) {
        return null;
      }
      var DbUtils = Java.type('com.wms.po.test.util.DbUtils');
      return DbUtils.queryWithParams(config.dbConfig, sql, params);
    },

    // Get single value
    getValue: function(sql) {
      var result = config.db.query(sql);
      if (result && result.length > 0) {
        var row = result[0];
        for (var key in row) {
          return row[key];
        }
      }
      return null;
    },

    // Check if record exists
    exists: function(table, whereClause) {
      var sql = "SELECT 1 FROM dbo." + table + " WHERE " + whereClause + " LIMIT 1";
      var result = config.db.query(sql);
      return result && result.length > 0;
    }
  };

  // ═══════════════════════════════════════════════════════════
  // Test Data Helpers
  // ═══════════════════════════════════════════════════════════
  config.testData = {
    // Valid PO creation request
    validPORequest: function(storerKey, facility) {
      return {
        storerKey: storerKey || config.testStorerKey,
        facility: facility || config.testFacility,
        externalOrderKey: 'EXT-' + config.uuid(),
        expectedDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        supplierName: 'Test Supplier',
        lines: [
          {
            sku: 'TEST-SKU-001',
            qtyOrdered: 100,
            uom: 'EA'
          }
        ]
      };
    },

    // Multi-line PO request
    multiLinePORequest: function(lineCount) {
      var lines = [];
      for (var i = 0; i < (lineCount || 5); i++) {
        lines.push({
          sku: 'TEST-SKU-00' + ((i % 3) + 1),
          qtyOrdered: 100 + (i * 10),
          uom: 'EA'
        });
      }
      return {
        storerKey: config.testStorerKey,
        facility: config.testFacility,
        externalOrderKey: 'EXT-MULTI-' + config.uuid(),
        expectedDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        supplierName: 'Multi-Line Supplier',
        lines: lines
      };
    }
  };

  karate.log('═══════════════════════════════════════════════════════════');
  karate.log('PO Modernization E2E Tests');
  karate.log('Environment:', env);
  karate.log('Base URL:', config.baseUrl);
  karate.log('DB Validation:', config.dbConfig ? 'Enabled' : 'Disabled');
  karate.log('═══════════════════════════════════════════════════════════');

  return config;
}
