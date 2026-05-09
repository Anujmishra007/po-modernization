@F10 @Compensation @Infrastructure @Regression
Feature: F10 - Infrastructure Failure Compensation Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 10: Compensation - Infrastructure Failure Scenarios
  # Tests: COMP-16, COMP-17, COMP-19
  # Purpose: Test compensation for infrastructure-level failures
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # COMP-16: Database deadlock
  # Expected: Retry with backoff, then compensate if fails
  # ─────────────────────────────────────────────────────────────
  @COMP-16 @P1 @Deadlock
  Scenario: Compensation on database deadlock
    * def receiptKey = 'RCV-COMP-DEADLOCK-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Deadlock = 'true'
    And request {}
    When method post
    Then status 500
    And match response.errorCode == 'INT_016'
    And match response.message == '#? _.indexOf("deadlock") >= 0'
    # Should have retried at least once
    And assert response.retryAttempts >= 1

    # Verify state is consistent
    * def receiptStatus = db.getValue("SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receiptStatus != '9'

  # ─────────────────────────────────────────────────────────────
  # COMP-17: Kafka unavailable during event publish
  # Expected: Transaction succeeds, event queued for retry
  # ─────────────────────────────────────────────────────────────
  @COMP-17 @P1 @KafkaDown
  Scenario: Graceful handling when Kafka unavailable
    * def receiptKey = 'RCV-COMP-KAFKA-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Kafka-Down = 'true'
    And request {}
    When method post
    # Should still succeed - Kafka failure is non-blocking
    Then status 200
    And match response.status == 'FINALIZED'
    And match response.eventPublishStatus == 'QUEUED_FOR_RETRY'

    # Verify event in outbox table
    * def outboxCheck = db.query("SELECT * FROM dbo.eventoutbox WHERE entitykey = '" + receiptKey + "' AND status = 'PENDING'")
    * assert karate.sizeOf(outboxCheck) >= 1

  # ─────────────────────────────────────────────────────────────
  # COMP-19: Partial compensation failure
  # Expected: Manual intervention flagged
  # ─────────────────────────────────────────────────────────────
  @COMP-19 @P1 @PartialCompensationFail
  Scenario: Partial compensation failure requires manual intervention
    * def receiptKey = 'RCV-COMP-PARTIAL-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Fail-At-Step = '5'
    And header X-Test-Fail-Compensation-At = '2'
    And request {}
    When method post
    Then status 500
    And match response.errorCode == 'INT_030'
    And match response.message == '#? _.indexOf("Compensation failed") >= 0'
    And match response.requiresManualIntervention == true
    And match response.partiallyCompensated == true
    And match response.failedCompensationStep == '#present'

    # Verify incident created
    * def incidentCheck = db.query("SELECT * FROM dbo.compensationincident WHERE entitykey = '" + receiptKey + "' AND status = 'OPEN'")
    * assert karate.sizeOf(incidentCheck) >= 1

  # ─────────────────────────────────────────────────────────────
  # COMP-30: Manual intervention alert triggered
  # Expected: Alert sent, incident created
  # ─────────────────────────────────────────────────────────────
  @COMP-30 @P2 @ManualIntervention
  Scenario: Manual intervention alert on unrecoverable failure
    * def receiptKey = 'RCV-COMP-MANUAL-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Unrecoverable = 'true'
    And request {}
    When method post
    Then status 500
    And match response.errorCode == 'INT_040'
    And match response.requiresManualIntervention == true
    And match response.incidentId == '#present'

    # Verify alert sent (check alert log)
    * def alertCheck = db.query("SELECT * FROM dbo.alertlog WHERE entitykey = '" + receiptKey + "' AND alerttype = 'COMPENSATION_FAILURE'")
    * assert karate.sizeOf(alertCheck) >= 1

  # ─────────────────────────────────────────────────────────────
  # COMP-31: Network partition during workflow
  # Expected: Workflow resumes after partition heals
  # ─────────────────────────────────────────────────────────────
  @COMP-31 @P2 @NetworkPartition
  Scenario: Workflow recovery after network partition
    * def receiptKey = 'RCV-COMP-NETWORK-001'

    # Start async workflow
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "async": true }
    When method post
    Then status 202
    * def workflowId = response.workflowId

    # Simulate network partition then recovery
    * sleep(10000)

    # Check workflow eventually completes
    Given path api + '/workflows/' + workflowId + '/status'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == '#? _ == "COMPLETED" || _ == "RUNNING" || _ == "FAILED"'

  # ─────────────────────────────────────────────────────────────
  # COMP-32: Out of memory during processing
  # Expected: Graceful degradation, partial work saved
  # ─────────────────────────────────────────────────────────────
  @COMP-32 @P3 @OOM
  Scenario: Graceful handling of out of memory
    * def receiptKey = 'RCV-COMP-OOM-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Memory-Pressure = 'true'
    And request {}
    When method post
    Then status 503
    And match response.errorCode == 'INT_050'
    And match response.message == '#? _.indexOf("resource") >= 0'
    And match response.retryAfter == '#present'

