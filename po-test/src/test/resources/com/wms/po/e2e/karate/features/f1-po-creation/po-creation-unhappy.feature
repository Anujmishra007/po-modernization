@F1 @POCreation @API @Unhappy @Regression
Feature: F1 - PO Creation Unhappy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - Unhappy Path Scenarios
  # Tests: F1-TC05 to F1-TC08
  # Entry Points: API
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token

  # ─────────────────────────────────────────────────────────────
  # F1-TC05: Duplicate PO key
  # Error Code: VAL_003 (69102)
  # ─────────────────────────────────────────────────────────────
  @F1-TC05 @P1 @VAL_003
  Scenario: Duplicate PO key returns error
    # First create a PO
    * def poRequest = testData.validPORequest()
    * def poKey = generatePoKey()
    * poRequest.externalOrderKey = poKey

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    * def createdPoKey = response.externalOrderKey

    # Try to create another PO with same external key
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 409
    And match response.errorCode == 'VAL_003'
    And match response.message == '#? _.toLowerCase().indexOf("duplicate") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC06: Missing required field (storerKey)
  # Error Code: VAL_001 (69100)
  # ─────────────────────────────────────────────────────────────
  @F1-TC06 @P1 @VAL_001
  Scenario: Missing storerKey returns validation error
    * def poRequest =
      """
      {
        "facility": "TEST01",
        "externalOrderKey": "MISSING-STORER-001",
        "lines": [
          { "sku": "TEST-SKU-001", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 400
    And match response.errorCode == 'VAL_001'
    And match response.message == '#? _.indexOf("storerKey") >= 0'
    And match response.field == 'storerKey'

  # ─────────────────────────────────────────────────────────────
  # F1-TC07: Invalid SKU in PO line
  # Error Code: PO_013 (68813)
  # ─────────────────────────────────────────────────────────────
  @F1-TC07 @P1 @PO_013
  Scenario: Invalid SKU returns error
    * def poRequest = testData.validPORequest()
    * poRequest.lines[0].sku = 'INVALID-SKU-999'

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 422
    And match response.errorCode == 'PO_013'
    And match response.message == '#? _.indexOf("SKU") >= 0'
    And match response.details.sku == 'INVALID-SKU-999'

  # ─────────────────────────────────────────────────────────────
  # F1-TC08: Invalid supplier
  # Error Code: PO_012 (68812)
  # ─────────────────────────────────────────────────────────────
  @F1-TC08 @P2 @PO_012
  Scenario: Invalid supplier returns error
    * def poRequest = testData.validPORequest()
    * poRequest.supplierKey = 'INVALID-SUPPLIER-999'

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 422
    And match response.errorCode == 'PO_012'
    And match response.message == '#? _.indexOf("Supplier") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC19: Inactive storer
  # Error Code: VAL_002 (69101)
  # ─────────────────────────────────────────────────────────────
  @F1-TC19 @P2 @VAL_002
  Scenario: Inactive storer returns error
    * def poRequest = testData.validPORequest('TEST_STORER_ERR')

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 422
    And match response.errorCode == 'VAL_002'
    And match response.message == '#? _.toLowerCase().indexOf("inactive") >= 0'
