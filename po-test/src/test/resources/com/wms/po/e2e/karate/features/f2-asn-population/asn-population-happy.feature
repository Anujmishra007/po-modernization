@F2 @ASNPopulation @EDI @Happy @Regression
Feature: F2 - ASN Population Happy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 2: ASN Population - Happy Path Scenarios
  # Tests: F2-TC01 to F2-TC08
  # Entry Points: EDI, API
  # Purpose: Populate PO lines from EDI 856 ASN messages
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def generatePoKey = function() { return 'PO-TEST-' + timestamp() }
    * def generateReceiptKey = function() { return 'RCV-TEST-' + timestamp() }

  # ─────────────────────────────────────────────────────────────
  # F2-TC01: Process standard EDI 856 ASN with 3 lines
  # ─────────────────────────────────────────────────────────────
  @F2-TC01 @P1
  Scenario: Process standard EDI 856 ASN successfully
    # Load EDI 856 sample file
    * def ediContent = read('classpath:test-data/edi/EDI-856-SAMPLE-001.txt')
    * def uniqueId = timestamp()
    * def modifiedEdi = ediContent.replace('{{ASN_NUMBER}}', 'ASN-' + uniqueId)

    # Send EDI 856 to inbound endpoint
    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And request modifiedEdi
    When method post
    Then status 202
    And match response.messageId == '#present'
    And match response.status == 'ACCEPTED'
    And match response.transactionType == '856'

    # Wait for async processing
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }
    * sleep(5000)

    # Verify receipt was created from ASN
    * def receiptResult = db.query("SELECT * FROM dbo.receipt WHERE externreceiptkey LIKE 'ASN-" + uniqueId + "%'")
    * match karate.sizeOf(receiptResult) == 1
    * def rcptRow = karate.toMap(receiptResult[0])
    * match rcptRow.status == '0'

    # Verify receipt details match ASN lines
    * def receiptKey = rcptRow.receiptkey
    * def detailResult = db.query("SELECT * FROM dbo.receiptdetail WHERE receiptkey = '" + receiptKey + "'")
    * match karate.sizeOf(detailResult) == 3

  # ─────────────────────────────────────────────────────────────
  # F2-TC02: Nike ASN with lottable tracking
  # ─────────────────────────────────────────────────────────────
  @F2-TC02 @P1 @Nike @Lottable
  Scenario: Process Nike ASN with style/color/size lottables
    # Load Nike-specific EDI 856
    * def ediContent = read('classpath:test-data/edi/EDI-856-SAMPLE-001.txt')
    * def uniqueId = timestamp()

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And header X-Storer-Key = 'NIKE_KR'
    And request ediContent
    When method post
    Then status 202
    * def messageId = response.messageId

    # Wait for processing
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }
    * sleep(5000)

    # Verify lottable fields populated
    * def lottableResult = db.query("SELECT lottable01, lottable02, lottable03 FROM dbo.receiptdetail WHERE receiptkey LIKE 'RCV-NIKE-%' ORDER BY adddate DESC LIMIT 1")
    * def lotRow = karate.toMap(lottableResult[0])
    * match lotRow.lottable01 == '#present'
    * match lotRow.lottable02 == '#present'
    * match lotRow.lottable03 == '#present'

  # ─────────────────────────────────────────────────────────────
  # F2-TC03: H&M fast-fashion ASN (high volume)
  # ─────────────────────────────────────────────────────────────
  @F2-TC03 @P1 @HM @FastFashion
  Scenario: Process H&M high-volume ASN
    * def ediContent = read('classpath:test-data/edi/EDI-856-SAMPLE-002-HM.txt')
    * def uniqueId = timestamp()

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And header X-Storer-Key = 'HM_KR'
    And request ediContent
    When method post
    Then status 202

    # Wait for async processing
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }
    * sleep(5000)

    # Verify receipt created with correct facility
    Given path api + '/edi/messages/' + response.messageId + '/status'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.processingStatus == 'COMPLETED'

  # ─────────────────────────────────────────────────────────────
  # F2-TC04: ASN linking to existing PO
  # ─────────────────────────────────────────────────────────────
  @F2-TC04 @P1 @POLink
  Scenario: ASN links correctly to existing PO
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
    * def createdPoKey = response.poKey

    # Now send ASN referencing this PO
    * def asnRequest =
      """
      {
        "poKey": "#(createdPoKey)",
        "asnNumber": "#('ASN-LINK-' + timestamp())",
        "shipDate": "2026-05-05",
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyShipped": 100,
            "cartonId": "CTN-001"
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
    And match response.linkedPoKey == createdPoKey

    # Verify PO status updated
    * def poStatus = db.getValue("SELECT status FROM dbo.po WHERE pokey = '" + createdPoKey + "'")
    # Status updated to 'ASN Received' (1)
    * match poStatus == '1'

  # ─────────────────────────────────────────────────────────────
  # F2-TC05: Multi-carton ASN with hierarchical structure
  # ─────────────────────────────────────────────────────────────
  @F2-TC05 @P1 @MultiCarton
  Scenario: Process multi-carton ASN with hierarchy
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-MULTI-' + timestamp())",
        "shipDate": "2026-05-05",
        "trailer": "TRL-12345",
        "bolNumber": "BOL-67890",
        "cartons": [
          {
            "cartonId": "CTN-001",
            "weight": 25.5,
            "lines": [
              { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 50 }
            ]
          },
          {
            "cartonId": "CTN-002",
            "weight": 30.0,
            "lines": [
              { "sku": "NK-AIRMAX90-BLK", "qtyShipped": 50 },
              { "sku": "NK-AF1-BLK", "qtyShipped": 25 }
            ]
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
    And match response.cartonCount == 2
    And match response.totalQty == 125

    # Verify carton tracking in DB
    * def cartonResult = db.query("SELECT * FROM dbo.cartonheader WHERE receiptkey = '" + response.receiptKey + "'")
    * assert cartonResult.size() == 2

  # ─────────────────────────────────────────────────────────────
  # F2-TC06: ASN with partial shipment
  # ─────────────────────────────────────────────────────────────
  @F2-TC06 @P2 @Partial
  Scenario: Process ASN with partial shipment quantity
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-001",
        "asnNumber": "#('ASN-PARTIAL-' + timestamp())",
        "shipDate": "2026-05-05",
        "isPartialShipment": true,
        "lines": [
          {
            "sku": "NK-AIRMAX90-BLK",
            "qtyOrdered": 100,
            "qtyShipped": 60
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
    And match response.partialShipment == true

    # Verify PO line shows open qty
    * def poDetail = db.query("SELECT qtyordered, qtyreceived FROM dbo.podetail WHERE pokey = 'PO-HAPPY-001' AND sku = 'NK-AIRMAX90-BLK'")
    * def poDetailRow = karate.toMap(poDetail[0])
    * match poDetailRow.qtyordered == 100
    # Original qty received plus new partial
    * match poDetailRow.qtyreceived >= 60

  # ─────────────────────────────────────────────────────────────
  # F2-TC07: ASN via API (non-EDI)
  # ─────────────────────────────────────────────────────────────
  @F2-TC07 @P1 @API
  Scenario: Populate PO via direct API call
    * def poKey = 'PO-HAPPY-002'
    * def populateRequest =
      """
      {
        "receiptDetails": [
          {
            "sku": "HM-BASIC-TEE-M",
            "qtyReceived": 500,
            "lot": "LOT-2026-001",
            "receiveLocation": "RCV-DOCK-01"
          }
        ]
      }
      """

    Given path api + '/po/' + poKey + '/populate'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request populateRequest
    When method post
    Then status 201
    And match response.receiptKey == '#present'
    And match response.status == 'POPULATED'

    # Verify receipt created
    * def receiptKey = response.receiptKey
    Given path api + '/receipts/' + receiptKey
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.poKey == poKey
    And match response.lineCount == 1

  # ─────────────────────────────────────────────────────────────
  # F2-TC08: ASN with over-receipt handling
  # ─────────────────────────────────────────────────────────────
  @F2-TC08 @P2 @OverReceipt
  Scenario: Process ASN with over-receipt allowed
    * def asnRequest =
      """
      {
        "poKey": "PO-HAPPY-003",
        "asnNumber": "#('ASN-OVER-' + timestamp())",
        "allowOverReceipt": true,
        "overReceiptTolerance": 10,
        "lines": [
          {
            "sku": "ADI-ULTRABOOST-BLK",
            "qtyOrdered": 100,
            "qtyShipped": 105
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
    And match response.overReceiptQty == 5

