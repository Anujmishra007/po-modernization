@F3 @ReceiptFinalization @Happy @Regression
Feature: F3 - Receipt Finalization Happy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 3: Receipt Finalization - Happy Path Scenarios
  # Tests: F3-TC01 to F3-TC10
  # Entry Points: API, RDT, Trigger
  # Purpose: Finalize receipts and post inventory
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F3-TC01: Finalize single-line receipt via API
  # ─────────────────────────────────────────────────────────────
  @F3-TC01 @P1 @API
  Scenario: Finalize single-line receipt successfully
    # Use pre-created receipt from test data
    * def receiptKey = 'RCV-HAPPY-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.status == 'FINALIZED'
    And match response.receiptKey == receiptKey
    And match response.workflowId == '#present'

    # Wait for async workflow
    * sleep(3000)

    # Verify receipt status in DB
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus == '9'

    # Verify inventory posted to LOTxLOCxID
    * def invResult = db.query("SELECT * FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "'")
    * assert invResult.size() >= 1
    * assert invResult.get(0).get('qty') > 0

  # ─────────────────────────────────────────────────────────────
  # F3-TC02: Finalize multi-line receipt
  # ─────────────────────────────────────────────────────────────
  @F3-TC02 @P1 @API
  Scenario: Finalize multi-line receipt successfully
    * def receiptKey = 'RCV-HAPPY-002'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.linesFinalized == '#? _ >= 3'

    # Wait for workflow
    * sleep(5000)

    # Verify all lines finalized
    * def lineStatuses = db.query("SELECT DISTINCT status FROM dbo.receiptdetail WHERE receiptkey = '" + receiptKey + "'")
    * match lineStatuses.get(0).get('status') == '9'

  # ─────────────────────────────────────────────────────────────
  # F3-TC03: Finalize receipt via RDT API
  # ─────────────────────────────────────────────────────────────
  @F3-TC03 @P1 @RDT
  Scenario: Finalize receipt via RDT handheld device
    * def receiptKey = 'RCV-HAPPY-003'
    * def deviceId = 'RDT-DEVICE-001'
    * def userId = 'RDT_USER_001'

    # RDT finalize endpoint
    Given path api + '/rdt/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = deviceId
    And header X-RDT-User-Id = userId
    And header Content-Type = 'application/json'
    And request { "confirmLocation": "KR01-RCV-DOCK-01" }
    When method post
    Then status 200
    And match response.status == 'FINALIZED'
    And match response.finalizedBy == userId
    And match response.device == deviceId

    # Verify audit trail
    * def audit = db.query("SELECT * FROM dbo.receiptaudit WHERE receiptkey = '" + receiptKey + "' AND action = 'FINALIZE'")
    * match audit.get(0).get('userid') == userId

  # ─────────────────────────────────────────────────────────────
  # F3-TC04: Finalize triggers putaway task creation
  # ─────────────────────────────────────────────────────────────
  @F3-TC04 @P1 @Putaway
  Scenario: Finalize creates putaway tasks
    * def receiptKey = 'RCV-HAPPY-004'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "createPutawayTasks": true }
    When method post
    Then status 200
    And match response.putawayTasksCreated == '#? _ >= 1'

    # Wait for task creation
    * sleep(3000)

    # Verify putaway tasks in DB
    * def tasks = db.query("SELECT * FROM dbo.task WHERE fromkey = '" + receiptKey + "' AND tasktype = 'PUTAWAY'")
    * assert tasks.size() >= 1
    * match tasks.get(0).get('status') == '0'

  # ─────────────────────────────────────────────────────────────
  # F3-TC05: Finalize receipt with lottable tracking
  # ─────────────────────────────────────────────────────────────
  @F3-TC05 @P1 @Lottable @Nike
  Scenario: Finalize with lottable fields preserved
    * def receiptKey = 'RCV-HAPPY-NIKE-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200

    * sleep(3000)

    # Verify lottables in inventory
    * def invResult = db.query("SELECT lottable01, lottable02, lottable03 FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "'")
    * match invResult.get(0).get('lottable01') == '#present'
    * match invResult.get(0).get('lottable02') == '#present'
    * match invResult.get(0).get('lottable03') == '#present'

  # ─────────────────────────────────────────────────────────────
  # F3-TC06: Finalize updates PO received quantities
  # ─────────────────────────────────────────────────────────────
  @F3-TC06 @P1 @POUpdate
  Scenario: Finalize updates PO received quantities
    * def receiptKey = 'RCV-HAPPY-005'

    # Get PO before finalize
    * def beforeQty = db.getValue("SELECT qtyreceived FROM dbo.podetail WHERE pokey = 'PO-HAPPY-001' AND polinenumber = '00001'")

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200

    * sleep(3000)

    # Verify PO quantities updated
    * def afterQty = db.getValue("SELECT qtyreceived FROM dbo.podetail WHERE pokey = 'PO-HAPPY-001' AND polinenumber = '00001'")
    * assert afterQty > beforeQty

  # ─────────────────────────────────────────────────────────────
  # F3-TC07: Finalize with specific target location
  # ─────────────────────────────────────────────────────────────
  @F3-TC07 @P2 @Location
  Scenario: Finalize to specific target location
    * def receiptKey = 'RCV-HAPPY-006'
    * def targetLoc = 'KR01-STOR-A01'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "targetLocation": "#(targetLoc)" }
    When method post
    Then status 200
    And match response.targetLocation == targetLoc

    * sleep(3000)

    # Verify inventory in target location
    * def invLoc = db.getValue("SELECT loc FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "'")
    * match invLoc == targetLoc

  # ─────────────────────────────────────────────────────────────
  # F3-TC08: Finalize via DB trigger (status change)
  # ─────────────────────────────────────────────────────────────
  @F3-TC08 @P1 @Trigger
  Scenario: Finalize triggered by status change
    * def receiptKey = 'RCV-TRIGGER-001'

    # Simulate status change via direct DB update (trigger should fire)
    * def updateSql = "UPDATE dbo.receipt SET status = '9' WHERE receiptkey = '" + receiptKey + "'"
    * db.execute(updateSql)

    * sleep(5000)

    # Verify trigger processed finalization
    * def invResult = db.query("SELECT * FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "'")
    * assert invResult.size() >= 1

    # Verify audit log shows trigger
    * def audit = db.query("SELECT * FROM dbo.receiptaudit WHERE receiptkey = '" + receiptKey + "' AND action = 'FINALIZE'")
    * match audit.get(0).get('source') == 'TRIGGER'

  # ─────────────────────────────────────────────────────────────
  # F3-TC09: Finalize closes PO when fully received
  # ─────────────────────────────────────────────────────────────
  @F3-TC09 @P1 @POClose
  Scenario: Finalize closes PO when fully received
    * def receiptKey = 'RCV-FULL-001'
    * def poKey = 'PO-CLOSE-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "closePoIfComplete": true }
    When method post
    Then status 200
    And match response.poClosedAutomatically == true

    * sleep(3000)

    # Verify PO status
    * def poStatus = db.getValue("SELECT status FROM dbo.po WHERE pokey = '" + poKey + "'")
    * match poStatus == '9'

  # ─────────────────────────────────────────────────────────────
  # F3-TC10: Finalize with quality hold
  # ─────────────────────────────────────────────────────────────
  @F3-TC10 @P2 @QualityHold
  Scenario: Finalize with quality hold applies hold code
    * def receiptKey = 'RCV-QC-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "qualityHold": true, "holdCode": "QC_PENDING" }
    When method post
    Then status 200
    And match response.holdApplied == true

    * sleep(3000)

    # Verify hold on inventory
    * def holdResult = db.query("SELECT holdcode FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "'")
    * match holdResult.get(0).get('holdcode') == 'QC_PENDING'

