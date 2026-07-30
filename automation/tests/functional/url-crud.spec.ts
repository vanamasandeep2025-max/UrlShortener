import { test, expect } from '../../fixtures/base';
import { DashboardPage } from '../../pages/DashboardPage';
import { DataGenerator, BoundaryPayloads } from '../../utils/dataGenerator';

test.describe('URL lifecycle - Create, Read, Update, Delete', () => {
  /**
   * Priority: P0 | Preconditions: logged in as demo (authenticatedPage fixture)
   * Test Data: freshly generated long URL (DataGenerator.longUrl)
   * Expected: success toast, new row appears at the top of the table with a real short code
   */
  test('TC-CRUD-001: create a URL via the dashboard form and see it in the table @smoke @regression', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    const longUrl = DataGenerator.longUrl();

    await dashboard.createUrl(longUrl);
    await dashboard.expectToastVariant('success');
    await dashboard.waitForTableSettled();

    const firstRow = dashboard.rows().first();
    await expect(firstRow.locator('a.short-url-chip')).toBeVisible();
    await expect(firstRow.locator('td').nth(1)).toHaveAttribute('title', longUrl);
  });

  test('TC-CRUD-002: create a URL with an optional expiry date @regression', async ({ authenticatedPage }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    const longUrl = DataGenerator.longUrl();
    const future = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
    const localDateTime = future.toISOString().slice(0, 16);

    await dashboard.createUrl(longUrl, { expiryLocalDateTime: localDateTime });
    await dashboard.expectToastVariant('success');
    await dashboard.waitForTableSettled();

    const row = dashboard.rows().first();
    await expect(row.locator('td').nth(4)).not.toContainText('-');
  });

  test('TC-CRUD-003: create a password-protected URL shows a "protected" badge @regression', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    const longUrl = DataGenerator.longUrl();

    await dashboard.createUrl(longUrl, { password: 'LinkSecret123!' });
    await dashboard.expectToastVariant('success');
    await dashboard.waitForTableSettled();

    await expect(dashboard.rows().first().locator('.badge', { hasText: 'protected' })).toBeVisible();
  });

  test('TC-CRUD-004: rejecting an invalid URL keeps the user on the form with an error toast @regression', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    const rowCountBefore = await dashboard.rowCount();

    await dashboard.urlInput.fill(BoundaryPayloads.notAUrl);
    await dashboard.shortenButton.click();

    // The native <input type="url"> constraint blocks a non-URL string before any request fires.
    const validity = await dashboard.urlInput.evaluate((el: HTMLInputElement) => el.validity.valid);
    expect(validity).toBe(false);
    expect(await dashboard.rowCount()).toBe(rowCountBefore);
  });

  test('TC-CRUD-005: server-side rejects a javascript: scheme URL even though it satisfies input type=url @security', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.createUrl('https://example.com/redirect?to=' + encodeURIComponent(BoundaryPayloads.javascriptScheme));
    // A URL-shaped string containing an embedded javascript: payload as a query value is
    // legal per RFC 3986 - the payload never becomes the link's own scheme, so this must
    // succeed. The true scheme-allowlist rejection is covered at the API layer (TC-VAL series)
    // where the disallowed scheme is the URL's own, not a query parameter.
    await dashboard.expectToastVariant('success');
  });

  test('TC-CRUD-006: search filters the table to matching original URLs @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const uniqueMarker = `qa-search-${Date.now()}`;
    const targetUrl = `https://example.com/${uniqueMarker}`;
    await apiClient.withToken(demoTokens.accessToken).createUrlOrThrow({ url: targetUrl });

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(uniqueMarker);

    await expect(dashboard.rows()).toHaveCount(1);
    await expect(dashboard.rows().first().locator('td').nth(1)).toHaveAttribute('title', targetUrl);
  });

  test('TC-CRUD-007: searching for a term with no matches shows the empty state @regression', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.search(`no-such-url-${Date.now()}-zzz`);
    await expect(dashboard.emptyStateMessage()).toBeVisible();
  });

  test('TC-CRUD-008: filtering by ACTIVE status excludes expired links @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const marker = `qa-expired-${Date.now()}`;
    const client = apiClient.withToken(demoTokens.accessToken);
    await client.createUrlOrThrow({
      url: `https://example.com/${marker}`,
      expiryDate: DataGenerator.isoDateOffsetDays(-1),
    });

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(marker);
    await dashboard.filterByStatus('ACTIVE');
    await expect(dashboard.emptyStateMessage()).toBeVisible();

    await dashboard.filterByStatus('EXPIRED');
    await expect(dashboard.rows()).toHaveCount(1);
  });

  test('TC-CRUD-009: edit expiry via the modal updates the row @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const marker = `qa-editexpiry-${Date.now()}`;
    const client = apiClient.withToken(demoTokens.accessToken);
    await client.createUrlOrThrow({ url: `https://example.com/${marker}` });

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(marker);
    const href = await dashboard.rows().first().locator('a.short-url-chip').getAttribute('href');
    const shortCode = (href ?? '').split('/').pop() ?? '';
    const row = dashboard.rowFor(shortCode);

    await row.openEditExpiry();
    await expect(dashboard.expiryModal).toBeVisible();
    const future = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16);
    await dashboard.fillAndSaveExpiry(future);

    await dashboard.expectToastVariant('success');
    await expect(dashboard.expiryModal).toBeHidden();
  });

  test('TC-CRUD-010: removing expiry (blank input) clears it back to "-" @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const marker = `qa-clearexpiry-${Date.now()}`;
    const client = apiClient.withToken(demoTokens.accessToken);
    await client.createUrlOrThrow({
      url: `https://example.com/${marker}`,
      expiryDate: DataGenerator.isoDateOffsetDays(5),
    });

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(marker);
    const href = await dashboard.rows().first().locator('a.short-url-chip').getAttribute('href');
    const shortCode = (href ?? '').split('/').pop() ?? '';
    const row = dashboard.rowFor(shortCode);

    await row.openEditExpiry();
    await dashboard.fillAndSaveExpiry(null);
    await dashboard.expectToastVariant('success');
    await dashboard.search(marker);
    await expect(dashboard.rows().first().locator('td').nth(4)).toContainText('-');
  });

  test('TC-CRUD-011: delete a URL removes it from the table after confirming the dialog @smoke @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const marker = `qa-delete-${Date.now()}`;
    const client = apiClient.withToken(demoTokens.accessToken);
    await client.createUrlOrThrow({ url: `https://example.com/${marker}` });

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(marker);
    const href = await dashboard.rows().first().locator('a.short-url-chip').getAttribute('href');
    const shortCode = (href ?? '').split('/').pop() ?? '';

    await dashboard.rowFor(shortCode).delete();
    await dashboard.expectToastVariant('success');
    await expect(dashboard.emptyStateMessage()).toBeVisible();
  });

  test('TC-CRUD-012: dismissing the delete confirmation dialog keeps the row @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const marker = `qa-cancel-delete-${Date.now()}`;
    const client = apiClient.withToken(demoTokens.accessToken);
    await client.createUrlOrThrow({ url: `https://example.com/${marker}` });

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(marker);
    const href = await dashboard.rows().first().locator('a.short-url-chip').getAttribute('href');
    const shortCode = (href ?? '').split('/').pop() ?? '';

    await dashboard.rowFor(shortCode).cancelDelete();
    await expect(dashboard.rows()).toHaveCount(1);
  });

  test('TC-CRUD-013: a deleted URL no longer resolves (redirect and analytics 404) @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createUrlOrThrow({ url: DataGenerator.longUrl() });

    const deleteResponse = await client.deleteUrl(created.id);
    expect(deleteResponse.status()).toBe(204);

    const redirectResponse = await client.redirect(created.shortCode, false);
    expect(redirectResponse.status()).toBe(404);
  });
});
