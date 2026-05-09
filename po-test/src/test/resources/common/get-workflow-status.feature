@ignore
Feature: Get Workflow Status

  # Reusable feature to get workflow status from API
  # Called by: karate.call('classpath:common/get-workflow-status.feature', { workflowId: workflowId })
  # Returns: { status: 'RUNNING' | 'COMPLETED' | 'FAILED' }

  Scenario: Get workflow status
    Given url baseUrl
    And path apiPath + '/workflows/' + workflowId + '/status'
    And header Authorization = 'Bearer ' + karate.callSingle('classpath:karate-auth.js').token
    When method get
    Then status 200
    * def status = response.status
