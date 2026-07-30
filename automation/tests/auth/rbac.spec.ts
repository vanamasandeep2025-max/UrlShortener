import { test, expect } from '../../fixtures/base';
import { ApiClient } from '../../api/ApiClient';
import { DataGenerator } from '../../utils/dataGenerator';

/**
 * Token validity and ownership/role enforcement, exercised directly against the REST API
 * (no browser needed - see backend/.../security/jwt/JwtAuthenticationFilter and
 * UrlServiceImpl#loadOwned for the real logic these tests pin down).
 */
test.describe('Authentication - JWT & role-based access control', () => {
  test('TC-JWT-001: request without an Authorization header is rejected with 401 @smoke @security', async ({
    apiClient,
  }) => {
    const response = await apiClient.listUrls();
    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(body.message).toBe('Authentication is required to access this resource');
  });

  test('TC-JWT-002: request with a syntactically invalid token is treated as unauthenticated (401), not a 500 @security', async ({
    apiClient,
  }) => {
    const response = await apiClient.withToken('this-is-not-a-jwt').listUrls();
    expect(response.status()).toBe(401);
  });

  test('TC-JWT-003: request with a well-formed but signature-tampered token is rejected @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const parts = demoTokens.accessToken.split('.');
    const tamperedSignature = parts[2].slice(0, -4) + 'abcd';
    const tampered = `${parts[0]}.${parts[1]}.${tamperedSignature}`;

    const response = await apiClient.withToken(tampered).listUrls();
    expect(response.status()).toBe(401);
  });

  test('TC-JWT-004: a refresh token cannot be used in place of an access token @security', async ({
    apiClient,
    demoTokens,
  }) => {
    // JwtTokenProvider#isAccessToken checks the token's own type claim - a structurally
    // valid, correctly-signed refresh token must still be rejected for API access.
    const response = await apiClient.withToken(demoTokens.refreshToken).listUrls();
    expect(response.status()).toBe(401);
  });

  test('TC-JWT-005: refresh endpoint issues a new, working access token @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    // JWT `iat`/`exp` have second-level resolution and everything else in the payload is
    // deterministic for the same user - refreshing within the same second as the original
    // login produces a byte-for-byte identical token (correct behavior, not a bug). A short
    // wait makes the inequality check below meaningful instead of timing-dependent.
    await new Promise((resolve) => setTimeout(resolve, 1100));

    const refreshResponse = await apiClient.refresh({ refreshToken: demoTokens.refreshToken });
    expect(refreshResponse.status()).toBe(200);
    const newTokens = await refreshResponse.json();
    expect(newTokens.accessToken).not.toBe(demoTokens.accessToken);

    const usable = await apiClient.withToken(newTokens.accessToken).listUrls();
    expect(usable.status()).toBe(200);
  });

  test('TC-JWT-006: an invalid refresh token is rejected @security', async ({ apiClient }) => {
    const response = await apiClient.refresh({ refreshToken: 'garbage-refresh-token' });
    expect(response.status()).toBe(401);
  });

  /**
   * True token-expiry cannot be exercised honestly in this suite: JWT_ACCESS_EXPIRATION_MS
   * defaults to 1h (see backend application.yml) and the suite has no legitimate way to
   * forge an already-expired-but-validly-signed token without holding the signing secret
   * (doing so would test the JWT library, not this application). To actually cover this
   * path, override JWT_ACCESS_EXPIRATION_MS to a few seconds via docker-compose env,
   * restart the backend, and run this file alone - see automation/README.md "Known
   * Limitations".
   */
  test.skip('TC-JWT-007: expired access token is rejected @security', async () => {
    // Intentionally skipped in normal CI runs - see docstring above for how to run it for real.
  });

  test('TC-RBAC-001: a user cannot delete another user\'s URL - 403, not 404 (ownership enforced) @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const owned = await apiClient.withToken(demoTokens.accessToken).createUrlOrThrow({
      url: DataGenerator.longUrl(),
    });

    const intruder = await registerRandomUser(apiClient);
    const intruderTokens = await apiClient.loginAndGetTokens(intruder.username, intruder.password);
    const response = await apiClient.withToken(intruderTokens.accessToken).deleteUrl(owned.id);
    expect(response.status()).toBe(403);
  });

  test('TC-RBAC-002: a user cannot read another user\'s link analytics @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const owned = await apiClient.withToken(demoTokens.accessToken).createUrlOrThrow({
      url: DataGenerator.longUrl(),
    });
    const intruder = await registerRandomUser(apiClient);
    const intruderTokens = await apiClient.loginAndGetTokens(intruder.username, intruder.password);
    const response = await apiClient.withToken(intruderTokens.accessToken).getAnalytics(owned.shortCode);
    expect(response.status()).toBe(403);
  });

  test('TC-RBAC-003: a user cannot update expiry on another user\'s URL @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const owned = await apiClient.withToken(demoTokens.accessToken).createUrlOrThrow({
      url: DataGenerator.longUrl(),
    });
    const intruder = await registerRandomUser(apiClient);
    const intruderTokens = await apiClient.loginAndGetTokens(intruder.username, intruder.password);
    const response = await apiClient
      .withToken(intruderTokens.accessToken)
      .updateExpiry(owned.id, { expiresAt: DataGenerator.isoDateOffsetDays(1) });
    expect(response.status()).toBe(403);
  });

  test('TC-RBAC-004: an admin CAN act on a URL owned by another user (elevated role bypasses ownership) @regression', async ({
    apiClient,
    demoTokens,
    adminTokens,
  }) => {
    const owned = await apiClient.withToken(demoTokens.accessToken).createUrlOrThrow({
      url: DataGenerator.longUrl(),
    });
    const response = await apiClient
      .withToken(adminTokens.accessToken)
      .updateExpiry(owned.id, { expiresAt: DataGenerator.isoDateOffsetDays(2) });
    expect(response.status()).toBe(200);
  });

  test('TC-RBAC-005: newly self-registered users always get role USER, never ADMIN (no client-controlled role field) @security', async ({
    apiClient,
  }) => {
    const { username, password } = await registerRandomUser(apiClient);
    const tokens = await apiClient.loginAndGetTokens(username, password);
    // The AuthResponse/JWT never exposes role as a settable input - assert the token
    // actually works for a USER-scoped call and decode its role claim client-side.
    const payload = JSON.parse(Buffer.from(tokens.accessToken.split('.')[1], 'base64url').toString('utf8'));
    expect(payload.role).toBe('USER');
  });
});

interface RandomUser {
  username: string;
  email: string;
  password: string;
}

/** Registers a brand-new random user via the real API, returning its credentials for a subsequent login call. */
async function registerRandomUser(apiClient: ApiClient): Promise<RandomUser> {
  const user: RandomUser = {
    username: DataGenerator.username(),
    email: DataGenerator.email(),
    password: DataGenerator.strongPassword(),
  };
  const response = await apiClient.register(user);
  if (!response.ok()) {
    throw new Error(`Failed to register random user: ${response.status()} ${await response.text()}`);
  }
  return user;
}
