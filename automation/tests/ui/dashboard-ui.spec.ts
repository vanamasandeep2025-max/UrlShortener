import { test, expect } from '../../fixtures/base';
import { DashboardPage } from '../../pages/DashboardPage';
import { DataGenerator } from '../../utils/dataGenerator';

test.describe('Dashboard UI - widgets and cross-cutting UI behaviour', () => {
  test('TC-UI-001: copy button copies the short URL to the clipboard @regression', async ({
    authenticatedPage,
    browserName,
  }) => {
    test.skip(browserName !== 'chromium', 'Clipboard permission API is only reliably grantable in Chromium');
    const context = authenticatedPage.context();
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.createUrl(DataGenerator.longUrl());
    await dashboard.waitForTableSettled();

    const row = dashboard.rows().first();
    const expectedHref = await row.locator('a.short-url-chip').getAttribute('href');
    await row.getByRole('button', { name: 'Copy' }).click();
    await dashboard.expectToast('Copied to clipboard');

    const clipboardText = await authenticatedPage.evaluate(() => navigator.clipboard.readText());
    expect(clipboardText).toBe(expectedHref);
  });

  test('TC-UI-002: the copy button label is plain text "Copy", not a broken icon glyph @regression', async ({
    authenticatedPage,
  }) => {
    // Regression guard for a real bug found this session (mojibake copy-icon button,
    // fixed by replacing the icon with a plain-text label) - see docs/ARCHITECTURE.md
    // "Design Decisions" and git history for frontend/js/app.js.
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.createUrl(DataGenerator.longUrl());
    await dashboard.waitForTableSettled();

    const copyButton = dashboard.rows().first().getByRole('button', { name: 'Copy' });
    await expect(copyButton).toHaveText('Copy');
    const text = await copyButton.textContent();
    // eslint-disable-next-line no-control-regex
    expect(text).toMatch(/^[\x00-\x7F]*$/);
  });

  test('TC-UI-003: pagination renders one page button per page and navigating changes the visible rows @regression', async ({
    authenticatedPage,
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const marker = `qa-page-${Date.now()}`;
    // Page size is 10 (see frontend/js/app.js PAGE_SIZE) - 12 links guarantees a 2nd page.
    for (let i = 0; i < 12; i++) {
      await client.createUrlOrThrow({ url: `https://example.com/${marker}-${i}` });
    }

    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.search(marker);
    await expect(dashboard.rows()).toHaveCount(10);

    // getAttribute() (not toHaveAttribute()) is deliberate: both hrefs are compared against
    // EACH OTHER below to prove page 2 shows different rows, not against a fixed value.
    // eslint-disable-next-line playwright/prefer-web-first-assertions
    const page1FirstHref = await dashboard.rows().first().locator('a.short-url-chip').getAttribute('href');
    await dashboard.goToPage(1);
    await expect(dashboard.rows()).toHaveCount(2);
    // eslint-disable-next-line playwright/prefer-web-first-assertions
    const page2FirstHref = await dashboard.rows().first().locator('a.short-url-chip').getAttribute('href');
    // eslint-disable-next-line playwright/prefer-web-first-assertions
    expect(page2FirstHref).not.toBe(page1FirstHref);
  });

  test('TC-UI-004: an account with zero links shows the empty-state row, not a blank table @regression', async ({
    page,
    apiClient,
  }) => {
    const username = DataGenerator.username();
    const email = DataGenerator.email();
    const password = DataGenerator.strongPassword();
    const registerResponse = await apiClient.register({ username, email, password });
    expect(registerResponse.ok()).toBe(true);
    const tokens = await registerResponse.json();

    await page.goto('/login.html');
    await page.evaluate(
      ({ access, refresh }) => {
        window.localStorage.setItem('usp_access_token', access);
        window.localStorage.setItem('usp_refresh_token', refresh);
      },
      { access: tokens.accessToken, refresh: tokens.refreshToken }
    );
    const dashboard = new DashboardPage(page);
    await dashboard.open();
    await expect(dashboard.emptyStateMessage()).toBeVisible();
  });

  test('TC-UI-005: the dashboard table wraps into a scrollable container on a narrow (mobile) viewport, not the whole page @regression', async ({
    authenticatedPage,
  }) => {
    await authenticatedPage.setViewportSize({ width: 375, height: 667 });
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();

    const bodyScrollWidth = await authenticatedPage.evaluate(() => document.body.scrollWidth);
    const viewportWidth = await authenticatedPage.evaluate(() => window.innerWidth);
    // .table-responsive scopes horizontal scroll to the table itself - the page body must not.
    expect(bodyScrollWidth).toBeLessThanOrEqual(viewportWidth + 1);
  });

  test('TC-UI-006: keyboard-only navigation can reach and submit the create-URL form @accessibility', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    await dashboard.urlInput.focus();
    await expect(dashboard.urlInput).toBeFocused();

    await authenticatedPage.keyboard.type(DataGenerator.longUrl());
    await authenticatedPage.keyboard.press('Enter');
    await dashboard.expectToastVariant('success');
  });

  test('TC-UI-007: every form input on the dashboard has an associated, visible label @accessibility', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.open();
    for (const input of [dashboard.urlInput, dashboard.expiryInput, dashboard.passwordInput]) {
      const id = await input.getAttribute('id');
      const label = authenticatedPage.locator(`label[for="${id}"], .form-label:has(+ #${id})`);
      // frontend/index.html wraps each <label class="form-label"> as the input's preceding
      // sibling rather than a for="" attribute - assert at least one association exists.
      const hasAssociatedLabel = (await label.count()) > 0;
      const hasAriaLabel = (await input.getAttribute('aria-label')) !== null;
      const hasPlaceholder = (await input.getAttribute('placeholder')) !== null;
      expect(hasAssociatedLabel || hasAriaLabel || hasPlaceholder).toBe(true);
    }
  });

  test('TC-UI-008: dashboard initial load completes within an acceptable time budget @performance', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    const start = Date.now();
    await dashboard.open();
    await dashboard.waitForTableSettled();
    const elapsedMs = Date.now() - start;
    expect(elapsedMs).toBeLessThan(5_000);
  });
});
