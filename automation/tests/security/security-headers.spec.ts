import { test, expect } from '../../fixtures/base';

/**
 * Asserts the exact header set Spring Security's defaults actually produce on this
 * backend today (confirmed live via `curl -D-` against a running instance before writing
 * these assertions - not assumed from documentation). No CSP and no HSTS header exist
 * yet since the app serves plain HTTP with no explicit `.headers(...)` customization in
 * SecurityConfig - see docs/ARCHITECTURE.md "Security Architecture" for the honest gap
 * list (TLS/HSTS/CSP are PROPOSED, not implemented).
 */
test.describe('Security - Response headers', () => {
  test('TC-SEC-HDR-001: every response carries clickjacking and MIME-sniffing protections @security', async ({
    apiRequestContext,
    apiClient,
  }) => {
    // Full absolute URL, deliberately outside the /api/v1-scoped apiRequestContext base,
    // to check a plain actuator endpoint alongside a real API endpoint below.
    const health = await apiRequestContext.fetch('http://localhost/actuator/health');
    expect(health.headers()['x-frame-options']).toBe('DENY');
    expect(health.headers()['x-content-type-options']).toBe('nosniff');

    const apiResponse = await apiClient.login({ username: 'nobody', password: 'nobody' });
    expect(apiResponse.headers()['x-frame-options']).toBe('DENY');
    expect(apiResponse.headers()['x-content-type-options']).toBe('nosniff');
  });

  test('TC-SEC-HDR-002: a correlation id (X-Request-Id) is present on every response for traceability @regression', async ({
    apiClient,
  }) => {
    const response = await apiClient.login({ username: 'nobody', password: 'nobody' });
    expect(response.headers()['x-request-id']).toMatch(/^[0-9a-f-]{36}$/i);
  });

  test('TC-SEC-HDR-003: error responses never leak a stack trace or internal class name @security', async ({
    apiClient,
  }) => {
    const response = await apiClient.login({ username: 'nobody', password: 'nobody' });
    const text = await response.text();
    expect(text).not.toMatch(/\.java:\d+/);
    expect(text).not.toContain('Exception');
    expect(text).not.toContain('com.urlshortener.');
  });

  test('TC-SEC-HDR-004: sensitive fields (password, password hash) never appear in any API response body @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const response = await client.listUrls({ page: 0, size: 1 });
    const text = await response.text();
    expect(text.toLowerCase()).not.toContain('passwordhash');
    expect(text.toLowerCase()).not.toContain('"password"');
  });
});
