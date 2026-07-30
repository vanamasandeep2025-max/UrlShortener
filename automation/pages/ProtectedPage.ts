import { Locator, Page } from '@playwright/test';
import { BasePage } from './BasePage';
import { Routes } from '../constants/routes';

/** Maps to frontend/protected.html - the password gate for a password-protected short link. */
export class ProtectedPage extends BasePage {
  readonly passwordInput: Locator;
  readonly continueButton: Locator;
  readonly errorMessage: Locator;

  constructor(page: Page) {
    super(page);
    this.passwordInput = page.locator('#linkPassword');
    this.continueButton = page.locator('#verifyForm').getByRole('button', { name: 'Continue' });
    this.errorMessage = page.locator('#errorMsg');
  }

  async open(shortCode: string): Promise<void> {
    await this.goto(Routes.protected(shortCode));
  }

  async submitPassword(password: string): Promise<void> {
    await this.passwordInput.fill(password);
    await this.continueButton.click();
  }
}
