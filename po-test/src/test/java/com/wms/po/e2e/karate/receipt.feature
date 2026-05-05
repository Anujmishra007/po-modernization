@receipt @crud
Feature: Receipt Operations

  Background:
    * url baseUrl
    * path apiPath

  @get
  Scenario: Get receipt by key
    # First populate a PO to create receipt
    * def poKey = 'PO-RCV-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

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
    * def receiptKey = response.receiptKey

    # Get receipt
    Given path '/receipt', receiptKey
    When method GET
    Then status 200
    And match response.receiptKey == receiptKey
    And match response.storerKey == testStorerKey
    And match response.facility == testFacility

  @details
  Scenario: Get receipt with details
    * def poKey = 'PO-RCV-DTL-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

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
    * def receiptKey = response.receiptKey

    # Get receipt with details
    Given path '/receipt', receiptKey, 'details'
    When method GET
    Then status 200
    And match response.receiptKey == receiptKey
    And match response.details == '#array'

  @list
  Scenario: List receipts by storer
    Given path '/receipt'
    And param storerKey = testStorerKey
    When method GET
    Then status 200
    And match response == '#array'
