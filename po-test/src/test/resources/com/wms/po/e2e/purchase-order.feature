@purchase-order @ignore
Feature: Purchase Order E2E Flow

  # This feature tests the complete PO lifecycle:
  # 1. Create PO
  # 2. Add PO Lines
  # 3. Validate PO
  # 4. Submit PO
  # 5. Check PO Status

  Background:
    * url baseUrl
    * path apiPath
    * def poId = null

  @create
  Scenario: Create a new Purchase Order
    Given path '/purchase-orders'
    And request
      """
      {
        "storerKey": "NIKE",
        "supplierCode": "SUP001",
        "expectedDeliveryDate": "2026-07-15",
        "warehouseCode": "WH01",
        "notes": "Test PO created by Karate E2E"
      }
      """
    When method POST
    Then status 201
    And match response.id == '#present'
    And match response.status == 'DRAFT'
    * def poId = response.id

  @add-lines @ignore
  Scenario: Add lines to Purchase Order
    # Requires a valid PO ID from previous test
    Given path '/purchase-orders', poId, 'lines'
    And request
      """
      {
        "lines": [
          {
            "sku": "SKU001",
            "expectedQty": 100,
            "unitPrice": 25.99,
            "uom": "EA"
          },
          {
            "sku": "SKU002",
            "expectedQty": 50,
            "unitPrice": 49.99,
            "uom": "CS"
          }
        ]
      }
      """
    When method POST
    Then status 200
    And match response.lineCount == 2

  @validate @ignore
  Scenario: Validate Purchase Order
    Given path '/purchase-orders', poId, 'validate'
    When method POST
    Then status 200
    And match response.valid == true
    And match response.errors == '#[0]'

  @submit @ignore
  Scenario: Submit Purchase Order
    Given path '/purchase-orders', poId, 'submit'
    When method POST
    Then status 200
    And match response.status == 'SUBMITTED'

  @get @ignore
  Scenario: Get Purchase Order details
    Given path '/purchase-orders', poId
    When method GET
    Then status 200
    And match response.id == poId
    And match response contains { storerKey: 'NIKE', status: '#string' }

  @list
  Scenario: List Purchase Orders with pagination
    Given path '/purchase-orders'
    And param page = 0
    And param size = 10
    And param storerKey = 'NIKE'
    When method GET
    Then status 200
    And match response.content == '#array'
    And match response.totalElements == '#number'
    And match response.pageable.pageSize == 10
