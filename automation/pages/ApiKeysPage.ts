import { Locator, Page } from '@playwright/test';
import { BasePage } from './BasePage';
import { Routes } from '../constants/routes';

/** Maps to frontend/api-keys.html (frontend/js/api-keys.js). */
export class ApiKeysPage extends BasePage {
  readonly keyNameInput: Locator;
  readonly createKeyButton: Locator;
  readonly newKeyAlert: Locator;
  readonly tableBody: Locator;

  constructor(page: Page) {
    super(page);
    this.keyNameInput = page.locator('#keyName');
    this.createKeyButton = page.locator('#createKeyForm').getByRole('button', { name: 'Create key' });
    this.newKeyAlert = page.locator('#newKeyAlert');
    this.tableBody = page.locator('#keysTableBody');
  }

  async open(): Promise<void> {
    await this.goto(Routes.apiKeys);
  }

  async createKey(name: string): Promise<void> {
    this.logger.info(`Creating API key "${name}"`);
    await this.keyNameInput.fill(name);
    await this.createKeyButton.click();
  }

  /** The plaintext secret is shown exactly once, inline in the success alert - never persisted client-side. */
  async plaintextKeyFromAlert(): Promise<string> {
    const text = await this.newKeyAlert.locator('code').textContent();
    if (!text) throw new Error('No plaintext key found in the creation alert');
    return text.trim();
  }

  rowFor(name: string): Locator {
    return this.tableBody.locator('tr', { hasText: name });
  }

  async revoke(name: string): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    const revokeResponsePromise = this.page.waitForResponse(
      (res) => res.url().includes('/api/v1/api-keys/') && res.request().method() === 'DELETE'
    );
    await this.rowFor(name).getByRole('button', { name: 'Revoke' }).click();
    // Waiting only for click() to resolve isn't enough: it resolves once the click event
    // dispatches, before the DELETE request api-keys.js#revokeKey() fires actually
    // completes. A caller checking the key's authentication state immediately afterward
    // (see TC-APIKEY-UI-003) would otherwise race the real revocation.
    await revokeResponsePromise;
  }
}
