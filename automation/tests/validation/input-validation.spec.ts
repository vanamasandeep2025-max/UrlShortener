import { test, expect } from '../../fixtures/base';
import { DataGenerator } from '../../utils/dataGenerator';

/**
 * Every assertion here is pinned to the real validator logic, not a guess:
 * - backend/.../validation/ValidHttpUrlValidator.java: scheme must be http/https,
 *   8192 char max, must resolve a non-blank host.
 * - backend/.../validation/NoScriptTagValidator.java: rejects (case-insensitively)
 *   <script, javascript:, data:text/html, on<word>=, <iframe - anywhere in the string.
 * - CreateUrlRequest.password: @Size(min=4, max=72).
 */
test.describe('Input validation - POST /urls', () => {
  test.describe.configure({ mode: 'parallel' });

  const rejectedUrls: Array<{ label: string; url: string }> = [
    { label: 'empty string', url: '' },
    { label: 'whitespace only', url: '   ' },
    { label: 'not a URL at all', url: 'this is not a url at all' },
    { label: 'javascript: scheme', url: 'javascript:alert(document.cookie)' },
    { label: 'data:text/html scheme', url: 'data:text/html,<script>alert(1)</script>' },
    { label: 'ftp scheme (not in allowlist)', url: 'ftp://example.com/file.txt' },
    { label: 'file scheme (not in allowlist)', url: 'file:///etc/passwd' },
    { label: '<script> tag embedded in an otherwise-valid URL path', url: 'https://example.com/<script>alert(1)</script>' },
    { label: 'onerror= handler embedded in path', url: 'https://example.com/<img src=x onerror=alert(1)>' },
    { label: '<iframe> embedded in path', url: 'https://example.com/<iframe src=evil.com>' },
    { label: 'over the 8192 character length limit', url: `https://example.com/${'a'.repeat(8200)}` },
    { label: 'missing host (scheme with no authority)', url: 'https:///no-host' },
  ];

  for (const { label, url } of rejectedUrls) {
    test(`TC-VAL-001: rejects - ${label} @security @regression`, async ({ apiClient, demoTokens }) => {
      const client = apiClient.withToken(demoTokens.accessToken);
      const response = await client.createUrl({ url });
      expect(response.status(), `expected 400 for payload: ${url.slice(0, 80)}`).toBe(400);
      const body = await response.json();
      expect(body.status).toBe(400);
    });
  }

  const acceptedUrls: Array<{ label: string; url: string }> = [
    { label: 'ordinary https URL', url: DataGenerator.longUrl() },
    { label: 'plain http URL', url: 'http://example.com/plain' },
    { label: 'unicode characters in the path', url: 'https://example.com/café-über-naïve' },
    { label: 'emoji in the path', url: 'https://example.com/🚀🔥💯' },
    { label: 'exactly at the 8192 char boundary', url: `https://example.com/${'a'.repeat(8192 - 'https://example.com/'.length)}` },
    { label: 'query string and fragment', url: 'https://example.com/search?q=test&page=2#results' },
    { label: 'duplicate slashes in the path', url: 'https://example.com//double//slash' },
  ];

  for (const { label, url } of acceptedUrls) {
    test(`TC-VAL-002: accepts - ${label} @regression`, async ({ apiClient, demoTokens }) => {
      const client = apiClient.withToken(demoTokens.accessToken);
      const response = await client.createUrl({ url });
      expect(response.status(), `expected 201 for payload: ${url.slice(0, 80)}`).toBe(201);
    });
  }

  test('TC-VAL-003: a SQL injection payload is stored as inert text, not executed (parameterized JPA) @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const maliciousUrl = "https://example.com/'; DROP TABLE urls; --";
    const created = await client.createUrl({ url: maliciousUrl });
    expect(created.status()).toBe(201);
    const body = await created.json();
    expect(body.originalUrl).toBe(maliciousUrl);

    // Proof the table (and the rest of the dataset) survived: a completely unrelated
    // request against the same table must still succeed immediately afterward.
    const stillWorks = await client.listUrls({ page: 0, size: 1 });
    expect(stillWorks.status()).toBe(200);
  });

  test('TC-VAL-004: unknown/extra JSON fields (incl. a literal "__proto__" key) are silently ignored, not applied @security', async ({
    apiRequestContext,
    demoTokens,
  }) => {
    // Built as a literal JSON string, NOT via an object-literal + JSON.stringify: a
    // "__proto__" key in an object literal sets the object's prototype rather than
    // becoming an own enumerable property, so JSON.stringify would silently drop it before
    // it ever reached the wire. Writing the JSON text directly guarantees the server
    // actually receives a field literally named "__proto__".
    const url = DataGenerator.longUrl();
    const rawBody = `{"url": ${JSON.stringify(url)}, "__proto__": {"polluted": true}, "admin": true, "role": "ADMIN"}`;
    const response = await apiRequestContext.post('urls', {
      headers: { Authorization: `Bearer ${demoTokens.accessToken}`, 'Content-Type': 'application/json' },
      data: rawBody,
    });
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.role).toBeUndefined();
    expect(body.admin).toBeUndefined();
  });

  const passwordBoundaries: Array<{ label: string; password: string; expectAccepted: boolean }> = [
    { label: 'below minimum (3 chars)', password: 'abc', expectAccepted: false },
    { label: 'at minimum (4 chars)', password: 'abcd', expectAccepted: true },
    { label: 'at maximum (72 chars)', password: 'a'.repeat(72), expectAccepted: true },
    { label: 'above maximum (73 chars)', password: 'a'.repeat(73), expectAccepted: false },
  ];

  for (const { label, password, expectAccepted } of passwordBoundaries) {
    test(`TC-VAL-005: link password boundary - ${label} @regression`, async ({ apiClient, demoTokens }) => {
      const client = apiClient.withToken(demoTokens.accessToken);
      const response = await client.createUrl({ url: DataGenerator.longUrl(), password });
      expect(response.status()).toBe(expectAccepted ? 201 : 400);
    });
  }

  test('TC-VAL-006: leading/trailing whitespace around an otherwise-valid URL is handled without corrupting the destination @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const clean = DataGenerator.longUrl();
    const response = await client.createUrl({ url: `  ${clean}  ` });
    // Either trimmed-and-accepted or rejected outright is acceptable backend behaviour;
    // the one unacceptable outcome is silently corrupting the stored destination.
    if (response.status() === 201) {
      const body = await response.json();
      expect(body.originalUrl.trim()).toBe(clean);
    } else {
      expect(response.status()).toBe(400);
    }
  });

  test('TC-VAL-007: an expiryDate that is not valid ISO-8601 is rejected with 400, not a 500 @regression', async ({
    apiRequestContext,
    demoTokens,
  }) => {
    const response = await apiRequestContext.post('urls', {
      headers: { Authorization: `Bearer ${demoTokens.accessToken}`, 'Content-Type': 'application/json' },
      data: { url: DataGenerator.longUrl(), expiryDate: 'not-a-date' },
    });
    expect(response.status()).toBe(400);
  });
});
