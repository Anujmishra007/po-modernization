@F7 @TradeReturn @Regression
Feature: F7 - Trade Return Flow Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 7: Trade Return Flow
  # Tests: F7-TC01 to F7-TC15
  # Entry Points: API, RDT
  # Purpose: Test trade return receipt processing
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F7-TC01: Create trade return PO
  # ─────────────────────────────────────────────────────────────
  @F7-TC01 @P1 @Happy
  Scenario: Create trade return PO successfully
    * def tradeReturnRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "returnType": "TRADE_RETURN",
        "originalOrderKey": "SO-NIKE-001",
        "reason": "DEFECTIVE",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReturned": 10,
            "returnReason": "DAMAGED"
          }
        ]
      }
      """

    Given path api + '/trade-returns'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request tradeReturnRequest
    When method post
    Then status 201
    And match response.returnKey == '#present'
    And match response.returnType == 'TRADE_RETURN'
    And match response.status == '0'

  # ─────────────────────────────────────────────────────────────
  # F7-TC02: Receive trade return via RDT
  # ─────────────────────────────────────────────────────────────
  @F7-TC02 @P1 @RDT @Happy
  Scenario: Receive trade return via RDT device
    * def returnKey = 'TR-001'
    * def receiveRequest =
      """
      {
        "lineNumber": "00001",
        "qtyReceived": 10,
        "condition": "DAMAGED",
        "location": "KR01-RCV-RET-01"
      }
      """

    Given path api + '/rdt/trade-returns/' + returnKey + '/receive'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header X-RDT-User-Id = 'RDT_USER_001'
    And header Content-Type = 'application/json'
    And request receiveRequest
    When method post
    Then status 200
    And match response.qtyReceived == 10
    And match response.lineStatus == 'RECEIVED'

  # ─────────────────────────────────────────────────────────────
  # F7-TC03: Trade return quality inspection
  # ─────────────────────────────────────────────────────────────
  @F7-TC03 @P1 @QC @Happy
  Scenario: Trade return passes quality inspection
    * def returnKey = 'TR-002'
    * def inspectionRequest =
      """
      {
        "lineNumber": "00001",
        "inspectionResult": "PASS",
        "disposition": "RETURN_TO_STOCK",
        "notes": "Minor cosmetic damage, acceptable"
      }
      """

    Given path api + '/trade-returns/' + returnKey + '/inspect'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request inspectionRequest
    When method post
    Then status 200
    And match response.disposition == 'RETURN_TO_STOCK'
    And match response.qcStatus == 'PASS'

  # ─────────────────────────────────────────────────────────────
  # F7-TC04: Trade return failed QC
  # ─────────────────────────────────────────────────────────────
  @F7-TC04 @P1 @QC @Unhappy
  Scenario: Trade return fails quality inspection
    * def returnKey = 'TR-003'
    * def inspectionRequest =
      """
      {
        "lineNumber": "00001",
        "inspectionResult": "FAIL",
        "disposition": "SCRAP",
        "defectCode": "DEF-001",
        "notes": "Beyond repair"
      }
      """

    Given path api + '/trade-returns/' + returnKey + '/inspect'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request inspectionRequest
    When method post
    Then status 200
    And match response.disposition == 'SCRAP'
    And match response.qcStatus == 'FAIL'
    And match response.scrapTaskCreated == true

  # ─────────────────────────────────────────────────────────────
  # F7-TC05: Trade return to inventory
  # ─────────────────────────────────────────────────────────────
  @F7-TC05 @P1 @Happy
  Scenario: Trade return restocks to inventory
    * def returnKey = 'TR-004'

    Given path api + '/trade-returns/' + returnKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "restockApproved": true }
    When method post
    Then status 200
    And match response.restocked == true

    * sleep(3000)

    # Verify inventory created
    * def inv = db.query("SELECT * FROM dbo.lotxlocxid WHERE returnkey = '" + returnKey + "'")
    * assert karate.sizeOf(inv) >= 1

  # ─────────────────────────────────────────────────────────────
  # F7-TC06: Trade return with credit memo
  # ─────────────────────────────────────────────────────────────
  @F7-TC06 @P2 @Credit
  Scenario: Trade return generates credit memo
    * def returnKey = 'TR-005'

    Given path api + '/trade-returns/' + returnKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "generateCreditMemo": true }
    When method post
    Then status 200
    And match response.creditMemoKey == '#present'
    And assert response.creditAmount > 0

  # ─────────────────────────────────────────────────────────────
  # F7-TC07: Trade return without original order
  # ─────────────────────────────────────────────────────────────
  @F7-TC07 @P2 @NoOriginal @Unhappy
  Scenario: Trade return without original order fails
    * def tradeReturnRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "returnType": "TRADE_RETURN",
        "originalOrderKey": "SO-DOES-NOT-EXIST",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReturned": 10
          }
        ]
      }
      """

    Given path api + '/trade-returns'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request tradeReturnRequest
    When method post
    Then status 404
    And match response.errorCode == 'TR_001'
    And match response.message == '#? _.indexOf("Original order not found") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F7-TC08: Trade return exceeds original qty
  # ─────────────────────────────────────────────────────────────
  @F7-TC08 @P1 @Unhappy
  Scenario: Trade return quantity exceeds original fails
    * def tradeReturnRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "returnType": "TRADE_RETURN",
        "originalOrderKey": "SO-NIKE-001",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReturned": 99999
          }
        ]
      }
      """

    Given path api + '/trade-returns'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request tradeReturnRequest
    When method post
    Then status 422
    And match response.errorCode == 'TR_002'
    And match response.message == '#? _.indexOf("exceeds original") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F7-TC09 to F7-TC15: Additional trade return scenarios
  # ─────────────────────────────────────────────────────────────
  @F7-TC09 @P2 @Partial
  Scenario: Partial trade return
    * def returnKey = 'TR-006'
    * def receiveRequest =
      """
      {
        "lineNumber": "00001",
        "qtyReceived": 5,
        "condition": "GOOD"
      }
      """

    Given path api + '/rdt/trade-returns/' + returnKey + '/receive'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request receiveRequest
    When method post
    Then status 200
    And match response.qtyReceived == 5
    And match response.qtyPending == '#? _ > 0'

  @F7-TC10 @P2 @Mixed
  Scenario: Trade return with mixed dispositions
    * def returnKey = 'TR-007'

    Given path api + '/trade-returns/' + returnKey + '/inspect-batch'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "inspections": [
          { "lineNumber": "00001", "result": "PASS", "disposition": "RETURN_TO_STOCK", "qty": 5 },
          { "lineNumber": "00001", "result": "FAIL", "disposition": "SCRAP", "qty": 3 },
          { "lineNumber": "00001", "result": "PASS", "disposition": "REFURBISH", "qty": 2 }
        ]
      }
      """
    When method post
    Then status 200
    And match response.processedCount == 3

  @F7-TC11 @P2 @Hold
  Scenario: Trade return with hold pending inspection
    * def returnKey = 'TR-008'

    Given path api + '/trade-returns/' + returnKey + '/hold'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "holdCode": "QC_PENDING", "reason": "Awaiting quality inspection" }
    When method put
    Then status 200
    And match response.holdApplied == true

  @F7-TC12 @P3 @Cancel
  Scenario: Cancel trade return
    * def returnKey = 'TR-009'

    Given path api + '/trade-returns/' + returnKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Customer changed mind" }
    When method post
    Then status 200
    And match response.status == 'CANCELLED'

  @F7-TC13 @P3 @Photos
  Scenario: Trade return with photo documentation
    * def returnKey = 'TR-010'

    Given path api + '/trade-returns/' + returnKey + '/photos'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'multipart/form-data'
    And multipart file photo = { read: 'classpath:test-data/images/damage-photo.jpg', contentType: 'image/jpeg' }
    And multipart field lineNumber = '00001'
    And multipart field description = 'Visible damage'
    When method post
    Then status 201
    And match response.photoKey == '#present'

  @F7-TC14 @P3 @Refurbish
  Scenario: Trade return routed to refurbishment
    * def returnKey = 'TR-011'

    Given path api + '/trade-returns/' + returnKey + '/inspect'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "lineNumber": "00001",
        "inspectionResult": "CONDITIONAL",
        "disposition": "REFURBISH",
        "refurbWorkOrder": "WO-REFURB-001"
      }
      """
    When method post
    Then status 200
    And match response.disposition == 'REFURBISH'
    And match response.workOrderCreated == true

  @F7-TC15 @P3 @Report
  Scenario: Trade return summary report
    Given path api + '/trade-returns/report'
    And header Authorization = 'Bearer ' + authToken
    And param storerKey = 'NIKE_KR'
    And param dateFrom = '2026-01-01'
    And param dateTo = '2026-12-31'
    When method get
    Then status 200
    And match response contains { totalReturns: '#number', totalValue: '#number', dispositionBreakdown: '#present' }

