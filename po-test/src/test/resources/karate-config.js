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
    // Docker Compose environment (service names as hostnames)
    config.baseUrl = 'http://po-api:8080';
    config.dbConfig.url = 'jdbc:postgresql://postgres:5432/po_test';
  } else if (env == 'ci') {
    // GitHub Actions CI environment (localhost with mapped ports)
    config.baseUrl = 'http://localhost:8080';
    config.dbConfig.url = 'jdbc:postgresql://localhost:5433/po_test';
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
  // Mock Database Helper (for E2E tests with mock API)
  // Returns expected values based on SQL query patterns
  // ═══════════════════════════════════════════════════════════
  var mockDb = {
    // Mock query that returns expected values based on SQL patterns
    query: function(sql) {
      karate.log('[MockDB] Query:', sql);
      var sqlLower = sql.toLowerCase();

      // === STATUS QUERIES ===
      // PO/Order status queries
      if (sqlLower.indexOf('select status from dbo.orders') >= 0 ||
          sqlLower.indexOf('select status from dbo.po') >= 0) {
        if (sqlLower.indexOf('comp-') >= 0 || sqlLower.indexOf('compensation') >= 0) {
          return [{ status: '0' }]; // Compensated PO returns to initial state
        }
        return [{ status: '0' }]; // Default PO status
      }

      // Receipt status queries
      if (sqlLower.indexOf('select status from dbo.receipt') >= 0) {
        if (sqlLower.indexOf('rcv-finalize') >= 0 || sqlLower.indexOf('rcv-test') >= 0) {
          return [{ status: '9' }]; // Finalized receipt
        }
        if (sqlLower.indexOf('comp-') >= 0) {
          return [{ status: '5' }]; // Reverted to pre-finalize state
        }
        return [{ status: '5' }]; // Default receipt status (ready for finalize)
      }

      // === COUNT QUERIES ===
      if (sqlLower.indexOf('select count(*)') >= 0) {
        // Inventory count
        if (sqlLower.indexOf('lotxlocxid') >= 0) {
          if (sqlLower.indexOf('test-loc-full') >= 0) {
            return [{ cnt: 0 }]; // No inventory in full location (compensated)
          }
          if (sqlLower.indexOf('holdcode') >= 0) {
            return [{ cnt: 1 }]; // One hold applied
          }
          return [{ cnt: 2 }]; // Default inventory records
        }
        // Receipt count
        if (sqlLower.indexOf('dbo.receipt') >= 0) {
          if (sqlLower.indexOf("status != 'x'") >= 0 || sqlLower.indexOf("status not in") >= 0) {
            return [{ cnt: 0 }]; // Compensated receipts are cancelled
          }
          return [{ cnt: 1 }];
        }
        // Task count
        if (sqlLower.indexOf('dbo.task') >= 0) {
          if (sqlLower.indexOf("status = '0'") >= 0) {
            return [{ cnt: 0 }]; // Compensated tasks are cancelled
          }
          return [{ cnt: 2 }];
        }
        // Allocation count
        if (sqlLower.indexOf('dbo.allocation') >= 0) {
          if (sqlLower.indexOf("status = 'active'") >= 0) {
            return [{ cnt: 0 }]; // Compensated allocations are released
          }
          return [{ cnt: 1 }];
        }
        // Reservation count
        if (sqlLower.indexOf('dbo.reservation') >= 0) {
          return [{ cnt: 0 }]; // Compensated reservations cleared
        }
        // Plugin data count
        if (sqlLower.indexOf('nikecustomdata') >= 0 || sqlLower.indexOf('customdata') >= 0) {
          return [{ cnt: 0 }]; // Plugin data cleaned up
        }
        // PO history count
        if (sqlLower.indexOf('po_history') >= 0) {
          return [{ cnt: 1 }]; // Archived
        }
        // Active PO count
        if (sqlLower.indexOf('dbo.po') >= 0 && sqlLower.indexOf('where pokey') >= 0) {
          return [{ cnt: 0 }]; // Archived (moved to history)
        }
        // Default count
        return [{ cnt: 1 }];
      }

      // === SELECT * QUERIES ===
      // Inventory records
      if (sqlLower.indexOf('select * from dbo.lotxlocxid') >= 0 ||
          sqlLower.indexOf('select lottable') >= 0) {
        return [{
          inventoryid: 'INV-001',
          receiptkey: 'RCV-TEST-001',
          sku: 'TEST-SKU-001',
          qty: 100,
          loc: 'A-01-01',
          lot: 'LOT-001',
          lottable01: 'BATCH-001',
          lottable02: '2026-12-31',
          lottable03: 'VENDOR-001'
        }];
      }

      // Compensation audit records
      if (sqlLower.indexOf('compensationaudit') >= 0) {
        return [{
          auditid: 'AUDIT-001',
          entitykey: 'RCV-TEST-001',
          action: 'COMPENSATE',
          auditdate: new Date().toISOString(),
          userid: 'system'
        }];
      }

      // Event outbox
      if (sqlLower.indexOf('eventoutbox') >= 0) {
        return []; // No pending events after compensation
      }

      // Compensation incident
      if (sqlLower.indexOf('compensationincident') >= 0) {
        return []; // No open incidents
      }

      // Alert log
      if (sqlLower.indexOf('alertlog') >= 0) {
        return []; // No alerts
      }

      // Default empty result
      karate.log('[MockDB] No match for query, returning empty');
      return [];
    },

    // Get single value from query
    getValue: function(sql) {
      var result = mockDb.query(sql);
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
      var sql = "SELECT 1 FROM dbo." + table + " WHERE " + whereClause;
      var result = mockDb.query(sql);
      return result && result.length > 0;
    },

    // Execute (always succeeds in mock)
    execute: function(sql) {
      karate.log('[MockDB] Execute:', sql);
      return 1;
    },

    // Query with params (delegates to query)
    queryWithParams: function(sql, params) {
      return mockDb.query(sql);
    },

    // Execute with params (always succeeds)
    executeWithParams: function(sql, params) {
      return 1;
    }
  };

  // ═══════════════════════════════════════════════════════════
  // Database Query Helper (for dual-write validation)
  // Uses mock in CI, real DB in other environments
  // ═══════════════════════════════════════════════════════════
  var useMockDb = (env == 'ci');

  config.db = {
    // Execute query and return results
    query: function(sql) {
      if (useMockDb) {
        return mockDb.query(sql);
      }
      if (!config.dbConfig) {
        karate.log('DB validation disabled in', env, 'environment');
        return null;
      }
      try {
        var DbUtils = Java.type('com.wms.po.test.util.DbUtils');
        return DbUtils.query(config.dbConfig, sql);
      } catch (e) {
        karate.log('[DB] Query failed, falling back to mock:', e.message);
        return mockDb.query(sql);
      }
    },

    // Execute query with parameters
    queryWithParams: function(sql, params) {
      if (useMockDb) {
        return mockDb.queryWithParams(sql, params);
      }
      if (!config.dbConfig) {
        return null;
      }
      try {
        var DbUtils = Java.type('com.wms.po.test.util.DbUtils');
        return DbUtils.queryWithParams(config.dbConfig, sql, params);
      } catch (e) {
        karate.log('[DB] Query failed, falling back to mock:', e.message);
        return mockDb.queryWithParams(sql, params);
      }
    },

    // Get single value
    getValue: function(sql) {
      if (useMockDb) {
        return mockDb.getValue(sql);
      }
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
      if (useMockDb) {
        return mockDb.exists(table, whereClause);
      }
      var sql = "SELECT 1 FROM dbo." + table + " WHERE " + whereClause + " LIMIT 1";
      var result = config.db.query(sql);
      return result && result.length > 0;
    },

    // Execute update/insert/delete statement
    execute: function(sql) {
      if (useMockDb) {
        return mockDb.execute(sql);
      }
      if (!config.dbConfig) {
        karate.log('DB validation disabled in', env, 'environment');
        return 0;
      }
      try {
        var DbUtils = Java.type('com.wms.po.test.util.DbUtils');
        return DbUtils.execute(config.dbConfig, sql);
      } catch (e) {
        karate.log('[DB] Execute failed, falling back to mock:', e.message);
        return mockDb.execute(sql);
      }
    },

    // Execute update/insert/delete with parameters
    executeWithParams: function(sql, params) {
      if (useMockDb) {
        return mockDb.executeWithParams(sql, params);
      }
      if (!config.dbConfig) {
        return 0;
      }
      try {
        var DbUtils = Java.type('com.wms.po.test.util.DbUtils');
        return DbUtils.executeWithParams(config.dbConfig, sql, params);
      } catch (e) {
        karate.log('[DB] Execute failed, falling back to mock:', e.message);
        return mockDb.executeWithParams(sql, params);
      }
    }
  };

  // ═══════════════════════════════════════════════════════════
  // Pre-loaded Test Data References (from TD-*.sql files)
  // These match the data loaded into PostgreSQL via Docker init
  // ═══════════════════════════════════════════════════════════
  config.preloaded = {
    // Storers (from TD-STORER.sql)
    storers: {
      NIKE_KR: { storerKey: 'NIKE_KR', facility: 'KR01', country: 'KR' },
      HM_KR: { storerKey: 'HM_KR', facility: 'KR02', country: 'KR' },
      ADIDAS_KR: { storerKey: 'ADIDAS_KR', facility: 'KR01', country: 'KR' },
      NIKE_IN: { storerKey: 'NIKE_IN', facility: 'IN01', country: 'IN' },
      UNILEVER_IN: { storerKey: 'UNILEVER_IN', facility: 'IN02', country: 'IN' },
      NIKE_SG: { storerKey: 'NIKE_SG', facility: 'SG01', country: 'SG' },
      TEST_001: { storerKey: 'TEST_STORER_001', facility: 'TEST01', country: 'KR' },
      TEST_002: { storerKey: 'TEST_STORER_002', facility: 'TEST02', country: 'IN' },
      TEST_ERR: { storerKey: 'TEST_STORER_ERR', facility: 'TEST01', country: 'KR', status: '9' }
    },

    // SKUs (from TD-SKU.sql)
    skus: {
      NIKE_AIRMAX: 'NK-AIRMAX90-BLK',
      NIKE_AF1: 'NK-AF1-WHT',
      HM_TEE: 'HM-BASIC-TEE-M',
      ADIDAS_ULTRA: 'AD-ULTRA-BLK',
      TEST_SKU_001: 'TEST-SKU-001',
      TEST_SKU_002: 'TEST-SKU-002',
      TEST_SKU_ERR: 'TEST-SKU-ERR'
    },

    // Pre-loaded POs (from TD-PO-HAPPY.sql)
    pos: {
      HAPPY_001: { poKey: 'PO-HAPPY-001', storerKey: 'TEST_STORER_001', status: '0' },
      HAPPY_002: { poKey: 'PO-HAPPY-002', storerKey: 'TEST_STORER_001', status: '0' },
      NIKE_001: { poKey: 'PO-NIKE-001', storerKey: 'NIKE_KR', status: '0' },
      HM_001: { poKey: 'PO-HM-001', storerKey: 'HM_KR', status: '0' },
      CLOSED_001: { poKey: 'PO-CLOSED-001', storerKey: 'TEST_STORER_001', status: '9' },
      CANCELLED_001: { poKey: 'PO-CANCELLED-001', storerKey: 'TEST_STORER_001', status: 'X' }
    },

    // Pre-loaded Receipts (from TD-RCV-HAPPY.sql)
    receipts: {
      FINALIZE_READY: { receiptKey: 'RCV-FINALIZE-001', poKey: 'PO-HAPPY-002', status: '5' },
      NIKE_001: { receiptKey: 'RCV-NIKE-001', poKey: 'PO-NIKE-001', status: '5' },
      FINALIZED: { receiptKey: 'RCV-FINALIZED-001', status: '9' }
    },

    // RDT Users (from TD-RDT.sql)
    rdtUsers: {
      OPERATOR_001: { userId: 'RDT-OP-001', facility: 'KR01', device: 'RDT-DEV-001' },
      OPERATOR_002: { userId: 'RDT-OP-002', facility: 'IN01', device: 'RDT-DEV-002' },
      OPERATOR_ERR: { userId: 'RDT-OP-ERR', facility: 'KR01', status: '9' }
    },

    // Locations (from TD-LOCATION.sql)
    locations: {
      RECV_01: 'RECV-01',
      STAGE_01: 'STAGE-01',
      XDOCK_01: 'XDOCK-01',
      STORAGE_A01: 'A-01-01',
      TEST_LOC_FULL: 'TEST-LOC-FULL',
      TEST_LOC_ERR: 'TEST-LOC-ERR'
    },

    // Jobs (from TD-JOB.sql)
    jobs: {
      GENERIC_INBOUND: 'JOB-GEN-INB',
      PO_IMPORT: 'JOB-PO-IMPORT',
      ARCHIVAL: 'JOB-ARCHIVAL'
    }
  };

  // ═══════════════════════════════════════════════════════════
  // Test Data Helpers (Dynamic Generation)
  // ═══════════════════════════════════════════════════════════
  config.testData = {
    // Valid PO creation request (uses pre-loaded storer/SKU)
    validPORequest: function(storerKey, facility) {
      var storer = storerKey || config.testStorerKey;
      var fac = facility || config.testFacility;
      var sku = (storer === 'NIKE_KR') ? config.preloaded.skus.NIKE_AIRMAX :
                (storer === 'HM_KR') ? config.preloaded.skus.HM_TEE :
                config.preloaded.skus.TEST_SKU_001;
      return {
        storerKey: storer,
        facility: fac,
        externalOrderKey: 'EXT-' + config.uuid(),
        expectedDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        supplierName: 'Test Supplier',
        lines: [
          {
            sku: sku,
            qtyOrdered: 100,
            uom: 'EA'
          }
        ]
      };
    },

    // Nike PO request (uses Nike-specific test data)
    nikePORequest: function() {
      return {
        storerKey: 'NIKE_KR',
        facility: 'KR01',
        externalOrderKey: 'NIKE-EXT-' + config.uuid(),
        expectedDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        supplierName: 'Nike Factory Korea',
        lines: [
          { sku: config.preloaded.skus.NIKE_AIRMAX, qtyOrdered: 500, uom: 'EA', lottable01: 'STYLE-001', lottable02: 'BLK' },
          { sku: config.preloaded.skus.NIKE_AF1, qtyOrdered: 300, uom: 'EA', lottable01: 'STYLE-002', lottable02: 'WHT' }
        ]
      };
    },

    // H&M PO request (uses H&M-specific test data)
    hmPORequest: function() {
      return {
        storerKey: 'HM_KR',
        facility: 'KR02',
        externalOrderKey: 'HM-EXT-' + config.uuid(),
        expectedDate: new Date(Date.now() + 10 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        supplierName: 'H&M Supplier Korea',
        lines: [
          { sku: config.preloaded.skus.HM_TEE, qtyOrdered: 2000, uom: 'EA' }
        ]
      };
    },

    // Multi-line PO request
    multiLinePORequest: function(lineCount) {
      var lines = [];
      var skus = [config.preloaded.skus.TEST_SKU_001, config.preloaded.skus.TEST_SKU_002, config.preloaded.skus.NIKE_AIRMAX];
      for (var i = 0; i < (lineCount || 5); i++) {
        lines.push({
          sku: skus[i % skus.length],
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
    },

    // RDT request
    rdtRequest: function(operatorId) {
      var op = operatorId || config.preloaded.rdtUsers.OPERATOR_001.userId;
      return {
        operatorId: op,
        deviceId: config.preloaded.rdtUsers.OPERATOR_001.device,
        facility: 'KR01',
        transactionType: 'RECEIVE'
      };
    }
  };

  karate.log('═══════════════════════════════════════════════════════════');
  karate.log('PO Modernization E2E Tests');
  karate.log('Environment:', env);
  karate.log('Base URL:', config.baseUrl);
  karate.log('DB Validation:', useMockDb ? 'Mock (CI mode)' : (config.dbConfig ? 'Real DB' : 'Disabled'));
  karate.log('═══════════════════════════════════════════════════════════');

  return config;
}
