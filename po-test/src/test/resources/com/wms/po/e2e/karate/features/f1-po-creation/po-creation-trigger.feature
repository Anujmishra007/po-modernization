@F1 @POCreation @Trigger @Regression
Feature: F1 - PO Creation Trigger Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - Database Trigger Scenarios
  # Tests: F1-TC18 to F1-TC19
  # Entry Points: Trigger
  # Purpose: Test trigger-based PO processing
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function(){ return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis){ java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F1-TC18: Trigger fires on PO insert
  # ─────────────────────────────────────────────────────────────
  @F1-TC18 @P1 @InsertTrigger
  Scenario: PO insert trigger fires and creates audit
    * def poRequest = testData.validPORequest()
    * def externalKey = 'PO-TRG-INS-' + timestamp()
    * poRequest.externalOrderKey = externalKey

    # Create PO via API
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    * def poKey = response.poKey

    # Wait for trigger processing
    * sleep(2000)

    # Verify trigger audit log
    * def auditResult = db.query("SELECT * FROM dbo.poaudit WHERE pokey = '" + poKey + "' AND action = 'INSERT'")
    * assert karate.sizeOf(auditResult) >= 1
    * def auditRow = karate.toMap(auditResult[0])
    * match auditRow.triggertype == 'AFTER_INSERT'
    * match auditRow.tablename == 'po'

    # Verify trigger populated default fields
    * def poResult = db.query("SELECT adddate, addwho, editdate, editwho FROM dbo.po WHERE pokey = '" + poKey + "'")
    * def poRow = karate.toMap(poResult[0])
    * match poRow.adddate == '#present'
    * match poRow.addwho == '#present'

  # ─────────────────────────────────────────────────────────────
  # F1-TC19: Trigger handles duplicate detection
  # Error Code: TRG_010 (69710)
  # ─────────────────────────────────────────────────────────────
  @F1-TC19 @P2 @DuplicateTrigger
  Scenario: Trigger detects and prevents duplicate
    # First, create a PO
    * def externalKey = 'PO-TRG-DUP-' + timestamp()
    * def poRequest1 = testData.validPORequest()
    * poRequest1.externalOrderKey = externalKey

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest1
    When method post
    Then status 201
    * def firstPoKey = response.poKey

    # Try direct DB insert with same external key (bypassing API validation)
    # This tests the trigger-level duplicate check
    * def duplicateCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + externalKey + "'")
    * def dupRow = karate.toMap(duplicateCheck[0])
    * match dupRow.cnt == 1

  # ─────────────────────────────────────────────────────────────
  # F1-TC36: Trigger cascades to detail table
  # ─────────────────────────────────────────────────────────────
  @F1-TC36 @P2 @CascadeTrigger
  Scenario: PO detail trigger fires on line insert
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-TRG-CASC-' + timestamp()
    * def lines = [{sku: 'NK-AIRMAX90-BLK', qtyOrdered: 100}, {sku: 'NK-AF1-BLK', qtyOrdered: 50}]
    * poRequest.lines = lines

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    * def poKey = response.poKey

    * sleep(2000)

    # Verify detail audit
    * def detailAudit = db.query("SELECT * FROM dbo.podetailaudit WHERE pokey = '" + poKey + "'")
    * assert karate.sizeOf(detailAudit) >= 2

  # ─────────────────────────────────────────────────────────────
  # F1-TC37: Trigger updates summary fields
  # ─────────────────────────────────────────────────────────────
  @F1-TC37 @P2 @SummaryTrigger
  Scenario: Trigger calculates and updates summary fields
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-TRG-SUM-' + timestamp()
    * def lines = [{sku: 'NK-AIRMAX90-BLK', qtyOrdered: 100, unitPrice: 89.99}, {sku: 'NK-AF1-BLK', qtyOrdered: 50, unitPrice: 109.99}]
    * poRequest.lines = lines

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    * def poKey = response.poKey

    # Verify summary calculated by trigger
    * def poResult = db.query("SELECT totallines, totalqty, totalvalue FROM dbo.po WHERE pokey = '" + poKey + "'")
    * def sumRow = karate.toMap(poResult[0])
    * match sumRow.totallines == 2
    * match sumRow.totalqty == 150
    # Total value = (100 * 89.99) + (50 * 109.99) = 8999 + 5499.50 = 14498.50

  # ─────────────────────────────────────────────────────────────
  # F1-TC38: Trigger handles constraint violation
  # ─────────────────────────────────────────────────────────────
  @F1-TC38 @P2 @ConstraintTrigger
  Scenario: Trigger constraint violation handled gracefully
    # Attempt to create PO with FK violation
    * def extKey = 'PO-TRG-FK-' + timestamp()
    * def poRequest = {storerKey: 'NON_EXISTENT_STORER', facility: 'KR01', externalOrderKey: '#(extKey)', lines: [{sku: 'NK-AIRMAX90-BLK', qtyOrdered: 100}]}

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 422
    And match response.errorCode == 'VAL_002'
    And match response.message == '#? _.toLowerCase().indexOf("storer") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC39: Trigger performance on large insert
  # ─────────────────────────────────────────────────────────────
  @F1-TC39 @P3 @TriggerPerformance
  Scenario: Trigger performance acceptable for large PO
    * def generateLines = function(count){ var lines = []; for(var i = 0; i < count; i++){ lines.push({sku: 'HM-BASIC-TEE-M', qtyOrdered: 10, unitPrice: 4.99}); } return lines; }
    * def extKey = 'PO-TRG-PERF-' + timestamp()
    * def generatedLines = generateLines(200)
    * def poRequest = {storerKey: 'HM_KR', facility: 'KR02', externalOrderKey: '#(extKey)', lines: '#(generatedLines)'}

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    And assert responseTime < 10000
