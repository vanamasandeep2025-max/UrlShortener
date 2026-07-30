import { Locator, Page } from '@playwright/test';

/**
 * Component Object for a single row of the "Your links" table (frontend/js/app.js#renderRows).
 * Constructed by DashboardPage.rowFor(shortCode) - never instantiated directly by a test.
 */
export class UrlRow {
  readonly shortUrlLink: Locator;
  readonly copyButton: Locator;
  readonly originalUrlCell: Locator;
  readonly clickCountCell: Locator;
  readonly analyticsLink: Locator;
  readonly editExpiryButton: Locator;
  readonly deleteButton: Locator;
  readonly protectedBadge: Locator;
  readonly expiredBadge: Locator;

  constructor(
    private readonly page: Page,
    readonly row: Locator
  ) {
    this.shortUrlLink = row.locator('a.short-url-chip');
    this.copyButton = row.getByRole('button', { name: 'Copy' });
    this.originalUrlCell = row.locator('td').nth(1);
    this.clickCountCell = row.locator('td').nth(2);
    this.analyticsLink = row.getByRole('link', { name: 'Analytics' });
    this.editExpiryButton = row.getByRole('button', { name: 'Edit expiry' });
    this.deleteButton = row.getByRole('button', { name: 'Delete' });
    this.protectedBadge = row.locator('.badge', { hasText: 'protected' });
    this.expiredBadge = row.locator('.badge', { hasText: 'expired' });
  }

  async shortCodeFromHref(): Promise<string> {
    const href = await this.shortUrlLink.getAttribute('href');
    if (!href) throw new Error('Row has no short URL link');
    return new URL(href).pathname.replace(/^\//, '');
  }

  async clickCount(): Promise<number> {
    return Number(await this.clickCountCell.textContent());
  }

  /** Accepts the native confirm() dialog raised by deleteUrl() in frontend/js/app.js. */
  async delete(): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    await this.deleteButton.click();
  }

  /** Exercises the "cancel" branch of the same confirm() dialog - the row must remain afterward. */
  async cancelDelete(): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.dismiss());
    await this.deleteButton.click();
  }

  async copyToClipboard(): Promise<void> {
    await this.copyButton.click();
  }

  async openEditExpiry(): Promise<void> {
    await this.editExpiryButton.click();
  }

  async openAnalytics(): Promise<void> {
    await this.analyticsLink.click();
  }
}
