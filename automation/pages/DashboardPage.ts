import { Locator, Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';
import { UrlRow } from '../components/UrlRow';
import { Routes } from '../constants/routes';
import { UrlStatusFilter } from '../api/types';

/** Maps to frontend/index.html - the "Your links" dashboard: create form, table, search/filter/pagination. */
export class DashboardPage extends BasePage {
  readonly currentUserLabel: Locator;
  readonly logoutButton: Locator;

  readonly urlInput: Locator;
  readonly expiryInput: Locator;
  readonly passwordInput: Locator;
  readonly shortenButton: Locator;

  readonly searchInput: Locator;
  readonly statusFilter: Locator;
  readonly tableBody: Locator;
  readonly pagination: Locator;

  readonly expiryModal: Locator;
  readonly expiryModalInput: Locator;
  readonly saveExpiryButton: Locator;

  constructor(page: Page) {
    super(page);
    this.currentUserLabel = page.locator('#currentUserLabel');
    this.logoutButton = page.locator('#logoutBtn');

    this.urlInput = page.locator('#urlInput');
    this.expiryInput = page.locator('#expiryInput');
    this.passwordInput = page.locator('#passwordInput');
    this.shortenButton = page.locator('#createForm').getByRole('button', { name: 'Shorten' });

    this.searchInput = page.locator('#searchInput');
    this.statusFilter = page.locator('#statusFilter');
    this.tableBody = page.locator('#urlsTableBody');
    this.pagination = page.locator('#pagination');

    this.expiryModal = page.locator('#expiryModal');
    this.expiryModalInput = page.locator('#expiryModalInput');
    this.saveExpiryButton = page.locator('#saveExpiryBtn');
  }

  async open(): Promise<void> {
    await this.goto(Routes.dashboard);
  }

  async createUrl(url: string, options: { expiryLocalDateTime?: string; password?: string } = {}): Promise<void> {
    this.logger.info(`Creating URL: ${url}`);
    await this.urlInput.fill(url);
    if (options.expiryLocalDateTime) {
      await this.expiryInput.fill(options.expiryLocalDateTime);
    }
    if (options.password) {
      await this.passwordInput.fill(options.password);
    }
    await this.shortenButton.click();
  }

  async search(term: string): Promise<void> {
    await this.searchInput.fill(term);
    // app.js debounces input by 350ms before firing the list request.
    await this.page.waitForTimeout(450);
    await this.waitForTableSettled();
  }

  async filterByStatus(status: UrlStatusFilter): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (res) => res.url().includes('/api/v1/urls') && res.request().method() === 'GET'
    );
    await this.statusFilter.selectOption(status);
    await responsePromise;
  }

  async goToPage(pageNumber: number): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (res) => res.url().includes('/api/v1/urls') && res.request().method() === 'GET'
    );
    await this.pagination.getByRole('button', { name: String(pageNumber + 1), exact: true }).click();
    await responsePromise;
  }

  /** Rows render async after a fetch; wait for the "Loading..." placeholder row to be gone. */
  async waitForTableSettled(): Promise<void> {
    await expect(this.tableBody.getByText('Loading...')).toBeHidden();
  }

  rows(): Locator {
    return this.tableBody.locator('tr').filter({ hasNot: this.page.locator('td[colspan]') });
  }

  async rowCount(): Promise<number> {
    return this.rows().count();
  }

  rowFor(shortCode: string): UrlRow {
    const row = this.tableBody.locator('tr', { has: this.page.locator(`a.short-url-chip[href*="/${shortCode}"]`) });
    return new UrlRow(this.page, row);
  }

  emptyStateMessage(): Locator {
    return this.tableBody.getByText('No links yet');
  }

  async fillAndSaveExpiry(localDateTime: string | null): Promise<void> {
    if (localDateTime === null) {
      await this.expiryModalInput.fill('');
    } else {
      await this.expiryModalInput.fill(localDateTime);
    }
    await this.saveExpiryButton.click();
  }

  async logout(): Promise<void> {
    await this.logoutButton.click();
  }
}
