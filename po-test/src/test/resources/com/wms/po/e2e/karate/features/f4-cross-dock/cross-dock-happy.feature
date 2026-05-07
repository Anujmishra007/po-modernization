@F4 @CrossDock @Happy @Regression
Feature: F4 - Cross-Dock Allocation Happy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 4: Cross-Dock Allocation - Happy Path Scenarios
  # Tests: F4-TC01 to F4-TC08
  # Entry Points: API, Trigger
  # Purpose: Allocate inbound receipts directly to outbound orders
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F4-TC01: Basic cross-dock allocation
  # ─────────────────────────────────────────────────────────────
  @F4-TC01 @P1 @API
  Scenario: Basic cross-dock allocation successful
    * def receiptKey = 'RCV-XDOCK-001'
    * def orderKey = 'XDOCK-SO-001'

    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 50
      }
      """
    When method post
    Then status 201
    And match response.allocationKey == '#present'
    And match response.status == 'ALLOCATED'
    And match response.allocType == 'XDOCK'

    # Verify allocation in DB
    * def alloc = db.query("SELECT * FROM dbo.allocation WHERE receiptkey = '" + receiptKey + "' AND orderkey = '" + orderKey + "'")
    * match alloc[0].qty == 50
    * match alloc[0].status == '1'

  # ─────────────────────────────────────────────────────────────
  # F4-TC02: Full receipt cross-dock
  # ─────────────────────────────────────────────────────────────
  @F4-TC02 @P1 @FullAllocation
  Scenario: Allocate full receipt quantity to order
    * def receiptKey = 'RCV-XDOCK-002'
    * def orderKey = 'XDOCK-SO-002'

    Given path api + '/xdock/allocate-full'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)"
      }
      """
    When method post
    Then status 201
    And match response.allocations.length >= 1
    And match response.totalQtyAllocated == '#? _ > 0'

    # Verify receipt marked as XDock
    * def receipt = db.query("SELECT xdockflag FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match receipt[0].xdockflag == '1'

  # ─────────────────────────────────────────────────────────────
  # F4-TC03: Multi-order cross-dock split
  # ─────────────────────────────────────────────────────────────
  @F4-TC03 @P1 @MultiOrder
  Scenario: Split receipt across multiple orders
    * def receiptKey = 'RCV-XDOCK-003'

    Given path api + '/xdock/allocate-multi'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "allocations": [
          { "orderKey": "SO-NIKE-001", "sku": "NK-AIRMAX90-BLK", "qty": 50 },
          { "orderKey": "SO-NIKE-002", "sku": "NK-AIRMAX90-BLK", "qty": 50 }
        ]
      }
      """
    When method post
    Then status 201
    And match response.allocations.length == 2
    And match response.allocations[0].status == 'ALLOCATED'
    And match response.allocations[1].status == 'ALLOCATED'

  # ─────────────────────────────────────────────────────────────
  # F4-TC04: Priority-based cross-dock allocation
  # ─────────────────────────────────────────────────────────────
  @F4-TC04 @P1 @Priority
  Scenario: Cross-dock allocates to highest priority orders first
    * def receiptKey = 'RCV-XDOCK-004'

    Given path api + '/xdock/auto-allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "strategy": "PRIORITY_FIRST",
        "sku": "NK-AIRMAX90-BLK"
      }
      """
    When method post
    Then status 200
    And match response.allocations.length >= 1
    # First allocation should be highest priority
    And match response.allocations[0].orderPriority == '#? _ <= 3'

  # ─────────────────────────────────────────────────────────────
  # F4-TC05: FIFO cross-dock allocation
  # ─────────────────────────────────────────────────────────────
  @F4-TC05 @P2 @FIFO
  Scenario: Cross-dock uses FIFO for order allocation
    * def receiptKey = 'RCV-XDOCK-005'

    Given path api + '/xdock/auto-allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "strategy": "FIFO",
        "sku": "HM-BASIC-TEE-M"
      }
      """
    When method post
    Then status 200
    And match response.allocations.length >= 1
    # Verify FIFO order (oldest order first)
    * def firstOrderDate = response.allocations[0].orderDate
    * def secondOrderDate = response.allocations[1].orderDate
    * match firstOrderDate <= secondOrderDate

  # ─────────────────────────────────────────────────────────────
  # F4-TC06: Cross-dock via XDock linkage table
  # ─────────────────────────────────────────────────────────────
  @F4-TC06 @P1 @Linkage
  Scenario: Cross-dock using pre-defined linkage
    * def poKey = 'PO-HAPPY-001'
    * def receiptKey = 'RCV-XDOCK-006'

    Given path api + '/xdock/process-linkage'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "useLinkageTable": true
      }
      """
    When method post
    Then status 200
    And match response.linksProcessed >= 1

    # Verify xdocklinkage updated
    * def link = db.query("SELECT status FROM dbo.xdocklinkage WHERE pokey = '" + poKey + "'")
    * match link[0].status == '1'

  # ─────────────────────────────────────────────────────────────
  # F4-TC07: Cross-dock creates pick task
  # ─────────────────────────────────────────────────────────────
  @F4-TC07 @P1 @PickTask
  Scenario: Cross-dock allocation creates pick task
    * def receiptKey = 'RCV-XDOCK-007'
    * def orderKey = 'SO-NIKE-001'

    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 50,
        "createPickTask": true
      }
      """
    When method post
    Then status 201
    And match response.pickTaskCreated == true
    And match response.pickTaskKey == '#present'

    # Verify pick task in DB
    * def task = db.query("SELECT * FROM dbo.task WHERE taskkey = '" + response.pickTaskKey + "'")
    * match task[0].tasktype == 'PICK'
    * match task[0].sourcekey == receiptKey

  # ─────────────────────────────────────────────────────────────
  # F4-TC08: Trigger-based auto cross-dock
  # ─────────────────────────────────────────────────────────────
  @F4-TC08 @P1 @Trigger
  Scenario: Receipt finalize triggers auto cross-dock
    * def receiptKey = 'RCV-AUTOXDOCK-001'

    # Finalize receipt with auto-xdock enabled
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "enableAutoCrossDock": true }
    When method post
    Then status 200
    And match response.autoCrossDockTriggered == true

    * sleep(5000)

    # Verify allocations created
    * def allocs = db.query("SELECT * FROM dbo.allocation WHERE sourcekey = '" + receiptKey + "' AND alloctype = 'XDOCK'")
    * match allocs.length >= 1

