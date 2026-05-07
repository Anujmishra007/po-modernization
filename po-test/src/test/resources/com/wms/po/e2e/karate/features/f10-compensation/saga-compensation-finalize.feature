@F10 @Compensation @Finalize @Regression
Feature: F10 - Finalize Flow Compensation Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 10: Compensation - Finalize Failure Scenarios
  # Tests: COMP-08, COMP-10 to COMP-12, COMP-14
  # Purpose: Test saga pattern rollback for finalize failures
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # COMP-08: Status update fails during finalize
  # Expected: Previous status restored
  # ─────────────────────────────────────────────────────────────
  @COMP-08 @P1 @StatusUpdateFail
  Scenario: Compensation when status update fails
    * def receiptKey = 'RCV-COMP-STATUS-001'

    # Get original status
    * def originalStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '3'
    And request {}
    When method post
    Then status 422
    And match response.failedStep == 'STATUS_UPDATE'
    And match response.compensated == true

    # Verify status not changed
    * def currentStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match currentStatus == originalStatus

  # ─────────────────────────────────────────────────────────────
  # COMP-10: Hold application fails during finalize
  # Expected: Status and inventory rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-10 @P1 @HoldApplyFail
  Scenario: Compensation when hold application fails
    * def receiptKey = 'RCV-COMP-HOLD-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '5'
    And request { "applyQualityHold": true, "holdCode": "QC_PENDING" }
    When method post
    Then status 422
    And match response.failedStep == 'HOLD_APPLICATION'
    And match response.compensated == true
    And match response.compensatedSteps contains ['INVENTORY_POSTING', 'STATUS_UPDATE']

    # Verify no inventory with hold
    * def invCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "' AND holdcode = 'QC_PENDING'")
    * match invCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-11: PO quantity update fails
  # Expected: Receipt finalize rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-11 @P1 @POQtyUpdateFail
  Scenario: Compensation when PO quantity update fails
    * def receiptKey = 'RCV-COMP-POQTY-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '6'
    And request { "updatePOQty": true }
    When method post
    Then status 422
    And match response.failedStep == 'PO_QTY_UPDATE'
    And match response.compensated == true

    # Verify receipt not finalized
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus != '9'

  # ─────────────────────────────────────────────────────────────
  # COMP-12: Putaway release fails
  # Expected: Inventory rolled back, receipt status reverted
  # ─────────────────────────────────────────────────────────────
  @COMP-12 @P1 @PutawayReleaseFail
  Scenario: Compensation when putaway release fails
    * def receiptKey = 'RCV-COMP-PUTAWAY-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '7'
    And request { "createPutawayTasks": true }
    When method post
    Then status 422
    And match response.failedStep == 'PUTAWAY_RELEASE'
    And match response.compensated == true

    # Verify no orphan tasks
    * def taskCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.task WHERE fromkey = '" + receiptKey + "' AND status = '0'")
    * match taskCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-14: User cancellation during finalize
  # Expected: All completed steps rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-14 @P1 @FinalizeCancelByUser
  Scenario: Compensation on user cancellation during finalize
    * def receiptKey = 'RCV-COMP-FCANCEL-001'

    # Start async finalize
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "async": true }
    When method post
    Then status 202
    * def workflowId = response.workflowId

    # Immediately cancel
    Given path api + '/workflows/' + workflowId + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "User cancelled" }
    When method post
    Then status 200

    * sleep(5000)

    # Verify receipt not finalized
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus != '9'

  # ─────────────────────────────────────────────────────────────
  # COMP-28: Compensation order verification
  # Expected: Steps compensated in reverse order
  # ─────────────────────────────────────────────────────────────
  @COMP-28 @P2 @CompensationOrder
  Scenario: Compensation executes in correct reverse order
    * def receiptKey = 'RCV-COMP-ORDER-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '6'
    And request { "createPutawayTasks": true, "applyQualityHold": true }
    When method post
    Then status 422
    And match response.compensated == true

    # Verify compensation order (reverse of execution)
    And match response.compensatedSteps[0] == 'PUTAWAY_RELEASE'
    And match response.compensatedSteps[1] == 'HOLD_APPLICATION'
    And match response.compensatedSteps[2] == 'INVENTORY_POSTING'
    And match response.compensatedSteps[3] == 'STATUS_UPDATE'

  # ─────────────────────────────────────────────────────────────
  # COMP-29: Audit trail after compensation
  # Expected: Complete audit of original and compensation actions
  # ─────────────────────────────────────────────────────────────
  @COMP-29 @P2 @CompensationAudit
  Scenario: Audit trail includes compensation details
    * def receiptKey = 'RCV-COMP-AUDIT-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '5'
    And request {}
    When method post
    Then status 422

    # Check audit trail
    * def auditResult = db.query("SELECT * FROM dbo.compensationaudit WHERE entitykey = '" + receiptKey + "' ORDER BY auditdate")
    * match auditResult.length >= 2

    # Should have both EXECUTE and COMPENSATE actions
    * def actions = karate.map(auditResult, function(x) { return x.action })
    * match actions contains ['EXECUTE', 'COMPENSATE']

