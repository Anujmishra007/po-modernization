@F4 @CrossDock @Unhappy @Regression
Feature: F4 - Cross-Dock Allocation Unhappy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 4: Cross-Dock Allocation - Unhappy Path Scenarios
  # Tests: F4-TC09 to F4-TC15
  # Entry Points: API
  # Purpose: Test error handling for cross-dock allocation
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token

  # ─────────────────────────────────────────────────────────────
  # F4-TC09: Cross-dock with insufficient receipt qty
  # Error Code: XDOCK_001 (66001)
  # ─────────────────────────────────────────────────────────────
  @F4-TC09 @P1 @XDOCK_001
  Scenario: Cross-dock fails with insufficient quantity
    * def receiptKey = 'RCV-XDOCK-001'
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
        "qty": 99999
      }
      """
    When method post
    Then status 422
    And match response.errorCode == 'XDOCK_001'
    And match response.message == '#? _.indexOf("Insufficient quantity") >= 0'
    And assert response.availableQty < 99999

  # ─────────────────────────────────────────────────────────────
  # F4-TC10: Cross-dock for non-existent order
  # Error Code: ORD_001 (67101)
  # ─────────────────────────────────────────────────────────────
  @F4-TC10 @P1 @ORD_001
  Scenario: Cross-dock fails for non-existent order
    * def receiptKey = 'RCV-XDOCK-001'

    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "SO-DOES-NOT-EXIST",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 50
      }
      """
    When method post
    Then status 404
    And match response.errorCode == 'ORD_001'
    And match response.message == '#? _.indexOf("Order not found") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F4-TC11: Cross-dock for already shipped order
  # Error Code: ORD_002 (67102)
  # ─────────────────────────────────────────────────────────────
  @F4-TC11 @P1 @ORD_002
  Scenario: Cross-dock fails for shipped order
    * def receiptKey = 'RCV-XDOCK-001'
    * def orderKey = 'SO-ERR-ALLOC'

    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 25
      }
      """
    When method post
    Then status 422
    And match response.errorCode == 'ORD_002'
    And match response.message == '#? _.indexOf("already shipped") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F4-TC12: Cross-dock SKU mismatch
  # Error Code: XDOCK_002 (66002)
  # ─────────────────────────────────────────────────────────────
  @F4-TC12 @P1 @XDOCK_002
  Scenario: Cross-dock fails for SKU not on order
    * def receiptKey = 'RCV-XDOCK-001'
    * def orderKey = 'SO-HM-001'

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
    Then status 422
    And match response.errorCode == 'XDOCK_002'
    And match response.message == '#? _.indexOf("SKU not on order") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F4-TC13: Cross-dock for different storer
  # Error Code: XDOCK_003 (66003)
  # ─────────────────────────────────────────────────────────────
  @F4-TC13 @P1 @XDOCK_003
  Scenario: Cross-dock fails for different storer
    * def receiptKey = 'RCV-HAPPY-NIKE-001'
    * def orderKey = 'SO-HM-001'

    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)",
        "sku": "HM-BASIC-TEE-M",
        "qty": 100
      }
      """
    When method post
    Then status 422
    And match response.errorCode == 'XDOCK_003'
    And match response.message == '#? _.indexOf("Storer mismatch") >= 0'
    And match response.receiptStorer != response.orderStorer

  # ─────────────────────────────────────────────────────────────
  # F4-TC14: Cross-dock for non-finalized receipt
  # Error Code: XDOCK_004 (66004)
  # ─────────────────────────────────────────────────────────────
  @F4-TC14 @P1 @XDOCK_004
  Scenario: Cross-dock fails for non-finalized receipt
    # Status 0, not finalized
    * def receiptKey = 'RCV-HAPPY-001'
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
        "requireFinalized": true
      }
      """
    When method post
    Then status 422
    And match response.errorCode == 'XDOCK_004'
    And match response.message == '#? _.indexOf("Receipt not finalized") >= 0'
    And match response.receiptStatus != '9'

  # ─────────────────────────────────────────────────────────────
  # F4-TC15: Duplicate cross-dock allocation
  # Error Code: XDOCK_005 (66005)
  # ─────────────────────────────────────────────────────────────
  @F4-TC15 @P1 @XDOCK_005
  Scenario: Duplicate cross-dock allocation fails
    * def receiptKey = 'RCV-XDOCK-001'
    * def orderKey = 'SO-NIKE-001'
    * def allocRequest =
      """
      {
        "receiptKey": "#(receiptKey)",
        "orderKey": "#(orderKey)",
        "sku": "NK-AIRMAX90-BLK",
        "qty": 25
      }
      """

    # First allocation
    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request allocRequest
    When method post
    Then status 201

    # Duplicate allocation
    Given path api + '/xdock/allocate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request allocRequest
    When method post
    Then status 409
    And match response.errorCode == 'XDOCK_005'
    And match response.message == '#? _.indexOf("Duplicate allocation") >= 0'

