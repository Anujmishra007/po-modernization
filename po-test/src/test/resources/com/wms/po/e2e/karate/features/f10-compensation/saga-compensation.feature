@F10 @Compensation @Saga @Regression
Feature: F10 - Compensation Flow Tests (Saga Pattern)

  # ═══════════════════════════════════════════════════════════
  # Flow 10: Compensation Flow Testing
  # Tests: COMP-01 to COMP-26
  # Validates saga pattern rollback scenarios
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token

  # ─────────────────────────────────────────────────────────────
  # COMP-01: Populate - Header created, detail fails
  # Expected: Header should be rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-01 @P1 @Populate
  Scenario: Compensation on detail creation failure
    # Create a PO that will fail during detail population
    * def poKey = 'PO-TEST-001'

    # Trigger populate with invalid detail data
    * def populateRequest =
      """
      {
        "poKey": "#(poKey)",
        "receiptDetails": [
          {
            "sku": "INVALID-SKU-FORCE-FAIL",
            "qtyReceived": 100
          }
        ]
      }
      """

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request populateRequest
    When method post
    Then status 422
    And match response.errorCode == '#present'
    And match response.compensated == true

    # Verify PO status was NOT changed (rolled back)
    * def poStatus = db.getValue("SELECT status FROM dbo.orders WHERE orderkey = '" + poKey + "'")
    * match poStatus == '0'

    # Verify no receipt was created
    * def receiptCount = db.getValue("SELECT COUNT(*) FROM dbo.receipt WHERE orderkey = '" + poKey + "' AND status = '0'")
    * match receiptCount == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-09: Finalize - Inventory posting fails
  # Expected: Receipt status should be reverted
  # ─────────────────────────────────────────────────────────────
  @COMP-09 @P1 @Finalize
  Scenario: Compensation on inventory posting failure
    # Use a receipt that will fail during inventory posting
    * def receiptKey = 'RCV-TEST-COMP-001'

    # Setup: Create receipt pointing to a full location
    * def setupRequest =
      """
      {
        "receiptKey": "#(receiptKey)",
        "toLocation": "TEST-LOC-FULL"
      }
      """

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request setupRequest
    When method post
    Then status 422
    And match response.errorCode == 'INV_011'
    And match response.message contains 'Location full'
    And match response.compensated == true

    # Verify receipt status was reverted
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus == '5'

    # Verify no inventory was created
    * def invCount = db.getValue("SELECT COUNT(*) FROM dbo.lotxlocxid WHERE loc = 'TEST-LOC-FULL'")
    * match invCount == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-13: Finalize - Workflow timeout
  # Expected: All completed steps should be rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-13 @P1 @Timeout
  Scenario: Compensation on workflow timeout
    * def receiptKey = 'RCV-TEST-TIMEOUT-001'

    # Trigger finalize with simulated timeout
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Timeout = 'true'
    And request {}
    When method post
    Then status 504
    And match response.errorCode == 'INT_003'
    And match response.message contains 'Workflow timeout'

    # Wait for compensation to complete
    * sleep(5000)

    # Verify state was rolled back
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus != '9'

  # ─────────────────────────────────────────────────────────────
  # COMP-15: Concurrent populate same PO
  # Expected: Second request should be blocked/rejected
  # ─────────────────────────────────────────────────────────────
  @COMP-15 @P1 @Concurrent
  Scenario: Concurrent populate blocked
    * def poKey = 'PO-TEST-001'

    # Start first populate (async)
    * def startPopulate =
      """
      function() {
        var config = karate.toMap({
          url: baseUrl + api + '/po/' + poKey + '/populate',
          method: 'post',
          headers: { 'Authorization': 'Bearer ' + authToken, 'Content-Type': 'application/json' },
          body: { async: true }
        });
        return karate.call('classpath:common/http-call.feature', config);
      }
      """

    # Immediately try second populate
    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "async": false }
    When method post
    Then status 409
    And match response.errorCode == 'INT_021'
    And match response.message contains 'concurrent'

  # ─────────────────────────────────────────────────────────────
  # COMP-18: Temporal worker crash recovery
  # Expected: Workflow should resume on new worker
  # ─────────────────────────────────────────────────────────────
  @COMP-18 @P1 @Recovery
  Scenario: Workflow recovery after worker crash
    * def receiptKey = generateReceiptKey()

    # Start finalize workflow
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Worker-Crash = 'true'
    And request {}
    When method post
    Then status 202
    * def workflowId = response.workflowId

    # Wait for recovery
    * sleep(10000)

    # Check workflow status - should have recovered
    Given path api + '/workflows/' + workflowId + '/status'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == '#? _ == "COMPLETED" || _ == "RUNNING"'

  # ─────────────────────────────────────────────────────────────
  # COMP-20: Double compensation prevention
  # Expected: Compensation should be idempotent
  # ─────────────────────────────────────────────────────────────
  @COMP-20 @P1 @Idempotent
  Scenario: Idempotent compensation
    * def poKey = 'PO-TEST-COMP-IDEMP'

    # Trigger compensation twice
    Given path api + '/po/' + poKey + '/compensate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test compensation" }
    When method post
    Then status 200

    # Second compensation should also succeed (idempotent)
    Given path api + '/po/' + poKey + '/compensate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test compensation retry" }
    When method post
    Then status 200
    And match response.alreadyCompensated == true

  # ─────────────────────────────────────────────────────────────
  # COMP-26: Full saga replay test
  # Expected: Complete end-to-end compensation
  # ─────────────────────────────────────────────────────────────
  @COMP-26 @P1 @FullReplay
  Scenario: Full saga replay with compensation
    # Create fresh PO for full saga test
    * def poRequest = testData.validPORequest()
    * def poKey = generatePoKey()
    * poRequest.externalOrderKey = poKey

    # Step 1: Create PO
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    * def createdPoKey = response.poKey

    # Step 2: Populate with forced failure at step 5
    Given path api + '/po/' + createdPoKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '5'
    And request {}
    When method post
    Then status 422
    And match response.compensated == true
    And match response.compensatedSteps contains ['RECEIPT_DETAIL', 'RECEIPT_HEADER']

    # Verify clean state - PO should still be open
    * def finalStatus = db.getValue("SELECT status FROM dbo.orders WHERE orderkey = '" + createdPoKey + "'")
    * match finalStatus == '0'

    # Verify no orphan receipts
    * def orphanReceipts = db.getValue("SELECT COUNT(*) FROM dbo.receipt WHERE orderkey = '" + createdPoKey + "'")
    * match orphanReceipts == 0
