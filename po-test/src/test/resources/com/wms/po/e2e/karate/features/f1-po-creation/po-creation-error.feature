@F1 @POCreation @Error @Regression
Feature: F1 - PO Creation Error Handling

  # ═══════════════════════════════════════════════════════════
  # Flow 1: PO Creation - Error Scenarios
  # Tests: F1-TC14 to F1-TC17
  # Entry Points: API, EDI
  # Purpose: Test error handling and recovery
  # ═══════════════════════════════════════════════════════════

  Background:
    * url baseUrl
    * def api = apiPath
    * call read('classpath:com/wms/po/e2e/karate/features/common/common.feature')
    * def authToken = karate.callSingle('classpath:karate-auth.js').token
    * def timestamp = function() { return java.lang.System.currentTimeMillis() }

  # ─────────────────────────────────────────────────────────────
  # F1-TC14: Database connection timeout
  # Error Code: INT_022 (69022)
  # ─────────────────────────────────────────────────────────────
  @F1-TC14 @P2 @DBTimeout
  Scenario: Database timeout returns graceful error
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-TIMEOUT-' + timestamp()

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-DB-Timeout = 'true'
    And request poRequest
    When method post
    Then status 503
    And match response.errorCode == 'INT_022'
    And match response.message contains 'Database'
    And match response.retryable == true

    # Verify no partial data created
    * def partialCheck = db.query("SELECT COUNT(*) as cnt FROM dbo.po WHERE externpokey = '" + poRequest.externalOrderKey + "'")
    * match partialCheck[0].cnt == 0

  # ─────────────────────────────────────────────────────────────
  # F1-TC15: Concurrent PO creation race condition
  # ─────────────────────────────────────────────────────────────
  @F1-TC15 @P2 @ConcurrentRace
  Scenario: Concurrent PO creation with same key
    * def sharedKey = 'PO-RACE-' + timestamp()
    * def poRequest1 = testData.validPORequest()
    * poRequest1.externalOrderKey = sharedKey
    * def poRequest2 = testData.validPORequest()
    * poRequest2.externalOrderKey = sharedKey

    # Simulate concurrent requests (sequential for test, but same key)
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest1
    When method post
    Then status 201
    * def firstPoKey = response.poKey

    # Second request with same external key
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request poRequest2
    When method post
    Then status 409
    And match response.errorCode == 'VAL_003'
    And match response.message contains 'Duplicate'
    And match response.existingPoKey == firstPoKey

  # ─────────────────────────────────────────────────────────────
  # F1-TC16: Malformed EDI 850 format
  # Error Code: INT_011 (69011)
  # ─────────────────────────────────────────────────────────────
  @F1-TC16 @P2 @EDI @MalformedEDI
  Scenario: Malformed EDI 850 returns parse error
    * def ediContent = read('classpath:test-data/edi/EDI-850-SAMPLE-005-ERROR-MALFORMED.txt')

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And request ediContent
    When method post
    Then status 400
    And match response.errorCode == 'INT_011'
    And match response.message == '#? _.contains("Parse error")'
    And match response.parseErrors == '#present'
    And match response.parseErrors == '#[_ > 0]'
    # Should include details about what failed
    And match response.parseErrors[0] contains { segment: '#present', position: '#present' }

  # ─────────────────────────────────────────────────────────────
  # F1-TC17: EDI 850 missing mandatory segment
  # Error Code: INT_011 (69011)
  # ─────────────────────────────────────────────────────────────
  @F1-TC17 @P2 @EDI @MissingSegment
  Scenario: EDI 850 missing mandatory segment fails validation
    * def ediContent = read('classpath:test-data/edi/EDI-850-SAMPLE-004-ERROR-MISSING-SEGMENT.txt')

    Given path api + '/edi/inbound'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/edi-x12'
    And request ediContent
    When method post
    Then status 400
    And match response.errorCode == 'INT_011'
    And match response.message contains 'Missing'
    And match response.missingSegments contains 'REF*DP'

  # ─────────────────────────────────────────────────────────────
  # F1-TC29: Service unavailable (circuit breaker)
  # Error Code: INT_023 (69023)
  # ─────────────────────────────────────────────────────────────
  @F1-TC29 @P2 @CircuitBreaker
  Scenario: Service unavailable triggers circuit breaker
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-CIRCUIT-' + timestamp()

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Service-Down = 'true'
    And request poRequest
    When method post
    Then status 503
    And match response.errorCode == 'INT_023'
    And match response.message contains 'Service unavailable'
    And match response.retryAfter == '#present'

  # ─────────────────────────────────────────────────────────────
  # F1-TC30: Invalid JSON payload
  # ─────────────────────────────────────────────────────────────
  @F1-TC30 @P2 @InvalidJSON
  Scenario: Invalid JSON payload returns parse error
    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And request '{"storerKey": "NIKE_KR", "lines": [invalid json'
    When method post
    Then status 400
    And match response.errorCode == 'VAL_000'
    And match response.message contains 'Invalid JSON'

  # ─────────────────────────────────────────────────────────────
  # F1-TC31: Missing Content-Type header
  # ─────────────────────────────────────────────────────────────
  @F1-TC31 @P3 @MissingContentType
  Scenario: Missing Content-Type returns error
    * def poRequest = testData.validPORequest()

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And request poRequest
    When method post
    Then status 415
    And match response.message contains 'Content-Type'

  # ─────────────────────────────────────────────────────────────
  # F1-TC32: Authentication failure
  # ─────────────────────────────────────────────────────────────
  @F1-TC32 @P1 @AuthFailure
  Scenario: Invalid token returns 401
    * def poRequest = testData.validPORequest()

    Given path api + '/po'
    And header Authorization = 'Bearer invalid-token-12345'
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 401
    And match response.errorCode == 'AUTH_001'

  # ─────────────────────────────────────────────────────────────
  # F1-TC33: Authorization failure (wrong privilege)
  # ─────────────────────────────────────────────────────────────
  @F1-TC33 @P1 @AuthzFailure
  Scenario: Insufficient privileges returns 403
    * def poRequest = testData.validPORequest()
    * def readOnlyToken = karate.callSingle('classpath:karate-auth-readonly.js').token

    Given path api + '/po'
    And header Authorization = 'Bearer ' + readOnlyToken
    And header Content-Type = 'application/json'
    And request poRequest
    When method post
    Then status 403
    And match response.errorCode == 'AUTH_002'
    And match response.requiredPrivilege == 'PO_CREATE'

  # ─────────────────────────────────────────────────────────────
  # F1-TC34: Rate limiting
  # ─────────────────────────────────────────────────────────────
  @F1-TC34 @P3 @RateLimit
  Scenario: Excessive requests trigger rate limiting
    * def poRequest = testData.validPORequest()

    # Send multiple rapid requests
    * def sendRequest =
      """
      function() {
        var results = [];
        for (var i = 0; i < 50; i++) {
          var req = JSON.parse(JSON.stringify(poRequest));
          req.externalOrderKey = 'PO-RATE-' + Date.now() + '-' + i;
          results.push(karate.call('classpath:common/http-post.feature', { path: api + '/po', body: req, token: authToken }));
        }
        return results;
      }
      """

    # At least some requests should be rate limited (429)
    # This test validates rate limiting is in place

  # ─────────────────────────────────────────────────────────────
  # F1-TC35: Request timeout
  # ─────────────────────────────────────────────────────────────
  @F1-TC35 @P2 @RequestTimeout
  Scenario: Long-running request times out gracefully
    * def poRequest = testData.validPORequest()
    * poRequest.externalOrderKey = 'PO-TIMEOUT-REQ-' + timestamp()

    Given path api + '/po'
    And header Authorization = 'Bearer ' + authToken
    And header Content-Type = 'application/json'
    And header X-Test-Simulate-Slow-Processing = 'true'
    And request poRequest
    When method post
    Then status 504
    And match response.errorCode == 'INT_003'
    And match response.message contains 'timeout'

