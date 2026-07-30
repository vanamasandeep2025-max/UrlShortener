import { Locator, Page } from '@playwright/test';
import { BasePage } from './BasePage';
import { Routes } from '../constants/routes';

/** Maps 1:1 to frontend/login.html - a single page with a login/register tab toggle, not two routes. */
export class LoginPage extends BasePage {
  readonly loginTab: Locator;
  readonly registerTab: Locator;

  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly signInButton: Locator;

  readonly registerUsernameInput: Locator;
  readonly registerEmailInput: Locator;
  readonly registerPasswordInput: Locator;
  readonly createAccountButton: Locator;

  constructor(page: Page) {
    super(page);
    const authTabs = page.locator('#authTabs');
    this.loginTab = authTabs.getByRole('button', { name: 'Sign in', exact: true });
    this.registerTab = authTabs.getByRole('button', { name: 'Register', exact: true });

    this.usernameInput = page.locator('#loginUsername');
    this.passwordInput = page.locator('#loginPassword');
    this.signInButton = page.locator('#loginForm').getByRole('button', { name: 'Sign in' });

    this.registerUsernameInput = page.locator('#registerUsername');
    this.registerEmailInput = page.locator('#registerEmail');
    this.registerPasswordInput = page.locator('#registerPassword');
    this.createAccountButton = page.locator('#registerForm').getByRole('button', { name: 'Create account' });
  }

  async open(): Promise<void> {
    await this.goto(Routes.login);
  }

  async switchToRegister(): Promise<void> {
    await this.registerTab.click();
  }

  async login(username: string, password: string): Promise<void> {
    this.logger.info(`Logging in as "${username}"`);
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.signInButton.click();
  }

  async register(username: string, email: string, password: string): Promise<void> {
    this.logger.info(`Registering "${username}"`);
    await this.switchToRegister();
    await this.registerUsernameInput.fill(username);
    await this.registerEmailInput.fill(email);
    await this.registerPasswordInput.fill(password);
    await this.createAccountButton.click();
  }
}
