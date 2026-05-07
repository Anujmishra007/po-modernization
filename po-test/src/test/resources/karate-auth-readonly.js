function fn() {
  // Return a mock read-only token for testing authorization scenarios
  // This token should have minimal privileges (read-only, no write access)
  var env = karate.env || 'local';

  if (env == 'local' || env == 'ci') {
    return {
      token: 'mock-jwt-token-readonly-for-testing',
      expiresIn: 3600,
      privileges: ['VIEW_PO', 'VIEW_RECEIPT']
    };
  }

  // For other environments, call the auth service with read-only scope
  var authUrl = 'http://auth-service.wms.internal/oauth/token';

  var response = karate.call('classpath:auth-request.feature', {
    authUrl: authUrl,
    clientId: karate.properties['auth.clientId'],
    clientSecret: karate.properties['auth.clientSecret'],
    scope: 'read-only'
  });

  return {
    token: response.access_token,
    expiresIn: response.expires_in
  };
}
