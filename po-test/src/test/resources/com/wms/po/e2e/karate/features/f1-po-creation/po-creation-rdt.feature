@F1 @POCreation @RDT @Regression
Feature: F1 - PO Creation via RDT

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - RDT (Handheld Device) Scenarios
  # Tests: F1-TC25
  # Entry Points: RDT
  # Purpose: Test PO creation through RDT handheld devices
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }

  # ─────────────────────────────────────────────────────────────
  # F1-TC25: Create PO via RDT device
  # ─────────────────────────────────────────────────────────────
  @F1-TC25 @P2 @RDTCreate
  Scenario: Create PO through RDT handheld device
    * def deviceId = 'RDT-KR01-001'
    * def userId = 'RDT-OPR-001'
    * def externalKey = 'PO-RDT-' + timestamp()

    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#(externalKey)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/rdt/po'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = deviceId
    And header X-RDT-User-Id = userId
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    And match response.poKey == '#present'
    And match response.createdBy == userId
    And match response.device == deviceId

    # Verify audit shows RDT source
    * def poKey = response.poKey
    * def poRecord = db.query("SELECT addwho, susr1 FROM dbo.po WHERE pokey = '" + poKey + "'")
    * def poRecordRow = karate.toMap(poRecord[0])
    * match poRecordRow.addwho == userId

  # ─────────────────────────────────────────────────────────────
  # F1-TC42: RDT PO with barcode scan
  # ─────────────────────────────────────────────────────────────
  @F1-TC42 @P2 @RDTBarcode
  Scenario: Create PO with scanned SKU barcode
    * def deviceId = 'RDT-KR01-002'
    * def userId = 'RDT-OPR-002'

    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-RDT-SCAN-' + timestamp())",
        "scannedItems": [
          { "barcode": "0123456789012", "qtyOrdered": 50 },
          { "barcode": "0123456789029", "qtyOrdered": 25 }
        ]
      }
      """

    Given path api + '/rdt/po/from-scan'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = deviceId
    And header X-RDT-User-Id = userId
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 201
    And match response.poKey == '#present'
    And match response.resolvedSkus == 2

  # ─────────────────────────────────────────────────────────────
  # F1-TC43: RDT invalid device
  # ─────────────────────────────────────────────────────────────
  @F1-TC43 @P2 @RDTInvalidDevice
  Scenario: RDT request with invalid device rejected
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-RDT-INV-' + timestamp()

    Given path api + '/rdt/po'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'INVALID-DEVICE-XXX'
    And header X-RDT-User-Id = 'RDT-OPR-001'
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 401
    And match response.errorCode == 'RDT_001'
    And match response.message == '#? _.toLowerCase().indexOf("device") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC44: RDT invalid operator
  # ─────────────────────────────────────────────────────────────
  @F1-TC44 @P2 @RDTInvalidUser
  Scenario: RDT request with invalid operator rejected
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-RDT-INV-USR-' + timestamp()

    Given path api + '/rdt/po'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-KR01-001'
    And header X-RDT-User-Id = 'INVALID-USER-XXX'
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 401
    And match response.errorCode == 'RDT_002'
    And match response.message == '#? _.toLowerCase().indexOf("operator") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC45: RDT operator not authorized for facility
  # ─────────────────────────────────────────────────────────────
  @F1-TC45 @P2 @RDTFacilityAuth
  Scenario: RDT operator not authorized for facility
    * def poRequest =
      """
      {
        "storerKey": "ADIDAS_IN",
        "facility": "IN01",
        "externalOrderKey": "#('PO-RDT-FAC-' + timestamp())",
        "lines": [
          { "sku": "ADI-ULTRABOOST-BLK", "qtyOrdered": 50 }
        ]
      }
      """

    # RDT-OPR-001 is authorized for KR01, not IN01
    Given path api + '/rdt/po'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-KR01-001'
    And header X-RDT-User-Id = 'RDT-OPR-001'
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 403
    And match response.errorCode == 'RDT_003'
    And match response.message == '#? _.toLowerCase().indexOf("authorized") >= 0'

  # ─────────────────────────────────────────────────────────────
  # F1-TC46: RDT offline mode queue
  # ─────────────────────────────────────────────────────────────
  @F1-TC46 @P3 @RDTOffline
  Scenario: RDT offline request queued for processing
    * def poRequest =
      """
      {
        "storerKey": "NIKE_KR",
        "facility": "KR01",
        "externalOrderKey": "#('PO-RDT-OFF-' + timestamp())",
        "offlineTimestamp": "#(timestamp() - 3600000)",
        "lines": [
          { "sku": "NK-AIRMAX90-BLK", "qtyOrdered": 100 }
        ]
      }
      """

    Given path api + '/rdt/po/offline-sync'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-KR01-001'
    And header X-RDT-User-Id = 'RDT-OPR-001'
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 202
    And match response.queuedForProcessing == true
    And match response.syncId == '#present'

  # ─────────────────────────────────────────────────────────────
  # F1-TC47: RDT session validation
  # ─────────────────────────────────────────────────────────────
  @F1-TC47 @P2 @RDTSession
  Scenario: RDT request validates active session
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-RDT-SESS-' + timestamp()

    Given path api + '/rdt/po'
    And header Authorization = 'Bearer ' + authToken
    And header X-RDT-Device-Id = 'RDT-KR01-001'
    And header X-RDT-User-Id = 'RDT-OPR-001'
    And header X-RDT-Session-Id = 'EXPIRED-SESSION-XXX'
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 401
    And match response.errorCode == 'RDT_004'
    And match response.message == '#? _.toLowerCase().indexOf("session") >= 0'

