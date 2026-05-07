@region @india @asia
Feature: India Region PO Population Workflow
  As an India warehouse operator
  I want to populate POs with GST validation
  So that Indian tax regulations are followed

  Background:
    * url baseUrl
    * def authHeaders = { 'X-User-Id': 'in_operator', 'X-Region': 'ASIA-IN', 'X-Client': 'GENERIC' }

  @smoke
  Scenario: Create India PO with GST invoice
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "INDIA_STORER",
      "facility": "IN01",
      "externPoKey": "IN-TEST-001",
      "supplierKey": "IN-SUPPLIER",
      "poType": "DOMESTIC",
      "lines": [
        {
          "sku": "IN-SKU-001",
          "qtyOrdered": 500,
          "lottables": {
            "LOT_NUMBER": "INLOT2024001",
            "GST_INVOICE": "GST/INV/2024/001234",
            "HSN_CODE": "84713010",
            "MRP": "1999.00"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    And match response.poKey == '#present'

  Scenario: Fail India PO without GST invoice
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "INDIA_STORER",
      "facility": "IN01",
      "externPoKey": "IN-INVALID-001",
      "lines": [
        {
          "sku": "IN-SKU-001",
          "qtyOrdered": 100,
          "lottables": {
            "LOT_NUMBER": "INLOT2024002"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'GST invoice number is required for India region'

  @populate @gst
  Scenario: Populate India PO with E-Way bill generation
    # Create PO
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "INDIA_STORER",
      "facility": "IN01",
      "externPoKey": "IN-POP-001",
      "lines": [
        {
          "sku": "IN-SKU-001",
          "qtyOrdered": 1000,
          "unitPrice": 1500.00,
          "lottables": {
            "LOT_NUMBER": "INLOT2024003",
            "GST_INVOICE": "GST/INV/2024/005678",
            "HSN_CODE": "84713010",
            "MRP": "2499.00"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    * def poKey = response.poKey

    # Populate - should generate E-Way bill for value > 50000
    Given path '/api/v1/population/po/' + poKey
    And headers authHeaders
    And request { "storerKey": "INDIA_STORER", "facility": "IN01" }
    When method POST
    Then status 200
    And match response.success == true
    And match response.metadata.eWayBillRequired == true

  Scenario: Validate HSN code format
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "INDIA_STORER",
      "facility": "IN01",
      "externPoKey": "IN-HSN-001",
      "lines": [
        {
          "sku": "IN-SKU-001",
          "qtyOrdered": 50,
          "lottables": {
            "LOT_NUMBER": "INLOT2024004",
            "GST_INVOICE": "GST/INV/2024/009999",
            "HSN_CODE": "INVALID"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'HSN code must be 4-8 digits'

  @mrp
  Scenario: Validate MRP is present for retail items
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "INDIA_STORER",
      "facility": "IN01",
      "externPoKey": "IN-MRP-001",
      "poType": "RETAIL",
      "lines": [
        {
          "sku": "IN-RETAIL-001",
          "qtyOrdered": 100,
          "lottables": {
            "LOT_NUMBER": "INLOT2024005",
            "GST_INVOICE": "GST/INV/2024/010101",
            "HSN_CODE": "62034200"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'MRP is required for retail items in India'
