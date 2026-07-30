import { test, expect } from '../../fixtures/base';
import { DataGenerator } from '../../utils/dataGenerator';

test.describe('API - URLs contract', () => {
  test('TC-API-URL-001: POST /urls returns 201 with a full UrlResponse schema @smoke', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const originalUrl = DataGenerator.longUrl();
    const response = await client.createUrl({ url: originalUrl });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body).toMatchObject({
      originalUrl,
      clickCount: 0,
      passwordProtected: false,
    });
    expect(body.shortCode).toMatch(/^[A-Za-z0-9]{5,10}$/);
    expect(body.shortUrl).toContain(body.shortCode);
    expect(body.id).toMatch(/^[0-9a-f-]{36}$/i);
  });

  test('TC-API-URL-002: GET /urls returns a paginated envelope with correct metadata @smoke', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const response = await client.listUrls({ page: 0, size: 5 });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');
    expect(body.page).toBe(0);
    expect(body.size).toBe(5);
    expect(Array.isArray(body.content)).toBe(true);
    expect(body.content.length).toBeLessThanOrEqual(5);
  });

  test('TC-API-URL-003: search query param filters results by original URL substring @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const marker = `qa-api-search-${Date.now()}`;
    await client.createUrlOrThrow({ url: `https://example.com/${marker}` });

    const response = await client.listUrls({ search: marker });
    const body = await response.json();
    expect(body.totalElements).toBeGreaterThanOrEqual(1);
    for (const item of body.content) {
      expect(item.originalUrl).toContain(marker);
    }
  });

  test('TC-API-URL-004: PATCH /urls/{id} updates expiry and DELETE removes it @smoke', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createUrlOrThrow({ url: DataGenerator.longUrl() });

    const newExpiry = DataGenerator.isoDateOffsetDays(10);
    const patchResponse = await client.updateExpiry(created.id, { expiresAt: newExpiry });
    expect(patchResponse.status()).toBe(200);
    const patched = await patchResponse.json();
    expect(new Date(patched.expiresAt).toISOString()).toBe(new Date(newExpiry).toISOString());

    const deleteResponse = await client.deleteUrl(created.id);
    expect(deleteResponse.status()).toBe(204);

    const listAfter = await client.listUrls({ search: created.shortCode });
    const listBody = await listAfter.json();
    expect(listBody.content.find((u: { id: string }) => u.id === created.id)).toBeUndefined();
  });

  test('TC-API-URL-005: PATCH /urls/{id} on a non-existent id returns 404 @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const response = await client.updateExpiry(DataGenerator.uuidLike(), {
      expiresAt: DataGenerator.isoDateOffsetDays(1),
    });
    expect(response.status()).toBe(404);
  });

  test('TC-API-URL-006: DELETE /urls/{id} is idempotent-safe - deleting twice returns 404 the second time @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createUrlOrThrow({ url: DataGenerator.longUrl() });

    const first = await client.deleteUrl(created.id);
    expect(first.status()).toBe(204);
    const second = await client.deleteUrl(created.id);
    expect(second.status()).toBe(404);
  });

  test('TC-API-URL-007: GET /urls/{shortCode}/analytics for a link with zero clicks returns real zeros, not a 404 @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createUrlOrThrow({ url: DataGenerator.longUrl() });
    const response = await client.getAnalytics(created.shortCode);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.totalClicks).toBe(0);
    expect(body.uniqueVisitors).toBe(0);
    expect(body.dailyClicks).toEqual([]);
  });

  test('TC-API-URL-008: GET /urls/{shortCode}/analytics for an unknown short code returns 404 @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const response = await client.getAnalytics(DataGenerator.randomShortCode());
    expect(response.status()).toBe(404);
  });

  test('TC-API-URL-009: pagination page/size boundaries are respected at the edges @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const beyondLastPage = await client.listUrls({ page: 9999, size: 10 });
    expect(beyondLastPage.status()).toBe(200);
    const body = await beyondLastPage.json();
    expect(body.content).toEqual([]);
    expect(body.last).toBe(true);
  });

  test('TC-API-URL-010: status=EXPIRED filters to only links whose expiry is in the past @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const marker = `qa-api-expired-${Date.now()}`;
    await client.createUrlOrThrow({ url: `https://example.com/${marker}`, expiryDate: DataGenerator.isoDateOffsetDays(-2) });

    const response = await client.listUrls({ search: marker, status: 'EXPIRED' });
    const body = await response.json();
    expect(body.totalElements).toBeGreaterThanOrEqual(1);
    for (const item of body.content) {
      expect(new Date(item.expiresAt).getTime()).toBeLessThan(Date.now());
    }
  });

  test('TC-API-URL-011: duplicate identical URLs from the same user each get distinct short codes @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const originalUrl = DataGenerator.longUrl();
    const first = await client.createUrlOrThrow({ url: originalUrl });
    const second = await client.createUrlOrThrow({ url: originalUrl });
    expect(first.shortCode).not.toBe(second.shortCode);
  });

  test('TC-API-URL-012: sort parameter is honoured - createdAt,desc returns newest first @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const marker = `qa-api-sort-${Date.now()}`;
    const first = await client.createUrlOrThrow({ url: `https://example.com/${marker}-a` });
    const second = await client.createUrlOrThrow({ url: `https://example.com/${marker}-b` });

    const response = await client.listUrls({ search: marker, sort: 'createdAt,desc' });
    const body = await response.json();
    const ids = body.content.map((u: { id: string }) => u.id);
    expect(ids.indexOf(second.id)).toBeLessThan(ids.indexOf(first.id));
  });
});
