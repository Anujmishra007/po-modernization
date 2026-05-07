@F3 @ReceiptFinalization @Unhappy @Regression
Feature: F3 - Receipt Finalization Unhappy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 3: Receipt Finalization - Unhappy Path Scenarios
  # Tests: F3-TC11 to F3-TC20
  # Entry Points: API, RDT
  # Purpose: Test error handling for receipt finalization
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token

  # ─────────────────────────────────────────────────────────────
  # F3-TC11: Finalize non-existent receipt
  # Error Code: RCV_001 (69301)
  # ─────────────────────────────────────────────────────────────
  @F3-TC11 @P1 @RCV_001
  Scenario: Finalize non-existent receipt fails
    * def receiptKey = 'RCV-DOES-NOT-EXIST-999'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 404
    And match response.errorCode == 'RCV_001'
    And match response.message contains 'Receipt not found'
    And match response.receiptKey == receiptKey

  # ─────────────────────────────────────────────────────────────
  # F3-TC12: Finalize already finalized receipt
  # Error Code: RCV_005 (69305)
  # ─────────────────────────────────────────────────────────────
  @F3-TC12 @P1 @RCV_005
  Scenario: Finalize already finalized receipt fails
    * def receiptKey = 'RCV-ERR-003'  # Already status 9

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'RCV_005'
    And match response.message contains 'already finalized'
    And match response.currentStatus == '9'

  # ─────────────────────────────────────────────────────────────
  # F3-TC13: Finalize cancelled receipt
  # Error Code: RCV_006 (69306)
  # ─────────────────────────────────────────────────────────────
  @F3-TC13 @P1 @RCV_006
  Scenario: Finalize cancelled receipt fails
    * def receiptKey = 'RCV-ERR-004'  # Cancelled receipt

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'RCV_006'
    And match response.message contains 'cancelled'

  # ─────────────────────────────────────────────────────────────
  # F3-TC14: Finalize to invalid location
  # Error Code: LOC_001 (67001)
  # ─────────────────────────────────────────────────────────────
  @F3-TC14 @P1 @LOC_001
  Scenario: Finalize to invalid location fails
    * def receiptKey = 'RCV-HAPPY-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "targetLocation": "INVALID-LOC-999" }
    When method post
    Then status 422
    And match response.errorCode == 'LOC_001'
    And match response.message contains 'Location not found'
    And match response.location == 'INVALID-LOC-999'

  # ─────────────────────────────────────────────────────────────
  # F3-TC15: Finalize to full location
  # Error Code: INV_011 (68011)
  # ─────────────────────────────────────────────────────────────
  @F3-TC15 @P1 @INV_011
  Scenario: Finalize to full location fails
    * def receiptKey = 'RCV-HAPPY-002'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "targetLocation": "TEST-LOC-FULL" }
    When method post
    Then status 422
    And match response.errorCode == 'INV_011'
    And match response.message contains 'Location full'
    And match response.availableCapacity == 0

  # ─────────────────────────────────────────────────────────────
  # F3-TC16: Finalize with missing lottable (when required)
  # Error Code: RCV_007 (69307)
  # ─────────────────────────────────────────────────────────────
  @F3-TC16 @P1 @RCV_007 @Nike
  Scenario: Finalize Nike receipt missing required lottables fails
    * def receiptKey = 'RCV-ERR-005'  # Nike receipt missing lottables

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'RCV_007'
    And match response.message contains 'Missing required lottable'
    And match response.missingLottables contains 'lottable01'

  # ─────────────────────────────────────────────────────────────
  # F3-TC17: Finalize receipt with on-hold PO
  # Error Code: PO_007 (68807)
  # ─────────────────────────────────────────────────────────────
  @F3-TC17 @P1 @PO_007
  Scenario: Finalize receipt with held PO fails
    * def receiptKey = 'RCV-ERR-006'  # Receipt linked to held PO

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'PO_007'
    And match response.message contains 'PO is on hold'
    And match response.holdCode == '#present'

  # ─────────────────────────────────────────────────────────────
  # F3-TC18: Finalize with zero quantity lines
  # Error Code: RCV_008 (69308)
  # ─────────────────────────────────────────────────────────────
  @F3-TC18 @P2 @RCV_008
  Scenario: Finalize receipt with zero quantity fails
    * def receiptKey = 'RCV-ERR-007'  # Receipt with qty=0 lines

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'RCV_008'
    And match response.message contains 'zero quantity'

  # ─────────────────────────────────────────────────────────────
  # F3-TC19: Finalize with workflow timeout
  # Error Code: INT_003 (60003)
  # ─────────────────────────────────────────────────────────────
  @F3-TC19 @P1 @INT_003 @Timeout
  Scenario: Finalize timeout triggers compensation
    * def receiptKey = 'RCV-TIMEOUT-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Timeout = 'true'
    And request {}
    When method post
    Then status 504
    And match response.errorCode == 'INT_003'
    And match response.message contains 'Workflow timeout'
    And match response.compensated == '#? _ == true || _ == null'

  # ─────────────────────────────────────────────────────────────
  # F3-TC20: Finalize with database error
  # Error Code: INT_001 (60001)
  # ─────────────────────────────────────────────────────────────
  @F3-TC20 @P1 @INT_001
  Scenario: Database error during finalize triggers rollback
    * def receiptKey = 'RCV-DBERR-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-DB-Error = 'true'
    And request {}
    When method post
    Then status 500
    And match response.errorCode == 'INT_001'
    And match response.message contains 'Database error'

    # Verify receipt not changed
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus != '9'  # Should NOT be finalized

