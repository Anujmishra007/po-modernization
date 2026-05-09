@F5 @LottableTracking @Regression
Feature: F5 - Lottable Field Tracking Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 5: Lottable Field Tracking
  # Tests: F5-TC01 to F5-TC18
  # Entry Points: API, EDI, RDT
  # Purpose: Validate client-specific lottable field tracking
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F5-TC01: Nike style/color/size tracking
  # ─────────────────────────────────────────────────────────────
  @F5-TC01 @P1 @Nike @Happy
  Scenario: Nike receipt captures style/color/size lottables
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "storerKey": "NIKE_KR",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReceived": 100,
            "lottable01": "AM90-2024",
            "lottable02": "BLACK",
            "lottable03": "US10",
            "lottable04": "SEASON-S24"
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'NIKE_KR'
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 201
    * def receiptKey = response.receiptKey

    # Verify lottables stored
    Given path api + '/receipts/' + receiptKey + '/details'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.lines[0].lottable01 == 'AM90-2024'
    And match response.lines[0].lottable02 == 'BLACK'
    And match response.lines[0].lottable03 == 'US10'
    And match response.lines[0].lottable04 == 'SEASON-S24'

  # ─────────────────────────────────────────────────────────────
  # F5-TC02: Nike lottable validation (plugin)
  # ─────────────────────────────────────────────────────────────
  @F5-TC02 @P1 @Nike @Plugin
  Scenario: Nike plugin validates lottable format
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "storerKey": "NIKE_KR",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReceived": 50,
            "lottable01": "INVALID-STYLE-FORMAT"
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'NIKE_KR'
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 422
    And match response.errorCode == 'LOT_001'
    And match response.message == '#? _.indexOf("Invalid lottable format") >= 0'
    And match response.field == 'lottable01'

  # ─────────────────────────────────────────────────────────────
  # F5-TC03: H&M no lottable tracking
  # ─────────────────────────────────────────────────────────────
  @F5-TC03 @P1 @HM @Happy
  Scenario: H&M fast-fashion skips lottable tracking
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-002",
        "storerKey": "HM_KR",
        "lines": [
          {
            "sku": "HM-BASIC-TEE-M",
            "qtyReceived": 500
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'HM_KR'
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 201
    * def receiptKey = response.receiptKey

    # Verify no lottable validation errors
    Given path api + '/receipts/' + receiptKey + '/details'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.lines[0].lottableRequired == false

  # ─────────────────────────────────────────────────────────────
  # F5-TC04: Adidas lot/batch tracking
  # ─────────────────────────────────────────────────────────────
  @F5-TC04 @P1 @Adidas @Happy
  Scenario: Adidas receipt captures lot/batch lottables
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-003",
        "storerKey": "ADIDAS_IN",
        "lines": [
          {
            "sku": "ADI-ULTRABOOST-BLK",
            "qtyReceived": 50,
            "lottable01": "BATCH-2026-001",
            "lottable05": "2027-12-31"
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'ADIDAS_IN'
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 201
    And match response.lottablesApplied == true

  # ─────────────────────────────────────────────────────────────
  # F5-TC05: Missing required lottable
  # ─────────────────────────────────────────────────────────────
  @F5-TC05 @P1 @Nike @Unhappy
  Scenario: Missing required Nike lottable fails
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "storerKey": "NIKE_KR",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReceived": 100
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header X-Storer-Key = 'NIKE_KR'
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 422
    And match response.errorCode == 'LOT_002'
    And match response.message == '#? _.indexOf("Missing required lottable") >= 0'
    And match response.missingLottables contains 'lottable01'

  # ─────────────────────────────────────────────────────────────
  # F5-TC06: Lottable from EDI 856
  # ─────────────────────────────────────────────────────────────
  @F5-TC06 @P1 @EDI @Nike
  Scenario: Lottables populated from EDI 856 REF segments
    * def ediContent = read('classpath:test-data/edi/EDI-856-SAMPLE-001.txt')

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And header X-Storer-Key = 'NIKE_KR'
    And request ediContent
    When method post
    Then status 202
    * def messageId = response.messageId

    * sleep(5000)

    # Verify lottables from EDI REF segments
    Given path api + '/edi/messages/' + messageId + '/parsed'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.lottables.lottable01 == '#present'

  # ─────────────────────────────────────────────────────────────
  # F5-TC07: Lottable inheritance from PO
  # ─────────────────────────────────────────────────────────────
  @F5-TC07 @P2 @Inheritance
  Scenario: Receipt inherits lottables from PO
    * def poKey = 'PO-WITH-LOTTABLES'

    # PO has lottable defaults
    * def receiptRequest =
      """
      {
        "poKey": "#(poKey)",
        "storerKey": "NIKE_KR",
        "inheritLottablesFromPO": true,
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReceived": 100
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 201
    And match response.lottablesInherited == true

  # ─────────────────────────────────────────────────────────────
  # F5-TC08: Lottable update via RDT
  # ─────────────────────────────────────────────────────────────
  @F5-TC08 @P1 @RDT
  Scenario: Update lottables via RDT scan
    * def receiptKey = 'RCV-LOT-UPDATE'
    * def lineNumber = '00001'

    Given path api + '/rdt/receipts/' + receiptKey + '/lines/' + lineNumber + '/lottables'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-DEVICE-001'
    And header Content-Type = 'application/json'
    And request
      """
      {
        "lottable01": "UPDATED-STYLE",
        "lottable02": "UPDATED-COLOR",
        "scannedValue": "BARCODE-123456"
      }
      """
    When method put
    Then status 200
    And match response.lottablesUpdated == true
    And match response.lottable01 == 'UPDATED-STYLE'

  # ─────────────────────────────────────────────────────────────
  # F5-TC09: Lottable preserved through inventory
  # ─────────────────────────────────────────────────────────────
  @F5-TC09 @P1 @Inventory
  Scenario: Lottables preserved in inventory records
    * def receiptKey = 'RCV-LOT-INV'

    # Finalize receipt
    Given path api + '/receipts/' + receiptKey + '/finalize'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200

    * sleep(3000)

    # Verify lottables in inventory
    * def invResult = db.query("SELECT lottable01, lottable02, lottable03 FROM dbo.lotxlocxid WHERE receiptkey = '" + receiptKey + "'")
    * match invResult[0].lottable01 == '#present'
    * match invResult[0].lottable02 == '#present'

  # ─────────────────────────────────────────────────────────────
  # F5-TC10: Lottable search/filter
  # ─────────────────────────────────────────────────────────────
  @F5-TC10 @P2 @Search
  Scenario: Search inventory by lottable values
    Given path api + '/inventory/search'
    And header Authorization = 'Bearer ' + authToken
    And param storerKey = 'NIKE_KR'
    And param lottable01 = 'AM90-2024'
    And param lottable02 = 'BLACK'
    When method get
    Then status 200
    And assert karate.sizeOf(response.results) >= 1
    And match each response.results contains { lottable01: 'AM90-2024', lottable02: 'BLACK' }

  # ─────────────────────────────────────────────────────────────
  # F5-TC11: Lottable date validation
  # ─────────────────────────────────────────────────────────────
  @F5-TC11 @P2 @Date @Unhappy
  Scenario: Invalid lottable date format fails
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-003",
        "storerKey": "ADIDAS_IN",
        "lines": [
          {
            "sku": "ADI-ULTRABOOST-BLK",
            "qtyReceived": 50,
            "lottable05": "INVALID-DATE"
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 422
    And match response.errorCode == 'LOT_003'
    And match response.message == '#? _.indexOf("Invalid date format") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F5-TC12: Lottable length validation
  # ─────────────────────────────────────────────────────────────
  @F5-TC12 @P2 @Length @Unhappy
  Scenario: Lottable exceeding max length fails
    # Create a string that exceeds 50 char limit
    * def longValue = 'A'.repeat(51)
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "storerKey": "NIKE_KR",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReceived": 100,
            "lottable01": "#(longValue)"
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 422
    And match response.errorCode == 'LOT_004'
    And match response.message == '#? _.indexOf("exceeds maximum length") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F5-TC13 to F5-TC18: Additional lottable scenarios
  # ─────────────────────────────────────────────────────────────
  @F5-TC13 @P2 @Merge
  Scenario: Lottable merge during inventory consolidation
    # When consolidating inventory, lottables should match
    Given path api + '/inventory/consolidate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "fromId": "INV-LOT-001",
        "toId": "INV-LOT-002",
        "validateLottables": true
      }
      """
    When method post
    Then status 200
    And match response.consolidated == true

  @F5-TC14 @P2 @Split
  Scenario: Lottables preserved during inventory split
    Given path api + '/inventory/split'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "sourceId": "INV-SPLIT-001",
        "splitQty": 50
      }
      """
    When method post
    Then status 201
    And match response.newId.lottables == response.sourceId.lottables

  @F5-TC15 @P3 @Bulk
  Scenario: Bulk lottable update
    Given path api + '/receipts/bulk-lottable-update'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "receiptKey": "RCV-BULK-LOT",
        "updateAll": {
          "lottable04": "SEASON-S24-UPDATED"
        }
      }
      """
    When method put
    Then status 200
    And assert response.linesUpdated >= 1

  @F5-TC16 @P3 @Default
  Scenario: Lottable defaults from SKU master
    * def receiptRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "storerKey": "NIKE_KR",
        "applySkuDefaults": true,
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyReceived": 100
          }
        ]
      }
      """

    Given path api + '/receipts'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request receiptRequest
    When method post
    Then status 201
    And match response.skuDefaultsApplied == true

  @F5-TC17 @P3 @Report
  Scenario: Lottable report generation
    Given path api + '/reports/lottable-summary'
    And header Authorization = 'Bearer ' + authToken
    And param storerKey = 'NIKE_KR'
    And param dateFrom = '2026-01-01'
    And param dateTo = '2026-12-31'
    When method get
    Then status 200
    And match response.summary contains { totalRecords: '#number' }

  @F5-TC18 @P3 @Audit
  Scenario: Lottable change audit trail
    Given path api + '/audit/lottable-changes'
    And header Authorization = 'Bearer ' + authToken
    And param entityKey = 'RCV-LOT-AUDIT'
    When method get
    Then status 200
    And assert karate.sizeOf(response.changes) >= 1
    And match response.changes[0] contains { field: '#present', oldValue: '#present', newValue: '#present' }

