import { test, expect } from '../../fixtures/base';
import { DataGenerator } from '../../utils/dataGenerator';
import { env } from '../../config/environment';

/**
 * Pure REST contract tests for /api/v1/auth/* - status codes, headers, response schema,
 * and validation-error shape. Token *validity* (tampering, RBAC, expiry) is covered
 * separately in tests/auth/rbac.spec.ts to keep each file's responsibility singular.
 */
test.describe('API - Auth contract', () => {
  test('TC-API-AUTH-001: POST /auth/register returns 201 with a full AuthResponse @smoke', async ({ apiClient }) => {
    const response = await apiClient.register({
      username: DataGenerator.username(),
      email: DataGenerator.email(),
      password: DataGenerator.strongPassword(),
    });

    expect(response.status()).toBe(201);
    expect(response.headers()['content-type']).toContain('application/json');
    const body = await response.json();
    expect(body).toMatchObject({
      tokenType: 'Bearer',
    });
    expect(typeof body.accessToken).toBe('string');
    expect(typeof body.refreshToken).toBe('string');
    expect(body.accessToken.split('.')).toHaveLength(3); // structurally a JWT
    expect(body.expiresInMs).toBeGreaterThan(0);
  });

  test('TC-API-AUTH-002: POST /auth/register with a missing field returns 400 with a validationErrors map @regression', async ({
    apiClient,
  }) => {
    const response = await apiClient.register({
      username: '',
      email: DataGenerator.email(),
      password: DataGenerator.strongPassword(),
    });
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.error).toBe('Bad Request');
    expect(body.validationErrors).toBeTruthy();
    expect(Object.keys(body.validationErrors)).toContain('username');
  });

  test('TC-API-AUTH-003: POST /auth/register with a duplicate username returns 409 @regression', async ({
    apiClient,
  }) => {
    const response = await apiClient.register({
      username: env.demoUsername,
      email: DataGenerator.email(),
      password: DataGenerator.strongPassword(),
    });
    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body.message).toContain('already taken');
  });

  test('TC-API-AUTH-004: POST /auth/login returns 200 with a valid token pair for correct credentials @smoke', async ({
    apiClient,
  }) => {
    const response = await apiClient.login({ username: env.demoUsername, password: env.demoPassword });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.tokenType).toBe('Bearer');
  });

  test('TC-API-AUTH-005: POST /auth/login returns 401 with the generic message for wrong credentials @smoke @security', async ({
    apiClient,
  }) => {
    const response = await apiClient.login({ username: env.demoUsername, password: 'WrongPassword!' });
    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(body.message).toBe('Invalid username or password');
    expect(body.correlationId).toBeTruthy();
    expect(body.timestamp).toBeTruthy();
  });

  test('TC-API-AUTH-006: POST /auth/login with a malformed JSON body returns 400, not a 500 @security', async ({
    apiRequestContext,
  }) => {
    const response = await apiRequestContext.post('auth/login', {
      headers: { 'Content-Type': 'application/json' },
      data: '{ "username": "demo", "password": ', // deliberately truncated/invalid JSON
    });
    expect(response.status()).toBe(400);
  });

  test('TC-API-AUTH-007: every error response includes a correlationId that can be cross-referenced in logs @regression', async ({
    apiClient,
  }) => {
    const response = await apiClient.login({ username: 'nobody-such-user', password: 'irrelevant' });
    const body = await response.json();
    expect(body.correlationId).toMatch(/^[0-9a-f-]{36}$/i);
  });

  test('TC-API-AUTH-008: POST /auth/refresh with a garbage token returns 401, not a stack trace leak @security', async ({
    apiClient,
  }) => {
    const response = await apiClient.refresh({ refreshToken: 'not-a-real-token' });
    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(body.message).not.toContain('Exception');
    expect(body.message).not.toContain('.java:');
  });
});
