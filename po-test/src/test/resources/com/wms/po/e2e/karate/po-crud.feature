@crud @po
Feature: PO CRUD Operations

  Background:
    * url baseUrl
    * path apiPath
    * def poKey = 'PO-' + timestamp()
    * def testData = read('classpath:com/wms/po/e2e/karate/data/po-test-data.json')

  @smoke @create
  Scenario: Create a new PO successfully
    Given path '/po'
    And request
    """
    {
      "poKey": "#(poKey)",
      "externPoKey": "EXT-#(poKey)",
      "storerKey": "#(testStorerKey)",
      "facility": "#(testFacility)",
      "poType": "STANDARD",
      "status": "0",
      "supplierKey": "SUP001",
      "supplierName": "Test Supplier"
    }
    """
    When method POST
    Then status 201
    And match response.poKey == poKey
    And match response.status == '0'

  @smoke @read
  Scenario: Get PO by key
    # First create a PO
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)" }
    When method POST
    Then status 201

    # Then retrieve it
    Given path '/po', poKey
    When method GET
    Then status 200
    And match response.poKey == poKey

  @update
  Scenario: Update PO status
    # First create a PO
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)", "status": "0" }
    When method POST
    Then status 201

    # Update status
    Given path '/po', poKey, 'status'
    And request { "status": "1" }
    When method PUT
    Then status 200
    And match response.status == '1'

  @delete
  Scenario: Delete PO
    # First create a PO
    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "#(testStorerKey)", "facility": "#(testFacility)" }
    When method POST
    Then status 201

    # Delete it
    Given path '/po', poKey
    When method DELETE
    Then status 204

    # Verify it's gone
    Given path '/po', poKey
    When method GET
    Then status 404

  @negative
  Scenario: Get non-existent PO returns 404
    Given path '/po', 'NON_EXISTENT_PO_KEY'
    When method GET
    Then status 404

  @negative
  Scenario: Create PO with missing required fields
    Given path '/po'
    And request { "poKey": "#(poKey)" }
    When method POST
    Then status 400
    And match response.errors contains 'storerKey is required'
