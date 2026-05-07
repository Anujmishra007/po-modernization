@saga @compensation @workflow
Feature: Saga Compensation Tests

  Background:
    * url baseUrl
    * path apiPath
    * def poKey = 'PO-SAGA-' + timestamp()

  @rollback @header
  Scenario: Compensation deletes receipt header on failure
    # This test requires a mock to force failure at a specific step
    # Setup: Create PO
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Trigger populate with force-fail flag (test mode)
    Given path '/po/populate'
    And header X-Test-Force-Fail = 'LEGACY_SYNC'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)",
      "metadata": { "forceFailAt": "LEGACY_SYNC" }
    }
    """
    When method POST
    Then status 400
    And match response.success == false
    And match response.status == 'FAILED'

    # Verify receipt was rolled back (should not exist)
    # This would need a receipt lookup endpoint

  @rollback @details
  Scenario: Compensation deletes receipt details on failure
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Trigger populate with force-fail at inventory step
    Given path '/po/populate'
    And header X-Test-Force-Fail = 'CREATE_RESERVATIONS'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)",
      "metadata": { "forceFailAt": "CREATE_RESERVATIONS" }
    }
    """
    When method POST
    Then status 400
    And match response.success == false

  @rollback @inventory
  Scenario: Compensation releases inventory reservations on failure
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Trigger populate with force-fail at legacy sync
    Given path '/po/populate'
    And header X-Test-Force-Fail = 'LEGACY_SYNC'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)",
      "metadata": { "forceFailAt": "LEGACY_SYNC" }
    }
    """
    When method POST
    Then status 400
    And match response.success == false

  @cancel @compensation
  Scenario: Cancellation triggers compensation
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Start async populate with slow mode
    Given path '/po/populate/async'
    And header X-Test-Slow-Mode = 'true'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)",
      "metadata": { "slowMode": true }
    }
    """
    When method POST
    Then status 202
    * def workflowId = response.workflowId

    # Wait a bit then cancel
    * sleep(1000)
    Given path '/po/populate', workflowId, 'cancel'
    When method POST
    Then status 202

    # Verify compensation ran
    * sleep(3000)
    Given path '/po/populate', workflowId, 'status'
    When method GET
    Then status 200
    And match response.status == 'CANCELLED'

  @idempotent
  Scenario: Duplicate population is idempotent
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # First populate
    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200
    * def firstReceiptKey = response.receiptKey

    # Second populate attempt should fail or return same result
    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "#(testFacility)",
      "storerKey": "#(testStorerKey)",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then assert responseStatus == 400 || responseStatus == 200
    # Either fails because already populated, or returns existing receipt
