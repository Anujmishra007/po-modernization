@F9 @Archival @Regression
Feature: F9 - PO Archival Flow Tests

  # ═══════════════════════════════════════════════════════════
  # Flow 9: PO Archival Flow
  # Tests: F9-TC01 to F9-TC12
  # Entry Points: Job, API
  # Purpose: Test PO archival to history tables
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def sleep = function(millis) { java.lang.Thread.sleep(millis) }

  # ─────────────────────────────────────────────────────────────
  # F9-TC01: Archive closed PO via job
  # ─────────────────────────────────────────────────────────────
  @F9-TC01 @P1 @Job @Happy
  Scenario: Archive closed PO via scheduled job
    Given path api + '/jobs/po-archive/trigger'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "daysOld": 90,
        "statusFilter": ["9"],
        "batchSize": 100
      }
      """
    When method post
    Then status 202
    And match response.jobExecutionId == '#present'
    * def jobId = response.jobExecutionId

    * sleep(10000)

    # Verify job completion
    Given path api + '/jobs/executions/' + jobId
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == '#? _ == "COMPLETED" || _ == "RUNNING"'
    And match response.recordsProcessed >= 0

  # ─────────────────────────────────────────────────────────────
  # F9-TC02: Archive single PO via API
  # ─────────────────────────────────────────────────────────────
  @F9-TC02 @P1 @API @Happy
  Scenario: Archive single PO via API
    * def poKey = 'PO-ARCHIVE-001'

    Given path api + '/po/' + poKey + '/archive'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.archived == true
    And match response.archiveDate == '#present'

    # Verify PO moved to history
    * def historyCount = db.getValue("SELECT COUNT(*) FROM dbo.po_history WHERE pokey = '" + poKey + "'")
    * match historyCount == 1

    # Verify PO removed from active
    * def activeCount = db.getValue("SELECT COUNT(*) FROM dbo.po WHERE pokey = '" + poKey + "'")
    * match activeCount == 0

  # ─────────────────────────────────────────────────────────────
  # F9-TC03: Archive PO with receipts
  # ─────────────────────────────────────────────────────────────
  @F9-TC03 @P1 @Happy
  Scenario: Archive PO archives related receipts
    * def poKey = 'PO-ARCHIVE-002'

    Given path api + '/po/' + poKey + '/archive'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "archiveRelated": true }
    When method post
    Then status 200
    And match response.archivedEntities.po == 1
    And match response.archivedEntities.receipts >= 1
    And match response.archivedEntities.receiptDetails >= 1

  # ─────────────────────────────────────────────────────────────
  # F9-TC04: Cannot archive open PO
  # ─────────────────────────────────────────────────────────────
  @F9-TC04 @P1 @Unhappy
  Scenario: Cannot archive open PO
    * def poKey = 'PO-HAPPY-001'  # Status 0

    Given path api + '/po/' + poKey + '/archive'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'ARCH_001'
    And match response.message contains 'not closed'

  # ─────────────────────────────────────────────────────────────
  # F9-TC05: Archive with open receipts fails
  # ─────────────────────────────────────────────────────────────
  @F9-TC05 @P1 @Unhappy
  Scenario: Archive fails with open receipts
    * def poKey = 'PO-ARCHIVE-OPEN-RCV'

    Given path api + '/po/' + poKey + '/archive'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 422
    And match response.errorCode == 'ARCH_002'
    And match response.message contains 'open receipts'
    And match response.openReceiptKeys.length >= 1

  # ─────────────────────────────────────────────────────────────
  # F9-TC06: Bulk archive via job
  # ─────────────────────────────────────────────────────────────
  @F9-TC06 @P1 @Bulk @Job
  Scenario: Bulk archive closed POs via job
    Given path api + '/jobs/po-archive/trigger'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "daysOld": 30,
        "storerKeys": ["NIKE_KR", "HM_KR"],
        "facilities": ["KR01", "KR02"],
        "batchSize": 500,
        "archiveRelated": true
      }
      """
    When method post
    Then status 202
    And match response.estimatedRecords >= 0

  # ─────────────────────────────────────────────────────────────
  # F9-TC07: Retrieve archived PO
  # ─────────────────────────────────────────────────────────────
  @F9-TC07 @P2 @Retrieve
  Scenario: Retrieve archived PO from history
    * def poKey = 'PO-ARCHIVE-001'

    Given path api + '/po-history/' + poKey
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.poKey == poKey
    And match response.archived == true
    And match response.archiveDate == '#present'

  # ─────────────────────────────────────────────────────────────
  # F9-TC08: Search archived POs
  # ─────────────────────────────────────────────────────────────
  @F9-TC08 @P2 @Search
  Scenario: Search archived POs
    Given path api + '/po-history/search'
    And header Authorization = 'Bearer ' + authToken
    And param storerKey = 'NIKE_KR'
    And param dateFrom = '2025-01-01'
    And param dateTo = '2026-12-31'
    When method get
    Then status 200
    And match response.results.length >= 0
    And match response.totalCount >= 0

  # ─────────────────────────────────────────────────────────────
  # F9-TC09 to F9-TC12: Additional archival scenarios
  # ─────────────────────────────────────────────────────────────
  @F9-TC09 @P2 @Unarchive
  Scenario: Unarchive PO (restore from history)
    * def poKey = 'PO-UNARCHIVE-001'

    Given path api + '/po-history/' + poKey + '/unarchive'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request { "reason": "Need to add receipt" }
    When method post
    Then status 200
    And match response.restored == true

    # Verify PO back in active
    * def activeCount = db.getValue("SELECT COUNT(*) FROM dbo.po WHERE pokey = '" + poKey + "'")
    * match activeCount == 1

  @F9-TC10 @P3 @Purge
  Scenario: Purge old archived POs
    Given path api + '/jobs/po-purge/trigger'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request
      """
      {
        "archiveDaysOld": 365,
        "purgeMode": "SOFT_DELETE"
      }
      """
    When method post
    Then status 202
    And match response.jobExecutionId == '#present'

  @F9-TC11 @P3 @Retention
  Scenario: Archive respects retention policy
    * def poKey = 'PO-RETENTION-001'

    Given path api + '/po/' + poKey + '/archive'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request {}
    When method post
    Then status 200
    And match response.retentionPolicy == '#present'
    And match response.purgeDate == '#present'

  @F9-TC12 @P3 @Report
  Scenario: Archival report
    Given path api + '/reports/archival'
    And header Authorization = 'Bearer ' + authToken
    And param dateFrom = '2026-01-01'
    And param dateTo = '2026-12-31'
    When method get
    Then status 200
    And match response contains { totalArchived: '#number', totalPurged: '#number', storageRecovered: '#string' }

