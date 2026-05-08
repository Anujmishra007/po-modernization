@F1 @POCreation @API @Happy @Regression
Feature: F1 - PO Creation Happy Path Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - Happy Path Scenarios
  # Tests: F1-TC01 to F1-TC04
  # Entry Points: API, EDI, Job
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token

  # ─────────────────────────────────────────────────────────────
  # F1-TC01: Create single-line PO via API
  # ─────────────────────────────────────────────────────────────
  @F1-TC01 @P1
  Scenario: Create single-line PO successfully
    # Generate unique PO key
    * def poKey = generatePoKey()
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = poKey

    # Create PO via API
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    And match response.poKey == '#present'
    And match response.status == '0'
    And match response.storerKey == testStorerKey

    # Verify in database (dual-write validation)
    * def createdPoKey = response.poKey
    * def dbResult = db.query("SELECT * FROM dbo.orders WHERE orderkey = '" + createdPoKey + "'")
    * def firstRow = karate.toMap(dbResult[0])
    * match firstRow.storerkey == testStorerKey
    * match firstRow.status == '0'

    # Verify PO detail
    * def detailResult = db.query("SELECT * FROM dbo.orderdetail WHERE orderkey = '" + createdPoKey + "'")
    * match karate.sizeOf(detailResult) == 1
    * def detailRow = karate.toMap(detailResult[0])
    * match detailRow.qtyordered == 100

  # ─────────────────────────────────────────────────────────────
  # F1-TC02: Create multi-line PO (50 lines)
  # ─────────────────────────────────────────────────────────────
  @F1-TC02 @P1
  Scenario: Create multi-line PO successfully
    * def poRequest = testData.multiLinePORequest(50)
    * def poKey = generatePoKey()
    * poRequest.externalOrderKey = poKey

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    And match response.poKey == '#present'
    And match response.lineCount == 50

    # Verify all lines created
    * def createdPoKey = response.poKey
    * def lineCount = db.getValue("SELECT COUNT(*) FROM dbo.orderdetail WHERE orderkey = '" + createdPoKey + "'")
    * match lineCount == 50

  # ─────────────────────────────────────────────────────────────
  # F1-TC03: Create PO via EDI 850
  # ─────────────────────────────────────────────────────────────
  @F1-TC03 @P1 @EDI
  Scenario: Create PO via EDI 850 message
    * def ediMessage = read('classpath:test-data/edi/edi-850-sample.txt')
    * def uniqueId = timestamp()
    * def modifiedEdi = ediMessage.replace('{{PO_NUMBER}}', 'EDI-' + uniqueId)

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And request modifiedEdi
    When method post
    Then status 202
    And match response.messageId == '#present'
    And match response.status == 'ACCEPTED'

    # Wait for async processing
    * sleep(3000)

    # Verify PO created from EDI
    * def poResult = db.query("SELECT * FROM dbo.orders WHERE externorderkey LIKE 'EDI-" + uniqueId + "%'")
    * match karate.sizeOf(poResult) == 1
    * def poRow = karate.toMap(poResult[0])
    * match poRow.status == '0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC04: Create PO via batch job
  # ─────────────────────────────────────────────────────────────
  @F1-TC04 @P1 @Job
  Scenario: Create PO via batch job trigger
    # Insert staging record for job to process
    * def stagingId = 'STG-' + timestamp()

    # Trigger job execution
    Given path api + '/jobs/generic-inbound-po/trigger'
    And header Authorization = 'Bearer ' + authToken
    And param stagingId = stagingId
    When method post
    Then status 202
    And match response.jobExecutionId == '#present'

    # Wait for job completion
    * sleep(5000)

    # Verify job status
    Given path api + '/jobs/executions/' + response.jobExecutionId
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == '#? _ == "COMPLETED" || _ == "RUNNING"'
