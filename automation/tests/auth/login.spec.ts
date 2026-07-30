import { test, expect } from '../../fixtures/base';
import { LoginPage } from '../../pages/LoginPage';
import { DashboardPage } from '../../pages/DashboardPage';
import { Routes } from '../../constants/routes';
import { env } from '../../config/environment';
import { DataGenerator } from '../../utils/dataGenerator';

test.describe('Authentication - Login', () => {
  /**
   * Priority: P0 | Preconditions: demo/Demo@12345 seeded via app.seed-demo-data (application.yml)
   * Test Data: env.demoUsername / env.demoPassword
   * Expected: redirected to index.html, tokens present in localStorage, greeting shows username
   */
  test('TC-AUTH-001: successful login with valid demo credentials @smoke @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const dashboard = new DashboardPage(page);

    await loginPage.open();
    await loginPage.login(env.demoUsername, env.demoPassword);

    await expect(page).toHaveURL(new RegExp(Routes.dashboard));
    await expect(dashboard.currentUserLabel).toContainText(env.demoUsername);

    const accessToken = await page.evaluate(() => window.localStorage.getItem('usp_access_token'));
    expect(accessToken).toBeTruthy();
  });

  test('TC-AUTH-002: successful login with valid admin credentials @smoke', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(env.adminUsername, env.adminPassword);
    await expect(page).toHaveURL(new RegExp(Routes.dashboard));
  });

  /**
   * Priority: P0 | Test Data: real username + wrong password
   * Expected: stays on login.html, danger toast shown, no tokens written
   */
  test('TC-AUTH-003: login rejected with wrong password @smoke @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(env.demoUsername, 'WrongPassword!123');

    await expect(page).toHaveURL(new RegExp(Routes.login));
    await loginPage.expectToastVariant('danger');
    const accessToken = await page.evaluate(() => window.localStorage.getItem('usp_access_token'));
    expect(accessToken).toBeNull();
  });

  test('TC-AUTH-004: login rejected for a username that does not exist @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(DataGenerator.username(), 'AnyPassword123!');
    await expect(page).toHaveURL(new RegExp(Routes.login));
    await loginPage.expectToastVariant('danger');
  });

  /**
   * The backend intentionally returns the SAME generic message for both "unknown username"
   * and "correct username, wrong password" (see AuthServiceImpl#login) - this is a
   * deliberate anti-enumeration control, not a bug. Assert both paths look identical.
   */
  test('TC-AUTH-005: unknown username and wrong password produce identical error messaging @security', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page);

    await loginPage.open();
    await loginPage.login(env.demoUsername, 'DefinitelyWrong123!');
    await expect(loginPage.toast()).toBeVisible();
    // textContent() (not toHaveText()) is deliberate: both captured strings are compared
    // against EACH OTHER below, not against a fixed expected value, so there's nothing for
    // a web-first toHaveText() assertion to assert against at this point.
    // eslint-disable-next-line playwright/prefer-web-first-assertions
    const wrongPasswordToast = await loginPage.toast().textContent();

    await page.reload();
    await loginPage.login(DataGenerator.username(), 'DefinitelyWrong123!');
    await expect(loginPage.toast()).toBeVisible();
    // eslint-disable-next-line playwright/prefer-web-first-assertions
    const unknownUserToast = await loginPage.toast().textContent();

    // Comparing two already-captured plain strings, not asserting against a locator.
    // eslint-disable-next-line playwright/prefer-web-first-assertions
    expect(wrongPasswordToast).toBe(unknownUserToast);
  });

  test('TC-AUTH-006: empty username and password are blocked by required-field HTML validation @regression', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.signInButton.click();
    // Native "required" constraint prevents submission - still on login.html, no request fired.
    await expect(page).toHaveURL(new RegExp(Routes.login));
    const validity = await loginPage.usernameInput.evaluate((el: HTMLInputElement) => el.validity.valid);
    expect(validity).toBe(false);
  });

  test('TC-AUTH-007: whitespace-only username is rejected @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login('   ', env.demoPassword);
    // api.js trims the username on submit; a whitespace-only value collapses to "", so the
    // native required-field constraint blocks submission just like the empty case above.
    await expect(page).toHaveURL(new RegExp(Routes.login));
  });

  test('TC-AUTH-008: SQL injection payload in username field is safely rejected, not authenticated @security', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login("admin' OR '1'='1", "' OR '1'='1");
    await expect(page).toHaveURL(new RegExp(Routes.login));
    await loginPage.expectToastVariant('danger');
  });

  test('TC-AUTH-009: XSS payload in username field is not executed and is safely rejected @security', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page);
    let dialogFired = false;
    page.on('dialog', () => {
      dialogFired = true;
    });

    await loginPage.open();
    await loginPage.login('<script>alert(1)</script>', 'irrelevant');
    await expect(page).toHaveURL(new RegExp(Routes.login));
    expect(dialogFired).toBe(false);
  });

  test('TC-AUTH-010: register tab switches form visibility without a page reload @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await expect(loginPage.usernameInput).toBeVisible();
    await loginPage.switchToRegister();
    await expect(loginPage.registerUsernameInput).toBeVisible();
    await expect(loginPage.usernameInput).toBeHidden();
  });

  test('TC-AUTH-011: already-authenticated user visiting login.html is redirected to the dashboard @regression', async ({
    authenticatedPage,
  }) => {
    // authenticatedPage fixture already seeded tokens + landed on index.html; navigating
    // back to login.html should bounce straight back per frontend/js/auth.js's isLoggedIn() guard.
    await authenticatedPage.goto(Routes.login);
    await expect(authenticatedPage).toHaveURL(new RegExp(Routes.dashboard));
  });
});
