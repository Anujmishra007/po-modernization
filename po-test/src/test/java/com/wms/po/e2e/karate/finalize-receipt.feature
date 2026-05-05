Feature: Receipt Finalization E2E Tests
  End-to-end tests for receipt finalization workflow

  Background:
    * url baseUrl
    * def auth = callonce read('classpath:karate-auth.js')
    * header Authorization = 'Bearer ' + auth.token

  Scenario: Successfully finalize receipt
    # First create a receipt via population
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["PO-FINALIZE-001"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 200
    And def receiptKey = response.receiptKey

    # Then finalize the receipt
    Given path '/api/v1/receipt/finalize'
    And request
    """
    {
      "receiptKey": "#(receiptKey)",
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 200
    And match response.success == true
    And match response.receiptKey == receiptKey
    And match response.status == 'FINALIZED'

  Scenario: Fail to finalize already finalized receipt
    Given path '/api/v1/receipt/finalize'
    And request
    """
    {
      "receiptKey": "RCV-ALREADY-FINALIZED",
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 400
    And match response.success == false
    And match response.errors[0] contains 'already finalized'

  Scenario: Fail to finalize non-existent receipt
    Given path '/api/v1/receipt/finalize'
    And request
    """
    {
      "receiptKey": "NON_EXISTENT_RECEIPT",
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 404
    And match response.success == false

  Scenario: Finalize with inventory posting verification
    # Create receipt
    Given path '/api/v1/po/populate'
    And request
    """
    {
      "poKeys": ["PO-FINALIZE-INV-001"],
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 200
    And def receiptKey = response.receiptKey

    # Finalize
    Given path '/api/v1/receipt/finalize'
    And request
    """
    {
      "receiptKey": "#(receiptKey)",
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 200

    # Verify inventory posted
    Given path '/api/v1/inventory/receipt/' + receiptKey
    When method GET
    Then status 200
    And match response.inventoryRecords.length > 0

  Scenario: Get finalization workflow status
    Given path '/api/v1/receipt/finalize/async'
    And request
    """
    {
      "receiptKey": "RCV-ASYNC-001",
      "storerKey": "TEST_STORER",
      "facility": "KR01",
      "userId": "e2e_test_user"
    }
    """
    When method POST
    Then status 202
    And def workflowId = response.workflowId

    # Check status
    Given path '/api/v1/receipt/finalize/status/' + workflowId
    When method GET
    Then status 200
    And match response.status == '#notnull'
