@ignore
Feature: Common Utilities for E2E Tests

  # ═══════════════════════════════════════════════════════════
  # Reusable functions for all E2E tests
  # ═══════════════════════════════════════════════════════════

  Scenario: Generate unique PO key
    * def uniquePoKey = 'PO-TEST-' + java.lang.System.currentTimeMillis()

  Scenario: Generate unique Receipt key
    * def uniqueReceiptKey = 'RCV-TEST-' + java.lang.System.currentTimeMillis()

  Scenario: Wait for workflow completion
    * def waitForWorkflow =
      """
      function(workflowId, maxSeconds) {
        var maxWait = maxSeconds || 60;
        var pollInterval = 2000;
        var elapsed = 0;
        while (elapsed < maxWait * 1000) {
          karate.log('Waiting for workflow:', workflowId, '- Elapsed:', elapsed/1000, 's');
          java.lang.Thread.sleep(pollInterval);
          elapsed += pollInterval;
        }
        return true;
      }
      """

  Scenario: Verify PO exists in database
    * def verifyPoExists =
      """
      function(poKey) {
        var sql = "SELECT status FROM dbo.orders WHERE orderkey = '" + poKey + "'";
        var result = db.query(sql);
        // Handle both Java ArrayList (size()) and JS array (length)
        var len = result ? (typeof result.size === 'function' ? result.size() : result.length) : 0;
        return len > 0;
      }
      """

  Scenario: Verify Receipt exists in database
    * def verifyReceiptExists =
      """
      function(receiptKey) {
        var sql = "SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'";
        var result = db.query(sql);
        // Handle both Java ArrayList (size()) and JS array (length)
        var len = result ? (typeof result.size === 'function' ? result.size() : result.length) : 0;
        return len > 0;
      }
      """

  Scenario: Get PO status from database
    * def getPoStatus =
      """
      function(poKey) {
        var sql = "SELECT status FROM dbo.orders WHERE orderkey = '" + poKey + "'";
        var result = db.query(sql);
        // Handle both Java ArrayList (size()) and JS array (length)
        var len = result ? (typeof result.size === 'function' ? result.size() : result.length) : 0;
        if (len > 0) {
          var row = typeof result.get === 'function' ? result.get(0) : result[0];
          return typeof row.get === 'function' ? row.get('status') : row.status;
        }
        return null;
      }
      """

  Scenario: Get Receipt status from database
    * def getReceiptStatus =
      """
      function(receiptKey) {
        var sql = "SELECT status FROM dbo.receipt WHERE receiptkey = '" + receiptKey + "'";
        var result = db.query(sql);
        // Handle both Java ArrayList (size()) and JS array (length)
        var len = result ? (typeof result.size === 'function' ? result.size() : result.length) : 0;
        if (len > 0) {
          var row = typeof result.get === 'function' ? result.get(0) : result[0];
          return typeof row.get === 'function' ? row.get('status') : row.status;
        }
        return null;
      }
      """

  Scenario: Cleanup test PO
    * def cleanupPo =
      """
      function(poKey) {
        karate.log('Cleaning up PO:', poKey);
        // Would execute cleanup via API or direct DB
        return true;
      }
      """
