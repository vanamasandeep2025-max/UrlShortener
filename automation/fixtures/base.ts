import { test as base, expect, request as playwrightRequest, APIRequestContext, Page } from '@playwright/test';
import { env } from '../config/environment';
import { ApiClient } from '../api/ApiClient';
import { AuthResponse } from '../api/types';
import { Routes } from '../constants/routes';
import { createLogger } from '../utils/logger';

const logger = createLogger('fixtures');

const ACCESS_TOKEN_KEY = 'usp_access_token';
const REFRESH_TOKEN_KEY = 'usp_refresh_token';

export interface Fixtures {
  /** A raw APIRequestContext bound to API_BASE_URL, independent of whichever project's baseURL is active. */
  apiRequestContext: APIRequestContext;
  /** Unauthenticated ApiClient - call `.withToken(...)` for authenticated calls. */
  apiClient: ApiClient;
  /** Demo user's fresh access/refresh token pair, obtained via a real POST /auth/login. */
  demoTokens: AuthResponse;
  /** Admin user's fresh access/refresh token pair. */
  adminTokens: AuthResponse;
  /** A Page already logged in as the demo user (tokens seeded into localStorage, dashboard loaded). */
  authenticatedPage: Page;
  /** A Page already logged in as the admin user. */
  adminPage: Page;
}

async function seedTokensAndGoto(page: Page, tokens: AuthResponse, path: string): Promise<void> {
  // Navigate to a same-origin page first - localStorage can only be set for the current origin.
  await page.goto(Routes.login);
  await page.evaluate(
    ({ access, refresh, accessKey, refreshKey }) => {
      window.localStorage.setItem(accessKey, access);
      window.localStorage.setItem(refreshKey, refresh);
    },
    { access: tokens.accessToken, refresh: tokens.refreshToken, accessKey: ACCESS_TOKEN_KEY, refreshKey: REFRESH_TOKEN_KEY }
  );
  await page.goto(path);
}

export const test = base.extend<Fixtures>({
  apiRequestContext: async ({}, use) => {
    const context = await playwrightRequest.newContext({ baseURL: env.apiBaseUrl });
    await use(context);
    await context.dispose();
  },

  apiClient: async ({ apiRequestContext }, use) => {
    await use(new ApiClient(apiRequestContext));
  },

  demoTokens: async ({ apiClient }, use) => {
    const tokens = await apiClient.loginAndGetTokens(env.demoUsername, env.demoPassword);
    await use(tokens);
  },

  adminTokens: async ({ apiClient }, use) => {
    const tokens = await apiClient.loginAndGetTokens(env.adminUsername, env.adminPassword);
    await use(tokens);
  },

  authenticatedPage: async ({ page, demoTokens }, use) => {
    logger.info('Seeding demo session before navigation');
    await seedTokensAndGoto(page, demoTokens, Routes.dashboard);
    await use(page);
  },

  adminPage: async ({ page, adminTokens }, use) => {
    logger.info('Seeding admin session before navigation');
    await seedTokensAndGoto(page, adminTokens, Routes.dashboard);
    await use(page);
  },
});

export { expect };
