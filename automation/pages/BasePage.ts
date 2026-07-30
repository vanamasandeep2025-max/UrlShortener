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

  /**
   * The toast host is created lazily on first showToast() call. Scoped to the LAST
   * matching toast, not just any: showToast() appends each new toast to #toastHost and
   * each stays visible for 4s (see frontend/js/api.js), so two toasts from two actions
   * taken in quick succession (e.g. create a link, then immediately copy it) can easily
   * both still be showing at once - especially under slow-motion/manual-observation runs
   * where actions are deliberately spaced out less than that 4s window apart. The most
   * recently appended toast is always the one relevant to whatever the test just did.
   */
  toast(): Locator {
    return this.page.locator('.toast.show').last();
  }

  async expectToast(textSubstring: string): Promise<void> {
    await expect(this.toast()).toContainText(textSubstring, { timeout: 8_000 });
  }

  async expectToastVariant(variant: 'success' | 'danger'): Promise<void> {
    await expect(this.toast()).toHaveClass(new RegExp(`text-bg-${variant}`));
  }
}
