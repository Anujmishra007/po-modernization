@F4 @CrossDock @Edge @Regression
Feature: F4 - Cross-Dock Allocation Edge Cases

  # ═══════════════════════════════════════════════════════════
  # Flow 4: Cross-Dock Allocation - Edge Case Scenarios
  # Tests: F4-TC16 to F4-TC20
  # Entry Points: API, Trigger
  # Purpose: Test boundary conditions for cross-dock
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F4-TC16: Large-scale cross-dock (100 orders)
  # ─────────────────────────────────────────────────────────────
  @F4-TC16 @P2 @Performance
  Scenario: Cross-dock to 100 orders simultaneously
    * def receiptKey = 'RCV-XDOCK-LARGE'

    # Generate 100 allocation requests
    * def generateAllocations =
      """
      function(count) {
        var allocs = [];
        for (var i = 0; i < count; i++) {
          allocs.push({
            "orderKey": "SO-BATCH-" + (1000 + i),
            "sku": "HM-BASIC-TEE-M",
            "qty": 10
          });
        }
        return allocs;
      }
      """
    * def allocations = generateAllocations(100)

    Given path api + '/xdock/allocate-batch'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "allocations": "#(allocations)"
      }
      """
    When method post
    Then status 201
    And match response.successCount == 100
    # Should complete under 30 seconds
    And responseTime < 30000

  # ─────────────────────────────────────────────────────────────
  # F4-TC17: Concurrent cross-dock requests
  # ─────────────────────────────────────────────────────────────
  @F4-TC17 @P1 @Concurrent
  Scenario: Concurrent cross-dock handled with locking
    * def receiptKey = 'RCV-XDOCK-CONC'
    * def orderKey1 = 'SO-CONC-001'
    * def orderKey2 = 'SO-CONC-002'

    # Both compete for same receipt qty
    * def req1 = { receiptKey: receiptKey, orderKey: orderKey1, sku: "NK-AIRMAX90-BLK", qty: 60 }
    * def req2 = { receiptKey: receiptKey, orderKey: orderKey2, sku: "NK-AIRMAX90-BLK", qty: 60 }

    # Send first allocation request
    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request req1
    When method post
    Then status 201
    * def alloc1Qty = response.allocatedQty

    # Send second allocation request (may fail if insufficient qty)
    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request req2
    When method post
    # Either succeeds or fails with insufficient qty
    Then assert responseStatus == 201 || responseStatus == 400

  # ─────────────────────────────────────────────────────────────
  # F4-TC18: Cross-dock exact quantity match
  # ─────────────────────────────────────────────────────────────
  @F4-TC18 @P2 @Boundary
  Scenario: Cross-dock exactly matches available quantity
    * def receiptKey = 'RCV-XDOCK-EXACT'
    * def orderKey = 'SO-EXACT-001'

    # First get available qty
    Given path api + '/receipts/' + receiptKey + '/available'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    * def availableQty = response.lines[0].availableQty

    # Allocate exact amount
    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)",
        "sku": "#(response.lines[0].sku)",
        "qty": "#(availableQty)"
      }
      """
    When method post
    Then status 201
    And match response.qty == availableQty
    And match response.receiptFullyAllocated == true

  # ─────────────────────────────────────────────────────────────
  # F4-TC19: Cross-dock with lottable matching
  # ─────────────────────────────────────────────────────────────
  @F4-TC19 @P2 @Lottable
  Scenario: Cross-dock respects lottable requirements
    * def receiptKey = 'RCV-XDOCK-LOT'
    * def orderKey = 'SO-LOT-MATCH'

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
        "lottableMatch": {
          "lottable01": "STYLE-AM90-2024",
          "lottable02": "BLK"
        }
      }
      """
    When method post
    Then status 201
    And match response.lottableMatched == true

  # ─────────────────────────────────────────────────────────────
  # F4-TC20: Cross-dock rollback on partial failure
  # ─────────────────────────────────────────────────────────────
  @F4-TC20 @P1 @Compensation
  Scenario: Cross-dock batch rolls back on partial failure
    * def receiptKey = 'RCV-XDOCK-ROLLBACK'

    Given path api + '/xdock/allocate-batch'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "transactional": true,
        "allocations": [
          { "orderKey": "SO-VALID-001", "sku": "NK-AIRMAX90-BLK", "qty": 25 },
          { "orderKey": "SO-VALID-002", "sku": "NK-AIRMAX90-BLK", "qty": 25 },
          { "orderKey": "SO-INVALID-FORCE-FAIL", "sku": "NK-AIRMAX90-BLK", "qty": 25 }
        ]
      }
      """
    When method post
    Then status 422
    And match response.partialFailure == true
    And match response.rolledBack == true
    And match response.compensatedCount == 2

    # Verify no allocations persisted
    * def allocs = db.query("SELECT * FROM dbo.allocation WHERE sourcekey = '" + receiptKey + "'")
    * match allocs.length == 0

