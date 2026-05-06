@F10 @Compensation @Populate @Regression
Feature: F10 - Populate Flow Compensation Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 10: Compensation - Populate Failure Scenarios
  # Tests: COMP-02 to COMP-07
  # Purpose: Test saga pattern rollback for populate failures
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # COMP-02: Details created, reservation fails
  # Expected: Receipt details rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-02 @P1 @ReservationFail
  Scenario: Compensation when reservation fails after details created
    * def poKey = 'PO-COMP-RES-001'

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '4'
    And request
      """
      {
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ]
      }
      """
    When method post
    Then status 422
    And match response.errorCode == '#present'
    And match response.failedStep == 'RESERVATION'
    And match response.compensated == true
    And match response.compensatedSteps contains ['RECEIPT_DETAIL', 'RECEIPT_HEADER']

    # Verify no receipt exists
    * def receiptCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.receipt WHERE orderkey = '" + poKey + "' AND status != 'X'")
    * match receiptCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-03: Reservation done, allocation fails
  # Expected: Reservation released, details rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-03 @P1 @AllocationFail
  Scenario: Compensation when allocation fails after reservation
    * def poKey = 'PO-COMP-ALLOC-001'

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '5'
    And request
      """
      {
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ],
        "enableAllocation": true
      }
      """
    When method post
    Then status 422
    And match response.failedStep == 'ALLOCATION'
    And match response.compensated == true
    And match response.compensatedSteps contains ['RESERVATION', 'RECEIPT_DETAIL', 'RECEIPT_HEADER']

    # Verify reservations released
    * def resCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.reservation WHERE orderkey = '" + poKey + "' AND status = 'ACTIVE'")
    * match resCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-04: Legacy sync fails
  # Expected: Modern system rolls back, warning logged
  # ─────────────────────────────────────────────────────────────
  @COMP-04 @P1 @LegacySyncFail
  Scenario: Compensation when legacy sync fails
    * def poKey = 'PO-COMP-LEGACY-001'

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '6'
    And request
      """
      {
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ],
        "legacySyncEnabled": true
      }
      """
    When method post
    Then status 422
    And match response.failedStep == 'LEGACY_SYNC'
    And match response.compensated == true
    And match response.legacySyncFailed == true

    # Verify no data in modern system
    * def receiptCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.receipt WHERE orderkey = '" + poKey + "' AND status != 'X'")
    * match receiptCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-05: Workflow timeout during populate
  # Expected: All steps rolled back on timeout
  # ─────────────────────────────────────────────────────────────
  @COMP-05 @P1 @PopulateTimeout
  Scenario: Compensation on populate workflow timeout
    * def poKey = 'PO-COMP-TIMEOUT-001'

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Timeout = 'true'
    And request
      """
      {
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ]
      }
      """
    When method post
    Then status 504
    And match response.errorCode == 'INT_003'
    And match response.message contains 'timeout'

    # Wait for compensation
    * sleep(5000)

    # Verify rollback
    * def receiptCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.receipt WHERE orderkey = '" + poKey + "' AND status NOT IN ('X', 'TIMEOUT')")
    * match receiptCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-06: User cancellation during populate
  # Expected: In-flight work rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-06 @P1 @UserCancel
  Scenario: Compensation on user cancellation during populate
    * def poKey = 'PO-COMP-CANCEL-001'

    # Start async populate
    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "async": true,
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ]
      }
      """
    When method post
    Then status 202
    * def workflowId = response.workflowId

    # Cancel the workflow
    Given path api + '/workflows/' + workflowId + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "User requested cancellation" }
    When method post
    Then status 200
    And match response.cancelled == true

    # Wait for compensation
    * sleep(5000)

    # Verify state cleaned up
    Given path api + '/workflows/' + workflowId + '/status'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == 'CANCELLED'
    And match response.compensated == true

  # ─────────────────────────────────────────────────────────────
  # COMP-07: Idempotent retry after failure
  # Expected: Retry succeeds or returns same error
  # ─────────────────────────────────────────────────────────────
  @COMP-07 @P1 @IdempotentRetry
  Scenario: Idempotent retry after populate failure
    * def poKey = 'PO-COMP-IDEMP-001'
    * def idempotencyKey = 'IDEMP-POP-' + timestamp()

    # First attempt (will fail)
    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = idempotencyKey
    And header X-Test-Fail-At-Step = '3'
    And request
      """
      {
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ]
      }
      """
    When method post
    Then status 422
    * def firstError = response.errorCode

    # Retry with same idempotency key (should return same error)
    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = idempotencyKey
    And request
      """
      {
        "receiptDetails": [
          { "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }
        ]
      }
      """
    When method post
    Then status 422
    And match response.errorCode == firstError
    And match response.idempotent == true

