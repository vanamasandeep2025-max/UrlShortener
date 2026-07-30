import { Locator, Page, expect } from '@playwright/test';
import { createLogger } from '../utils/logger';

/**
 * Shared behaviour across every page object: navigation logging and the toast
 * assertion, since every page in this app surfaces success/error feedback through
 * the same Bootstrap toast component (see frontend/js/api.js#showToast).
 */
export abstract class BasePage {
  protected readonly logger = createLogger(this.constructor.name);

  constructor(protected readonly page: Page) {}

  async goto(path: string): Promise<void> {
    this.logger.info(`Navigating to ${path}`);
    await this.page.goto(path);
  }

  /** The toast host is created lazily on first showToast() call - scope the locator to whatever's live now. */
  toast(): Locator {
    return this.page.locator('.toast.show');
  }

  async expectToast(textSubstring: string): Promise<void> {
    await expect(this.toast()).toContainText(textSubstring, { timeout: 8_000 });
  }

  async expectToastVariant(variant: 'success' | 'danger'): Promise<void> {
    await expect(this.toast()).toHaveClass(new RegExp(`text-bg-${variant}`));
  }
}
