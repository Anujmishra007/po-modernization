@smoke @health
Feature: Health Check API

  Background:
    * url baseUrl
    * path apiPath

  @hello
  Scenario: Service health check returns UP
    Given path '/health'
    When method GET
    Then status 200
    And match response.status == 'UP'
    And match response.service == 'po-modernization'

  @hello
  Scenario: Service readiness check returns READY
    Given path '/ready'
    When method GET
    Then status 200
    And match response.status == 'READY'

  Scenario: Temporal connection is healthy
    Given path '/health'
    When method GET
    Then status 200
    And match response.temporal == 'UP'
