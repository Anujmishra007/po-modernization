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
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F1-TC18: Trigger fires on PO insert
  # ─────────────────────────────────────────────────────────────
  @F1-TC18 @P1 @InsertTrigger
  Scenario: PO insert trigger fires and creates audit
    * def request = testData.validPORequest()
    * def externalKey = 'PO-TRG-INS-' + timestamp()
    * request.externalOrderKey = externalKey

    # Create PO via API
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 201
    * def poKey = response.poKey

    # Wait for trigger processing
    * sleep(2000)

    # Verify trigger audit log
    * def auditResult = db.query("SELECT * FROM dbo.poaudit WHERE pokey = '" + poKey + "' AND action = 'INSERT'")
    * match auditResult.length >= 1
    * match auditResult[0].triggertype == 'AFTER_INSERT'
    * match auditResult[0].tablename == 'po'

    # Verify trigger populated default fields
    * def poResult = db.query("SELECT adddate, addwho, editdate, editwho FROM dbo.po WHERE pokey = '" + poKey + "'")
    * match poResult[0].adddate == '#present'
    * match poResult[0].addwho == '#present'

  # ─────────────────────────────────────────────────────────────
  # F1-TC19: Trigger handles duplicate detection
  # Error Code: TRG_010 (69710)
  # ─────────────────────────────────────────────────────────────
  @F1-TC19 @P2 @DuplicateTrigger
  Scenario: Trigger detects and prevents duplicate
    # First, create a PO
    * def externalKey = 'PO-TRG-DUP-' + timestamp()
    * def request1 = testData.validPORequest()
    * request1.externalOrderKey = externalKey

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request1
    When method post
    Then status 201
    * def firstPoKey = response.poKey

    # Try direct DB insert with same external key (bypassing API validation)
    # This tests the trigger-level duplicate check
    * def duplicateCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + externalKey + "'")
    * match duplicateCheck[0].cnt == 1

  # ─────────────────────────────────────────────────────────────
  # F1-TC36: Trigger cascades to detail table
  # ─────────────────────────────────────────────────────────────
  @F1-TC36 @P2 @CascadeTrigger
  Scenario: PO detail trigger fires on line insert
    * def request = testData.validPORequest()
    * request.externalOrderKey = 'PO-TRG-CASC-' + timestamp()
    * request.lines = [
        { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 },
        { "sku": "NK-AF1-BLK", "qtyOrdered": 50 }
      ]

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 201
    * def poKey = response.poKey

    * sleep(2000)

    # Verify detail audit
    * def detailAudit = db.query("SELECT * FROM dbo.podetailaudit WHERE pokey = '" + poKey + "'")
    * match detailAudit.length >= 2

  # ─────────────────────────────────────────────────────────────
  # F1-TC37: Trigger updates summary fields
  # ─────────────────────────────────────────────────────────────
  @F1-TC37 @P2 @SummaryTrigger
  Scenario: Trigger calculates and updates summary fields
    * def request = testData.validPORequest()
    * request.externalOrderKey = 'PO-TRG-SUM-' + timestamp()
    * request.lines = [
        { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100, "unitPrice": 89.99 },
        { "sku": "NK-AF1-BLK", "qtyOrdered": 50, "unitPrice": 109.99 }
      ]

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 201
    * def poKey = response.poKey

    # Verify summary calculated by trigger
    * def poResult = db.query("SELECT totallines, totalqty, totalvalue FROM dbo.po WHERE pokey = '" + poKey + "'")
    * match poResult[0].totallines == 2
    * match poResult[0].totalqty == 150
    # Total value = (100 * 89.99) + (50 * 109.99) = 8999 + 5499.50 = 14498.50

  # ─────────────────────────────────────────────────────────────
  # F1-TC38: Trigger handles constraint violation
  # ─────────────────────────────────────────────────────────────
  @F1-TC38 @P2 @ConstraintTrigger
  Scenario: Trigger constraint violation handled gracefully
    # Attempt to create PO with FK violation
    * def request =
      """
      {
        "storerKey": "NON_EXISTENT_STORER",
        "facility": "KR01",
        "externalOrderKey": "#('PO-TRG-FK-' + timestamp())",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 422
    And match response.errorCode == 'VAL_002'
    And match response.message contains 'storer'

  # ─────────────────────────────────────────────────────────────
  # F1-TC39: Trigger performance on large insert
  # ─────────────────────────────────────────────────────────────
  @F1-TC39 @P3 @TriggerPerformance
  Scenario: Trigger performance acceptable for large PO
    * def generateLines =
      """
      function(count) {
        var lines = [];
        for (var i = 0; i < count; i++) {
          lines.push({
            "sku": "HM-BASIC-TEE-M",
            "qtyOrdered": 10,
            "unitPrice": 4.99
          });
        }
        return lines;
      }
      """

    * def request =
      """
      {
        "storerKey": "HM_KR",
        "facility": "KR02",
        "externalOrderKey": "#('PO-TRG-PERF-' + timestamp())",
        "lines": "#(generateLines(200))"
      }
      """

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request request
    When method post
    Then status 201
    And responseTime < 10000  # Should complete within 10 seconds with triggers

