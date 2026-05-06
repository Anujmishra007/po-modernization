@F6 @PutawayTask @Regression
Feature: F6 - Putaway Task Flow Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 6: Putaway Task Flow
  # Tests: F6-TC01 to F6-TC20
  # Entry Points: RDT, API, Trigger
  # Purpose: Test putaway task creation, assignment, and completion
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F6-TC01: Auto-create putaway task on receipt finalize
  # ─────────────────────────────────────────────────────────────
  @F6-TC01 @P1 @AutoCreate @Happy
  Scenario: Putaway task auto-created on receipt finalize
    * def receiptKey = 'RCV-PUTAWAY-001'

    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "createPutawayTasks": true }
    When method post
    Then status 200
    And match response.putawayTasksCreated >= 1
    * def taskKeys = response.putawayTaskKeys

    # Verify tasks in DB
    * def tasks = db.query("SELECT * FROM dbo.task WHERE fromkey = '" + receiptKey + "' AND tasktype = 'PUTAWAY'")
    * match tasks.length >= 1
    * match tasks[0].status == '0'  # Open

  # ─────────────────────────────────────────────────────────────
  # F6-TC02: Manual putaway task creation
  # ─────────────────────────────────────────────────────────────
  @F6-TC02 @P1 @Manual @Happy
  Scenario: Create putaway task manually via API
    * def taskRequest =
      """
      {
        "taskType": "PUTAWAY",
        "fromLocation": "KR01-RCV-DOCK-01",
        "toLocation": "KR01-STOR-A01",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 100,
        "sourceKey": "RCV-MANUAL-001",
        "priority": 5
      }
      """

    Given path api + '/tasks'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request taskRequest
    When method post
    Then status 201
    And match response.taskKey == '#present'
    And match response.status == 'OPEN'
    And match response.taskType == 'PUTAWAY'

  # ─────────────────────────────────────────────────────────────
  # F6-TC03: Assign putaway task to user via RDT
  # ─────────────────────────────────────────────────────────────
  @F6-TC03 @P1 @RDT @Assignment
  Scenario: Assign putaway task via RDT device
    * def taskKey = 'TASK-PUTAWAY-001'
    * def userId = 'RDT_USER_001'
    * def deviceId = 'RDT-DEVICE-001'

    Given path api + '/rdt/tasks/' + taskKey + '/assign'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = deviceId
    And header X-RDT-User-Id = userId
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.assignedTo == userId
    And match response.status == 'ASSIGNED'

    # Verify assignment in DB
    * def task = db.query("SELECT assignedto, status FROM dbo.task WHERE taskkey = '" + taskKey + "'")
    * match task[0].assignedto == userId
    * match task[0].status == '1'  # Assigned

  # ─────────────────────────────────────────────────────────────
  # F6-TC04: Complete putaway task via RDT
  # ─────────────────────────────────────────────────────────────
  @F6-TC04 @P1 @RDT @Complete
  Scenario: Complete putaway task via RDT scan
    * def taskKey = 'TASK-PUTAWAY-002'
    * def userId = 'RDT_USER_001'
    * def deviceId = 'RDT-DEVICE-001'

    Given path api + '/rdt/tasks/' + taskKey + '/complete'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = deviceId
    And header X-RDT-User-Id = userId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "scannedLocation": "KR01-STOR-A01",
        "scannedQty": 100,
        "confirmationMethod": "SCAN"
      }
      """
    When method post
    Then status 200
    And match response.status == 'COMPLETED'
    And match response.completedBy == userId

    # Verify inventory moved
    * def inv = db.query("SELECT * FROM dbo.lotxlocxid WHERE taskkey = '" + taskKey + "'")
    * match inv[0].loc == 'KR01-STOR-A01'

  # ─────────────────────────────────────────────────────────────
  # F6-TC05: Putaway to suggested location
  # ─────────────────────────────────────────────────────────────
  @F6-TC05 @P1 @LocationSuggestion
  Scenario: Putaway uses system-suggested location
    * def receiptKey = 'RCV-SUGGEST-001'

    # Get suggested location
    Given path api + '/putaway/suggest-location'
    And header Authorization = 'Bearer ' + authToken
    And param sku = 'NK-AIRMAX90-BLK'
    And param qty = 100
    And param facility = 'KR01'
    When method get
    Then status 200
    And match response.suggestedLocation == '#present'
    * def suggestedLoc = response.suggestedLocation

    # Create putaway to suggested location
    * def taskRequest =
      """
      {
        "taskType": "PUTAWAY",
        "fromLocation": "KR01-RCV-DOCK-01",
        "toLocation": "#(suggestedLoc)",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 100,
        "sourceKey": "#(receiptKey)"
      }
      """

    Given path api + '/tasks'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request taskRequest
    When method post
    Then status 201

  # ─────────────────────────────────────────────────────────────
  # F6-TC06: Putaway to invalid location fails
  # ─────────────────────────────────────────────────────────────
  @F6-TC06 @P1 @Unhappy @InvalidLocation
  Scenario: Putaway to non-existent location fails
    * def taskKey = 'TASK-PUTAWAY-003'

    Given path api + '/rdt/tasks/' + taskKey + '/complete'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request
      """
      {
        "scannedLocation": "INVALID-LOC-999",
        "scannedQty": 100
      }
      """
    When method post
    Then status 422
    And match response.errorCode == 'LOC_001'
    And match response.message contains 'Location not found'

  # ─────────────────────────────────────────────────────────────
  # F6-TC07: Putaway to full location fails
  # ─────────────────────────────────────────────────────────────
  @F6-TC07 @P1 @Unhappy @FullLocation
  Scenario: Putaway to full location fails
    * def taskKey = 'TASK-PUTAWAY-004'

    Given path api + '/rdt/tasks/' + taskKey + '/complete'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request
      """
      {
        "scannedLocation": "TEST-LOC-FULL",
        "scannedQty": 100
      }
      """
    When method post
    Then status 422
    And match response.errorCode == 'LOC_002'
    And match response.message contains 'Location full'

  # ─────────────────────────────────────────────────────────────
  # F6-TC08: Partial putaway
  # ─────────────────────────────────────────────────────────────
  @F6-TC08 @P2 @Partial
  Scenario: Partial putaway creates remainder task
    * def taskKey = 'TASK-PUTAWAY-005'

    Given path api + '/rdt/tasks/' + taskKey + '/complete'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request
      """
      {
        "scannedLocation": "KR01-STOR-A01",
        "scannedQty": 50,
        "allowPartial": true
      }
      """
    When method post
    Then status 200
    And match response.status == 'PARTIAL'
    And match response.remainderTaskKey == '#present'
    And match response.completedQty == 50

  # ─────────────────────────────────────────────────────────────
  # F6-TC09: Zone-directed putaway
  # ─────────────────────────────────────────────────────────────
  @F6-TC09 @P2 @Zone
  Scenario: Putaway respects zone restrictions
    * def taskRequest =
      """
      {
        "taskType": "PUTAWAY",
        "fromLocation": "KR01-RCV-DOCK-01",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 100,
        "sourceKey": "RCV-ZONE-001",
        "zoneRestriction": "ZONE-A"
      }
      """

    Given path api + '/tasks'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request taskRequest
    When method post
    Then status 201
    And match response.suggestedZone == 'ZONE-A'

  # ─────────────────────────────────────────────────────────────
  # F6-TC10: Putaway task priority
  # ─────────────────────────────────────────────────────────────
  @F6-TC10 @P2 @Priority
  Scenario: High priority putaway tasks assigned first
    Given path api + '/rdt/tasks/next'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-User-Id = 'RDT_USER_001'
    And param taskType = 'PUTAWAY'
    And param facility = 'KR01'
    When method get
    Then status 200
    And match response.priority <= 3  # High priority

  # ─────────────────────────────────────────────────────────────
  # F6-TC11 to F6-TC20: Additional putaway scenarios
  # ─────────────────────────────────────────────────────────────
  @F6-TC11 @P2 @Batch
  Scenario: Batch putaway multiple tasks
    Given path api + '/tasks/batch-complete'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "tasks": [
          { "taskKey": "TASK-BATCH-001", "location": "KR01-STOR-A01", "qty": 50 },
          { "taskKey": "TASK-BATCH-002", "location": "KR01-STOR-A02", "qty": 50 }
        ]
      }
      """
    When method post
    Then status 200
    And match response.completedCount == 2

  @F6-TC12 @P2 @Cancel
  Scenario: Cancel putaway task
    * def taskKey = 'TASK-CANCEL-001'

    Given path api + '/tasks/' + taskKey + '/cancel'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Wrong location assigned" }
    When method post
    Then status 200
    And match response.status == 'CANCELLED'

  @F6-TC13 @P2 @Reassign
  Scenario: Reassign putaway task
    * def taskKey = 'TASK-REASSIGN-001'

    Given path api + '/tasks/' + taskKey + '/reassign'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "newUser": "RDT_USER_002" }
    When method put
    Then status 200
    And match response.assignedTo == 'RDT_USER_002'

  @F6-TC14 @P2 @Override
  Scenario: Override putaway location
    * def taskKey = 'TASK-OVERRIDE-001'

    Given path api + '/rdt/tasks/' + taskKey + '/complete'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request
      """
      {
        "scannedLocation": "KR01-STOR-B01",
        "overrideLocation": true,
        "overrideReason": "Original location blocked"
      }
      """
    When method post
    Then status 200
    And match response.locationOverridden == true

  @F6-TC15 @P3 @Timeout
  Scenario: Putaway task timeout handling
    * def taskKey = 'TASK-TIMEOUT-001'

    # Simulate timeout during putaway
    Given path api + '/tasks/' + taskKey + '/timeout'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.status == 'TIMED_OUT'
    And match response.reassignmentRequired == true

  @F6-TC16 @P3 @Audit
  Scenario: Putaway audit trail
    * def taskKey = 'TASK-AUDIT-001'

    Given path api + '/tasks/' + taskKey + '/audit'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.events.length >= 1
    And match response.events contains { action: 'CREATED' }

  @F6-TC17 @P3 @Metrics
  Scenario: Putaway performance metrics
    Given path api + '/tasks/metrics'
    And header Authorization = 'Bearer ' + authToken
    And param taskType = 'PUTAWAY'
    And param facility = 'KR01'
    And param dateFrom = '2026-01-01'
    When method get
    Then status 200
    And match response contains { avgCompletionTime: '#number', totalCompleted: '#number' }

  @F6-TC18 @P3 @FIFO
  Scenario: Putaway follows FIFO for task assignment
    Given path api + '/rdt/tasks/queue'
    And header Authorization = 'Bearer ' + authToken
    And param taskType = 'PUTAWAY'
    And param facility = 'KR01'
    When method get
    Then status 200
    # First task should have earliest creation date
    * def firstTaskDate = response.tasks[0].createdDate
    * def secondTaskDate = response.tasks[1].createdDate
    * match firstTaskDate <= secondTaskDate

  @F6-TC19 @P3 @Interleave
  Scenario: Interleaved putaway with picking
    Given path api + '/tasks/interleaved-assignment'
    And header Authorization = 'Bearer ' + authToken
    And param userId = 'RDT_USER_001'
    And param facility = 'KR01'
    When method get
    Then status 200
    And match response.nextTask.taskType == '#? _ == "PUTAWAY" || _ == "PICK"'

  @F6-TC20 @P3 @Consolidation
  Scenario: Putaway task consolidation
    Given path api + '/tasks/consolidate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "taskKeys": ["TASK-CONSOL-001", "TASK-CONSOL-002"],
        "targetLocation": "KR01-STOR-A01"
      }
      """
    When method post
    Then status 200
    And match response.consolidatedTaskKey == '#present'

