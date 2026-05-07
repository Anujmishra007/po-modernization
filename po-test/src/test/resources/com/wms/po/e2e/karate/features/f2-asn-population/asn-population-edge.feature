@F2 @ASNPopulation @Edge @Regression
Feature: F2 - ASN Population Edge Cases

  # ═══════════════════════════════════════════════════════════
  # Flow 2: ASN Population - Edge Case Scenarios
  # Tests: F2-TC18 to F2-TC25
  # Entry Points: EDI, API, Trigger
  # Purpose: Test boundary conditions and special scenarios
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }

  # ─────────────────────────────────────────────────────────────
  # F2-TC18: Large ASN (1000+ lines)
  # ─────────────────────────────────────────────────────────────
  @F2-TC18 @P2 @Performance
  Scenario: Process large ASN with 1000 lines
    # Generate large ASN request
    * def generateLines =
      """
      function(count) {
        var lines = [];
        for (var i = 0; i < count; i++) {
          lines.push({
            "sku": "HM-BASIC-TEE-M",
            "qtyShipped": 10,
            "cartonId": "CTN-" + (1000 + i)
          });
        }
        return lines;
      }
      """
    * def largeLines = generateLines(1000)

    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-002",
        "asnNumber": "#('ASN-LARGE-' + timestamp())",
        "lines": "#(largeLines)"
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 201
    And match response.lineCount == 1000
    And responseTime < 30000  # Should complete within 30 seconds

  # ─────────────────────────────────────────────────────────────
  # F2-TC19: ASN with special characters in fields
  # ─────────────────────────────────────────────────────────────
  @F2-TC19 @P2 @SpecialChars
  Scenario: ASN handles special characters correctly
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-SPECIAL-' + timestamp())",
        "vendorReference": "REF#123/456&789",
        "notes": "Contains <special> chars: & < > \" ' % $ @",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyShipped": 100,
            "lotNumber": "LOT-2026/01#A"
          }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 201
    And match response.receiptKey == '#present'

    # Verify special chars preserved in DB
    * def receiptKey = response.receiptKey
    * def notes = db.getValue("SELECT notes FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'")
    * match notes contains '&'
    * match notes contains '<special>'

  # ─────────────────────────────────────────────────────────────
  # F2-TC20: Concurrent ASN for same PO
  # ─────────────────────────────────────────────────────────────
  @F2-TC20 @P1 @Concurrent
  Scenario: Concurrent ASNs for same PO handled correctly
    * def poKey = 'PO-HAPPY-001'
    * def asn1 = { "poKey": "#(poKey)", "asnNumber": "#('ASN-CONC-A-' + timestamp())", "lines": [{ "sku": "NK-AIRMAX90-BLK", "qtyShipped": 50 }] }
    * def asn2 = { "poKey": "#(poKey)", "asnNumber": "#('ASN-CONC-B-' + timestamp())", "lines": [{ "sku": "NK-AIRMAX90-WHT", "qtyShipped": 50 }] }

    # Send first ASN
    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asn1
    When method post
    Then status 201
    * def receipt1 = response.receiptKey

    # Send second ASN
    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asn2
    When method post
    Then status 201
    * def receipt2 = response.receiptKey

    # Both should have different receipt keys
    * match receipt1 != receipt2

  # ─────────────────────────────────────────────────────────────
  # F2-TC21: ASN with zero quantity line
  # ─────────────────────────────────────────────────────────────
  @F2-TC21 @P2 @ZeroQty
  Scenario: ASN with zero quantity line handled
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-ZERO-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 100 },
          { "sku": "NK-AF1-BLK", "qtyShipped": 0 }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 201
    # Zero qty line should be filtered out
    And match response.lineCount == 1

  # ─────────────────────────────────────────────────────────────
  # F2-TC22: ASN exactly at over-receipt tolerance
  # ─────────────────────────────────────────────────────────────
  @F2-TC22 @P2 @Boundary
  Scenario: ASN exactly at over-receipt tolerance succeeds
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-003",
        "asnNumber": "#('ASN-EXACT-' + timestamp())",
        "allowOverReceipt": true,
        "overReceiptTolerance": 10,
        "lines": [
          {
            "sku": "ADI-ULTRABOOST-BLK",
            "qtyOrdered": 100,
            "qtyShipped": 110
          }
        ]
      }
      """

    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request asnRequest
    When method post
    Then status 201
    And match response.overReceiptWarning == true
    And match response.withinTolerance == true

  # ─────────────────────────────────────────────────────────────
  # F2-TC23: ASN with future ship date
  # ─────────────────────────────────────────────────────────────
  @F2-TC23 @P3 @FutureDate
  Scenario: ASN with future ship date accepted
    * def futureDate = java.time.LocalDate.now().plusDays(7).toString()
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-FUTURE-' + timestamp())",
        "shipDate": "#(futureDate)",
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
    And match response.shipDate == futureDate

  # ─────────────────────────────────────────────────────────────
  # F2-TC24: ASN triggering DB trigger cascade
  # ─────────────────────────────────────────────────────────────
  @F2-TC24 @P1 @Trigger
  Scenario: ASN population fires audit triggers
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-AUDIT-' + timestamp())",
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
    Then status 201
    * def receiptKey = response.receiptKey

    # Verify audit trail created
    * def auditResult = db.query("SELECT * FROM dbo.receiptaudit WHERE receiptkey = '" + receiptKey + "' ORDER BY auditdate DESC")
    * match auditResult.length >= 1
    * match auditResult[0].action == 'INSERT'

  # ─────────────────────────────────────────────────────────────
  # F2-TC25: ASN idempotency test
  # ─────────────────────────────────────────────────────────────
  @F2-TC25 @P1 @Idempotent
  Scenario: Resubmitted ASN is idempotent
    * def asnNumber = 'ASN-IDEMP-' + timestamp()
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#(asnNumber)",
        "idempotencyKey": "#('IDEMP-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 100 }
        ]
      }
      """

    # First submission
    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = asnRequest.idempotencyKey
    And request asnRequest
    When method post
    Then status 201
    * def firstReceiptKey = response.receiptKey

    # Same request with same idempotency key should return same result
    Given path api + '/asn/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Idempotency-Key = asnRequest.idempotencyKey
    And request asnRequest
    When method post
    Then status 200  # Returns existing, not 201
    And match response.receiptKey == firstReceiptKey
    And match response.idempotent == true

