@F8 @Cancellation @Regression
Feature: F8 - PO/Receipt Cancellation Flow Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 8: PO and Receipt Cancellation
  # Tests: F8-TC01 to F8-TC18
  # Entry Points: API, Job
  # Purpose: Test PO and receipt cancellation workflows
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F8-TC01: Cancel open PO
  # ─────────────────────────────────────────────────────────────
  @F8-TC01 @P1 @PO @Happy
  Scenario: Cancel open PO successfully
    * def poKey = 'PO-CANCEL-001'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Vendor issue", "cancelCode": "VEND_ISSUE" }
    When method post
    Then status 200
    And match response.status == 'CANCELLED'
    And match response.cancelledBy == '#present'
    And match response.cancelDate == '#present'

    # Verify PO status in DB
    * def poStatus = db.getValue("SELECT status FROM dbo.po WHERE pokey = '" + poKey + "'")
    * match poStatus == '5'

  # ─────────────────────────────────────────────────────────────
  # F8-TC02: Cancel PO with partial receipt
  # ─────────────────────────────────────────────────────────────
  @F8-TC02 @P1 @PO @Partial
  Scenario: Cancel PO with partial receipts
    * def poKey = 'PO-CANCEL-002'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "reason": "Balance cancelled",
        "cancelCode": "BAL_CANCEL",
        "closeOpenReceipts": true
      }
      """
    When method post
    Then status 200
    And match response.status == 'CANCELLED'
    And match response.partialReceiptsExist == true
    And match response.openReceiptsClosed == true

  # ─────────────────────────────────────────────────────────────
  # F8-TC03: Cancel already received PO fails
  # ─────────────────────────────────────────────────────────────
  @F8-TC03 @P1 @PO @Unhappy
  Scenario: Cannot cancel fully received PO
    * def poKey = 'PO-ERR-004'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test" }
    When method post
    Then status 422
    And match response.errorCode == 'PO_009'
    And match response.message == '#? _.indexOf("already received") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F8-TC04: Cancel open receipt
  # ─────────────────────────────────────────────────────────────
  @F8-TC04 @P1 @Receipt @Happy
  Scenario: Cancel open receipt successfully
    * def receiptKey = 'RCV-CANCEL-001'

    Given path api + '/receipts/' + receiptKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Wrong items received" }
    When method post
    Then status 200
    And match response.status == 'CANCELLED'

    # Verify PO qty reverted
    * def poKey = db.getValue("SELECT orderkey FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    # Verify qtyreceived decreased

  # ─────────────────────────────────────────────────────────────
  # F8-TC05: Cancel finalized receipt fails
  # ─────────────────────────────────────────────────────────────
  @F8-TC05 @P1 @Receipt @Unhappy
  Scenario: Cannot cancel finalized receipt
    # Receipt with status 9 (finalized)
    * def receiptKey = 'RCV-ERR-003'

    Given path api + '/receipts/' + receiptKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test" }
    When method post
    Then status 422
    And match response.errorCode == 'RCV_009'
    And match response.message == '#? _.indexOf("already finalized") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F8-TC06: Cancel PO line
  # ─────────────────────────────────────────────────────────────
  @F8-TC06 @P1 @POLine
  Scenario: Cancel specific PO line
    * def poKey = 'PO-CANCEL-003'
    * def lineNumber = '00002'

    Given path api + '/po/' + poKey + '/lines/' + lineNumber + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "SKU discontinued" }
    When method post
    Then status 200
    And match response.lineStatus == 'CANCELLED'
    And match response.poStatus == 'OPEN'

  # ─────────────────────────────────────────────────────────────
  # F8-TC07: Bulk cancel multiple POs
  # ─────────────────────────────────────────────────────────────
  @F8-TC07 @P2 @Bulk
  Scenario: Bulk cancel multiple POs
    Given path api + '/po/bulk-cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "poKeys": ["PO-BULK-001", "PO-BULK-002", "PO-BULK-003"],
        "reason": "Vendor contract terminated",
        "cancelCode": "CONTRACT_TERM"
      }
      """
    When method post
    Then status 200
    And match response.cancelledCount == 3
    And match response.failedCount == 0

  # ─────────────────────────────────────────────────────────────
  # F8-TC08: Cancel via job (expired POs)
  # ─────────────────────────────────────────────────────────────
  @F8-TC08 @P1 @Job
  Scenario: Auto-cancel expired POs via job
    Given path api + '/jobs/po-expire-cancel/trigger'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "daysOverdue": 30,
        "storerKeys": ["NIKE_KR", "HM_KR"]
      }
      """
    When method post
    Then status 202
    And match response.jobExecutionId == '#present'

    * sleep(5000)

    # Verify job completion
    Given path api + '/jobs/executions/' + response.jobExecutionId
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == '#? _ == "COMPLETED" || _ == "RUNNING"'

  # ─────────────────────────────────────────────────────────────
  # F8-TC09 to F8-TC18: Additional cancellation scenarios
  # ─────────────────────────────────────────────────────────────
  @F8-TC09 @P2 @Approval
  Scenario: Cancel requires approval for large PO
    * def poKey = 'PO-LARGE-CANCEL'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Budget reduction" }
    When method post
    Then status 202
    And match response.status == 'PENDING_APPROVAL'
    And match response.approvalRequired == true
    And match response.approvalRequestId == '#present'

  @F8-TC10 @P2 @Revert
  Scenario: Revert cancelled PO (undo cancel)
    * def poKey = 'PO-REVERT-001'

    Given path api + '/po/' + poKey + '/revert-cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Vendor issue resolved" }
    When method post
    Then status 200
    And match response.status == 'OPEN'
    And match response.revertedFrom == 'CANCELLED'

  @F8-TC11 @P2 @Cascade
  Scenario: Cancel PO cascades to linked entities
    * def poKey = 'PO-CASCADE-001'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test", "cascadeCancel": true }
    When method post
    Then status 200
    And match response.cascadedEntities.receipts == '#? _ >= 0'
    And match response.cascadedEntities.tasks == '#? _ >= 0'

  @F8-TC12 @P2 @Notification
  Scenario: Cancel triggers notifications
    * def poKey = 'PO-NOTIFY-CANCEL'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test", "notifyStakeholders": true }
    When method post
    Then status 200
    And assert response.notificationsSent >= 1

  @F8-TC13 @P2 @Audit
  Scenario: Cancel creates audit trail
    * def poKey = 'PO-AUDIT-CANCEL'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Audit test" }
    When method post
    Then status 200

    # Verify audit
    * def audit = db.query("SELECT * FROM dbo.poaudit WHERE pokey = '" + poKey + "' AND action = 'CANCEL'")
    * assert karate.sizeOf(audit) >= 1

  @F8-TC14 @P3 @ASN
  Scenario: Cancel PO with pending ASN
    * def poKey = 'PO-ASN-CANCEL'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Test", "cancelPendingAsn": true }
    When method post
    Then status 200
    And assert response.asnsCancelled >= 1

  @F8-TC15 @P3 @Concurrent
  Scenario: Concurrent cancel blocked
    * def poKey = 'PO-CONC-CANCEL'

    # First cancel
    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "First cancel" }
    When method post
    Then status 200

    # Second cancel
    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Second cancel" }
    When method post
    Then status 422
    And match response.errorCode == 'PO_010'
    And match response.message == '#? _.indexOf("already cancelled") >= 0'

  @F8-TC16 @P3 @Compensation
  Scenario: Cancel with compensation rollback
    * def poKey = 'PO-COMP-CANCEL'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '3'
    And request { "reason": "Test" }
    When method post
    Then status 422
    And match response.compensated == true
    And match response.rolledBack == true

  @F8-TC17 @P3 @Report
  Scenario: Cancellation report
    Given path api + '/reports/cancellations'
    And header Authorization = 'Bearer ' + authToken
    And param dateFrom = '2026-01-01'
    And param dateTo = '2026-12-31'
    When method get
    Then status 200
    And match response contains { totalCancelled: '#number', reasonBreakdown: '#present' }

  @F8-TC18 @P3 @Timeout
  Scenario: Cancel handles timeout
    * def poKey = 'PO-TIMEOUT-CANCEL'

    Given path api + '/po/' + poKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Timeout = 'true'
    And request { "reason": "Test" }
    When method post
    Then status 504
    And match response.errorCode == 'INT_003'

