@region @korea @asia
Feature: Korea Region PO Population Workflow
  As a Korea warehouse operator
  I want to populate POs with customs validation
  So that Korean import regulations are followed

  Background:
    * url baseUrl
    * def authHeaders = { 'X-User-Id': 'kr_operator', 'X-Region': 'ASIA-KR', 'X-Client': 'GENERIC' }

  @smoke
  Scenario: Create Korea PO with customs reference
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "KOREA_STORER",
      "facility": "KR01",
      "externPoKey": "KR-TEST-001",
      "supplierKey": "KR-SUPPLIER",
      "poType": "IMPORT",
      "lines": [
        {
          "sku": "KR-SKU-001",
          "qtyOrdered": 100,
          "lottables": {
            "LOT_NUMBER": "KRLOT2024001",
            "KC_MARK": "KC-12345",
            "CUSTOMS_REF": "CUSTOMS-2024-001",
            "COUNTRY_OF_ORIGIN": "CN"
          }
        }
      ]
    }
    """
    When method POST
    Then status 201
    And match response.poKey == '#present'

  Scenario: Fail Korea PO without customs reference
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "KOREA_STORER",
      "facility": "KR01",
      "externPoKey": "KR-INVALID-001",
      "lines": [
        {
          "sku": "KR-SKU-001",
          "qtyOrdered": 50,
          "lottables": {
            "LOT_NUMBER": "KRLOT2024002"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'Customs reference is required for Korea region'

  @populate @customs
  Scenario: Populate Korea PO with customs clearance
    # Create PO
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "KOREA_STORER",
      "facility": "KR01",
      "externPoKey": "KR-POP-001",
      "lines": [
        {
          "sku": "KR-SKU-001",
          "qtyOrdered": 200,
          "lottables": {
            "LOT_NUMBER": "KRLOT2024003",
            "KC_MARK": "KC-67890",
            "CUSTOMS_REF": "CUSTOMS-2024-003",
            "COUNTRY_OF_ORIGIN": "VN"
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
    And request { "storerKey": "KOREA_STORER", "facility": "KR01" }
    When method POST
    Then status 200
    And match response.success == true
    And match response.metadata.customsCleared == true

  Scenario: Validate KC mark format
    Given path '/api/v1/po'
    And headers authHeaders
    And request
    """
    {
      "storerKey": "KOREA_STORER",
      "facility": "KR01",
      "externPoKey": "KR-KC-001",
      "lines": [
        {
          "sku": "KR-SKU-001",
          "qtyOrdered": 50,
          "lottables": {
            "LOT_NUMBER": "KRLOT2024004",
            "KC_MARK": "INVALID",
            "CUSTOMS_REF": "CUSTOMS-2024-004"
          }
        }
      ]
    }
    """
    When method POST
    Then status 400
    And match response.errors contains 'KC mark format invalid'
