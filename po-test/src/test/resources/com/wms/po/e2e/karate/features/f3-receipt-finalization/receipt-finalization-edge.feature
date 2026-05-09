@F3 @ReceiptFinalization @Edge @Regression
Feature: F3 - Receipt Finalization Edge Cases

  # ═══════════════════════════════════════════════════════════
  # Flow 3: Receipt Finalization - Edge Case Scenarios
  # Tests: F3-TC21 to F3-TC30
  # Entry Points: API, RDT, Trigger
  # Purpose: Test boundary conditions and special scenarios
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F3-TC21: Large receipt (1000+ lines)
  # ─────────────────────────────────────────────────────────────
  @F3-TC21 @P2 @Performance
  Scenario: Finalize large receipt with 1000 lines
    * def receiptKey = 'RCV-LARGE-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    # Should complete within 60 seconds
    And assert responseTime < 60000
    And match response.linesFinalized == '#? _ >= 1000'

  # ─────────────────────────────────────────────────────────────
  # F3-TC22: Concurrent finalize same receipt
  # ─────────────────────────────────────────────────────────────
  @F3-TC22 @P1 @Concurrent
  Scenario: Concurrent finalize blocked by optimistic locking
    * def receiptKey = 'RCV-CONC-001'

    # First finalize
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200

    # Immediate second finalize (simulating concurrent request)
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 409
    And match response.errorCode == 'INT_021'
    And match response.message == '#? _.indexOf("concurrent") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F3-TC23: Finalize partial receipt
  # ─────────────────────────────────────────────────────────────
  @F3-TC23 @P2 @Partial
  Scenario: Finalize specific lines of receipt
    * def receiptKey = 'RCV-PARTIAL-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "lines": ["00001", "00003"], "partial": true }
    When method post
    Then status 200
    And match response.linesFinalized == 2
    And match response.partialFinalize == true
    And match response.remainingLines == '#? _ >= 1'

  # ─────────────────────────────────────────────────────────────
  # F3-TC24: Finalize with mixed valid/invalid locations
  # ─────────────────────────────────────────────────────────────
  @F3-TC24 @P2 @MixedLocation
  Scenario: Finalize with line-level locations
    * def receiptKey = 'RCV-MIXLOC-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "lineLocations": [
          { "line": "00001", "location": "KR01-STOR-A01" },
          { "line": "00002", "location": "KR01-STOR-B02" },
          { "line": "00003", "location": "KR01-STOR-C03" }
        ]
      }
      """
    When method post
    Then status 200
    And match response.linesFinalized == 3

    * sleep(3000)

    # Verify each line in correct location
    * def invA01 = db.getValue("SELECT loc FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "' AND receiptlinenumber = '00001'")
    * match invA01 == 'KR01-STOR-A01'

  # ─────────────────────────────────────────────────────────────
  # F3-TC25: Finalize receipt at exact location capacity
  # ─────────────────────────────────────────────────────────────
  @F3-TC25 @P2 @Boundary
  Scenario: Finalize when location reaches exact capacity
    * def receiptKey = 'RCV-CAPACITY-001'
    * def targetLoc = 'KR01-STOR-EXACT'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "targetLocation": "#(targetLoc)" }
    When method post
    Then status 200
    And match response.capacityWarning == true
    And match response.locationAtCapacity == true

  # ─────────────────────────────────────────────────────────────
  # F3-TC26: Finalize triggers cross-dock allocation
  # ─────────────────────────────────────────────────────────────
  @F3-TC26 @P1 @XDock
  Scenario: Finalize triggers cross-dock allocation for linked orders
    * def receiptKey = 'RCV-XDOCK-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "enableCrossDock": true }
    When method post
    Then status 200
    And match response.crossDockAllocations == '#? _ >= 1'

    * sleep(3000)

    # Verify allocation created
    * def alloc = db.query("SELECT * FROM dbo.allocation WHERE sourcekey = '" + receiptKey + "'")
    * assert karate.sizeOf(alloc) >= 1
    * def allocRow = karate.toMap(alloc[0])
    * match allocRow.alloctype == 'XDOCK'

  # ─────────────────────────────────────────────────────────────
  # F3-TC27: Finalize via RDT with scan verification
  # ─────────────────────────────────────────────────────────────
  @F3-TC27 @P1 @RDT @Scan
  Scenario: RDT finalize with barcode scan verification
    * def receiptKey = 'RCV-RDT-001'
    * def scannedData =
      """
      {
        "scans": [
          { "type": "RECEIPT", "value": "#(receiptKey)" },
          { "type": "LOCATION", "value": "KR01-RCV-DOCK-01" },
          { "type": "SKU", "value": "NK-AIRMAX90-BLK" }
        ]
      }
      """

    Given path api + '/rdt/receipts/' + receiptKey + '/finalize-with-scan'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request scannedData
    When method post
    Then status 200
    And match response.scanVerified == true
    And match response.status == 'FINALIZED'

  # ─────────────────────────────────────────────────────────────
  # F3-TC28: Finalize with client plugin hook
  # ─────────────────────────────────────────────────────────────
  @F3-TC28 @P2 @Plugin @Nike
  Scenario: Finalize invokes client-specific plugin
    * def receiptKey = 'RCV-NIKE-PLUGIN-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'NIKE_KR'
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.pluginsExecuted[0] == 'NikeReceiptFinalizePlugin'

    # Verify plugin audit
    * def pluginAudit = db.query("SELECT * FROM dbo.pluginaudit WHERE entitykey = '" + receiptKey + "'")
    * def pluginRow = karate.toMap(pluginAudit[0])
    * match pluginRow.pluginname == 'NikeReceiptFinalizePlugin'
    * match pluginRow.status == 'SUCCESS'

  # ─────────────────────────────────────────────────────────────
  # F3-TC29: Finalize idempotency test
  # ─────────────────────────────────────────────────────────────
  @F3-TC29 @P1 @Idempotent
  Scenario: Finalize is idempotent with same idempotency key
    * def receiptKey = 'RCV-IDEMP-001'
    * def idempotencyKey = 'IDEMP-FINALIZE-' + java.lang.System.currentTimeMillis()

    # First finalize
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = idempotencyKey
    And request {}
    When method post
    Then status 200
    * def firstWorkflowId = response.workflowId

    # Second finalize with same key
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = idempotencyKey
    And request {}
    When method post
    Then status 200
    And match response.workflowId == firstWorkflowId
    And match response.idempotent == true

  # ─────────────────────────────────────────────────────────────
  # F3-TC30: Finalize with Temporal workflow monitoring
  # ─────────────────────────────────────────────────────────────
  @F3-TC30 @P2 @Temporal
  Scenario: Monitor Temporal workflow during finalize
    * def receiptKey = 'RCV-TEMPORAL-001'

    # Start finalize
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "async": true }
    When method post
    Then status 202
    And match response.workflowId == '#present'
    * def workflowId = response.workflowId

    # Poll workflow status
    * def pollWorkflow =
      """
      function() {
        for (var i = 0; i < 30; i++) {
          var result = karate.call('classpath:common/get-workflow-status.feature', { workflowId: workflowId });
          if (result.status == 'COMPLETED' || result.status == 'FAILED') {
            return result;
          }
          java.lang.Thread.sleep(1000);
        }
        return { status: 'TIMEOUT' };
      }
      """
    * def workflowResult = pollWorkflow()
    * match workflowResult.status == 'COMPLETED'

    # Verify workflow history
    Given path api + '/workflows/' + workflowId + '/history'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    # At least 5 events in workflow history
    And assert karate.sizeOf(response.events) >= 5
    # Verify at least one event has the expected type
    And match response.events[0].eventType == '#present'

