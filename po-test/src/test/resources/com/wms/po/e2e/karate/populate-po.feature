Feature: PO Population E2E Tests
  End-to-end tests for PO population workflow

  Background:
    * url baseUrl
    * def auth = callonce read('classpath:karate-auth.js')
    * header Authorization = 'Bearer ' + auth.token

  Scenario: Successfully populate single PO
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["PO-TEST-001"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 200
    And match response.success == true
    And match response.receiptKey == '#notnull'
    And match response.detailCount > 0

  Scenario: Populate multiple POs into single receipt
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["PO-TEST-002", "PO-TEST-003"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 200
    And match response.success == true
    And match response.receiptKey == '#notnull'

  Scenario: Fail to populate non-existent PO
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["NON_EXISTENT_PO"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 400
    And match response.success == false
    And match response.errors[0] contains 'not found'

  Scenario: Fail to populate closed PO
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["PO-CLOSED-001"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 400
    And match response.success == false
    And match response.errors[0] contains 'closed'

  Scenario: Fail to populate with missing storer key
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["PO-TEST-001"],
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 400
    And match response.success == false
    And match response.errors[0] contains 'Storer key is required'

  Scenario: Get population workflow status
    # First start a population
    Given path '/api/v1/po/populate/async'
    And request
    """
    {
      "poKeys": ["PO-TEST-004"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 202
    And def workflowId = response.workflowId

    # Then check status
    Given path '/api/v1/po/populate/status/' + workflowId
    When method GET
    Then status 200
    And match response.status == '#notnull'
