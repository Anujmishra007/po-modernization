@F2 @ASNPopulation @EDI @Unhappy @Regression
Feature: F2 - ASN Population Unhappy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 2: ASN Population - Unhappy Path Scenarios
  # Tests: F2-TC09 to F2-TC17
  # Entry Points: EDI, API
  # Purpose: Test error handling and validation for ASN processing
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }

  # ─────────────────────────────────────────────────────────────
  # F2-TC09: EDI 856 with missing mandatory segments
  # Error Code: EDI_001 (65001)
  # ─────────────────────────────────────────────────────────────
  @F2-TC09 @P1 @EDI_001
  Scenario: EDI 856 with missing mandatory segment fails
    * def ediContent = read('classpath:test-data/edi/EDI-850-SAMPLE-004-ERROR-MISSING-SEGMENT.txt')

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And request ediContent
    When method post
    Then status 400
    And match response.errorCode == 'EDI_001'
    And match response.message == '#? _.indexOf("Missing mandatory segment") >= 0'
    And match response.missingSegments contains 'REF*DP'

  # ─────────────────────────────────────────────────────────────
  # F2-TC10: EDI 856 with malformed data
  # Error Code: EDI_002 (65002)
  # ─────────────────────────────────────────────────────────────
  @F2-TC10 @P1 @EDI_002
  Scenario: EDI 856 with malformed data fails parsing
    * def ediContent = read('classpath:test-data/edi/EDI-850-SAMPLE-005-ERROR-MALFORMED.txt')

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And request ediContent
    When method post
    Then status 400
    And match response.errorCode == 'EDI_002'
    And match response.message == '#? _.indexOf("Parse error") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F2-TC11: ASN for non-existent PO
  # Error Code: PO_001 (68801)
  # ─────────────────────────────────────────────────────────────
  @F2-TC11 @P1 @PO_001
  Scenario: ASN referencing non-existent PO fails
    * def asnRequest =
      """
      {
        "poKey": "PO-DOES-NOT-EXIST-999",
        "asnNumber": "#('ASN-INVALID-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 100 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 404
    And match response.errorCode == 'PO_001'
    And match response.message == '#? _.indexOf("PO not found") >= 0'
    And match response.poKey == 'PO-DOES-NOT-EXIST-999'

  # ─────────────────────────────────────────────────────────────
  # F2-TC12: ASN for closed PO
  # Error Code: PO_008 (68808)
  # ─────────────────────────────────────────────────────────────
  @F2-TC12 @P1 @PO_008
  Scenario: ASN for closed PO fails
    * def asnRequest =
      """
      {
        "poKey": "PO-ERR-004",
        "asnNumber": "#('ASN-CLOSED-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 100 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 422
    And match response.errorCode == 'PO_008'
    And match response.message == '#? _.indexOf("PO is closed") >= 0'
    And match response.currentStatus == '9'

  # ─────────────────────────────────────────────────────────────
  # F2-TC13: ASN for cancelled PO
  # Error Code: PO_006 (68806)
  # ─────────────────────────────────────────────────────────────
  @F2-TC13 @P1 @PO_006
  Scenario: ASN for cancelled PO fails
    * def asnRequest =
      """
      {
        "poKey": "PO-ERR-005",
        "asnNumber": "#('ASN-CANCELLED-' + timestamp())",
        "lines": [
          { "sku": "HM-SLIM-JEANS-32", "qtyShipped": 50 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 422
    And match response.errorCode == 'PO_006'
    And match response.message == '#? _.indexOf("cancelled") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F2-TC14: ASN with invalid SKU
  # Error Code: PO_013 (68813)
  # ─────────────────────────────────────────────────────────────
  @F2-TC14 @P1 @PO_013
  Scenario: ASN with invalid SKU fails
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-BADSKU-' + timestamp())",
        "lines": [
          { "sku": "INVALID-SKU-NOT-IN-DB", "qtyShipped": 100 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 422
    And match response.errorCode == 'PO_013'
    And match response.message == '#? _.indexOf("SKU not found") >= 0'
    And match response.invalidSku == 'INVALID-SKU-NOT-IN-DB'

  # ─────────────────────────────────────────────────────────────
  # F2-TC15: ASN over-receipt exceeds tolerance
  # Error Code: RCV_003 (69303)
  # ─────────────────────────────────────────────────────────────
  @F2-TC15 @P1 @RCV_003
  Scenario: ASN over-receipt beyond tolerance fails
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-OVERTOL-' + timestamp())",
        "allowOverReceipt": true,
        "overReceiptTolerance": 5,
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyOrdered": 100,
            "qtyShipped": 150
          }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 422
    And match response.errorCode == 'RCV_003'
    And match response.message == '#? _.indexOf("Over-receipt exceeds tolerance") >= 0'
    And match response.tolerance == 5
    And match response.actualOverage == 50

  # ─────────────────────────────────────────────────────────────
  # F2-TC16: Duplicate ASN number
  # Error Code: ASN_001 (65101)
  # ─────────────────────────────────────────────────────────────
  @F2-TC16 @P1 @ASN_001
  Scenario: Duplicate ASN number fails
    # First ASN
    * def asnNumber = 'ASN-DUP-' + timestamp()
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#(asnNumber)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 50 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 201

    # Try same ASN number again
    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 409
    And match response.errorCode == 'ASN_001'
    And match response.message == '#? _.indexOf("Duplicate ASN") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F2-TC17: ASN with SKU not on PO
  # Error Code: RCV_004 (69304)
  # ─────────────────────────────────────────────────────────────
  @F2-TC17 @P1 @RCV_004
  Scenario: ASN with SKU not on PO fails
    # PO-HAPPY-001 only has Nike SKUs
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-WRONGSKU-' + timestamp())",
        "validateSkuOnPO": true,
        "lines": [
          { "sku": "HM-BASIC-TEE-M", "qtyShipped": 100 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 422
    And match response.errorCode == 'RCV_004'
    And match response.message == '#? _.indexOf("SKU not found on PO") >= 0'

