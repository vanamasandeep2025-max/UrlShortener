import { test, expect } from '../../fixtures/base';
import { LoginPage } from '../../pages/LoginPage';
import { DashboardPage } from '../../pages/DashboardPage';
import { Routes } from '../../constants/routes';

test.describe('Authentication - Session handling', () => {
  test('TC-SESSION-001: logout clears tokens and redirects to login @smoke @regression', async ({
    authenticatedPage,
  }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    await dashboard.logout();

    await expect(authenticatedPage).toHaveURL(new RegExp(Routes.login));
    const accessToken = await authenticatedPage.evaluate(() => window.localStorage.getItem('usp_access_token'));
    expect(accessToken).toBeNull();
  });

  test('TC-SESSION-002: visiting the dashboard while unauthenticated redirects to login @smoke @security', async ({
    page,
  }) => {
    await page.goto(Routes.dashboard);
    await expect(page).toHaveURL(new RegExp(Routes.login));
  });

  test('TC-SESSION-003: visiting api-keys.html while unauthenticated redirects to login @security', async ({
    page,
  }) => {
    await page.goto(Routes.apiKeys);
    await expect(page).toHaveURL(new RegExp(Routes.login));
  });

  test('TC-SESSION-004: session survives a full page refresh @regression', async ({ authenticatedPage }) => {
    const dashboard = new DashboardPage(authenticatedPage);
    await authenticatedPage.reload();
    await expect(authenticatedPage).toHaveURL(new RegExp(Routes.dashboard));
    await expect(dashboard.currentUserLabel).not.toBeEmpty();
  });

  test('TC-SESSION-005: clearing localStorage mid-session forces a redirect to login on next navigation @security', async ({
    authenticatedPage,
  }) => {
    await authenticatedPage.evaluate(() => window.localStorage.clear());
    await authenticatedPage.goto(Routes.apiKeys);
    await expect(authenticatedPage).toHaveURL(new RegExp(Routes.login));
  });

  test('TC-SESSION-006: a tampered/malformed access token is treated as unauthenticated, not a crash @security', async ({
    page,
  }) => {
    await page.goto(Routes.login);
    await page.evaluate(() => {
      window.localStorage.setItem('usp_access_token', 'not.a.valid.jwt');
      window.localStorage.setItem('usp_refresh_token', 'also-not-valid');
    });
    await page.goto(Routes.dashboard);
    // No refresh token is valid either, so apiFetch's 401 -> refresh -> still-401 path
    // clears tokens and bounces to login rather than leaving a broken dashboard rendered.
    await expect(page).toHaveURL(new RegExp(Routes.login));
  });

  test('TC-SESSION-007: two independent logins in two browser contexts do not interfere with each other @regression', async ({
    browser,
  }) => {
    const demoContext = await browser.newContext();
    const adminContext = await browser.newContext();
    try {
      const demoPage = await demoContext.newPage();
      const adminPage = await adminContext.newPage();
      const demoLogin = new LoginPage(demoPage);
      const adminLogin = new LoginPage(adminPage);

      await demoLogin.open();
      await demoLogin.login('demo', 'Demo@12345');
      await adminLogin.open();
      await adminLogin.login('admin', 'Admin@12345');

      await expect(demoPage).toHaveURL(new RegExp(Routes.dashboard));
      await expect(adminPage).toHaveURL(new RegExp(Routes.dashboard));

      // toHaveURL only waits for the redirect itself - app.js populates #currentUserLabel
      // asynchronously afterward (decodeJwtSubject on DOMContentLoaded), so reading
      // textContent() immediately races that population under any real load.
      const demoLabel = demoPage.locator('#currentUserLabel');
      const adminLabel = adminPage.locator('#currentUserLabel');
      await expect(demoLabel).not.toBeEmpty();
      await expect(adminLabel).not.toBeEmpty();

      expect(await demoLabel.textContent()).toContain('demo');
      expect(await adminLabel.textContent()).toContain('admin');
    } finally {
      await demoContext.close();
      await adminContext.close();
    }
  });
});
