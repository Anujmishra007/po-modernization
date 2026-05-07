@F1 @POCreation @Edge @Regression
Feature: F1 - PO Creation Edge Cases

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - Edge Case Scenarios
  # Tests: F1-TC09 to F1-TC13
  # Entry Points: API
  # Purpose: Test boundary conditions and special scenarios
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }

  # ─────────────────────────────────────────────────────────────
  # F1-TC09: Max lines boundary (500+ lines)
  # ─────────────────────────────────────────────────────────────
  @F1-TC09 @P2 @MaxLines
  Scenario: Create PO with 501 lines tests boundary
    # Generate 501 line items
    * def generateLines =
      """
      function(count) {
        var lines = [];
        for (var i = 0; i < count; i++) {
          lines.push({
            "sku": "HM-BASIC-TEE-M",
            "qtyOrdered": 10,
            "lineNumber": String(i + 1).padStart(5, '0')
          });
        }
        return lines;
      }
      """
    * def largeLines = generateLines(501)

    * def poRequest =
      """
      {
        "storerKey": "HM_KR",
        "facility": "KR02",
        "externalOrderKey": "#('PO-LARGE-' + timestamp())",
        "lines": "#(largeLines)"
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    # Either succeeds with 501 lines or returns max lines error
    Then assert responseStatus == 201 || responseStatus == 400
    * if (responseStatus == 201) karate.match(response.lineCount, 501)
    * if (responseStatus == 400) karate.match(response.errorCode, 'VAL_010')

  # ─────────────────────────────────────────────────────────────
  # F1-TC10: Unicode characters in address fields
  # ─────────────────────────────────────────────────────────────
  @F1-TC10 @P2 @Unicode
  Scenario: Create PO with Korean/Chinese/Japanese unicode in address
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-UNICODE-' + timestamp())",
        "shipToAddress": {
          "company": "나이키 코리아",
          "address1": "서울시 강남구 테헤란로 123",
          "address2": "삼성타워 15층",
          "city": "서울",
          "state": "서울특별시",
          "zip": "06234",
          "country": "KR",
          "contact": "김담당자",
          "phone": "+82-2-1234-5678"
        },
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    And match response.poKey == '#present'
    * def poKey = response.poKey

    # Verify unicode stored correctly
    Given path api + '/po/' + poKey
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.shipToAddress.company == '나이키 코리아'
    And match response.shipToAddress.address1 == '서울시 강남구 테헤란로 123'
    And match response.shipToAddress.city == '서울'

  # ─────────────────────────────────────────────────────────────
  # F1-TC11: Zero quantity line
  # Error Code: VAL_008 (69107)
  # ─────────────────────────────────────────────────────────────
  @F1-TC11 @P2 @ZeroQty
  Scenario: Zero quantity line rejected
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-ZERO-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 0 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 400
    And match response.errorCode == 'VAL_008'
    And match response.message contains 'quantity'
    And match response.field == 'qtyOrdered'
    And match response.invalidValue == 0

  # ─────────────────────────────────────────────────────────────
  # F1-TC12: Negative quantity
  # Error Code: VAL_009 (69108)
  # ─────────────────────────────────────────────────────────────
  @F1-TC12 @P2 @NegativeQty
  Scenario: Negative quantity rejected
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-NEG-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": -10 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 400
    And match response.errorCode == 'VAL_009'
    And match response.message contains 'Negative'
    And match response.invalidValue == -10

  # ─────────────────────────────────────────────────────────────
  # F1-TC13: Past expected date
  # Error Code: VAL_006 (69105)
  # ─────────────────────────────────────────────────────────────
  @F1-TC13 @P3 @PastDate
  Scenario: Past expected date rejected
    * def yesterday = java.time.LocalDate.now().minusDays(1).toString()
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-PAST-' + timestamp())",
        "expectedDate": "#(yesterday)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 400
    And match response.errorCode == 'VAL_006'
    And match response.message contains 'past'
    And match response.field == 'expectedDate'

  # ─────────────────────────────────────────────────────────────
  # F1-TC26: Decimal quantity handling
  # ─────────────────────────────────────────────────────────────
  @F1-TC26 @P3 @DecimalQty
  Scenario: Decimal quantity handled correctly
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-DEC-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 10.5 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    # Either truncates to integer or rejects based on SKU config
    Then assert responseStatus == 201 || responseStatus == 400

  # ─────────────────────────────────────────────────────────────
  # F1-TC27: Very long external PO key
  # ─────────────────────────────────────────────────────────────
  @F1-TC27 @P3 @LongKey
  Scenario: Very long external PO key handled
    * def longKey = 'PO-' + 'X'.repeat(100)
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#(longKey)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 400
    And match response.errorCode == 'VAL_011'
    And match response.message contains 'exceeds maximum length'

  # ─────────────────────────────────────────────────────────────
  # F1-TC28: Special characters in external PO key
  # ─────────────────────────────────────────────────────────────
  @F1-TC28 @P3 @SpecialChars
  Scenario: Special characters in PO key handled
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-SPEC!@#$%^&*()-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    # Either accepts or rejects based on validation rules
    Then assert responseStatus == 201 || responseStatus == 400

