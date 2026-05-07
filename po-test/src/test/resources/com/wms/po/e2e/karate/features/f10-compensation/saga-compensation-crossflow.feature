@F10 @Compensation @CrossFlow @Regression
Feature: F10 - Cross-Flow Compensation Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 10: Compensation - Cross-Flow Scenarios
  # Tests: COMP-21 to COMP-25, COMP-27
  # Purpose: Test compensation across multiple flows
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # COMP-21: XDock allocation rollback
  # Expected: XDock allocations removed on failure
  # ─────────────────────────────────────────────────────────────
  @COMP-21 @P1 @XDockRollback
  Scenario: Compensation rolls back cross-dock allocations
    * def receiptKey = 'RCV-COMP-XDOCK-001'

    # Finalize with XDock enabled
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '8'
    And request { "enableCrossDock": true }
    When method post
    Then status 422
    And match response.failedStep == 'POST_FINALIZE_XDOCK'
    And match response.compensated == true
    And match response.compensatedSteps contains 'XDOCK_ALLOCATION'

    # Verify XDock allocations removed
    * def allocCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.allocation WHERE sourcekey = '" + receiptKey + "' AND alloctype = 'XDOCK' AND status = 'ACTIVE'")
    * match allocCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-22: Lottable rule failure compensation
  # Expected: Receipt created without lottables, warning logged
  # ─────────────────────────────────────────────────────────────
  @COMP-22 @P1 @LottableRuleRollback
  Scenario: Compensation on lottable rule failure
    * def poKey = 'PO-COMP-LOTTABLE-001'

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = 'LOTTABLE_MAPPING'
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
    And match response.failedStep == 'LOTTABLE_MAPPING'
    And match response.compensated == true

    # Verify no receipt with partial lottables
    * def receiptCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.receipt WHERE orderkey = '" + poKey + "' AND status != 'X'")
    * match receiptCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-23: Putaway task rollback
  # Expected: Orphan tasks cleaned up
  # ─────────────────────────────────────────────────────────────
  @COMP-23 @P1 @PutawayTaskRollback
  Scenario: Compensation cleans up orphan putaway tasks
    * def receiptKey = 'RCV-COMP-PUTAWAY-TASK-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '9'
    And request { "createPutawayTasks": true }
    When method post
    Then status 422
    And match response.compensated == true
    And match response.tasksDeleted >= 0

    # Verify no orphan tasks
    * def taskCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.task WHERE fromkey = '" + receiptKey + "' AND tasktype = 'PUTAWAY' AND status = '0'")
    * match taskCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-24: Plugin failure compensation
  # Expected: Plugin side effects rolled back
  # ─────────────────────────────────────────────────────────────
  @COMP-24 @P1 @PluginRollback
  Scenario: Compensation on client plugin failure
    * def receiptKey = 'RCV-COMP-PLUGIN-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'NIKE_KR'
    And header Content-Type = 'application/json'
    And header X-Test-Fail-Plugin = 'NikePostFinalizePlugin'
    And request {}
    When method post
    Then status 422
    And match response.failedPlugin == 'NikePostFinalizePlugin'
    And match response.compensated == true
    And match response.pluginCompensated == true

    # Verify plugin side effects rolled back
    * def pluginDataCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.nikecustomdata WHERE receiptkey = '" + receiptKey + "'")
    * match pluginDataCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # COMP-25: Job failure compensation
  # Expected: Job marks batch as failed, individual items rollback
  # ─────────────────────────────────────────────────────────────
  @COMP-25 @P1 @JobRollback
  Scenario: Compensation on batch job failure
    Given path api + '/jobs/auto-finalize/trigger'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "batchSize": 10,
        "simulateFailureAt": 5
      }
      """
    When method post
    Then status 202
    * def jobId = response.jobExecutionId

    * sleep(10000)

    # Verify job partially completed with rollback
    Given path api + '/jobs/executions/' + jobId
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == 'COMPLETED_WITH_ERRORS'
    And match response.successCount == 4
    And match response.failedCount == 1
    And match response.compensatedCount == 1

  # ─────────────────────────────────────────────────────────────
  # COMP-27: Cascading compensation across flows
  # Expected: Full E2E compensation chain executed
  # ─────────────────────────────────────────────────────────────
  @COMP-27 @P1 @CascadingCompensation
  Scenario: Cascading compensation across PO->Populate->Finalize
    * def poKey = 'PO-COMP-CASCADE-001'

    # Create PO successfully
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = poKey

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    * def createdPoKey = response.poKey

    # Populate successfully
    Given path api + '/po/' + createdPoKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "receiptDetails": [{ "sku": "NK-AIRMAX90-BLK", "qtyReceived": 100 }] }
    When method post
    Then status 201
    * def receiptKey = response.receiptKey

    # Finalize fails - should cascade compensation back
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Trigger-Full-Cascade-Compensation = 'true'
    And request {}
    When method post
    Then status 422
    And match response.cascadeCompensation == true
    And match response.compensatedFlows contains ['FINALIZE', 'POPULATE']

    # Verify full cascade - receipt gone, PO back to original state
    * def receiptCheck = db.query("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptCheck[0].status == 'X'

    * def poStatus = db.getValue("SELECT status FROM dbo.po WHERE pokey = '" + createdPoKey + "'")
    * match poStatus == '0'

  # ─────────────────────────────────────────────────────────────
  # COMP-33: End-to-end saga with multiple participants
  # Expected: All saga participants compensated in correct order
  # ─────────────────────────────────────────────────────────────
  @COMP-33 @P1 @E2ESaga
  Scenario: Full E2E saga test with all participants
    * def poKey = 'PO-E2E-SAGA-' + java.lang.System.currentTimeMillis()

    # Create full workflow via saga orchestrator
    Given path api + '/saga/po-to-inventory'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalPoKey": "#(poKey)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ],
        "autoPopulate": true,
        "autoFinalize": true,
        "failAtStep": "PUTAWAY_CREATE"
      }
      """
    When method post
    Then status 422
    And match response.sagaStatus == 'COMPENSATED'
    And match response.participantsExecuted == ['PO_CREATE', 'POPULATE', 'FINALIZE']
    And match response.participantsCompensated == ['FINALIZE', 'POPULATE', 'PO_CREATE']

    # Verify complete cleanup
    * def cleanup = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + poKey + "' AND status NOT IN ('X', 'COMPENSATED')")
    * match cleanup[0].cnt == 0

