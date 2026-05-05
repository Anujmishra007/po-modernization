@client @hm
Feature: H&M Client PO Population Workflow
  As an H&M warehouse operator
  I want to populate H&M POs with article number validation
  So that H&M inventory is correctly received

  Background:
    * url baseUrl
    * def authHeaders = { 'X-User-Id': 'hm_operator', 'X-Region': 'EU', 'X-Client': 'HM' }

  @smoke
  Scenario: Create H&M PO with valid article number
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "HM",
      "facility": "EU01",
      "externPoKey": "HM-TEST-001",
      "supplierKey": "HM-SUPPLIER-BD",
      "poType": "SEASONAL",
      "lines": [
        {
          "sku": "HM-DRESS-001-S",
          "qtyOrdered": 500,
          "lottables": {
            "ARTICLE_NUMBER": "1234567",
            "COLOR_CODE": "WHITE",
            "SIZE_CODE": "S",
            "QUALITY_CODE": "A"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    And match response.poKey == '#present'

  Scenario: Fail H&M PO with invalid article number
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "HM",
      "facility": "EU01",
      "externPoKey": "HM-INVALID-001",
      "lines": [
        {
          "sku": "HM-TEST",
          "qtyOrdered": 100,
          "lottables": {
            "ARTICLE_NUMBER": "12345",
            "COLOR_CODE": "RED"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'H&M article number must be 7 digits'

  @populate
  Scenario: Populate H&M PO with EAN13 barcode
    # Create PO
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "HM",
      "facility": "EU01",
      "externPoKey": "HM-POP-001",
      "lines": [
        {
          "sku": "HM-SHIRT-001-M",
          "qtyOrdered": 1000,
          "lottables": {
            "ARTICLE_NUMBER": "9876543",
            "COLOR_CODE": "BLUE",
            "SIZE_CODE": "M",
            "QUALITY_CODE": "A"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    * def poKey = response.poKey

    # Populate
    Given path '/api/v1/population/po/' + poKey
    And headers authHeaders
    And request { "storerKey": "HM", "facility": "EU01" }
    When method POST
    Then status 200
    And match response.success == true
    And match response.metadata.barcodeFormat == 'EAN13'

  @seasonal
  Scenario: Process H&M seasonal collection PO
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "HM",
      "facility": "EU01",
      "externPoKey": "HM-SEASON-SP24",
      "poType": "SEASONAL_COLLECTION",
      "lines": [
        {
          "sku": "HM-SP24-JACKET-001",
          "qtyOrdered": 250,
          "lottables": {
            "ARTICLE_NUMBER": "5555555",
            "COLOR_CODE": "NAVY",
            "SIZE_CODE": "L",
            "SEASON": "SPRING2024"
          }
        },
        {
          "sku": "HM-SP24-PANTS-001",
          "qtyOrdered": 300,
          "lottables": {
            "ARTICLE_NUMBER": "6666666",
            "COLOR_CODE": "BEIGE",
            "SIZE_CODE": "M",
            "SEASON": "SPRING2024"
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
    And request { "storerKey": "HM", "facility": "EU01", "priority": "HIGH" }
    When method POST
    Then status 200
    And match response.success == true
    And match response.linesProcessed == 2
