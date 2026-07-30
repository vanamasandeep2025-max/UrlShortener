import { test, expect } from '../../fixtures/base';
import { DataGenerator } from '../../utils/dataGenerator';

test.describe('API - API Keys contract', () => {
  test('TC-API-KEY-001: POST /api-keys returns 201 with a plaintext secret shown only on creation @smoke', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const response = await client.createApiKey({ name: DataGenerator.apiKeyName() });
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.plaintextKey).toMatch(/^usk_/);
    expect(body.keyPrefix).toBeTruthy();
    expect(body.plaintextKey).toContain(body.keyPrefix);
  });

  test('TC-API-KEY-002: GET /api-keys never returns the plaintext secret, only the prefix @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const name = DataGenerator.apiKeyName();
    await client.createApiKeyOrThrow({ name });

    const listResponse = await client.listApiKeys();
    expect(listResponse.status()).toBe(200);
    const keys = await listResponse.json();
    const found = keys.find((k: { name: string }) => k.name === name);
    expect(found).toBeTruthy();
    expect(found.plaintextKey).toBeUndefined();
  });

  test('TC-API-KEY-003: a freshly created key authenticates a real request via X-API-Key @smoke', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createApiKeyOrThrow({ name: DataGenerator.apiKeyName() });

    const response = await apiClient.withApiKey(created.plaintextKey!).listUrls();
    expect(response.status()).toBe(200);
  });

  test('TC-API-KEY-004: DELETE /api-keys/{id} revokes the key immediately @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createApiKeyOrThrow({ name: DataGenerator.apiKeyName() });

    const revokeResponse = await client.revokeApiKey(created.id);
    expect(revokeResponse.status()).toBe(204);

    const authResponse = await apiClient.withApiKey(created.plaintextKey!).listUrls();
    expect(authResponse.status()).toBe(401);
  });

  test('TC-API-KEY-005: an unrecognized API key value is rejected with 401 @security', async ({ apiClient }) => {
    const response = await apiClient.withApiKey('usk_totally_made_up_value_12345').listUrls();
    expect(response.status()).toBe(401);
  });

  test('TC-API-KEY-006: creating a key with an empty name is rejected with 400 @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const response = await client.createApiKey({ name: '' });
    expect(response.status()).toBe(400);
  });

  test('TC-API-KEY-007: one user cannot revoke another user\'s API key @security', async ({
    apiClient,
    demoTokens,
    adminTokens,
  }) => {
    const demoClient = apiClient.withToken(demoTokens.accessToken);
    const created = await demoClient.createApiKeyOrThrow({ name: DataGenerator.apiKeyName() });

    const adminAttempt = await apiClient.withToken(adminTokens.accessToken).revokeApiKey(created.id);
    expect([403, 404]).toContain(adminAttempt.status());

    // The key must still work - the cross-user revoke attempt must not have succeeded silently.
    const stillWorks = await apiClient.withApiKey(created.plaintextKey!).listUrls();
    expect(stillWorks.status()).toBe(200);
  });
});
