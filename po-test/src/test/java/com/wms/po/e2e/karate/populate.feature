@populate @workflow
Feature: PO Population to ASN/Receipt

  Background:
    * url baseUrl
    * path apiPath
    * def poKey = 'PO-' + timestamp()
    * def populateRequest =
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)"
    }
    """

  @smoke @sync
  Scenario: Synchronous population - happy path
    # Create PO first
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Populate PO to ASN
    Given path '/po/populate'
    And request populateRequest
    When method POST
    Then status 200
    And match response.success == true
    And match response.receiptKey != null
    And match response.detailCount >= 0

  @async
  Scenario: Asynchronous population returns workflow ID
    # Create PO first
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Start async population
    Given path '/po/populate/async'
    And request populateRequest
    When method POST
    Then status 202
    And match response.workflowId != null
    And match response.statusUrl contains '/status'
    * def workflowId = response.workflowId

    # Check workflow status
    Given path '/po/populate', workflowId, 'status'
    And retry until response.status == 'COMPLETED' || response.status == 'FAILED'
    When method GET
    Then status 200
    And match response.workflowId == workflowId

  @status
  Scenario: Query workflow status shows progress
    # Create and start async population
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate/async'
    And request populateRequest
    When method POST
    Then status 202
    * def workflowId = response.workflowId

    # Query status
    Given path '/po/populate', workflowId, 'status'
    When method GET
    Then status 200
    And match response.status == '#string'
    And match response.currentStep == '#string'
    And match response.progress >= 0
    And match response.progress <= 100

  @cancel
  Scenario: Cancel running workflow
    # Create and start async population
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate/async'
    And request populateRequest
    When method POST
    Then status 202
    * def workflowId = response.workflowId

    # Cancel workflow
    Given path '/po/populate', workflowId, 'cancel'
    When method POST
    Then status 202

    # Verify cancelled
    * sleep(2000)
    Given path '/po/populate', workflowId, 'status'
    When method GET
    Then status 200
    And match response.status == '#? _ == "CANCELLED" || _ == "COMPENSATING"'

  @multi-po
  Scenario: Populate multiple POs to single receipt
    * def poKey1 = 'PO-MULTI-1-' + timestamp()
    * def poKey2 = 'PO-MULTI-2-' + timestamp()

    # Create first PO
    Given path '/po'
    And request { "poKey": "#(poKey1)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Create second PO
    Given path '/po'
    And request { "poKey": "#(poKey2)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Populate both
    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey1)", "#(poKey2)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200
    And match response.success == true
    And match response.receiptKey != null

  @negative @validation
  Scenario: Populate closed PO fails validation
    # Create closed PO
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "9" }
    When method POST
    Then status 201

    # Try to populate
    Given path '/po/populate'
    And request populateRequest
    When method POST
    Then status 400
    And match response.success == false
    And match response.errors[0] contains 'closed'

  @negative @not-found
  Scenario: Populate non-existent PO fails
    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["NON_EXISTENT_PO"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 400
    And match response.success == false
    And match response.errors[0] contains 'not found'
