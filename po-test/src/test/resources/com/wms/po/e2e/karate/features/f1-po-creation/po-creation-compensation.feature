@F1 @POCreation @Compensation @Regression
Feature: F1 - PO Creation Compensation Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - Compensation/Rollback Scenarios
  # Tests: F1-TC20 to F1-TC24
  # Entry Points: API, Job
  # Purpose: Test transaction rollback and saga compensation
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F1-TC20: Line insert fails at line 25/50 - full rollback
  # ─────────────────────────────────────────────────────────────
  @F1-TC20 @P1 @LineFailRollback
  Scenario: Multi-line PO rolls back all lines on single failure
    * def generateLines =
      """
      function(count, failAt) {
        var lines = [];
        for (var i = 0; i < count; i++) {
          var line = {
            "sku": "HM-BASIC-TEE-M",
            "qtyOrdered": 10,
            "lineNumber": String(i + 1).padStart(5, '0')
          };
          // Insert invalid SKU at failAt position
          if (i == failAt - 1) {
            line.sku = "INVALID-SKU-FORCE-FAIL";
          }
          lines.push(line);
        }
        return lines;
      }
      """

    * def externalKey = 'PO-COMP-LINE-' + timestamp()
    * def request =
      """
      {
        "storerKey": "HM_KR",
        "facility": "KR02",
        "externalOrderKey": "#(externalKey)",
        "lines": "#(generateLines(50, 25))"
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 422
    And match response.errorCode == 'PO_013'
    And match response.failedLine == 25

    # Verify NO PO created (full rollback)
    * def poCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + externalKey + "'")
    * match poCheck[0].cnt == 0

    # Verify NO detail lines created
    * def detailCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.podetail WHERE externpokey = '" + externalKey + "'")
    * match detailCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # F1-TC21: Header created, details fail - header rolled back
  # ─────────────────────────────────────────────────────────────
  @F1-TC21 @P1 @HeaderDetailRollback
  Scenario: Header rolls back when detail insertion fails
    * def externalKey = 'PO-COMP-HDR-' + timestamp()
    * def request =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#(externalKey)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 },
          { "sku": "INVALID-SKU-XXX", "qtyOrdered": 50 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 422

    # Verify no orphan header
    * def headerCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + externalKey + "'")
    * match headerCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # F1-TC22: Partial rollback verification
  # ─────────────────────────────────────────────────────────────
  @F1-TC22 @P1 @PartialRollbackVerify
  Scenario: Verify database state is clean after failed PO
    * def externalKey = 'PO-COMP-CLEAN-' + timestamp()
    * def request =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#(externalKey)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    # Simulate failure mid-transaction
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '3'
    And request request
    When method post
    Then status 500

    # Comprehensive rollback verification
    * def tables = ['po', 'podetail', 'poaudit', 'podetailaudit']
    * def verifyClean =
      """
      function(tables, key) {
        for (var i = 0; i < tables.length; i++) {
          var sql = "SELECT COUNT(*) as cnt FROM dbo." + tables[i] + " WHERE externpokey = '" + key + "' OR pokey LIKE '%-" + key + "'";
          var result = db.query(sql);
          if (result[0].cnt > 0) return false;
        }
        return true;
      }
      """
    * match verifyClean(tables, externalKey) == true

  # ─────────────────────────────────────────────────────────────
  # F1-TC23: Job retry on transient failure
  # ─────────────────────────────────────────────────────────────
  @F1-TC23 @P2 @JobRetry
  Scenario: PO creation job retries on transient failure
    # Trigger job with simulated transient failure
    Given path api + '/jobs/generic-inbound-po/trigger'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "simulateTransientFailure": true,
        "failOnAttempt": 1,
        "maxRetries": 3
      }
      """
    When method post
    Then status 202
    * def jobId = response.jobExecutionId

    # Wait for retries
    * sleep(10000)

    # Verify job eventually succeeded
    Given path api + '/jobs/executions/' + jobId
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == 'COMPLETED'
    # Should have retried at least once
    And match response.attemptCount > 1

  # ─────────────────────────────────────────────────────────────
  # F1-TC24: Idempotency check
  # ─────────────────────────────────────────────────────────────
  @F1-TC24 @P1 @Idempotency
  Scenario: Same request with idempotency key returns existing PO
    * def idempotencyKey = 'IDEMP-' + timestamp()
    * def request = testData.validPORequest()
    * request.externalOrderKey = 'PO-IDEMP-' + timestamp()

    # First request
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = idempotencyKey
    And request request
    When method post
    Then status 201
    * def firstPoKey = response.poKey
    * def firstTimestamp = response.createdAt

    # Second request with same idempotency key
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = idempotencyKey
    And request request
    When method post
    # Returns existing PO (200), not new (201)
    Then status 200
    And match response.poKey == firstPoKey
    And match response.createdAt == firstTimestamp
    And match response.idempotent == true

    # Verify only one PO exists
    * def poCount = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + request.externalOrderKey + "'")
    * match poCount[0].cnt == 1

  # ─────────────────────────────────────────────────────────────
  # F1-TC40: Compensation audit trail
  # ─────────────────────────────────────────────────────────────
  @F1-TC40 @P2 @CompensationAudit
  Scenario: Failed PO creation logs compensation audit
    * def externalKey = 'PO-COMP-AUDIT-' + timestamp()
    * def request =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#(externalKey)",
        "lines": [
          { "sku": "INVALID-SKU-XXX", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 422

    # Verify compensation event logged
    * def compAudit = db.query("SELECT * FROM dbo.compensationaudit WHERE entitykey = '" + externalKey + "'")
    * match compAudit.length >= 1
    * match compAudit[0].compensationtype == 'PO_CREATION_ROLLBACK'

  # ─────────────────────────────────────────────────────────────
  # F1-TC41: Nested transaction rollback
  # ─────────────────────────────────────────────────────────────
  @F1-TC41 @P2 @NestedRollback
  Scenario: Nested transaction failure rolls back parent
    * def externalKey = 'PO-NESTED-' + timestamp()
    * def request =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#(externalKey)",
        "triggerNestedOperation": true,
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-Nested-Operation = 'true'
    And request request
    When method post
    Then status 500

    # Verify parent transaction rolled back
    * def poCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + externalKey + "'")
    * match poCheck[0].cnt == 0

