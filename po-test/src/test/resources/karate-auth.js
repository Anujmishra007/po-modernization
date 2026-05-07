function fn() {
  // For local and CI testing, return a mock token
  // In real environments, this would call the auth service
  var env = karate.env || 'local';

  if (env == 'local' || env == 'ci') {
    return {
      token: 'mock-jwt-token-for-testing',
      expiresIn: 3600
    };
  }

  // For other environments, call the auth service
  var authUrl = 'http://auth-service.wms.internal/oauth/token';

  var response = karate.call('classpath:auth-request.feature', {
    authUrl: authUrl,
    clientId: karate.properties['auth.clientId'],
    clientSecret: karate.properties['auth.clientSecret']
  });

  return {
    token: response.access_token,
    expiresIn: response.expires_in
  };
}
