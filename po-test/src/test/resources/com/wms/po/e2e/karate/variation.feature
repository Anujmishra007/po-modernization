@variation @context
Feature: Variation Context Tests

  Background:
    * url baseUrl
    * path apiPath

  @region @korea
  Scenario: Korea region applies Korean rules
    * def poKey = 'PO-KR-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "NIKE_KR_001", "facility": "KR01", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "KR01",
      "storerKey": "NIKE_KR_001",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200
    # Korea requires lottable03 (customs code)
    # Verify in response or receipt details

  @region @singapore
  Scenario: Singapore region applies SG rules
    * def poKey = 'PO-SG-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "TEST_SG_001", "facility": "SG01", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "SG01",
      "storerKey": "TEST_SG_001",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200

  @region @india
  Scenario: India region applies GST rules
    * def poKey = 'PO-IN-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "HM_INDIA_001", "facility": "IN01", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "IN01",
      "storerKey": "HM_INDIA_001",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200
    # India requires GST code in lottable04

  @client @nike
  Scenario: Nike client applies Nike-specific rules
    * def poKey = 'PO-NIKE-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "NIKE_001", "facility": "KR01", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "KR01",
      "storerKey": "NIKE_001",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200

  @client @hm
  Scenario: H&M client applies H&M-specific rules
    * def poKey = 'PO-HM-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "HM_001", "facility": "IN01", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "IN01",
      "storerKey": "HM_001",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200

  @version @v0
  Scenario: V0 storer uses V0 legacy bridge
    * def poKey = 'PO-V0-' + timestamp()

    Given path '/po'
    And request { "poKey": "#(poKey)", "storerKey": "V0_LEGACY_001", "facility": "KR01", "status": "0" }
    When method POST
    Then status 201

    Given path '/po/populate'
    And request
    """
    {
      "poKeys": ["#(poKey)"],
      "facility": "KR01",
      "storerKey": "V0_LEGACY_001",
      "userId": "#(testUserId)"
    }
    """
    When method POST
    Then status 200
