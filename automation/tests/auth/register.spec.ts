import { test, expect } from '../../fixtures/base';
import { LoginPage } from '../../pages/LoginPage';
import { Routes } from '../../constants/routes';
import { DataGenerator } from '../../utils/dataGenerator';
import { env } from '../../config/environment';

test.describe('Authentication - Registration', () => {
  /**
   * Priority: P0 | Test Data: freshly generated username/email/password (DataGenerator)
   * Expected: 201-equivalent UI outcome - redirected to dashboard, logged in immediately
   */
  test('TC-REG-001: new user can register and is logged in immediately @smoke @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const username = DataGenerator.username();
    const email = DataGenerator.email();

    await loginPage.open();
    await loginPage.register(username, email, DataGenerator.strongPassword());

    await expect(page).toHaveURL(new RegExp(Routes.dashboard));
    const accessToken = await page.evaluate(() => window.localStorage.getItem('usp_access_token'));
    expect(accessToken).toBeTruthy();
  });

  test('TC-REG-002: registering with an already-taken username is rejected @regression', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.register(env.demoUsername, DataGenerator.email(), DataGenerator.strongPassword());
    await expect(page).toHaveURL(new RegExp(Routes.login));
    await loginPage.expectToast('already taken');
  });

  test('TC-REG-003: registering with an already-registered email is rejected @regression', async ({ page, apiClient }) => {
    // Seed a real user via API first so we know its email deterministically, independent of demo-data seeding.
    const username = DataGenerator.username();
    const email = DataGenerator.email();
    await apiClient.register({ username, email, password: DataGenerator.strongPassword() });

    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.register(DataGenerator.username(), email, DataGenerator.strongPassword());
    await expect(page).toHaveURL(new RegExp(Routes.login));
    await loginPage.expectToast('already registered');
  });

  /**
   * Backend policy (RegisterRequest): 8+ chars, upper, lower, digit. Table-driven so each
   * boundary is its own assertion without duplicating the open/switch/submit boilerplate.
   */
  const weakPasswords: Array<{ label: string; password: string }> = [
    { label: 'too short (7 chars)', password: 'Ab1defg'.slice(0, 7) },
    { label: 'no digit', password: 'Abcdefgh' },
    { label: 'no uppercase', password: 'abcdefgh1' },
    { label: 'no lowercase', password: 'ABCDEFGH1' },
    { label: 'empty', password: '' },
  ];

  for (const { label, password } of weakPasswords) {
    test(`TC-REG-004: weak password rejected - ${label} @regression`, async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.open();
      await loginPage.switchToRegister();
      await loginPage.registerUsernameInput.fill(DataGenerator.username());
      await loginPage.registerEmailInput.fill(DataGenerator.email());
      await loginPage.registerPasswordInput.fill(password);
      await loginPage.createAccountButton.click();
      // Either blocked client-side (native required/minlength) or server-side (400 -> toast) -
      // either way the user must not end up authenticated on the dashboard.
      await expect(page).toHaveURL(new RegExp(Routes.login));
    });
  }

  test('TC-REG-005: invalid email format is rejected by the native email input type @regression', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.switchToRegister();
    await loginPage.registerUsernameInput.fill(DataGenerator.username());
    await loginPage.registerEmailInput.fill('not-an-email');
    await loginPage.registerPasswordInput.fill(DataGenerator.strongPassword());
    await loginPage.createAccountButton.click();

    await expect(page).toHaveURL(new RegExp(Routes.login));
    const validity = await loginPage.registerEmailInput.evaluate((el: HTMLInputElement) => el.validity.valid);
    expect(validity).toBe(false);
  });

  test('TC-REG-006: a unicode username is rejected - usernames are ASCII-only by design @regression', async ({
    page,
  }) => {
    // Pinned to the real constraint: RegisterRequest.username has
    // @Pattern(regexp = "^[a-zA-Z0-9_.-]+$") - letters/digits/underscore/dot/dash only.
    const loginPage = new LoginPage(page);
    const username = `qa_ünïcödé_${Date.now().toString(36)}`;
    const password = DataGenerator.strongPassword();

    await loginPage.open();
    await loginPage.register(username, DataGenerator.email(), password);
    await expect(page).toHaveURL(new RegExp(Routes.login));
    await loginPage.expectToastVariant('danger');
  });

  test('TC-REG-007: an ASCII username with the full allowed character set (letters, digits, ., _, -) registers successfully @regression', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page);
    const username = `qa.User_${Date.now().toString(36)}-1`;
    const password = DataGenerator.strongPassword();

    await loginPage.open();
    await loginPage.register(username, DataGenerator.email(), password);
    await expect(page).toHaveURL(new RegExp(Routes.dashboard));
  });
});
