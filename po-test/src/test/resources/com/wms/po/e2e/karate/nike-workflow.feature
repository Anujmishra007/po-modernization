@client @nike
Feature: Nike Client PO Population Workflow
  As a Nike warehouse operator
  I want to populate Nike POs with style-color validation
  So that Nike inventory is correctly received

  Background:
    * url baseUrl
    * def authHeaders = { 'X-User-Id': 'nike_operator', 'X-Region': 'US', 'X-Client': 'NIKE' }

  @smoke
  Scenario: Create Nike PO with valid style-color
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "NIKE",
      "facility": "US01",
      "externPoKey": "NIKE-TEST-001",
      "supplierKey": "NIKE-FACTORY-VN",
      "poType": "REPLENISHMENT",
      "lines": [
        {
          "sku": "NIKE-AF1-WHT-10",
          "qtyOrdered": 100,
          "lottables": {
            "STYLE_COLOR": "AF123456-100",
            "SIZE_CODE": "10.0",
            "SEASON": "SP24"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    And match response.poKey == '#present'

  Scenario: Fail Nike PO with invalid style-color format
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "NIKE",
      "facility": "US01",
      "externPoKey": "NIKE-INVALID-001",
      "lines": [
        {
          "sku": "NIKE-TEST",
          "qtyOrdered": 50,
          "lottables": {
            "STYLE_COLOR": "INVALID-FORMAT",
            "SIZE_CODE": "10.0"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'Nike style-color must match format'

  @populate
  Scenario: Populate Nike PO to ASN
    # First create a PO
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "NIKE",
      "facility": "US01",
      "externPoKey": "NIKE-POP-001",
      "lines": [
        {
          "sku": "NIKE-AF1-WHT-10",
          "qtyOrdered": 200,
          "lottables": {
            "STYLE_COLOR": "AF123456-100",
            "SIZE_CODE": "10.0",
            "SEASON": "SP24"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    * def poKey = response.poKey

    # Then populate it
    Given path '/api/v1/population/po/' + poKey
    And headers authHeaders
    And request { "storerKey": "NIKE", "facility": "US01" }
    When method POST
    Then status 200
    And match response.success == true
    And match response.receiptKey == '#present'

  Scenario: Validate Nike barcode format on population
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "NIKE",
      "facility": "US01",
      "externPoKey": "NIKE-BARCODE-001",
      "lines": [
        {
          "sku": "NIKE-JORDAN-001",
          "qtyOrdered": 50,
          "lottables": {
            "STYLE_COLOR": "JD789012-001",
            "SIZE_CODE": "9.5"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    * def poKey = response.poKey

    Given path '/api/v1/population/po/' + poKey
    And headers authHeaders
    And request { "storerKey": "NIKE", "facility": "US01", "validateBarcode": true }
    When method POST
    Then status 200
    And match response.metadata.barcodeFormat == 'NIKE_UPC'
