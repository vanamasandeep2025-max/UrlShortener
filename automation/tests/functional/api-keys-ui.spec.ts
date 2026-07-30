import { test, expect } from '../../fixtures/base';
import { ApiKeysPage } from '../../pages/ApiKeysPage';
import { DataGenerator } from '../../utils/dataGenerator';
import { Routes } from '../../constants/routes';

test.describe('API Keys management (UI)', () => {
  test('TC-APIKEY-UI-001: creating a key shows the plaintext secret exactly once @smoke @regression', async ({
    authenticatedPage,
  }) => {
    const apiKeysPage = new ApiKeysPage(authenticatedPage);
    const keyName = DataGenerator.apiKeyName();

    await apiKeysPage.open();
    await apiKeysPage.createKey(keyName);

    await expect(apiKeysPage.newKeyAlert).toBeVisible();
    const plaintext = await apiKeysPage.plaintextKeyFromAlert();
    expect(plaintext).toMatch(/^usk_/);

    await expect(apiKeysPage.rowFor(keyName)).toBeVisible();
    // The row shows only a prefix, never the full secret.
    await expect(apiKeysPage.rowFor(keyName)).not.toContainText(plaintext);
  });

  test('TC-APIKEY-UI-002: revoking a key removes it from the list after confirming the dialog @regression', async ({
    authenticatedPage,
  }) => {
    const apiKeysPage = new ApiKeysPage(authenticatedPage);
    const keyName = DataGenerator.apiKeyName();

    await apiKeysPage.open();
    await apiKeysPage.createKey(keyName);
    await expect(apiKeysPage.rowFor(keyName)).toBeVisible();

    await apiKeysPage.revoke(keyName);
    await apiKeysPage.expectToastVariant('success');
    await expect(apiKeysPage.rowFor(keyName)).toHaveCount(0);
  });

  test('TC-APIKEY-UI-003: a revoked key no longer authenticates API requests @security', async ({
    authenticatedPage,
    apiClient,
  }) => {
    const apiKeysPage = new ApiKeysPage(authenticatedPage);
    const keyName = DataGenerator.apiKeyName();
    await apiKeysPage.open();
    await apiKeysPage.createKey(keyName);
    const plaintext = await apiKeysPage.plaintextKeyFromAlert();

    // Sanity: the fresh key authenticates before revocation (X-API-Key header - see
    // ApiKeyAuthenticationFilter, an alternative to JWT for programmatic clients).
    const beforeRevoke = await apiClient.withApiKey(plaintext).listUrls();
    expect(beforeRevoke.status()).toBe(200);

    await apiKeysPage.revoke(keyName);

    const afterRevoke = await apiClient.withApiKey(plaintext).listUrls();
    expect(afterRevoke.status()).toBe(401);
  });

  test('TC-APIKEY-UI-004: back-to-links navigation returns to the dashboard @regression', async ({
    authenticatedPage,
  }) => {
    const apiKeysPage = new ApiKeysPage(authenticatedPage);
    await apiKeysPage.open();
    await authenticatedPage.getByRole('link', { name: /Back to links/ }).click();
    await expect(authenticatedPage).toHaveURL(new RegExp(Routes.dashboard));
  });
});
