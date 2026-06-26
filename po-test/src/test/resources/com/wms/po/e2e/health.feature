@health
Feature: Health Check APIs

  Scenario: Verify health endpoint returns UP status
    Given url baseUrl + '/api/v1/health'
    When method GET
    Then status 200
    And match response.status == 'UP'
    And match response.service == 'po-modernization'
    And match response.timestamp == '#present'

  Scenario: Verify info endpoint returns service details
    Given url baseUrl + '/api/v1/info'
    When method GET
    Then status 200
    And match response.name == 'PO Modernization Service'
    And match response.version == '1.0.0-SNAPSHOT'
    And match response contains { description: '#string' }

  Scenario: Verify actuator health endpoint
    Given url baseUrl + '/actuator/health'
    When method GET
    Then status 200
    And match response.status == 'UP'

  Scenario: Verify Swagger UI is accessible
    Given url baseUrl + '/swagger-ui/index.html'
    When method GET
    Then status 200
