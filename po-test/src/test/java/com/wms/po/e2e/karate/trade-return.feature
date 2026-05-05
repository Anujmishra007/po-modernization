Feature: Trade Return E2E Tests
  End-to-end tests for trade return workflow

  Background:
    * url baseUrl
    * def auth = callonce read('classpath:karate-auth.js')
    * header Authorization = 'Bearer ' + auth.token

  Scenario: Successfully process trade return
    Given path '/api/v1/trade-return/process'
    And request
    """
    {
      "receiptKey": "ASN-RETURN-001",
      "storerKey": "TEST_STORER",
      "countryCode": "KR",
      "facility": "KR01",
      "userId": "e2e_test_user",
      "returnType": "RETURN",
      "reasonCode": "DAMAGED",
      "customerCode": "CUST-001"
    }
    """
    When method POST
    Then status 200
    And match response.success == true
    And match response.orderKey == '#notnull'

  Scenario: Trade return with original order reference
    Given path '/api/v1/trade-return/process'
    And request
    """
    {
      "receiptKey": "ASN-RETURN-002",
      "storerKey": "TEST_STORER",
      "countryCode": "KR",
      "facility": "KR01",
      "userId": "e2e_test_user",
      "returnType": "EXCHANGE",
      "reasonCode": "WRONG_ITEM",
      "originalOrderNumber": "ORD-ORIG-001"
    }
    """
    When method POST
    Then status 200
    And match response.success == true

  Scenario: Trade return with auto-release
    Given path '/api/v1/trade-return/process'
    And request
    """
    {
      "receiptKey": "ASN-RETURN-003",
      "storerKey": "TEST_STORER",
      "countryCode": "KR",
      "facility": "KR01",
      "userId": "e2e_test_user",
      "returnType": "RMA",
      "reasonCode": "DEFECTIVE",
      "autoRelease": true
    }
    """
    When method POST
    Then status 200
    And match response.success == true
    And match response.autoReleased == true

  Scenario: Fail trade return with invalid receipt
    Given path '/api/v1/trade-return/process'
    And request
    """
    {
      "receiptKey": "INVALID_RECEIPT",
      "storerKey": "TEST_STORER",
      "countryCode": "KR",
      "facility": "KR01",
      "userId": "e2e_test_user",
      "returnType": "RETURN"
    }
    """
    When method POST
    Then status 404
    And match response.success == false

  Scenario: Fail trade return with missing required fields
    Given path '/api/v1/trade-return/process'
    And request
    """
    {
      "receiptKey": "ASN-RETURN-001",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 400
    And match response.success == false
