function fn() {
  var env = karate.env; // get system property 'karate.env'
  karate.log('karate.env system property was:', env);

  if (!env) {
    env = 'local';
  }

  var config = {
    env: env,
    baseUrl: 'http://localhost:8080',
    apiPath: '/api/v1',
    temporalNamespace: 'default',

    // Test data
    testStorerKey: 'TEST_STORER_001',
    testFacility: 'KR01',
    testUserId: 'test_user',

    // Timeouts
    connectTimeout: 5000,
    readTimeout: 30000,

    // Retry configuration
    retryCount: 3,
    retryInterval: 1000
  };

  if (env == 'local') {
    config.baseUrl = 'http://localhost:8080';
  } else if (env == 'docker') {
    config.baseUrl = 'http://po-api:8080';
  } else if (env == 'staging') {
    config.baseUrl = 'https://po-modernization-staging.example.com';
  } else if (env == 'prod') {
    config.baseUrl = 'https://po-modernization.example.com';
  }

  // Helper functions
  config.sleep = function(ms) {
    java.lang.Thread.sleep(ms);
  };

  config.uuid = function() {
    return java.util.UUID.randomUUID() + '';
  };

  config.timestamp = function() {
    return java.lang.System.currentTimeMillis() + '';
  };

  karate.log('Running tests against:', config.baseUrl);

  return config;
}
